package com.yqz.openblog.user.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderImageGeneratorTest {

    @Test
    void generate_returnsPngDataUrisWithTargetInsideDraggableBounds() {
        SliderImageGenerator generator = new SliderImageGenerator(new Random(1));

        for (int i = 0; i < 20; i++) {
            SliderImageGenerator.SliderImage image = generator.generate();

            assertTrue(image.backgroundDataUri().startsWith("data:image/png;base64,"));
            assertTrue(image.pieceDataUri().startsWith("data:image/png;base64,"));
            assertTrue(image.x() >= SliderImageGenerator.MIN_X && image.x() <= SliderImageGenerator.MAX_X,
                    "缺口 x 越界: " + image.x());
            assertTrue(image.y() >= SliderImageGenerator.MIN_Y && image.y() <= SliderImageGenerator.MAX_Y,
                    "缺口 y 越界: " + image.y());
        }
    }

    @Test
    void generate_decodesToExpectedDimensions() throws IOException {
        SliderImageGenerator.SliderImage image = new SliderImageGenerator(new Random(2)).generate();

        BufferedImage background = decode(image.backgroundDataUri());
        assertEquals(SliderImageGenerator.WIDTH, background.getWidth());
        assertEquals(SliderImageGenerator.HEIGHT, background.getHeight());

        BufferedImage piece = decode(image.pieceDataUri());
        assertEquals(SliderImageGenerator.PIECE, piece.getWidth());
        assertEquals(SliderImageGenerator.PIECE, piece.getHeight());
    }

    @Test
    void generate_sameSeed_reproducesIdenticalOutput() {
        // 可复现是测试夹具依赖的性质；正因为可复现，生产入口才必须用 SecureRandom 而非可播种源
        SliderImageGenerator.SliderImage a = new SliderImageGenerator(new Random(7)).generate();
        SliderImageGenerator.SliderImage b = new SliderImageGenerator(new Random(7)).generate();

        assertEquals(a.x(), b.x());
        assertEquals(a.y(), b.y());
        assertEquals(a.backgroundDataUri(), b.backgroundDataUri());
        assertEquals(a.pieceDataUri(), b.pieceDataUri());
    }

    @Test
    void generate_differentSeeds_produceDifferentPuzzles() {
        SliderImageGenerator.SliderImage a = new SliderImageGenerator(new Random(11)).generate();
        SliderImageGenerator.SliderImage b = new SliderImageGenerator(new Random(12)).generate();

        assertNotEquals(a.backgroundDataUri(), b.backgroundDataUri());
    }

    @Test
    void defaultConstructor_usesUnpredictableSource() {
        // 缺口坐标是安全凭证：默认入口必须不可预测。连续 40 次里若缺口 x 一次都没变，
        // 说明随机源退化成了常量——那等于把答案白送给客户端。
        SliderImageGenerator generator = new SliderImageGenerator();
        int first = generator.generate().x();

        boolean sawDifferentX = false;
        for (int i = 0; i < 40 && !sawDifferentX; i++) {
            sawDifferentX = generator.generate().x() != first;
        }

        assertTrue(sawDifferentX, "默认随机源疑似可预测");
    }

    private static BufferedImage decode(String dataUri) throws IOException {
        String base64 = dataUri.substring(dataUri.indexOf(',') + 1);
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }
}
