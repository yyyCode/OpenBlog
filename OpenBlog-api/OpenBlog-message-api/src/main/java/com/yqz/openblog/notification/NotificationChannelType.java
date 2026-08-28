package com.yqz.openblog.notification;

/**
 * 通知渠道类型。
 * <p>
 * 当前只实现 EMAIL（message 内进程直调 EmailService 投递）。SMS / FEISHU 为未来扩展位，
 * 接入时新增枚举值 + 对应 {@link NotificationChannel} 策略 + 配置即可，主链路零改动。
 */
public enum NotificationChannelType {

    /** 邮件 */
    EMAIL;

    // 未来扩展位：
    // SMS —— 阿里云短信（新增 SmsNotificationChannel）
    // FEISHU —— 飞书机器人 webhook（新增 FeishuNotificationChannel）
}
