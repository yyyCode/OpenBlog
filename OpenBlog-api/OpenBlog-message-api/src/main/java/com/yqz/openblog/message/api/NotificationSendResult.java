package com.yqz.openblog.message.api;

import java.io.Serializable;

/**
 * 通知提交结果（跨服务显式错误语义）。
 * <p>
 * success=false 时携带 errorCode / errorMsg（如 5002「邮件服务暂不可用」），
 * 供 business 侧映射回 {@code BizException} 并做相应清理。
 */
public class NotificationSendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private Integer errorCode;
    private String errorMsg;

    public NotificationSendResult() {}

    public NotificationSendResult(boolean success, Integer errorCode, String errorMsg) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public static NotificationSendResult ok() {
        return new NotificationSendResult(true, null, null);
    }

    public static NotificationSendResult fail(int errorCode, String errorMsg) {
        return new NotificationSendResult(false, errorCode, errorMsg);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Integer getErrorCode() { return errorCode; }
    public void setErrorCode(Integer errorCode) { this.errorCode = errorCode; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}
