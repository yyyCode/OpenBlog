package com.yqz.openblog.notification.outbox;

import com.yqz.openblog.notification.NotificationChannelType;
import com.yqz.openblog.notification.NotificationMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationOutboxTest {

    @Test
    void fromMessageMapsFields() {
        NotificationMessage message = NotificationMessage.builder()
                .messageId("msg-1")
                .channel(NotificationChannelType.EMAIL)
                .recipient("a@b.com")
                .subject("主题")
                .templateCode("register-verification-code")
                .build();

        NotificationOutbox row = NotificationOutbox.from(message, "{\"code\":\"123456\"}");

        assertEquals("msg-1", row.getMessageId());
        assertEquals("EMAIL", row.getChannel());
        assertEquals("a@b.com", row.getRecipient());
        assertEquals("主题", row.getSubject());
        assertEquals("register-verification-code", row.getTemplateCode());
        assertEquals("{\"code\":\"123456\"}", row.getParamsJson());
        assertEquals(NotificationOutbox.STATUS_PENDING, row.getStatus());
        assertEquals(0, row.getRetryCount());
        assertNull(row.getId(), "id 由 DB 自增");
    }
}
