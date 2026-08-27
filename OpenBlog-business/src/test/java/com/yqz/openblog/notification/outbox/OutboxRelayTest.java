package com.yqz.openblog.notification.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.notification.NotificationChannelType;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationProperties;
import com.yqz.openblog.notification.mq.NotificationMqProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final NotificationOutboxMapper outboxMapper = mock(NotificationOutboxMapper.class);
    private final NotificationMqProducer producer = mock(NotificationMqProducer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationProperties properties = new NotificationProperties();

    private OutboxRelay newRelay() {
        return new OutboxRelay(outboxMapper, producer, properties, objectMapper);
    }

    private NotificationOutbox pendingRow(String messageId, String paramsJson) {
        NotificationOutbox row = new NotificationOutbox();
        row.setId(1L);
        row.setMessageId(messageId);
        row.setChannel(NotificationChannelType.EMAIL.name());
        row.setRecipient("a@b.com");
        row.setSubject("主题");
        row.setTemplateCode("register-verification-code");
        row.setParamsJson(paramsJson);
        row.setStatus(NotificationOutbox.STATUS_PENDING);
        row.setRetryCount(0);
        row.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        return row;
    }

    @Test
    void publishSuccessMarksPublished() {
        NotificationOutbox row = pendingRow("msg-1", "{\"code\":\"123456\"}");
        when(outboxMapper.selectList(any())).thenReturn(List.of(row));

        newRelay().relay();

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(producer).publish(messageCaptor.capture());
        NotificationMessage published = messageCaptor.getValue();
        assertEquals("msg-1", published.getMessageId(), "MQ 载荷应携带同一 messageId");
        assertEquals("123456", published.getParams().get("code"), "参数应反序列化还原");
        assertEquals(NotificationChannelType.EMAIL, published.getChannel());

        verify(outboxMapper).markPublished(1L);
    }

    @Test
    void publishFailureKeepsPendingAndRecordsError() {
        NotificationOutbox row = pendingRow("msg-2", "{}");
        when(outboxMapper.selectList(any())).thenReturn(List.of(row));
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(producer).publish(any(NotificationMessage.class));

        newRelay().relay();

        verify(outboxMapper, never()).markPublished(any());
        assertEquals(NotificationOutbox.STATUS_PENDING, row.getStatus(), "发布失败保持 PENDING，下轮重扫");
        assertEquals("broker down", row.getLastError());
        assertEquals(1, row.getRetryCount());
        verify(outboxMapper).updateById(row);
    }

    @Test
    void publishSuccessCarriesParamsWhenJsonNullSafe() {
        NotificationOutbox row = pendingRow("msg-3", null);
        when(outboxMapper.selectList(any())).thenReturn(List.of(row));

        newRelay().relay();

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(producer).publish(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().getParams().isEmpty(), "无参数时按空 Map 处理");
    }
}
