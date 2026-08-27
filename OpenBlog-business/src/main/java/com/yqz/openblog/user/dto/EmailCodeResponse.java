package com.yqz.openblog.user.dto;

/**
 * 发送邮箱验证码响应。
 */
public class EmailCodeResponse {

    /**
     * 本次发送后的冷却秒数，前端用它启动倒计时。
     */
    private int cooldownSeconds;

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
}
