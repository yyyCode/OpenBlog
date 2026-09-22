package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 滑动验证、失败锁定等安全相关配置。
 */
@ConfigurationProperties(prefix = "openblog.auth-security")
public class AuthSecurityProperties {

    private Slider slider = new Slider();
    private LoginLockout loginLockout = new LoginLockout();
    private DeviceLockout deviceLockout = new DeviceLockout();
    private EmailCode emailCode = new EmailCode();

    public Slider getSlider() {
        return slider;
    }

    public void setSlider(Slider slider) {
        this.slider = slider;
    }

    public LoginLockout getLoginLockout() {
        return loginLockout;
    }

    public void setLoginLockout(LoginLockout loginLockout) {
        this.loginLockout = loginLockout;
    }

    public DeviceLockout getDeviceLockout() {
        return deviceLockout;
    }

    public void setDeviceLockout(DeviceLockout deviceLockout) {
        this.deviceLockout = deviceLockout;
    }

    public EmailCode getEmailCode() {
        return emailCode;
    }

    public void setEmailCode(EmailCode emailCode) {
        this.emailCode = emailCode;
    }

    /**
     * 滑动验证码：服务端随机生成缺口图，客户端算出缺口 x 并提交拖动轨迹。
     * 强度上限为「拦掉不做图像分析的脚本、抬高随手写脚本的成本」——缺口位置存在 Redis，
     * 客户端只能从像素里解出来；而会写局部统计或接缝分析的人仍能解出，这是滑块验证码这一
     * 形态的固有上限。真正的门禁是坐标容差 + 一次性消费 + 网关限流，轨迹阈值一律<b>偏向不误伤真人</b>。
     */
    public static class Slider {
        /**
         * 是否要求发码前先完成滑动验证（Redis 记录一次性凭证）。
         */
        private boolean enabled = false;
        /**
         * challenge / 通过标记有效时间（秒）。
         */
        private int ttlSeconds = 300;
        /**
         * 允许的落点误差（像素）。容差 6px / 随机空间 176px → 盲猜单次命中率约 3.4%。
         */
        private int tolerancePx = 6;
        /**
         * 拖动最短耗时（毫秒），低于此值判为机器瞬移。
         * <p>
         * 刻意取 100 而非更高：滑块最远要拖 272 图像像素（约 340 CSS px），快甩一下就能在
         * 200ms 内完成，阈值取 200 会误伤真实用户。100ms 仍远高于脚本瞬移的耗时。
         */
        private int minDurationMs = 100;
        /**
         * 拖动最长耗时（毫秒），超时判为非人工（也防慢速重放）。
         */
        private int maxDurationMs = 30000;
        /**
         * 轨迹最少采样点数。
         */
        private int minTrailPoints = 5;
        /**
         * 速度变异系数下限。取相邻采样点间平均速度（px/ms）序列，要求 标准差/均值 >= 本值，
         * 即拒绝完全匀速的「机器直线」。启发式阈值，非强保证——前端采样位置取整后，恒速拖动
         * 的速度也会自然出现起伏，故它只拦得住数学上完美匀速的提交。
         */
        private double minSpeedCv = 0.05;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getTolerancePx() {
            return tolerancePx;
        }

        public void setTolerancePx(int tolerancePx) {
            this.tolerancePx = tolerancePx;
        }

        public int getMinDurationMs() {
            return minDurationMs;
        }

        public void setMinDurationMs(int minDurationMs) {
            this.minDurationMs = minDurationMs;
        }

        public int getMaxDurationMs() {
            return maxDurationMs;
        }

        public void setMaxDurationMs(int maxDurationMs) {
            this.maxDurationMs = maxDurationMs;
        }

        public int getMinTrailPoints() {
            return minTrailPoints;
        }

        public void setMinTrailPoints(int minTrailPoints) {
            this.minTrailPoints = minTrailPoints;
        }

        public double getMinSpeedCv() {
            return minSpeedCv;
        }

        public void setMinSpeedCv(double minSpeedCv) {
            this.minSpeedCv = minSpeedCv;
        }
    }

    public static class LoginLockout {
        private boolean enabled = true;
        /**
         * 同一 IP 在窗口内允许的最大失败次数（仅统计密码错误）。
         */
        private int maxFailuresPerIp = 5;
        /**
         * 失败计数滑动窗口（秒）。
         */
        private int failureWindowSeconds = 300;
        /**
         * 触发锁定后的禁止登录时长（秒）。
         */
        private int lockoutSeconds = 900;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFailuresPerIp() {
            return maxFailuresPerIp;
        }

        public void setMaxFailuresPerIp(int maxFailuresPerIp) {
            this.maxFailuresPerIp = maxFailuresPerIp;
        }

        public int getFailureWindowSeconds() {
            return failureWindowSeconds;
        }

        public void setFailureWindowSeconds(int failureWindowSeconds) {
            this.failureWindowSeconds = failureWindowSeconds;
        }

        public int getLockoutSeconds() {
            return lockoutSeconds;
        }

        public void setLockoutSeconds(int lockoutSeconds) {
            this.lockoutSeconds = lockoutSeconds;
        }
    }

    /**
     * 设备级失败封禁（按设备指纹计，与 IP 锁互补）。
     * 指纹是网关校验过的弱信号：挡「换 IP 但设备固定」的爆破；设备指纹非权威身份，
     * 对「每请求换指纹」的脚本无效——那由网关层（指纹轮换守卫 + 签名设备令牌）负责。
     * 误伤面比 IP 锁小（只锁定单一指纹），故默认开启；IP 锁因 NAT 误伤在生产关闭。
     */
    public static class DeviceLockout {
        private boolean enabled = true;
        /**
         * 同一设备（指纹）在窗口内允许的最大失败次数（仅统计密码错误）。
         */
        private int maxFailuresPerFp = 5;
        /**
         * 失败计数滑动窗口（秒）。
         */
        private int failureWindowSeconds = 300;
        /**
         * 触发锁定后的禁止登录时长（秒）。
         */
        private int lockoutSeconds = 900;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFailuresPerFp() {
            return maxFailuresPerFp;
        }

        public void setMaxFailuresPerFp(int maxFailuresPerFp) {
            this.maxFailuresPerFp = maxFailuresPerFp;
        }

        public int getFailureWindowSeconds() {
            return failureWindowSeconds;
        }

        public void setFailureWindowSeconds(int failureWindowSeconds) {
            this.failureWindowSeconds = failureWindowSeconds;
        }

        public int getLockoutSeconds() {
            return lockoutSeconds;
        }

        public void setLockoutSeconds(int lockoutSeconds) {
            this.lockoutSeconds = lockoutSeconds;
        }
    }

    public static class EmailCode {
        /**
         * 验证码有效时间（秒），默认 5 分钟。
         */
        private int codeTtlSeconds = 300;
        /**
         * 重新发送冷却时间（秒），默认 60 秒。
         */
        private int resendCooldownSeconds = 60;
        /**
         * 校验失败允许的最大次数，超过后验证码作废，默认 5 次。
         */
        private int maxVerifyAttempts = 5;

        public int getCodeTtlSeconds() {
            return codeTtlSeconds;
        }

        public void setCodeTtlSeconds(int codeTtlSeconds) {
            this.codeTtlSeconds = codeTtlSeconds;
        }

        public int getResendCooldownSeconds() {
            return resendCooldownSeconds;
        }

        public void setResendCooldownSeconds(int resendCooldownSeconds) {
            this.resendCooldownSeconds = resendCooldownSeconds;
        }

        public int getMaxVerifyAttempts() {
            return maxVerifyAttempts;
        }

        public void setMaxVerifyAttempts(int maxVerifyAttempts) {
            this.maxVerifyAttempts = maxVerifyAttempts;
        }
    }
}
