package com.yqz.openblog.notification;

/**
 * 通知渠道策略接口。
 * <p>
 * 每个渠道一个实现（当前仅 {@code EmailNotificationChannel}；未来 SMS / 飞书各加一个实现），
 * 由 {@link ChannelRegistry} 按 {@link #type()} 路由。新增渠道只需新增实现类，主链路零改动。
 */
public interface NotificationChannel {

    /** 本渠道对应的类型（用于路由）。 */
    NotificationChannelType type();

    /** 投递一条通知。失败抛 {@link com.yqz.openblog.common.BizException}（由调用方决定是否回滚）。 */
    void send(NotificationMessage message);
}
