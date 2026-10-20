package com.yqz.openblog.sensitiveword.runtime;

import com.yqz.openblog.common.BizException;
import org.springframework.stereotype.Component;

/** 业务接入统一入口：任一字段命中即 4003，不回显命中词。 */
@Component
public class SensitiveWordGuard {

    private final SensitiveWordManager manager;

    public SensitiveWordGuard(SensitiveWordManager manager) {
        this.manager = manager;
    }

    public void rejectIfHit(String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            if (manager.contains(text)) {
                throw new BizException(4003, "内容包含敏感词，请修改后重试");
            }
        }
    }
}
