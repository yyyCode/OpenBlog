package com.yqz.openblog.notification;

import com.yqz.openblog.common.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelRegistryTest {

    private static class FakeChannel implements NotificationChannel {
        private final NotificationChannelType type;
        private int sendCount = 0;

        FakeChannel(NotificationChannelType type) {
            this.type = type;
        }

        @Override
        public NotificationChannelType type() {
            return type;
        }

        @Override
        public void send(NotificationMessage message) {
            sendCount++;
        }
    }

    @Test
    void routesToRegisteredChannel() {
        FakeChannel email = new FakeChannel(NotificationChannelType.EMAIL);
        ChannelRegistry registry = new ChannelRegistry(List.of(email));

        registry.resolve(NotificationChannelType.EMAIL).send(new NotificationMessage());

        assertEquals(1, email.sendCount, "应分发到 EMAIL 渠道");
    }

    @Test
    void unconfiguredChannelFailsClosed() {
        ChannelRegistry registry = new ChannelRegistry(List.of());

        BizException e = assertThrows(BizException.class,
                () -> registry.resolve(NotificationChannelType.EMAIL));
        assertEquals(4000, e.getCode());
    }

    @Test
    void duplicateRegistrationRejected() {
        FakeChannel a = new FakeChannel(NotificationChannelType.EMAIL);
        FakeChannel b = new FakeChannel(NotificationChannelType.EMAIL);

        assertThrows(IllegalStateException.class, () -> new ChannelRegistry(List.of(a, b)));
    }
}
