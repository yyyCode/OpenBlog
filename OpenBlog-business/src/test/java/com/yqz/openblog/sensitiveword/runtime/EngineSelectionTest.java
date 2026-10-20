package com.yqz.openblog.sensitiveword.runtime;

import com.yqz.openblog.config.SensitiveWordProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EngineSelectionTest {

    @Test
    void request_defaultOrBlank_meansFollowConfig() {
        assertNull(EngineSelection.parseRequest(null));
        assertNull(EngineSelection.parseRequest("  "));
        assertNull(EngineSelection.parseRequest("default"));
        assertNull(EngineSelection.parseRequest("DEFAULT"));
    }

    @Test
    void request_acceptsSupportedEnginesIgnoringCase() {
        assertEquals(SensitiveWordProperties.Engine.HOUBB, EngineSelection.parseRequest("houbb"));
        assertEquals(SensitiveWordProperties.Engine.TRIE, EngineSelection.parseRequest("TRIE"));
        assertEquals(SensitiveWordProperties.Engine.AC, EngineSelection.parseRequest(" ac "));
    }

    @Test
    void request_rejectsNoneAndUnknown() {
        assertThrows(IllegalArgumentException.class, () -> EngineSelection.parseRequest("none"));
        assertThrows(IllegalArgumentException.class, () -> EngineSelection.parseRequest("re2"));
    }

    @Test
    void override_invalidValueFallsBackToNull() {
        assertNull(EngineSelection.parseOverride("garbage"));
        assertNull(EngineSelection.parseOverride("none"));
        assertNull(EngineSelection.parseOverride(""));
        assertEquals(SensitiveWordProperties.Engine.AC, EngineSelection.parseOverride("ac"));
    }

    @Test
    void wireName_isLowercase() {
        assertEquals("houbb", EngineSelection.toWire(SensitiveWordProperties.Engine.HOUBB));
        assertEquals("trie", EngineSelection.toWire(SensitiveWordProperties.Engine.TRIE));
        assertEquals("ac", EngineSelection.toWire(SensitiveWordProperties.Engine.AC));
        assertNull(EngineSelection.toWire(null));
    }
}
