package com.yqz.openblog.user.dto;

/**
 * 滑动验证：先拉取 challengeId，滑到尽头后调用 {@code /auth/slider-complete}，再登录时带上同一 id。
 */
public class SliderChallengeResponse {

    private boolean enabled;
    private String challengeId;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }
}
