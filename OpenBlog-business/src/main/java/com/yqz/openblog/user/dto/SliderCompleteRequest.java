package com.yqz.openblog.user.dto;

import jakarta.validation.constraints.NotBlank;

public class SliderCompleteRequest {

    @NotBlank
    private String challengeId;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }
}
