package com.yqz.openblog.notification;

/**
 * 通知渠道模板方法基类。
 * <p>
 * 固定投递骨架：模板渲染 → doSend（子类实现渠道差异）。子类只需实现 {@link #type()} 与
 * {@link #doSend(NotificationMessage, String)}，统一处理模板渲染，降低渠道实现与调用方的耦合。
 * 新增渠道（SMS / 飞书）只需继承本类，主链路零改动。
 */
public abstract class AbstractNotificationChannel implements NotificationChannel {

    private final NotificationTemplateService templateService;

    protected AbstractNotificationChannel(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * 模板方法（final，子类不可覆写骨架）：渲染内容后交给子类真正投递。
     * 渲染失败抛 {@link com.yqz.openblog.common.BizException}(4000)，投递失败由 doSend 抛（5002 等）。
     */
    @Override
    public final void send(NotificationMessage message) {
        String content = templateService.render(message.getTemplateCode(), message.getParams());
        doSend(message, content);
    }

    /**
     * 子类实现：真正投递。入参为完整消息（含 {@code messageId} 幂等键）与已渲染内容。
     * Email → Dubbo RPC 调 email 模块；未来 SMS → 阿里云短信；Feishu → webhook。
     * 失败必须抛 {@link com.yqz.openblog.common.BizException}，由调用方决定是否回滚。
     */
    protected abstract void doSend(NotificationMessage message, String content);
}
