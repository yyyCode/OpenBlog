package com.yqz.openblog.sensitiveword.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 快照编解码。
 * <ul>
 *   <li>明文：UTF-8，字典序，{@code \n} 分隔，末尾无换行</li>
 *   <li>MySQL：gzip(明文)</li>
 *   <li>Redis：{@code checksum + "\n" + Base64(gzip(明文))}</li>
 * </ul>
 */
public final class SnapshotCodec {

    private SnapshotCodec() {
    }

    public static byte[] encode(List<String> words) {
        List<String> sorted = words.stream()
                .filter(w -> w != null && !w.isEmpty())
                .distinct()
                .sorted()
                .toList();
        return String.join("\n", sorted).getBytes(StandardCharsets.UTF_8);
    }

    public static List<String> decode(byte[] plain) {
        if (plain == null || plain.length == 0) {
            return List.of();
        }
        String text = new String(plain, StandardCharsets.UTF_8);
        List<String> words = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                if (i > start) {
                    words.add(text.substring(start, i));
                }
                start = i + 1;
            }
        }
        return words;
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] gzip(byte[] plain) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(plain);
            gz.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] gunzip(byte[] gz) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            in.transferTo(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toRedisValue(String checksum, byte[] gzipped) {
        return checksum + "\n" + Base64.getEncoder().encodeToString(gzipped);
    }

    public record Decoded(byte[] plain, String checksum) {
    }

    public static Decoded parseRedisValue(String value) {
        int idx = value.indexOf('\n');
        if (idx <= 0) {
            throw new IllegalArgumentException("invalid snapshot value");
        }
        String checksum = value.substring(0, idx);
        byte[] gz = Base64.getDecoder().decode(value.substring(idx + 1));
        return new Decoded(gunzip(gz), checksum);
    }
}
