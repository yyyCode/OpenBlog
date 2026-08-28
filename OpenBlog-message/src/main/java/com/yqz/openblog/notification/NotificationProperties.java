package com.yqz.openblog.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知渠道配置（prefix: openblog.notification），对齐 AuthSecurityProperties 的嵌套类风格。
 * <p>
 * 当前只 {@code email} 生效；SMS / 飞书为未来扩展位（新增嵌套类 + 配置注释即可），
 * 未配置的渠道由 {@link ChannelRegistry} fail-closed 拒绝。
 */
@ConfigurationProperties(prefix = "openblog.notification")
public class NotificationProperties {

    private Email email = new Email();
    private Outbox outbox = new Outbox();

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    /** 邮件渠道配置。 */
    public static class Email {
        /** 是否启用邮件渠道（false 时 EmailNotificationChannel 不注册）。 */
        private boolean enabled = true;
        /** 调用方未指定 subject 时的默认主题。 */
        private String defaultSubject = "OpenBlog";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultSubject() {
            return defaultSubject;
        }

        public void setDefaultSubject(String defaultSubject) {
            this.defaultSubject = defaultSubject;
        }
    }

    /** 异步通知本地消息表（outbox）配置。 */
    public static class Outbox {
        /** 是否启用异步通知（false 时 submitAsync 直接抛 4000）。 */
        private boolean enabled = true;
        /** Relay 扫描间隔（毫秒），@Scheduled fixedDelay。 */
        private long relayIntervalMs = 5000;
        /** 跳过刚提交的记录窗口（毫秒），给业务事务提交留出余量，避免读到未提交的脏窗口。 */
        private long publishWindowMs = 1000;
        /** 投递失败最大重试次数，超过后进死信（P2 延时重试使用，本期先暴露配置位）。 */
        private int maxRetry = 8;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getRelayIntervalMs() {
            return relayIntervalMs;
        }

        public void setRelayIntervalMs(long relayIntervalMs) {
            this.relayIntervalMs = relayIntervalMs;
        }

        public long getPublishWindowMs() {
            return publishWindowMs;
        }

        public void setPublishWindowMs(long publishWindowMs) {
            this.publishWindowMs = publishWindowMs;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }
    }
}
