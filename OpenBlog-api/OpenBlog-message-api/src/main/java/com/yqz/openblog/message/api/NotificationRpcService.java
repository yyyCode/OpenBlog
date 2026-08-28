package com.yqz.openblog.message.api;

import com.yqz.openblog.notification.NotificationMessage;

/**
 * 统一通知 RPC 接口（business 消费，message 提供）。
 * <p>
 * 与旧 EmailRpcService 的区别：入参是渠道无关的 {@link NotificationMessage}，
 * 由 message 内部按渠道路由投递；同步返回结果对象（显式错误码，不依赖 Dubbo 异常传播）。
 */
public interface NotificationRpcService {

    /** 注册验证码模板 code（共享常量：business 构造消息与 message 渲染共用同一来源）。 */
    String TEMPLATE_REGISTER_VERIFICATION_CODE = "register-verification-code";

    /** 邮件渠道不可用（同步链路降级码，随 result.errorCode 跨服务回传，business 映射回 BizException(5002)）。 */
    int ERROR_CODE_EMAIL_UNAVAILABLE = 5002;

    /** 同步提交一条通知并投递。失败时返回 result.success=false + 错误码/消息。 */
    NotificationSendResult submit(NotificationMessage message);
}
