package com.yqz.openblog.message.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.message.api.NotificationRpcService;
import com.yqz.openblog.message.api.NotificationSendResult;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一通知 RPC provider。入参消息交进程内 {@link NotificationService} 路由投递；
 * 业务异常显式转成 {@link NotificationSendResult} 返回（不依赖 Dubbo 异常序列化）。
 */
@DubboService
public class NotificationRpcServiceImpl implements NotificationRpcService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRpcServiceImpl.class);

    private final NotificationService notificationService;

    public NotificationRpcServiceImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public NotificationSendResult submit(NotificationMessage message) {
        try {
            notificationService.submit(message);
            return NotificationSendResult.ok();
        } catch (BizException e) {
            log.warn("通知投递失败 channel={} code={} msg={}",
                    message == null ? "null" : message.getChannel(), e.getCode(), e.getMessage());
            return NotificationSendResult.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            // 非业务异常（DB/序列化等）：显式转通用错误码返回，不依赖 Dubbo 异常序列化。
            log.error("通知投递发生未预期异常 channel={}", message == null ? "null" : message.getChannel(), e);
            return NotificationSendResult.fail(NotificationRpcService.ERROR_CODE_INTERNAL, "通知服务内部错误，请稍后再试");
        }
    }
}
