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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(redisOps.hasKey(anyString())).thenReturn(false);
        when(redisOps.get(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void sendCode_failResult_mapsToBizExceptionAndCleansKeys() {
        stubPreconditions();
        when(notificationRpcService.submit(any(NotificationMessage.class)))
                .thenReturn(NotificationSendResult.fail(5002, "邮件服务暂不可用，请稍后再试"));

        BizException ex = assertThrows(BizException.class, () -> emailCodeService.sendCode("a@example.com"));

        assertEquals(5002, ex.getCode());
        verify(redisOps).delete(RedisKeys.emailCode("a@example.com"));
        verify(redisOps).delete(RedisKeys.emailCooldown("a@example.com"));
    }

    @Test
    void sendCode_transportFailure_mapsTo5002AndCleansKeys() {
        stubPreconditions();
        when(notificationRpcService.submit(any(NotificationMessage.class)))
                .thenThrow(new RpcException("No provider"));

        BizException ex = assertThrows(BizException.class, () -> emailCodeService.sendCode("a@example.com"));

        assertEquals(NotificationRpcService.ERROR_CODE_EMAIL_UNAVAILABLE, ex.getCode());
        verify(redisOps).delete(RedisKeys.emailCode("a@example.com"));
        verify(redisOps).delete(RedisKeys.emailCooldown("a@example.com"));
    }

    @Test
    void sendCode_success_returnsCooldownAndDoesNotDeleteKeys() {
        stubPreconditions();
        when(notificationRpcService.submit(any(NotificationMessage.class))).thenReturn(NotificationSendResult.ok());

        int seconds = emailCodeService.sendCode("a@example.com");

        assertEquals(authSecurityProperties.getEmailCode().getResendCooldownSeconds(), seconds);
        verify(redisOps, never()).delete(anyString());
    }
}
