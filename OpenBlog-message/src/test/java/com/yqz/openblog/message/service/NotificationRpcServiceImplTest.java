package com.yqz.openblog.message.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.message.api.NotificationRpcService;
import com.yqz.openblog.message.api.NotificationSendResult;
import com.yqz.openblog.notification.NotificationChannelType;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class NotificationRpcServiceImplTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationRpcServiceImpl rpcService;

    private static NotificationMessage message() {
        return NotificationMessage.builder()
                .channel(NotificationChannelType.EMAIL)
                .recipient("a@example.com")
                .subject("OpenBlog")
                .templateCode("register-verification-code")
                .build();
    }

    @Test
    void submit_success_returnsOk() {
        doNothing().when(notificationService).submit(any(NotificationMessage.class));

        NotificationSendResult result = rpcService.submit(message());

        assertTrue(result.isSuccess());
    }

    @Test
    void submit_bizException_mapsToFailWithCodeAndMsg() {
        doThrow(new BizException(5002, "邮件服务暂不可用，请稍后再试"))
                .when(notificationService).submit(any(NotificationMessage.class));

        NotificationSendResult result = rpcService.submit(message());

        assertFalse(result.isSuccess());
        assertEquals(5002, result.getErrorCode());
        assertEquals("邮件服务暂不可用，请稍后再试", result.getErrorMsg());
    }

    @Test
    void submit_nullMessage_mapsFail() {
        doThrow(new BizException(4000, "未配置发送渠道"))
                .when(notificationService).submit(null);

        NotificationSendResult result = rpcService.submit(null);

        assertFalse(result.isSuccess());
        assertEquals(4000, result.getErrorCode());
    }

    @Test
    void submit_unexpectedException_mapsToInternalError() {
        doThrow(new RuntimeException("db down"))
                .when(notificationService).submit(any(NotificationMessage.class));

        NotificationSendResult result = rpcService.submit(message());

        assertFalse(result.isSuccess());
        assertEquals(NotificationRpcService.ERROR_CODE_INTERNAL, result.getErrorCode());
    }
}
