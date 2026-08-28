package com.yqz.openblog.notification.mq;

/**
 * RocketMQ topic / 消费组常量（单一来源）。
 * <p>
 * {@code @RocketMQMessageListener} 注解属性要求编译期常量，故不放配置中心；
 * 生产/消费端统一引用，避免两处配置漂移。topic 变更需在 RocketMQ 控制台创建同名 topic。
 */
public final class NotificationTopics {

    public static final String TOPIC = "openblog_notification";
    public static final String CONSUMER_GROUP = "notification-consumer";
    public static final String PRODUCER_GROUP = "openblog-notification-producer";

    private NotificationTopics() {
    }
}
