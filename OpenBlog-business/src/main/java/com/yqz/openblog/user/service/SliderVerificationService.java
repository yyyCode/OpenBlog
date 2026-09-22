package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.config.ClientIpResolver;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.user.dto.SliderChallengeResponse;
import com.yqz.openblog.user.dto.SliderCompleteRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 滑动验证码：签发缺口图 → 校验落点与拖动轨迹 → 写入一次性通过标记供发码消费。
 * <p>
 * 安全模型三件套，缺一即可被绕过：
 * <ol>
 *   <li><b>坐标容差</b>——缺口 x 由服务端随机生成且只存 Redis，客户端必须从底图像素解出，
 *       容差内才算命中（盲猜命中率约 3.4%）</li>
 *   <li><b>一次性消费</b>——pending 用 getAndDelete 取，ok 标记用 getAndDelete 取，均不可重放</li>
 *   <li><b>轨迹校验</b>——识别"算出 x 后瞬移过去"的机器行为（时长 / 点数 / 非匀速）</li>
 * </ol>
 * 强度上限为「挡脚本、不挡打码平台」：缺口位置的安全性来自服务端存真值，
 * 而能识别图像的服务天然能解出 x。轨迹校验是启发式的，不是强保证。
 */
@Service
public class SliderVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SliderVerificationService.class);

    /**
     * 轨迹「总路径 / 净位移」上限。刻意不做逐段回退拒绝：人手拖过头再拉回来
     * （overshoot-correct）是常见行为，按段判定会误伤正常用户。人手拖动的比值约 1.0~1.5，
     * 来回锯齿的机器轨迹远大于此，3 倍足以区分。
     */
    private static final double MAX_PATH_TO_NET_RATIO = 3.0;

    private final RedisOps redisOps;
    private final AuthSecurityProperties authSecurityProperties;
    private final SliderImageGenerator imageGenerator;

    @Autowired
    public SliderVerificationService(RedisOps redisOps, AuthSecurityProperties authSecurityProperties) {
        this(redisOps, authSecurityProperties, new SliderImageGenerator());
    }

    /** 测试专用：注入可播种的图片生成器以复现图形。 */
    SliderVerificationService(RedisOps redisOps,
                              AuthSecurityProperties authSecurityProperties,
                              SliderImageGenerator imageGenerator) {
        this.redisOps = redisOps;
        this.authSecurityProperties = authSecurityProperties;
        this.imageGenerator = imageGenerator;
    }

    /**
     * 签发票据与图片。缺口真值坐标只落 Redis（key 值形如 {@code x:ipSeg}），绝不回传客户端。
     * <p>
     * 值用 {@code :} 分隔而非 JSON：状态只有两个标量，且 {@link ClientIpResolver#toRedisKeySegment}
     * 已把 IP 中的 {@code :} 替换为 {@code _}，故分隔符不会与内容冲突。有效期由 Redis TTL 保证，
     * 无需另存时间戳。
     */
    public SliderChallengeResponse issue(HttpServletRequest request) {
        SliderChallengeResponse r = new SliderChallengeResponse();
        if (!authSecurityProperties.getSlider().isEnabled()) {
            r.setEnabled(false);
            return r;
        }
        SliderImageGenerator.SliderImage image = imageGenerator.generate();
        String id = UUID.randomUUID().toString();
        redisOps.set(RedisKeys.sliderPending(id), image.x() + ":" + ipSegment(request),
                Duration.ofSeconds(ttlSeconds()));
        r.setEnabled(true);
        r.setChallengeId(id);
        r.setBackground(image.backgroundDataUri());
        r.setSlider(image.pieceDataUri());
        r.setSliderY(image.y());
        return r;
    }

    /**
     * 校验落点与轨迹，通过则写入一次性 ok 标记。
     * 所有失败分支统一 4001，不区分"坐标错"与"轨迹可疑"——避免给攻击者反馈信号。
     */
    public void complete(HttpServletRequest request, SliderCompleteRequest req) {
        AuthSecurityProperties.Slider cfg = authSecurityProperties.getSlider();
        if (!cfg.isEnabled()) {
            return;
        }
        String id = req == null || req.getChallengeId() == null ? null : req.getChallengeId().trim();
        if (id == null || id.isEmpty()) {
            throw new BizException(4001, "缺少验证凭证");
        }

        // 一次性取走：同一 challengeId 无法二次 complete，天然抗重放
        String raw = redisOps.getAndDelete(RedisKeys.sliderPending(id)).orElse(null);
        if (raw == null) {
            throw new BizException(4001, "验证已失效，请刷新重试");
        }
        int sep = raw.indexOf(':');
        if (sep <= 0) {
            // 值形态异常（理论上不会出现），按失效处理而非猜测
            log.warn("滑块 challenge 值形态异常。id={}", id);
            throw new BizException(4001, "验证已失效，请刷新重试");
        }
        if (!raw.substring(sep + 1).equals(ipSegment(request))) {
            throw new BizException(4001, "验证环境异常，请刷新重试");
        }

        Integer claimedX = req.getX();
        if (claimedX == null || !withinTolerance(claimedX, raw)) {
            throw new BizException(4001, "验证未通过，请重试");
        }
        if (!trailLooksHuman(req.getTrail(), cfg)) {
            throw new BizException(4001, "验证未通过，请重试");
        }

        redisOps.set(RedisKeys.sliderOk(id), "1", Duration.ofSeconds(ttlSeconds()));
    }

    /**
     * 发码前校验：必须已调用 complete，且仅能使用一次。
     * <p>
     * 策略（有意 fail-closed）：通过与否依赖 Redis 中一次性 ok 标记判定，Redis 不可用时一律视为
     * 「未验证」直接拒绝。与网关限流 / 幂等框架的 fail-open 不同——那是为避免整个站点不可用而放行；
     * 滑块是发码门禁，放行等于绕过验证，故取保守侧。
     * （曾试图用 Redis 健康检查做故障放行，但 {@link RedisOps} 是 fail-safe 封装、异常不外抛，
     * 无法区分「Redis 故障」与「无该键」，该分支永不生效，已移除。）
     */
    public void verifyAndConsume(String sliderChallengeId) {
        if (!authSecurityProperties.getSlider().isEnabled()) {
            return;
        }
        if (sliderChallengeId == null || sliderChallengeId.isBlank()) {
            throw new BizException(4001, "请先完成滑动验证");
        }
        String ok = redisOps.getAndDelete(RedisKeys.sliderOk(sliderChallengeId.trim())).orElse(null);
        if (ok == null) {
            throw new BizException(4001, "请先完成滑动验证");
        }
    }

    private boolean withinTolerance(int claimedX, String raw) {
        int targetX;
        try {
            targetX = Integer.parseInt(raw.substring(0, raw.indexOf(':')));
        } catch (NumberFormatException e) {
            log.warn("滑块 challenge 坐标无法解析。raw={}", raw);
            return false;
        }
        return Math.abs(claimedX - targetX) <= authSecurityProperties.getSlider().getTolerancePx();
    }

    /**
     * 轨迹启发式判定。任一条件不满足即判为机器：采样点足够多、耗时在人类区间内、
     * 不是来回锯齿、速度有起伏（拒绝匀速直线）。
     * <p>
     * 刻意<b>不</b>做逐段回退拒绝：人手拖过头再拉回来是常见行为，按段判定会误伤正常用户，
     * 改用总路径与净位移之比兜住锯齿。速度取绝对值后再算变异系数，避免回退段的负速度
     * 把均值拉向 0 使 CV 失真。
     * <p>
     * 速度样本少于 2 段时无从判断波动，直接拒绝——这是 fail-closed 取舍，
     * 也意味着 {@code minTrailPoints} 配成 &lt; 3 会让所有验证都失败。
     */
    private boolean trailLooksHuman(List<SliderCompleteRequest.TrailPoint> trail,
                                    AuthSecurityProperties.Slider cfg) {
        if (trail == null || trail.size() < cfg.getMinTrailPoints()) {
            return false;
        }
        for (SliderCompleteRequest.TrailPoint p : trail) {
            if (p == null || p.getX() == null || p.getT() == null) {
                return false;
            }
        }

        long duration = trail.get(trail.size() - 1).getT() - trail.get(0).getT();
        if (duration < cfg.getMinDurationMs() || duration > cfg.getMaxDurationMs()) {
            return false;
        }

        List<Double> speeds = new ArrayList<>(trail.size() - 1);
        double path = 0d;
        for (int i = 1; i < trail.size(); i++) {
            int dx = trail.get(i).getX() - trail.get(i - 1).getX();
            long dt = trail.get(i).getT() - trail.get(i - 1).getT();
            if (dt <= 0) {
                return false;
            }
            path += Math.abs(dx);
            speeds.add(Math.abs(dx) / (double) dt);
        }
        if (speeds.size() < 2) {
            return false;
        }

        double net = Math.abs(trail.get(trail.size() - 1).getX() - trail.get(0).getX());
        if (net <= 0d || path > net * MAX_PATH_TO_NET_RATIO) {
            return false;
        }

        double mean = speeds.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        if (mean <= 0d) {
            return false;
        }
        double variance = 0d;
        for (double s : speeds) {
            variance += (s - mean) * (s - mean);
        }
        double cv = Math.sqrt(variance / (speeds.size() - 1)) / mean;
        return cv >= cfg.getMinSpeedCv();
    }

    private int ttlSeconds() {
        return Math.max(30, authSecurityProperties.getSlider().getTtlSeconds());
    }

    private static String ipSegment(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        return ClientIpResolver.toRedisKeySegment(ip);
    }
}
