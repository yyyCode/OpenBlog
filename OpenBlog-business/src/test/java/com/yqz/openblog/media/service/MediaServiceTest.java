package com.yqz.openblog.media.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 媒体 Content-Type 安全回归：白名单直通、非法类型降级、解码器按文件头识别真实格式（防伪造）。
 */
class MediaServiceTest {

    @Test
    void sanitizeContentType_passesWhitelistedThrough() {
        assertThat(MediaService.sanitizeContentType("image/png")).isEqualTo("image/png");
        assertThat(MediaService.sanitizeContentType("image/jpeg")).isEqualTo("image/jpeg");
        assertThat(MediaService.sanitizeContentType("image/gif")).isEqualTo("image/gif");
        assertThat(MediaService.sanitizeContentType("image/bmp")).isEqualTo("image/bmp");
        assertThat(MediaService.sanitizeContentType("image/webp")).isEqualTo("image/webp");
    }

    @Test
    void detectImageContentType_detectsRealWebp(@TempDir Path tempDir) throws IOException {
        // 真实 WebP：依赖 twelvemonkeys imageio-webp 注册的解码器按文件头识别为 image/webp。
        // 若无该插件，ImageIO 无 WebP reader → 返回 null → 上传层会拒绝（回归保护）。
        Path webp = tempDir.resolve("real.webp");
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "webp", webp.toFile());
        assertThat(MediaService.detectImageContentType(webp)).isEqualTo("image/webp");
    }

    @Test
    void sanitizeContentType_downgradesNonWhitelisted() {
        assertThat(MediaService.sanitizeContentType("image/svg+xml")).isEqualTo("application/octet-stream");
        assertThat(MediaService.sanitizeContentType("text/html")).isEqualTo("application/octet-stream");
        assertThat(MediaService.sanitizeContentType(null)).isEqualTo("application/octet-stream");
    }

    @Test
    void detectImageContentType_detectsRealPngAndRejectsSvg(@TempDir Path tempDir) throws IOException {
        // 真实 PNG：解码器按文件头识别为 image/png，而非客户端伪造的 content-type
        Path png = tempDir.resolve("real.png");
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "png", png.toFile());
        assertThat(MediaService.detectImageContentType(png)).isEqualTo("image/png");

        // SVG 文本：ImageIO 无对应解码器 → 返回 null，上传层据此拒绝（防 svg 内联脚本）
        Path svg = tempDir.resolve("fake.svg");
        Files.writeString(svg, "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>", StandardCharsets.UTF_8);
        assertThat(MediaService.detectImageContentType(svg)).isNull();
    }
}
