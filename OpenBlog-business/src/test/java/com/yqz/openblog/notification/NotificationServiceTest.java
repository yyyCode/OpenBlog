package com.yqz.openblog.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.notification.outbox.NotificationOutbox;
import com.yqz.openblog.notification.outbox.NotificationOutboxMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    private final ChannelRegistry registry = mock(ChannelRegistry.class);
    private final NotificationProperties properties = new NotificationProperties();
    private final NotificationOutboxMapper outboxMapper = mock(NotificationOutboxMapper.class);

    private NotificationService newService() {
        return new NotificationService(registry, properties, outboxMapper, new ObjectMapper());
    }

    private NotificationMessage emailMsg() {
        return NotificationMessage.builder()
                .channel(NotificationChannelType.EMAIL)
                .recipient("a@b.com")
                .templateCode(NotificationTemplateService.REGISTER_VERIFICATION_CODE)
                .build();
    }

    @Test
    void submitAsyncGeneratesMessageIdAndInsertsPending() {
        NotificationMessage message = emailMsg();

        newService().submitAsync(message);

        assertNotNull(message.getMessageId());
        assertFalse(message.getMessageId().isBlank(), "submitAsync 应生成 messageId");

        // argThat 需显式类型见证：MyBatis-Plus 3.5.16 BaseMapper 有 insert(T) / insert(Collection<T>) 两个重载，
        // 泛型推断在两者间歧义，显式钉死 T=NotificationOutbox。
        verify(outboxMapper).insert(org.mockito.ArgumentMatchers.<NotificationOutbox>argThat(row -> {
            assertEquals(message.getMessageId(), row.getMessageId(), "outbox 应记录同一 messageId");
            assertEquals(NotificationOutbox.STATUS_PENDING, row.getStatus());
            return true;
        }));
    }

    @Test
    void submitAsyncDisabledFailsClosed() {
        properties.getOutbox().setEnabled(false);

        BizException e = assertThrows(BizException.class, () -> newService().submitAsync(emailMsg()));
        assertEquals(4000, e.getCode());
        verify(outboxMapper, never()).insert(any(NotificationOutbox.class));
    }

    @Test
    void submitAsyncKeepsProvidedMessageId() {
        NotificationMessage message = emailMsg();
        message.setMessageId("pre-set-id");

        newService().submitAsync(message);

        assertEquals("pre-set-id", message.getMessageId(), "已有 messageId 不应被覆盖");
        verify(outboxMapper).insert(org.mockito.ArgumentMatchers.<NotificationOutbox>argThat(
                row -> "pre-set-id".equals(row.getMessageId())));
    }
}
