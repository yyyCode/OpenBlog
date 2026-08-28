package com.yqz.openblog.notification;

import com.yqz.openblog.common.BizException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道路由表：把容器里所有启用的 {@link NotificationChannel} 按 {@code type()} 收敛成 Map，
 * {@link NotificationService} 据此分发。新增渠道 = 新增实现类（自动被 Spring 收集），主链路零改动。
 */
@Component
public class ChannelRegistry {

    private final Map<NotificationChannelType, NotificationChannel> channels =
            new EnumMap<>(NotificationChannelType.class);

    public ChannelRegistry(List<NotificationChannel> channelList) {
        for (NotificationChannel channel : channelList) {
            NotificationChannelType type = channel.type();
            if (channels.put(type, channel) != null) {
                throw new IllegalStateException("通知渠道重复注册: " + type);
            }
        }
    }

    /** 按类型取渠道；未配置/被禁用时抛 4000（fail-closed）。 */
    public NotificationChannel resolve(NotificationChannelType type) {
        NotificationChannel channel = channels.get(type);
        if (channel == null) {
            throw new BizException(4000, "未配置的通知渠道: " + type);
        }
        return channel;
    }

    public Map<NotificationChannelType, NotificationChannel> all() {
        return channels;
    }
}
