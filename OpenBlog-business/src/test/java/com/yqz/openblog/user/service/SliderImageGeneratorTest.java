package com.yqz.openblog.user.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderImageGeneratorTest {

    /** 实测底图 data URI 约 4.6 万字符；留约七成余量，纹理层若被改得体积暴涨会在这里失败。 */
    private static final int BACKGROUND_URI_LIMIT = 80_000;

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
    void generate_backgroundStaysWithinPayloadBudget() {
        // 底图带密集纹理是为了压掉缺口的可定位性，代价就是体积。守住上界，
        // 免得后续调纹理参数时不知不觉把每次挑战的响应撑到几百 KB。
        SliderImageGenerator generator = new SliderImageGenerator(new Random(3));

        for (int i = 0; i < 10; i++) {
            int length = generator.generate().backgroundDataUri().length();
            assertTrue(length < BACKGROUND_URI_LIMIT, "底图体积超预算: " + length);
        }
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
    void defaultConstructor_producesUnpredictableOutput() {
        // 缺口坐标是安全凭证，默认入口必须不可预测。两个独立实例若输出相同，说明随机源被写成了
        // 固定种子（如 new Random(42)）——那等于把答案白送给客户端。
        // 断言整张底图而非仅 x：x 的取值空间只有 177 个，单看 x 有约 0.6% 概率偶然相同，
        // 断言 base64 全串则这个概率可以忽略。
        SliderImageGenerator first = new SliderImageGenerator();
        SliderImageGenerator second = new SliderImageGenerator();

        assertNotEquals(first.generate().backgroundDataUri(), second.generate().backgroundDataUri());
    }

    @Test
    void scramblePatch_isPurePermutationOfHolePixels() {
        // 加固的核心不变式：缺口处必须是原像素的**重排**，不得有任何平均或着色。
        // 只要做了平均，某个方向的高频就必然减弱，局部方差扫描就能一次定位缺口
        // （实测二维马赛克命中 50/200、横向涂抹 52/200，都是这一条被破坏的后果）。
        // 用像素多重集相等来钉住它：统计量精确不变是"没有信号"，比"信号很弱"强得多。
        SliderImageGenerator generator = new SliderImageGenerator(new Random(23));
        BufferedImage source = randomImage(41);
        int x = SliderImageGenerator.MIN_X;
        int y = SliderImageGenerator.MIN_Y;
        Map<Integer, Integer> sync = countPixels(source, x, y);

        BufferedImage scrambled = generator.scramblePatch(source, x, y);

        assertEquals(sync, countPixels(scrambled, x, y),
                "缺口处像素多重集被改变，说明做了平均/着色而非纯置换");
        assertNotEquals(describeHole(source, x, y), describeHole(scrambled, x, y),
                "缺口内容与打乱前完全相同，说明置换没有生效");
    }

    @Test
    void scramblePatch_writesOpaquePixels() {
        // 回归防线：源图是 TYPE_INT_RGB，getRGB 回来 alpha 位为 0。若复制时忘了补 0xFF，
        // 叠加后整块透明，缺口会从底图上直接消失（用户看不到目标位置）。
        SliderImageGenerator generator = new SliderImageGenerator(new Random(29));
        BufferedImage source = randomImage(43);

        BufferedImage scrambled = generator.scramblePatch(source, SliderImageGenerator.MIN_X, SliderImageGenerator.MIN_Y);

        for (int py = SliderImageGenerator.MIN_Y; py < SliderImageGenerator.MIN_Y + SliderImageGenerator.PIECE; py++) {
            for (int px = SliderImageGenerator.MIN_X; px < SliderImageGenerator.MIN_X + SliderImageGenerator.PIECE; px++) {
                int alpha = scrambled.getRGB(px, py) >>> 24;
                assertEquals(255, alpha, "打乱图层在 (" + px + "," + py + ") 不是不透明");
            }
        }
    }

    @Test
    void carvePiece_containsNoBackgroundPixels() throws IOException {
        // 滑块块必须是纯示意块。块里一旦带上缺口处的真实像素（哪怕先做过模糊/马赛克等低通处理），
        // 拿块到底图上做模板匹配就能直接命中落点——实测抠真实像素的版本命中 173/200。
        // 这里用"所有不透明像素都接近纯白"钉住它：只要混进任何底图像素，这个断言立刻失败。
        SliderImageGenerator.SliderImage image = new SliderImageGenerator(new Random(31)).generate();
        BufferedImage piece = decode(image.pieceDataUri());

        boolean sawOpaque = false;
        for (int py = 0; py < piece.getHeight(); py++) {
            for (int px = 0; px < piece.getWidth(); px++) {
                int rgb = piece.getRGB(px, py);
                if ((rgb >>> 24) == 0) {
                    continue;
                }
                sawOpaque = true;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                assertTrue(r >= 250 && g >= 250 && b >= 250,
                        "滑块块在 (" + px + "," + py + ") 混入了底图像素: " + r + "," + g + "," + b);
            }
        }
        assertTrue(sawOpaque, "滑块块整块透明，形状没有画出来");
    }

    // ==================== 夹具 ====================

    private static BufferedImage randomImage(long seed) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(SliderImageGenerator.WIDTH, SliderImageGenerator.HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < image.getHeight(); py++) {
            for (int px = 0; px < image.getWidth(); px++) {
                image.setRGB(px, py, random.nextInt(0x1000000));
            }
        }
        return image;
    }

    private static Map<Integer, Integer> countPixels(BufferedImage image, int x, int y) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int py = y; py < y + SliderImageGenerator.PIECE; py++) {
            for (int px = x; px < x + SliderImageGenerator.PIECE; px++) {
                counts.merge(image.getRGB(px, py), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String describeHole(BufferedImage image, int x, int y) {
        StringBuilder sb = new StringBuilder();
        for (int py = y; py < y + SliderImageGenerator.PIECE; py++) {
            for (int px = x; px < x + SliderImageGenerator.PIECE; px++) {
                sb.append(image.getRGB(px, py)).append(',');
            }
        }
        return sb.toString();
    }

    private static BufferedImage decode(String dataUri) throws IOException {
        String base64 = dataUri.substring(dataUri.indexOf(',') + 1);
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }
}
