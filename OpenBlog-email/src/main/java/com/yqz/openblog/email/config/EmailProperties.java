package com.yqz.openblog.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openblog.email.aliyun")
public class EmailProperties {

    /** 阿里云 AccessKey ID */
    private String accessKeyId;

    /** 阿里云 AccessKey Secret */
    private String accessKeySecret;

    /** DirectMail 区域（如 cn-hangzhou） */
    private String regionId = "cn-hangzhou";

    /** 发件人地址（需在阿里云 DirectMail 控制台验证） */
    private String fromAddress;

    /** 发件人别名 */
    private String fromAlias = "OpenBlog";

    /** 是否启用邮件发送（开发环境可关闭） */
    private boolean enabled = true;

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getFromAlias() { return fromAlias; }
    public void setFromAlias(String fromAlias) { this.fromAlias = fromAlias; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
