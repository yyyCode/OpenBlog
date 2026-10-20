package com.yqz.openblog.sensitiveword.runtime;

import com.yqz.openblog.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveWordGuardTest {

    @Test
    void rejectIfHit_throws4003() {
        SensitiveWordManager manager = mock(SensitiveWordManager.class);
        when(manager.contains("含敏感词")).thenReturn(true);
        SensitiveWordGuard guard = new SensitiveWordGuard(manager);

        BizException e = assertThrows(BizException.class, () -> guard.rejectIfHit("正常", "含敏感词"));
        assertEquals(4003, e.getCode());
    }

    @Test
    void blankFieldsSkipped() {
        SensitiveWordManager manager = mock(SensitiveWordManager.class);
        SensitiveWordGuard guard = new SensitiveWordGuard(manager);
        assertDoesNotThrow(() -> guard.rejectIfHit(null, "", "   "));
    }
}
