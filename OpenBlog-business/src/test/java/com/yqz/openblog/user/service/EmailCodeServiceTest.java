package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.message.api.NotificationRpcService;
import com.yqz.openblog.message.api.NotificationSendResult;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.user.repo.UserMapper;
import com.yqz.openblog.user.validator.EmailValidator;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailCodeServiceTest {

    @Mock private RedisOps redisOps;
    @Spy private AuthSecurityProperties authSecurityProperties = new AuthSecurityProperties();
    @Mock private UserMapper userMapper;
    @Mock private EmailValidator emailValidator;
    @Mock private NotificationRpcService notificationRpcService;

    private EmailCodeService emailCodeService;

    @BeforeEach
    void setUp() throws Exception {
        emailCodeService = new EmailCodeService(redisOps, authSecurityProperties, userMapper, emailValidator);
        // @DubboReference 字段为私有 field 注入（无 setter、不在构造器），手动反射注入 mock。
        Field field = EmailCodeService.class.getDeclaredField("notificationRpcService");
        field.setAccessible(true);
        field.set(emailCodeService, notificationRpcService);
    }

    private void stubPreconditions() {
        when(emailValidator.validate(anyString())).thenReturn(null);
        when(userMapper.selectCount(any())).thenReturn(0L);
        // 冷却占位成功 = 本次可发信（SETNX 返回 true）
        when(redisOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(redisOps.get(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void sendCode_cooldownAlreadyHeld_rejectsWithoutSending() {
        // 冷却占位失败（SETNX false）→ 4293，且完全不触碰通知服务。
        // 这是并发重复发信的闸门：原先 hasKey + set 两步会让并发请求各发一封信。
        when(emailValidator.validate(anyString())).thenReturn(null);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(redisOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> emailCodeService.sendCode("a@example.com", EmailCodeService.PURPOSE_REGISTER));

        assertEquals(4293, ex.getCode());
        verify(redisOps).setIfAbsent(eq(RedisKeys.emailCooldown("a@example.com")), eq("1"), any());
        verify(notificationRpcService, never()).submit(any(NotificationMessage.class));
    }

    @Test
    void sendCode_failResult_mapsToBizExceptionAndCleansKeys() {
        stubPreconditions();
        when(notificationRpcService.submit(any(NotificationMessage.class)))
                .thenReturn(NotificationSendResult.fail(5002, "邮件服务暂不可用，请稍后再试"));

        BizException ex = assertThrows(BizException.class,
                () -> emailCodeService.sendCode("a@example.com", EmailCodeService.PURPOSE_REGISTER));

        assertEquals(5002, ex.getCode());
        verify(redisOps).delete(RedisKeys.emailCode("a@example.com"));
        verify(redisOps).delete(RedisKeys.emailCooldown("a@example.com"));
    }

    @Test
    void sendCode_transportFailure_mapsTo5002AndCleansKeys() {
        stubPreconditions();
        when(notificationRpcService.submit(any(NotificationMessage.class)))
                .thenThrow(new RpcException("No provider"));

        BizException ex = assertThrows(BizException.class,
                () -> emailCodeService.sendCode("a@example.com", EmailCodeService.PURPOSE_REGISTER));

        assertEquals(NotificationRpcService.ERROR_CODE_EMAIL_UNAVAILABLE, ex.getCode());
        verify(redisOps).delete(RedisKeys.emailCode("a@example.com"));
        verify(redisOps).delete(RedisKeys.emailCooldown("a@example.com"));
    }

    @Test
    void sendCode_success_returnsCooldownAndDoesNotDeleteKeys() {
        stubPreconditions();
        when(notificationRpcService.submit(any(NotificationMessage.class))).thenReturn(NotificationSendResult.ok());

        int seconds = emailCodeService.sendCode("a@example.com", EmailCodeService.PURPOSE_REGISTER);

        assertEquals(authSecurityProperties.getEmailCode().getResendCooldownSeconds(), seconds);
        verify(redisOps, never()).delete(anyString());
    }

    @Test
    void sendCode_resetPurpose_requiresRegisteredEmail() {
        // 找回密码用途：邮箱未注册（selectCount=0）→ 拒绝，防匿名枚举撞号。
        // 该方法在 redis 冷却检查前抛错，只 stub 走到的那两步，避免 UnnecessaryStubbing。
        when(emailValidator.validate(anyString())).thenReturn(null);
        when(userMapper.selectCount(any())).thenReturn(0L);

        BizException ex = assertThrows(BizException.class,
                () -> emailCodeService.sendCode("a@example.com", EmailCodeService.PURPOSE_RESET));

        assertEquals(4090, ex.getCode());
    }

    @Test
    void sendCode_resetPurpose_registeredEmail_usesResetTemplate() {
        // 找回密码用途：邮箱已注册 → 用 reset 模板与主题发信
        stubPreconditions();
        when(userMapper.selectCount(any())).thenReturn(1L);
        when(notificationRpcService.submit(any(NotificationMessage.class))).thenReturn(NotificationSendResult.ok());

        emailCodeService.sendCode("a@example.com", EmailCodeService.PURPOSE_RESET);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationRpcService).submit(captor.capture());
        assertEquals(NotificationRpcService.TEMPLATE_RESET_VERIFICATION_CODE, captor.getValue().getTemplateCode());
        assertEquals("OpenBlog 找回密码验证码", captor.getValue().getSubject());
    }
}
