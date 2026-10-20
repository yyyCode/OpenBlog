package com.yqz.openblog.sensitiveword.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCodecTest {

    @Test
    void encode_sortsDeduplicatesAndHasNoTrailingNewline() {
        byte[] plain = SnapshotCodec.encode(List.of("丙", "甲", "乙", "甲"));
        String text = new String(plain, StandardCharsets.UTF_8);
        assertEquals("丙\n乙\n甲", text);
        assertEquals(List.of("丙", "乙", "甲"), SnapshotCodec.decode(plain));
    }

    @Test
    void decode_emptyAndNull() {
        assertEquals(List.of(), SnapshotCodec.decode(null));
        assertEquals(List.of(), SnapshotCodec.decode(new byte[0]));
    }

    @Test
    void gzipRoundTrip() {
        byte[] plain = SnapshotCodec.encode(List.of("敏感词", "广告", "abc"));
        byte[] gz = SnapshotCodec.gzip(plain);
        assertTrue(gz.length > 0);
        assertArrayEquals(plain, SnapshotCodec.gunzip(gz));
    }

    @Test
    void checksum_stableAndDifferent() {
        byte[] a = "abc".getBytes(StandardCharsets.UTF_8);
        assertEquals(SnapshotCodec.sha256Hex(a), SnapshotCodec.sha256Hex(a));
        assertNotEquals(SnapshotCodec.sha256Hex(a), SnapshotCodec.sha256Hex("abd".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void redisValueRoundTrip() {
        byte[] plain = SnapshotCodec.encode(List.of("甲", "乙"));
        String checksum = SnapshotCodec.sha256Hex(plain);
        String value = SnapshotCodec.toRedisValue(checksum, SnapshotCodec.gzip(plain));
        SnapshotCodec.Decoded decoded = SnapshotCodec.parseRedisValue(value);
        assertEquals(checksum, decoded.checksum());
        assertArrayEquals(plain, decoded.plain());
    }
}
