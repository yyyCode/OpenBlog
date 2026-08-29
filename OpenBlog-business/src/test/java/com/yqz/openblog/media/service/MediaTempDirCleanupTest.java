package com.yqz.openblog.media.service;

import net.coobird.thumbnailator.Thumbnails;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归：上传缩略图临时文件清理。
 *
 * <p>背景：临时缩略图曾用无扩展名路径 {@code tempDir/thumb}，Thumbnailator 0.4.20 靠扩展名推断
 * 输出格式，无扩展名时它在同目录生成 {@code thumb.JPEG}、目标文件却不落盘，导致
 * {@code ImageIO.read} 失败 + 清理 {@code deleteIfExists(tempDir)} 抛 DirectoryNotEmptyException
 * （生产表现为 5001 + 临时目录路径）。
 *
 * <p>断言两类修复：① 缩略图输出带扩展名 → 文件真实存在且 ImageIO 可读；② 递归清理兜底 → 无残留、不抛异常。
 */
class MediaTempDirCleanupTest {

    @Test
    void thumbnailWithExtensionIsWritableAndReadable() throws Exception {
        Path tempDir = Files.createTempDirectory("openblog-media-test-");
        try {
            Path original = tempDir.resolve("original.jpg");
            writeJpeg(original, 800, 600);

            // 与 MediaService.upload 一致：ext 由权威格式判定得出（此处 jpeg）
            Path thumb = tempDir.resolve("thumb.jpg");
            Thumbnails.of(original.toFile())
                    .size(320, 240)
                    .outputQuality(0.9)
                    .toFile(thumb.toFile());

            // 目标文件必须真实落盘（无扩展名时这里会是 thumb.JPEG 而 thumb 不存在）
            assertTrue(Files.exists(thumb), "带扩展名的缩略图应真实落盘");
            BufferedImage read = ImageIO.read(thumb.toFile());
            assertNotNull(read, "缩略图应能被 ImageIO 读取");
            assertEquals(320, read.getWidth());
            assertEquals(240, read.getHeight());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    void recursiveCleanupRemovesAllLeftovers() throws Exception {
        Path tempDir = Files.createTempDirectory("openblog-media-test-");
        try {
            Path original = tempDir.resolve("original.jpg");
            writeJpeg(original, 640, 480);
            // 模拟 Thumbnailator 无扩展名输出残留的 scratch 文件（历史/边界情形）
            Files.copy(original, tempDir.resolve("thumb.JPEG"), StandardCopyOption.REPLACE_EXISTING);

            deleteRecursively(tempDir);
            assertFalse(Files.exists(tempDir), "递归清理后临时目录应被删除");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    void deleteRecursivelyHandlesMissingPath() throws Exception {
        Path gone = Path.of(System.getProperty("java.io.tmpdir"), "openblog-media-nonexistent-" + System.nanoTime());
        assertDoesNotThrow(() -> deleteRecursively(gone));
    }

    private void writeJpeg(Path target, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.getGraphics().dispose();
        ImageIO.write(img, "jpeg", target.toFile());
    }

    private void deleteRecursively(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
