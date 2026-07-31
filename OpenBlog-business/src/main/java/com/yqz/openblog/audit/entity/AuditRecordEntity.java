package com.yqz.openblog.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("audit_records")
public class AuditRecordEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("trace_id")
    private String traceId;

    @TableField("user_id")
    private Long userId;

    @TableField("username")
    private String username;

    @TableField("action")
    private String action;

    @TableField("entity_type")
    private String entityType;

    @TableField("entity_id")
    private String entityId;

    @TableField("method")
    private String method;

    @TableField("success")
    private Boolean success;

    @TableField("elapsed_ms")
    private Long elapsedMs;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("request_uri")
    private String requestUri;

    @TableField("http_method")
    private String httpMethod;

    @TableField("before_snapshot")
    private String beforeSnapshot;

    @TableField("after_snapshot")
    private String afterSnapshot;

    @TableField("args")
    private String args;

    @TableField("result")
    private String result;

    @TableField("error_msg")
    private String errorMsg;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public Long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getRequestUri() { return requestUri; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(String beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }

    public String getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(String afterSnapshot) { this.afterSnapshot = afterSnapshot; }

    public String getArgs() { return args; }
    public void setArgs(String args) { this.args = args; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
