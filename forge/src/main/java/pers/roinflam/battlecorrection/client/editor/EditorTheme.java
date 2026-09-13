package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * 物品编辑器的主题：科技风（近黑底 + 青色电光）
 * <p>
 * 配色和画法都集中在这里。风格要点：
 * <ul>
 *   <li>面板和按钮都是切角/六边轮廓，不用圆角——直线切角在像素字体旁边更硬朗</li>
 *   <li>底色接近纯黑但不是纯黑（纯黑上亮色会发光晕，字边糊）</li>
 *   <li>凹槽（输入框、列表）有内阴影 + 隔行扫描线，像屏幕不像实物</li>
 *   <li>四角画 HUD 式的准线角饰，面板边框上有一颗青光沿边巡游</li>
 * </ul>
 * 原版 GuiGraphics 只能画矩形，所有形状都是一行行矩形扫出来的，每帧几百次 fill 以内。
 * 所有颜色都是 ARGB。
 */
public final class EditorTheme {

    // ── 强调色族（青色电光） ──
    public static final int ACCENT = 0xFF37C8FF;
    public static final int ACCENT_HOVER = 0xFF7FE0FF;
    public static final int ACCENT_HI = 0xFFC8F4FF;
    public static final int ACCENT_MID = 0xFF1C7BA0;
    public static final int ACCENT_FAINT = 0xFF0E4358;
    public static final int ACCENT_SOFT = 0x3837C8FF;
    public static final int ACCENT_GLOW = 0x1E37C8FF;

    // ── 基材 ──
    public static final int PANEL_BG = 0xFF0B1116;
    public static final int PANEL_LIGHT = 0xFF121B23;
    public static final int PANEL_EDGE_HI = 0xFF2C4252;
    public static final int PANEL_EDGE_LO = 0xFF05080B;
    public static final int PANEL_BORDER = 0xFF1F5C74;
    public static final int PANEL_SHADOW = 0x8A000000;
    public static final int SIDEBAR_BG = 0xFF080D11;
    public static final int CONTENT_BG = 0xFF0B1116;
    public static final int CARD_BG = 0xFF0E161D;
    public static final int CARD_BORDER = 0xFF1E3542;
    public static final int DIVIDER = 0xFF193443;

    // ── 凹槽 ──
    public static final int GROOVE = 0xFF04070A;
    public static final int WELL = 0xFF070C10;
    public static final int WELL_TOP = 0xFF030507;
    public static final int WELL_LIGHT = 0xFF1A2C38;
    public static final int SCANLINE = 0x1437C8FF;

    // ── 文字 ──
    public static final int TEXT = 0xFFDDF2FA;
    public static final int TEXT_MUTED = 0xFF92B4C4;
    public static final int TEXT_DIM = 0xFF6F8B9A;
    public static final int TEXT_ON_ACCENT = 0xFF04141C;

    // ── 状态色 ──
    public static final int DANGER = 0xFFFF5C7A;
    public static final int DANGER_HOVER = 0xFFFF8AA0;
    public static final int SUCCESS = 0xFF5CF2A6;
    public static final int WARNING = 0xFFF2C25C;

    // ── 按钮 / 输入框 ──
    public static final int BUTTON_BG = 0xFF13212B;
    public static final int BUTTON_HOVER = 0xFF1B3242;
    public static final int BUTTON_BORDER = 0xFF2A4E62;
    public static final int BUTTON_DISABLED_BG = 0xFF0E161D;
    public static final int BUTTON_HI = 0x40FFFFFF;
    public static final int BUTTON_LO = 0x60000000;
    public static final int INPUT_BG = 0xFF070C10;
    public static final int INPUT_BORDER = 0xFF244252;
    public static final int INPUT_BORDER_FOCUS = ACCENT;

    /**
     * 行高：单行文字 + 上下留白
     */
    public static final int LINE_HEIGHT = 12;

    /**
     * 面板切角大小
     */
    public static final int PANEL_CHAMFER = 6;

    /**
     * 扫描线最多画多少行（大列表每帧别画太多）
     */
    private static final int SCANLINE_BUDGET = 140;

    private EditorTheme() {
    }

    // ═══════════════════════════════════════════════════════════════
    // 颜色
    // ═══════════════════════════════════════════════════════════════

    /**
     * 按系数缩放透明度
     *
     * @param argb 颜色
     * @param a    系数 0~1
     * @return 新颜色
     */
    public static int alpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, (int) (base * a)));
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 两个颜色之间线性插值
     *
     * @param a 起始
     * @param b 结束
     * @param t 系数 0~1
     * @return 插值结果
     */
    public static int lerp(int a, int b, float t) {
        float k = Math.max(0F, Math.min(1F, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * k) << 24)
                | ((int) (ar + (br - ar) * k) << 16)
                | ((int) (ag + (bg - ag) * k) << 8)
                | (int) (ab + (bb - ab) * k);
    }

    /**
     * 按启用/悬停状态取按钮底色
     *
     * @param hovered 是否悬停
     * @param active  是否可用
     * @return 颜色
     */
    public static int buttonBackground(boolean hovered, boolean active) {
        if (!active) {
            return BUTTON_DISABLED_BG;
        }
        return hovered ? BUTTON_HOVER : BUTTON_BG;
    }

    // ═══════════════════════════════════════════════════════════════
    // 基本形状
    // ═══════════════════════════════════════════════════════════════

    /**
     * 矩形（左上角 + 宽高，比 fill 的两个坐标好算）
     */
    public static void rect(@Nonnull GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, color);
    }

    /**
     * 切角矩形（实心）
     *
     * @param c 切角大小（像素）
     */
    public static void chamfer(@Nonnull GuiGraphics g, int x, int y, int w, int h, int c, int color) {
        c = Math.min(c, Math.min(w, h) / 2);
        if (c <= 0) {
            rect(g, x, y, w, h, color);
            return;
        }
        for (int i = 0; i < c; i++) {
            int ins = c - i;
            rect(g, x + ins, y + i, w - 2 * ins, 1, color);
            rect(g, x + ins, y + h - 1 - i, w - 2 * ins, 1, color);
        }
        rect(g, x, y + c, w, h - 2 * c, color);
    }

    /**
     * 切角矩形（描边，1px）
     */
    public static void chamferOutline(@Nonnull GuiGraphics g, int x, int y, int w, int h, int c, int color) {
        c = Math.min(c, Math.min(w, h) / 2);
        rect(g, x + c, y, w - 2 * c, 1, color);
        rect(g, x + c, y + h - 1, w - 2 * c, 1, color);
        rect(g, x, y + c, 1, h - 2 * c, color);
        rect(g, x + w - 1, y + c, 1, h - 2 * c, color);
        for (int i = 0; i < c; i++) {
            rect(g, x + c - 1 - i, y + i, 1, 1, color);
            rect(g, x + w - c + i, y + i, 1, 1, color);
            rect(g, x + c - 1 - i, y + h - 1 - i, 1, 1, color);
            rect(g, x + w - c + i, y + h - 1 - i, 1, 1, color);
        }
    }

    /**
     * 六边形（上下两端向内收的按钮轮廓），实心
     */
    public static void hex(@Nonnull GuiGraphics g, int x, int y, int w, int h, int color) {
        int depth = Math.min(3, h / 4);
        int mid = Math.max(1, (h - 1) / 2);
        for (int i = 0; i < h; i++) {
            int ins = depth * Math.abs(i - mid) / mid;
            rect(g, x + ins, y + i, w - 2 * ins, 1, color);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 组合部件
    // ═══════════════════════════════════════════════════════════════

    /**
     * 主面板：投影 + 切角沟槽 + 底色 + 斜面明暗边 + 四角准线
     */
    public static void panel(@Nonnull GuiGraphics g, int x, int y, int w, int h) {
        int c = PANEL_CHAMFER;
        chamfer(g, x + 3, y + 4, w, h, c, PANEL_SHADOW);
        chamfer(g, x, y, w, h, c, PANEL_BORDER);
        chamfer(g, x + 1, y + 1, w - 2, h - 2, c - 1, PANEL_EDGE_LO);
        chamfer(g, x + 2, y + 2, w - 4, h - 4, c - 2, PANEL_BG);
        // 上缘一道亮线、下缘一道暗线：薄薄的斜面
        rect(g, x + c + 1, y + 2, w - 2 * c - 2, 1, PANEL_EDGE_HI);
        rect(g, x + c + 1, y + h - 3, w - 2 * c - 2, 1, PANEL_EDGE_LO);
        cornerReticle(g, x + 4, y + 4, 1, 1, 1F);
        cornerReticle(g, x + w - 5, y + 4, -1, 1, 1F);
        cornerReticle(g, x + 4, y + h - 5, 1, -1, 1F);
        cornerReticle(g, x + w - 5, y + h - 5, -1, -1, 1F);
    }

    /**
     * 凹槽：输入框、列表这类"屏幕"区域。沟槽外圈 + 内阴影 + 扫描线
     *
     * @param scanlines 是否铺扫描线（单行输入框不用）
     */
    public static void well(@Nonnull GuiGraphics g, int x, int y, int w, int h, boolean scanlines) {
        rect(g, x, y, w, h, GROOVE);
        rect(g, x + 1, y + 1, w - 2, h - 2, WELL);
        rect(g, x + 1, y + 1, w - 2, 1, WELL_TOP);
        rect(g, x + 1, y + 1, 1, h - 2, WELL_TOP);
        rect(g, x + 1, y + h - 2, w - 2, 1, WELL_LIGHT);
        rect(g, x + w - 2, y + 1, 1, h - 2, WELL_LIGHT);
        if (scanlines) {
            scanlines(g, x + 2, y + 2, w - 4, h - 4);
        }
    }

    /**
     * 凹槽的描边层（画在内容之上，用于焦点态换色）
     */
    public static void wellOutline(@Nonnull GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }

    /**
     * 隔行扫描线，行数超过预算就拉大间距
     */
    public static void scanlines(@Nonnull GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 2) {
            return;
        }
        int step = 2;
        while (h / step > SCANLINE_BUDGET) {
            step++;
        }
        for (int row = 1; row < h; row += step) {
            rect(g, x, y + row, w, 1, SCANLINE);
        }
    }

    /**
     * 卡片：比面板浅一点的托底 + 细描边（预览栏用）
     */
    public static void card(@Nonnull GuiGraphics g, int x, int y, int w, int h) {
        chamfer(g, x, y, w, h, 3, CARD_BORDER);
        chamfer(g, x + 1, y + 1, w - 2, h - 2, 2, CARD_BG);
        rect(g, x + 3, y + 1, w - 6, 1, PANEL_EDGE_HI);
    }

    /**
     * 水平分割线：中间一段暗线，左端一小截强调色
     */
    public static void divider(@Nonnull GuiGraphics g, int x, int y, int width) {
        rect(g, x, y, width, 1, DIVIDER);
        rect(g, x, y, Math.min(18, width), 1, ACCENT_MID);
    }

    /**
     * 小标题下的强调线：一小段亮线 + 渐隐的长线
     */
    public static void headingLine(@Nonnull GuiGraphics g, int x, int y, int width) {
        int bright = Math.min(24, width);
        rect(g, x, y, bright, 1, ACCENT);
        int rest = width - bright;
        if (rest > 0) {
            for (int i = 0; i < rest; i++) {
                float k = 1F - i / (float) rest;
                int color = alpha(ACCENT_MID, 0.15F + 0.85F * k);
                rect(g, x + bright + i, y, 1, 1, color);
            }
        }
    }

    /**
     * 六边按钮：描边 + 底色 + 上亮下暗的斜面
     */
    public static void button(@Nonnull GuiGraphics g, int x, int y, int w, int h, int edge, int fill) {
        if (w < 6 || h < 6) {
            rect(g, x, y, w, h, edge);
            rect(g, x + 1, y + 1, w - 2, h - 2, fill);
            return;
        }
        hex(g, x, y, w, h, edge);
        hex(g, x + 1, y + 1, w - 2, h - 2, fill);
        rect(g, x + 3, y + 1, w - 6, 1, BUTTON_HI);
        rect(g, x + 3, y + h - 2, w - 6, 1, BUTTON_LO);
    }

    /**
     * HUD 准线角饰：两条臂 + 刻度 + 臂端短横 + 空心状态方块
     *
     * @param sx 水平方向（1 向右，-1 向左）
     * @param sy 垂直方向（1 向下，-1 向上）
     * @param a  透明度系数
     */
    public static void cornerReticle(@Nonnull GuiGraphics g, int x, int y, int sx, int sy, float a) {
        int gd = alpha(ACCENT, a);
        int gm = alpha(ACCENT_MID, a);
        int gh = alpha(ACCENT_HI, a);
        rect(g, Math.min(x, x + sx * 13), y, 14, 1, gd);
        rect(g, x, Math.min(y, y + sy * 13), 1, 14, gd);
        for (int i = 1; i <= 3; i++) {
            rect(g, x + sx * (i * 4), Math.min(y + sy, y + sy * 2), 1, 2, gm);
            rect(g, Math.min(x + sx, x + sx * 2), y + sy * (i * 4), 2, 1, gm);
        }
        rect(g, x + sx * 14, Math.min(y - sy, y + sy), 1, 3, gd);
        rect(g, Math.min(x - sx, x + sx), y + sy * 14, 3, 1, gd);
        int bx = Math.min(x + sx * 6, x + sx * 10);
        int by = Math.min(y + sy * 6, y + sy * 10);
        rect(g, bx, by, 5, 1, gm);
        rect(g, bx, by + 4, 5, 1, gm);
        rect(g, bx, by + 1, 1, 3, gm);
        rect(g, bx + 4, by + 1, 1, 3, gm);
        rect(g, bx + 2, by + 2, 1, 1, gh);
    }

    /**
     * 巡游光点：一小段亮线沿矩形边框循环走位，带拖尾
     */
    public static void traceBorder(@Nonnull GuiGraphics g, int x, int y, int w, int h) {
        long timeMs = Util.getMillis();
        int rgb = ACCENT & 0x00FFFFFF;
        final int period = 4200;
        int perimeter = 2 * (w + h);
        if (perimeter <= 0) {
            return;
        }
        int head = (int) ((timeMs % period) * (long) perimeter / period);
        final int tail = 56;
        for (int i = 0; i < tail; i++) {
            float k = 1F - i / (float) tail;
            int alpha = (int) (200 * k * k);
            if (alpha <= 0) {
                continue;
            }
            int t = ((head - i) % perimeter + perimeter) % perimeter;
            int px;
            int py;
            if (t < w) {
                px = x + t;
                py = y;
            } else if (t < w + h) {
                px = x + w - 1;
                py = y + (t - w);
            } else if (t < 2 * w + h) {
                px = x + w - 1 - (t - w - h);
                py = y + h - 1;
            } else {
                px = x;
                py = y + h - 1 - (t - 2 * w - h);
            }
            rect(g, px, py, 1, 1, (alpha << 24) | rgb);
        }
    }

    /**
     * 呼吸系数 0~1（约 2.4 秒一个周期），用于强调元素的明暗起伏
     */
    public static float pulse() {
        double t = (Util.getMillis() % 2400L) / 2400.0D;
        return (float) (0.5D + 0.5D * Math.sin(t * Math.PI * 2));
    }
}
