package com.yqz.openblog.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.message.api.NotificationRpcService;
import com.yqz.openblog.message.api.NotificationSendResult;
import com.yqz.openblog.notification.NotificationChannelType;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserMapper;
import com.yqz.openblog.user.validator.EmailValidator;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮箱注册验证码：生成 / 发送（经 Dubbo 调 email 服务）/ 校验。
 * <p>
 * 采用「验证码前置」模型：先发码再建号，验证码以邮箱为键存 Redis
 * （注册前用户尚未创建，邮箱即注册身份；建号后 users.email ↔ id 关联自然建立）。
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

    private static final String SUBJECT = "OpenBlog 注册验证码";

    private final RedisOps redisOps;
    private final AuthSecurityProperties authSecurityProperties;
    private final UserMapper userMapper;
    private final EmailValidator emailValidator;

    /** Dubbo 调统一通知服务。retries=0：同步链路 messageId 为空 → 渠道每次生成新幂等键，重试会重复发信。 */
    @DubboReference(retries = 0, timeout = 5000)
    private NotificationRpcService notificationRpcService;

    public EmailCodeService(RedisOps redisOps,
                            AuthSecurityProperties authSecurityProperties,
                            UserMapper userMapper,
                            EmailValidator emailValidator) {
        this.redisOps = redisOps;
        this.authSecurityProperties = authSecurityProperties;
        this.userMapper = userMapper;
        this.emailValidator = emailValidator;
    }

    /**
     * 发送注册验证码到指定邮箱，返回冷却秒数（供前端倒计时）。
     * 前置流程：邮箱格式白名单 → 邮箱未注册 → 冷却检查 → 生成 6 位码入 Redis → 通知层发信（Email 渠道 → Dubbo）。
     * 发信失败时删除已落库的验证码与冷却键，让用户可立即重试。
     */
    public int sendCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);

        String emailError = emailValidator.validate(email);
        if (emailError != null) {
            throw new BizException(4000, emailError);
        }

        if (userMapper.selectCount(Wrappers.lambdaQuery(User.class)
                .eq(User::getEmail, email)) > 0) {
            throw new BizException(4090, "该邮箱已注册，请直接登录");
        }

        AuthSecurityProperties.EmailCode cfg = authSecurityProperties.getEmailCode();

        String cooldownKey = RedisKeys.emailCooldown(email);
        if (redisOps.hasKey(cooldownKey)) {
            throw new BizException(4293, "发送过于频繁，请稍后再试");
        }

        // 复用未过期验证码：重发不生成新码，避免旧邮件里的验证码被作废造成混淆。
        String codeKey = RedisKeys.emailCode(email);
        String code = redisOps.get(codeKey).orElse(null);
        if (code == null) {
            code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        }
        // 无论新生成还是复用，都刷新验证码 TTL（从本次发送重新计 5 分钟）。
        // 先落库再发信：即便发信失败也保留冷却，防止滥用。
        redisOps.set(codeKey, code, Duration.ofSeconds(Math.max(30, cfg.getCodeTtlSeconds())));
        redisOps.set(cooldownKey, "1", Duration.ofSeconds(Math.max(10, cfg.getResendCooldownSeconds())));

        // 经 Dubbo 调 message 模块统一通知服务，EMAIL 渠道在 message 内部路由投递。
        // 幂等保障由渠道内部完成（retries=0 + 幂等键 + email_records 唯一索引），
        // 见 EmailService.send 幂等逻辑与 docs/dev-experiences.md。
        try {
            NotificationSendResult result = notificationRpcService.submit(NotificationMessage.builder()
                    .channel(NotificationChannelType.EMAIL)
                    .recipient(email)
                    .subject(SUBJECT)
                    .templateCode(NotificationRpcService.TEMPLATE_REGISTER_VERIFICATION_CODE)
                    .params(Map.of("code", code))
                    .build());
            if (!result.isSuccess()) {
                throw new BizException(result.getErrorCode(), result.getErrorMsg());
            }
        } catch (BizException e) {
            // 发送失败（通知服务返回 5002 等）：清理验证码与冷却，允许立即重试。
            redisOps.delete(codeKey);
            redisOps.delete(cooldownKey);
            log.warn("发送注册验证码失败。email={}", email, e);
            throw e;
        } catch (Exception e) {
            // message 服务不可达 / Dubbo 传输异常：同样清理后按 5002 降级，避免脏状态。
            redisOps.delete(codeKey);
            redisOps.delete(cooldownKey);
            log.warn("Dubbo 调用通知服务失败。email={}", email, e);
            throw new BizException(NotificationRpcService.ERROR_CODE_EMAIL_UNAVAILABLE, "邮件服务暂不可用，请稍后再试");
        }

        return cfg.getResendCooldownSeconds();
    }

    /**
     * 注册前校验验证码：一次性消费 + 错误次数限制。
     * <p>
     * 成功路径为 get+delete 两步（RedisOps 无「比较后删除」原语）；并发双提交可能都读到同一验证码，
     * 但最终由注册流程的邮箱唯一性兜底——同邮箱只会建一个号，因此该竞态无实际危害。
     * 刻意不用 getAndDelete：错误提交会误删掉正确的验证码。
     */
    public void verifyAndConsume(String rawEmail, String code) {
        String email = normalizeEmail(rawEmail);
        if (code == null || code.isBlank()) {
            throw new BizException(4003, "请输入邮箱验证码");
        }

        AuthSecurityProperties.EmailCode cfg = authSecurityProperties.getEmailCode();
        String codeKey = RedisKeys.emailCode(email);
        String attemptKey = RedisKeys.emailAttempt(email);

        String stored = redisOps.get(codeKey).orElse(null);
        if (stored == null) {
            throw new BizException(4003, "验证码错误或已过期，请重新获取");
        }

        if (!stored.equals(code)) {
            Long attempts = redisOps.increment(attemptKey).orElse(null);
            if (attempts != null && attempts == 1L) {
                redisOps.expire(attemptKey, Duration.ofSeconds(Math.max(30, cfg.getCodeTtlSeconds())));
            }
            if (attempts != null && attempts >= cfg.getMaxVerifyAttempts()) {
                redisOps.delete(codeKey);
                redisOps.delete(attemptKey);
                throw new BizException(4004, "验证码错误次数过多，请重新获取");
            }
            throw new BizException(4002, "验证码错误，请重新输入");
        }

        // 一次性消费
        redisOps.delete(codeKey);
        redisOps.delete(attemptKey);
    }

    private String normalizeEmail(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
