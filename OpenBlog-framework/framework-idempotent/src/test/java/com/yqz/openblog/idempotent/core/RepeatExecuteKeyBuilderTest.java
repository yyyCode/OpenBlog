package com.yqz.openblog.idempotent.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatExecuteKeyBuilderTest {

    @Test
    void buildsKeysWithEnvPrefix() {
        RepeatExecuteKeyBuilder b = new RepeatExecuteKeyBuilder("prod");
        assertEquals("openblog:prod:repeat:lock:comment_create:42:7:abc",
                b.lockKey("comment_create", List.of("42", "7", "abc")));
        assertEquals("openblog:prod:repeat:flag:comment_create:42:7:abc",
                b.flagKey("comment_create", List.of("42", "7", "abc")));
        assertEquals("openblog:prod:repeat:result:comment_create:42:7:abc",
                b.resultKey("comment_create", List.of("42", "7", "abc")));
    }

    @Test
    void omitsEnvSegmentWhenBlank() {
        RepeatExecuteKeyBuilder b = new RepeatExecuteKeyBuilder("");
        assertEquals("openblog:repeat:lock:name:a", b.lockKey("name", List.of("a")));
    }

    @Test
    void trimsEnvPrefix() {
        RepeatExecuteKeyBuilder b = new RepeatExecuteKeyBuilder("  dev  ");
        assertEquals("openblog:dev:repeat:lock:name:a", b.lockKey("name", List.of("a")));
    }
}
