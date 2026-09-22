package com.yqz.openblog.user.service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

/**
 * 滑动验证码图片生成：纯 Java2D 程序化绘制，不依赖任何素材图。
 * <p>
 * 零素材带来两个好处：无图片版权风险；每次生成的底图都不同，天然抗重放（缓存固定图的攻击无效）。
 * <p>
 * 缺口坐标由本类随机决定并<b>返回给调用方落库</b>——客户端只拿到 {@code 带缺口底图 + 滑块块}，
 * 要得到 x 必须做像素识别，这是本方案的安全基础（也因此挡不住打码平台）。
 * <p>
 * 无 Spring 依赖，可独立单测。默认用 {@link SecureRandom}：缺口坐标是安全凭证，不可取自
 * 可预测序列；可播种入口仅供测试复现图形，生产不要使用。
 */
public class SliderImageGenerator {

    /** 底图宽（像素）。 */
    public static final int WIDTH = 320;
    /** 底图高（像素）。 */
    public static final int HEIGHT = 160;
    /** 滑块块边长（像素）。 */
    public static final int PIECE = 48;
    /** 缺口 x 下界：避开贴起点——x 过小则滑块与缺口初始位置重叠，用户看不出要拖多远。 */
    static final int MIN_X = 96;
    /** 缺口 x 上界：滑块可拖到的右边界。 */
    static final int MAX_X = WIDTH - PIECE;
    static final int MIN_Y = 16;
    static final int MAX_Y = HEIGHT - PIECE - 16;

    private final Random random;

    public SliderImageGenerator() {
        this(new SecureRandom());
    }

    /** 测试专用：注入可播种随机源以复现图形。 */
    SliderImageGenerator(Random random) {
        this.random = random;
    }

    /**
     * 一次生成的产物：两张 data URI 与缺口真值坐标（真值只应落库，绝不返回给客户端）。
     */
    public record SliderImage(String backgroundDataUri, String pieceDataUri, int x, int y) {
    }

    public SliderImage generate() {
        BufferedImage background = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = background.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackdrop(g);
        } finally {
            g.dispose();
        }

        int x = MIN_X + random.nextInt(MAX_X - MIN_X + 1);
        int y = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
        Shape hole = new RoundRectangle2D.Double(x, y, PIECE, PIECE, 12, 12);

        BufferedImage piece = carvePiece(background, hole, x, y);
        punchHole(background, hole);

        return new SliderImage(toDataUri(background), toDataUri(piece), x, y);
    }

    /** 从底图抠出缺口区域的像素作为滑块块，并描边使其与底图边缘可辨。 */
    private BufferedImage carvePiece(BufferedImage background, Shape hole, int x, int y) {
        BufferedImage piece = new BufferedImage(PIECE, PIECE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = piece.createGraphics();
        try {
            pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 平移到缺口原点后，hole 的绝对坐标即可直接用于绘制与裁剪
            pg.translate(-x, -y);
            pg.setClip(hole);
            pg.drawImage(background, 0, 0, null);
            pg.setClip(null);
            pg.setColor(new Color(255, 255, 255, 180));
            pg.setStroke(new BasicStroke(2f));
            pg.draw(hole);
        } finally {
            pg.dispose();
        }
        return piece;
    }

    /** 在底图上留下半透明缺口，让用户能看出目标位置。 */
    private void punchHole(BufferedImage background, Shape hole) {
        Graphics2D g = background.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(hole);
            g.setColor(new Color(255, 255, 255, 90));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(hole);
        } finally {
            g.dispose();
        }
    }

    /**
     * 随机渐变 + 半透明几何图形 + 斜线纹理。
     * 刻意不用逐像素噪点：噪点会毁掉 PNG 压缩率，base64 体积翻数倍，而抗识别收益为零
     * （缺口位置的安全性来自"服务端存真值"，不来自底图复杂度）。
     */
    private void paintBackdrop(Graphics2D g) {
        g.setPaint(new LinearGradientPaint(0, 0, WIDTH, HEIGHT,
                new float[]{0f, 1f}, new Color[]{randomColor(90, 200), randomColor(60, 170)}));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(pick(0, 255), pick(0, 255), pick(0, 255), 40));
            int w = pick(40, 160);
            int h = pick(30, 120);
            int gx = random.nextInt(Math.max(1, WIDTH - w));
            int gy = random.nextInt(Math.max(1, HEIGHT - h));
            if (random.nextBoolean()) {
                g.fillOval(gx, gy, w, h);
            } else {
                g.fillRoundRect(gx, gy, w, h, 20, 20);
            }
        }

        g.setColor(new Color(255, 255, 255, 28));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = -HEIGHT; i < WIDTH; i += 18) {
            g.drawLine(i, HEIGHT, i + HEIGHT, 0);
        }
    }

    private Color randomColor(int lo, int hi) {
        return new Color(pick(lo, hi), pick(lo, hi), pick(lo, hi));
    }

    private int pick(int lo, int hi) {
        return lo + random.nextInt(hi - lo + 1);
    }

    private static String toDataUri(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("PNG 编码器不可用");
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
