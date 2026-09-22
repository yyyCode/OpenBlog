package com.yqz.openblog.user.service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

/**
 * 滑动验证码图片生成：纯 Java2D 程序化绘制，不依赖任何素材图。
 * <p>
 * 零素材带来两个好处：无图片版权风险；每次生成的底图都不同，天然抗重放（缓存固定图的攻击无效）。
 * <p>
 * 缺口坐标由本类随机决定并<b>返回给调用方落库</b>——客户端只拿到底图与滑块块两张图，
 * 要得到 x 必须做像素识别。
 * <p>
 * <b>缺口为什么这么画</b>——以下每一条都对应一种实测有效的破解手法（数字见
 * {@code SliderImageGeneratorTest#harden_* }），改动前请先读完：
 * <ol>
 *   <li><b>缺口是原像素的重排，不做任何平均或着色</b>——纯置换不增删像素，缺口窗口内像素的
 *       多重集与周围完全一致，亮度/饱和度/方差在数学上精确不变，任何一阶或二阶统计量都没有
 *       信号。这条是踩了三次坑才到的位置：压灰度让「最低饱和度」argmin 命中 176/200；二维
 *       马赛克与横向涂抹都削弱某方向高频，让「局部方差最小」命中 50/200 上下。教训是
 *       <b>只要对像素做平均，就必然留下可检测的统计量</b>。</li>
 *   <li><b>滑块块不含底图任何像素，只画形状示意块</b>——块与缺口在内容上<b>构造性地不相关</b>，
 *       因此「拿块做模板匹配定位落点」这条路直接消失。这条一旦松动代价极大：块只要取自底图
 *       （哪怕先做模糊/马赛克等低通处理），模板匹配命中 173/200。用户对齐的是轮廓而非内容，
 *       所以块不需要那块像素。</li>
 *   <li><b>底图铺满密集纹理，另留若干成片平滑区</b>——纹理让「缺口是全图唯一平滑的一块」
 *       不成立；平滑区则让「局部方差最小」这个统计量出现多个自然候选。两者缺一，方差 argmin
 *       的命中率都会抬头（见上一条的实测数字）。</li>
 *   <li><b>形状是半径抖动的不规则多边形，且不描边</b>——矩形核扫描失配；白色描边曾是最强的
 *       定位特征（一条已知形状的高对比闭合曲线），已移除。</li>
 * </ol>
 * 强度上限不变（见 {@link SliderVerificationService} 类注释）：会写轮廓/纹理分析的人仍能解出 x
 * ——这是滑块验证码这一形态的固有上限，不是本类的实现缺陷。加固把破解成本从「一次 argmin」
 * 抬到「真正写一遍局部统计或轮廓分析」，并让单次命中率落到盲猜基线附近。
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

    /**
     * 缺口打乱用的图块边长（像素），决定缺口的「碎块感」。
     * 块太小则碎片过细、接缝太密，缺口看上去像噪点；块太大则接近原图，肉眼难辨。
     * 8px 是实测下来既明显可见、碎片又不过碎的一档。
     */
    private static final int SCRAMBLE_TILE = 8;
    /** 底图纹理元素个数。 */
    private static final int TEXTURE_ELEMENTS = 520;
    /** 成片平滑区个数：给「局部方差最小」制造自然候选，是压低方差 argmin 的关键。 */
    private static final int SMOOTH_REGIONS = 4;
    /** 缺口轮廓顶点数。 */
    private static final int BLOB_VERTICES = 14;

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
        Shape hole = blob(x, y);

        BufferedImage piece = carvePiece(hole, x, y);
        renderHole(background, hole, x, y);

        return new SliderImage(toDataUri(background), toDataUri(piece), x, y);
    }

    /**
     * 不规则闭合轮廓：顶点在 0.80~1.0 倍半边长之间抖动。
     * 既不是矩形也不是圆，矩形核扫描会失配；抖动下限保证形状始终落在 {@link #PIECE} 见方的框内，
     * 因此滑块块图像不需要额外留边（否则前端定位要多算一个偏移量）。
     */
    private Shape blob(int x, int y) {
        double cx = x + PIECE / 2.0;
        double cy = y + PIECE / 2.0;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < BLOB_VERTICES; i++) {
            double angle = 2 * Math.PI * i / BLOB_VERTICES;
            double radius = PIECE / 2.0 * (0.80 + random.nextDouble() * 0.20);
            double px = cx + Math.cos(angle) * radius;
            double py = cy + Math.sin(angle) * radius;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.closePath();
        return path;
    }

    /**
     * 滑块块：只画缺口形状的示意块，<b>不含底图的任何像素</b>。
     * <p>
     * 这是刻意的，不是省事。块里一旦带上缺口处的真实像素（哪怕先做过模糊/马赛克等低通处理），
     * 拿块到底图上做模板匹配就能直接命中落点——实测抠真实像素的版本命中 173/200，
     * 换成纯示意块后相关性归零。用户对齐的是<b>轮廓</b>而不是内容，所以不需要那块像素。
     */
    private BufferedImage carvePiece(Shape hole, int x, int y) {
        BufferedImage piece = new BufferedImage(PIECE, PIECE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = piece.createGraphics();
        try {
            pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 平移到缺口原点后，hole 的绝对坐标即可直接用于绘制
            pg.translate(-x, -y);
            pg.setColor(new Color(255, 255, 255, 90));
            pg.fill(hole);
            pg.setColor(new Color(255, 255, 255, 220));
            pg.setStroke(new BasicStroke(1.6f));
            pg.draw(hole);
        } finally {
            pg.dispose();
        }
        return piece;
    }

    /**
     * 把缺口盖成打乱图块。
     * <p>
     * 关键在于<b>统计量精确保持</b>——缺口处是原像素的重新排列，亮度/饱和度/方差的均值与方差
     * 都与邻域一致，任何一阶或二阶统计量都定位不到它。
     * <p>
     * 刻意不描边：白色描边曾是最强的定位特征（一条已知形状的高对比闭合曲线）。
     */
    private void renderHole(BufferedImage background, Shape hole, int x, int y) {
        BufferedImage patch = scramblePatch(background, x, y);
        Graphics2D g = background.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(hole);
            g.drawImage(patch, 0, 0, null);
            g.setClip(null);
        } finally {
            g.dispose();
        }
    }

    /**
     * 生成覆盖缺口外接矩形的打乱图层，其余区域透明（坐标与底图一致，便于直接叠加）。
     * <p>
     * 做法是把缺口自身的 {@link #SCRAMBLE_TILE} 见方图块<b>重排</b>——这是纯置换，不增删
     * 任何像素。因此缺口窗口内像素的<b>多重集完全不变</b>，亮度均值、饱和度均值、方差这些量
     * 在数学上精确等于原图，而不是"接近"。差分定位因此不是"变难了"而是"没有信号"——实测
     * 亮度/饱和度的 min 与 max 四个方向全部回落到盲猜基线。
     * <p>
     * 此前试过两条更直觉的路，都被实测否掉，不要退回去：二维马赛克（局部方差远低于底图，
     * 方差 argmin 命中 50/200）、横向涂抹（横向高频被抹掉，同样掉方差，命中 52/200）。
     * 只要对像素做了平均，就必然削弱某个方向的高频，也就必然留下可检测的统计量。
     * 纯置换是唯一不留下这类信号的做法，代价是图块接缝可见——那需要定向的接缝/轮廓分析才能定位。
     * <p>
     * 包级可见而非私有：{@code SliderImageGeneratorTest} 要直接断言"像素多重集不变"这条核心
     * 不变式。走 {@link #generate} 拿到的是叠加后的图，无法区分缺口像素与周围像素，验证不了。
     */
    BufferedImage scramblePatch(BufferedImage src, int x, int y) {
        int side = PIECE / SCRAMBLE_TILE;
        int tiles = side * side;
        int[] order = new int[tiles];
        for (int i = 0; i < tiles; i++) {
            order[i] = i;
        }
        do {
            for (int i = tiles - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
            }
            // 恒等置换等于没打乱：随机撞上它的概率是 1/tiles!，但代价为零，干脆排除
        } while (isIdentity(order));

        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int t = 0; t < tiles; t++) {
            int from = order[t];
            int srcX = x + (from % side) * SCRAMBLE_TILE;
            int srcY = y + (from / side) * SCRAMBLE_TILE;
            int dstX = x + (t % side) * SCRAMBLE_TILE;
            int dstY = y + (t / side) * SCRAMBLE_TILE;
            for (int dy = 0; dy < SCRAMBLE_TILE; dy++) {
                for (int dx = 0; dx < SCRAMBLE_TILE; dx++) {
                    // 源图是 TYPE_INT_RGB，getRGB 回来 alpha 位为 0，必须补成不透明，
                    // 否则叠加时整块透明、缺口直接消失
                    out.setRGB(dstX + dx, dstY + dy, src.getRGB(srcX + dx, srcY + dy) | 0xFF000000);
                }
            }
        }
        return out;
    }

    private static boolean isIdentity(int[] order) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * 随机渐变 + 半透明几何图形 + 密集纹理 + 成片平滑区 + 斜线。
     * <p>
     * 纹理与平滑区是一对，缺一不可：只铺纹理则缺口是「全图唯一平滑的一块」，局部方差扫描一次
     * 就定位到；只留平滑区则底图本身太干净，缺口的高频缺失同样刺眼。两者并存后，「平滑」这个
     * 统计量在底图上自然有多个候选，方差 argmin 的命中率被压回盲猜基线附近。
     * <p>
     * 代价是 PNG 体积上升（底图从约 26KB 涨到约 45KB，体积上界由
     * {@code SliderImageGeneratorTest} 的断言钉住）。这是有意的取舍——程序化生成若不付这份体积，
     * 缺口的可定位性就压不下去。仍然不用逐像素噪点：那会让体积再翻数倍，收益与密集色块相同。
     */
    private void paintBackdrop(Graphics2D g) {
        g.setPaint(new LinearGradientPaint(0, 0, WIDTH, HEIGHT,
                new float[]{0f, 1f}, new Color[]{randomColor(30, 235), randomColor(20, 200)}));
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

        // 成片平滑区：低对比度纯色罩，纹理层会跳过落在其中的元素，因此区内保持平坦。
        // 刻意做大做淡：它们的作用是给「局部方差最小」制造自然候选，若做得小而显眼，
        // 用户会误把它们当成缺口去对齐，反而更糟。
        List<Ellipse2D.Double> smooth = new ArrayList<>(SMOOTH_REGIONS);
        for (int i = 0; i < SMOOTH_REGIONS; i++) {
            int w = pick(110, 190);
            int h = pick(70, 120);
            Ellipse2D.Double region = new Ellipse2D.Double(
                    random.nextInt(Math.max(1, WIDTH - w)), random.nextInt(Math.max(1, HEIGHT - h)), w, h);
            smooth.add(region);
            g.setColor(new Color(pick(60, 220), pick(60, 220), pick(60, 220), 32));
            g.fill(region);
        }

        for (int i = 0; i < TEXTURE_ELEMENTS; i++) {
            int px = random.nextInt(WIDTH);
            int py = random.nextInt(HEIGHT);
            if (insideAny(smooth, px, py)) {
                continue;
            }
            int size = 2 + random.nextInt(5);
            int v = pick(0, 255);
            boolean neutral = random.nextInt(3) == 0;
            g.setColor(neutral
                    ? new Color(v, v, v, pick(30, 100))
                    : new Color(v, pick(0, 255), pick(0, 255), pick(30, 100)));
            if (random.nextBoolean()) {
                g.fillOval(px, py, size, size);
            } else {
                g.fillRect(px, py, size, size);
            }
        }

        g.setColor(new Color(255, 255, 255, 28));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = -HEIGHT; i < WIDTH; i += 18) {
            g.drawLine(i, HEIGHT, i + HEIGHT, 0);
        }
    }

    private static boolean insideAny(List<Ellipse2D.Double> regions, int px, int py) {
        for (Ellipse2D.Double region : regions) {
            if (region.contains(px, py)) {
                return true;
            }
        }
        return false;
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
