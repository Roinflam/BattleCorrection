package pers.roinflam.battlecorrection.editor;

import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 颜色运算：色标插值、十六进制读写、换成原版 16 色
 * <p>
 * 纯计算，不碰界面，调色盘和渐变改写共用。颜色一律是 {@code 0xRRGGBB}（只看低 24 位）。
 * <p>
 * 1.12.2 的文字只有 16 种颜色，渐变照样按 RGB 在色标之间线性插值，每个字再换成离它最近的原版颜色
 * （{@link #nearestLegacy}）。距离按 CIELAB 色差（CIE76，D65 白点）算，而不是直接比 RGB：
 * RGB 距离对人眼不均匀，红（&c）到蓝（&9）的正中间 AA55AA 离深紫和离灰色一样远，Lab 里它离深紫更近，渐变的过渡色更自然。
 * <p>
 * 渐变另有一条规矩（{@link #sampleLegacy}）：黑、深灰、灰、白这四个灰阶色只在这一段两端的色标里有它时才会出现。
 * 两个鲜艳颜色之间的过渡色饱和度会掉下来，只按距离找常常落到灰色上（青到蓝的中段就是深灰），彩虹字中间冒出一截灰的很难看。
 */
public final class ColorMath {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /**
     * 原版 16 色的 RGB，下标同 {@link LegacyText#VANILLA_COLORS}（字体渲染器 colorCode 表的前 16 项）
     */
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    /**
     * 16 色的 Lab 值（L, a, b 连着放）
     */
    private static final double[] LEGACY_LAB = new double[16 * 3];

    static {
        double[] lab = new double[3];
        for (int i = 0; i < 16; i++) {
            toLab(LEGACY_RGB[i], lab);
            System.arraycopy(lab, 0, LEGACY_LAB, i * 3, 3);
        }
    }

    private ColorMath() {
    }

    // ═══════════════════════════════════════════════════════════════
    // 通道
    // ═══════════════════════════════════════════════════════════════

    /**
     * 把一个值夹到 0~255
     *
     * @param value 值
     * @return 0~255
     */
    public static int clamp8(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    /**
     * 取一个通道
     *
     * @param rgb     颜色
     * @param channel 0 = 红，1 = 绿，2 = 蓝
     * @return 0~255
     */
    public static int channel(int rgb, int channel) {
        return (rgb >> (16 - 8 * channel)) & 0xFF;
    }

    private static double clamp01(double value) {
        return value < 0 ? 0 : Math.min(value, 1.0);
    }

    // ═══════════════════════════════════════════════════════════════
    // 插值
    // ═══════════════════════════════════════════════════════════════

    /**
     * 两个颜色之间按通道线性插值（四舍五入）
     * <p>
     * t = 0 正好是 from、t = 1 正好是 to：两端没有舍入误差，渐变的首尾字一定是色标本身的颜色。
     *
     * @param from 起点
     * @param to   终点
     * @param t    0~1
     * @return 插值结果
     */
    public static int lerp(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 多色标渐变上取一点：色标等距分布在 0~1 上，落在哪两个色标之间就在这两个之间线性插值
     * <p>
     * 只有一个色标时整条都是它；两个色标时和 {@link #lerp} 完全一样（t 乘 1 再取整数部分不引入误差）。
     *
     * @param stops 色标（至少一个）
     * @param t     位置 0~1（超出的夹回来）
     * @return 颜色
     */
    public static int sample(@Nonnull int[] stops, double t) {
        int count = stops.length;
        if (count == 1) {
            return stops[0] & 0xFFFFFF;
        }
        double position = clamp01(t) * (count - 1);
        int segment = Math.min((int) Math.floor(position), count - 2);
        return lerp(stops[segment], stops[segment + 1], position - segment);
    }

    /**
     * 多色标渐变上取一点，换成原版 16 色（写进文字的就是这个颜色）
     * <p>
     * 先和 {@link #sample} 一样在所在那一段里插值，再找最近的原版颜色；候选是 12 个彩色加上这一段两端色标自己的颜色，
     * 灰阶色不是端点就不选。两端正好是原版颜色时，t = 0 / 1 取到的就是色标本身。
     *
     * @param stops 色标（至少一个）
     * @param t     位置 0~1
     * @return 16 色之一的 RGB
     */
    public static int sampleLegacy(@Nonnull int[] stops, double t) {
        int count = stops.length;
        if (count == 1) {
            return quantize(stops[0]);
        }
        double position = clamp01(t) * (count - 1);
        int segment = Math.min((int) Math.floor(position), count - 2);
        int from = stops[segment];
        int to = stops[segment + 1];
        int rgb = lerp(from, to, position - segment);
        return LEGACY_RGB[nearestIndex(rgb, nearestIndex(from), nearestIndex(to))];
    }

    // ═══════════════════════════════════════════════════════════════
    // 原版 16 色
    // ═══════════════════════════════════════════════════════════════

    /**
     * 原版颜色的 RGB
     *
     * @param formatting 颜色（不是颜色时返回白色）
     * @return RGB
     */
    public static int legacyRgb(@Nonnull TextFormatting formatting) {
        int index = formatting.getColorIndex();
        return formatting.isColor() && index >= 0 && index < 16 ? LEGACY_RGB[index] : 0xFFFFFF;
    }

    /**
     * RGB 是不是正好等于某个原版颜色
     *
     * @param rgb 颜色
     * @return 对应的颜色；不是时为 null
     */
    @Nullable
    public static TextFormatting exactLegacy(int rgb) {
        int value = rgb & 0xFFFFFF;
        for (int i = 0; i < 16; i++) {
            if (LEGACY_RGB[i] == value) {
                return LegacyText.VANILLA_COLORS[i];
            }
        }
        return null;
    }

    /**
     * 离这个颜色最近的原版颜色（CIELAB 色差最小；正好是 16 色之一时就是它自己）
     *
     * @param rgb 颜色
     * @return 16 色之一
     */
    @Nonnull
    public static TextFormatting nearestLegacy(int rgb) {
        return LegacyText.VANILLA_COLORS[nearestIndex(rgb)];
    }

    /**
     * 换成最近的原版颜色
     *
     * @param rgb 颜色
     * @return 16 色之一的 RGB
     */
    public static int quantize(int rgb) {
        return LEGACY_RGB[nearestIndex(rgb)];
    }

    private static int nearestIndex(int rgb) {
        return nearestIndex(rgb, -1, -1);
    }

    /**
     * 是不是灰阶色（黑 &0、灰 &7、深灰 &8、白 &f）
     */
    private static boolean achromatic(int index) {
        return index == 0 || index == 7 || index == 8 || index == 15;
    }

    /**
     * 最近的原版颜色
     *
     * @param rgb    颜色
     * @param allowA 渐变用：灰阶色里只允许这两个（-1 = 不限制，16 色都是候选）
     * @param allowB 同上
     * @return 下标
     */
    private static int nearestIndex(int rgb, int allowA, int allowB) {
        boolean limit = allowA >= 0 || allowB >= 0;
        int value = rgb & 0xFFFFFF;
        for (int i = 0; i < 16; i++) {
            if (LEGACY_RGB[i] == value && (!limit || !achromatic(i) || i == allowA || i == allowB)) {
                return i;
            }
        }
        double[] lab = new double[3];
        toLab(value, lab);
        int best = 15;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            if (limit && achromatic(i) && i != allowA && i != allowB) {
                continue;
            }
            double dl = lab[0] - LEGACY_LAB[i * 3];
            double da = lab[1] - LEGACY_LAB[i * 3 + 1];
            double db = lab[2] - LEGACY_LAB[i * 3 + 2];
            double distance = dl * dl + da * da + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * sRGB → CIELAB（D65）
     *
     * @param rgb 颜色
     * @param out 长度至少 3：{L, a, b}
     */
    private static void toLab(int rgb, @Nonnull double[] out) {
        double r = linear(((rgb >> 16) & 0xFF) / 255.0);
        double g = linear(((rgb >> 8) & 0xFF) / 255.0);
        double b = linear((rgb & 0xFF) / 255.0);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = labF(x);
        double fy = labF(y);
        double fz = labF(z);
        out[0] = 116.0 * fy - 16.0;
        out[1] = 500.0 * (fx - fy);
        out[2] = 200.0 * (fy - fz);
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 216.0 / 24389.0 ? Math.cbrt(t) : (24389.0 / 27.0 * t + 16.0) / 116.0;
    }

    // ═══════════════════════════════════════════════════════════════
    // 十六进制
    // ═══════════════════════════════════════════════════════════════

    /**
     * 颜色 → 6 位大写十六进制（不带 #）
     *
     * @param rgb 颜色
     * @return 例如 FFAA00
     */
    @Nonnull
    public static String hex(int rgb) {
        char[] out = new char[6];
        for (int i = 5; i >= 0; i--) {
            out[i] = HEX_DIGITS[rgb & 0xF];
            rgb >>= 4;
        }
        return new String(out);
    }

    /**
     * 读一个写成十六进制的颜色：{@code RRGGBB}，前面可以带 {@code #}、{@code &#}、{@code 0x}，前后空白忽略
     *
     * @param text 文本
     * @return RGB；不是这种写法时返回 -1
     */
    public static int parseHex(@Nonnull String text) {
        String value = text.trim();
        if (value.startsWith("&#")) {
            value = value.substring(2);
        } else if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }
        if (value.length() != 6) {
            return -1;
        }
        int rgb = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(value.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            rgb = (rgb << 4) | digit;
        }
        return rgb;
    }

    // ═══════════════════════════════════════════════════════════════
    // 显示用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 感知亮度 0~1（按 Rec.601 的权重，和输入框里给暗色代码提亮用的算法一致）
     *
     * @param rgb 颜色
     * @return 亮度
     */
    public static double luminance(int rgb) {
        return (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF)) / 255.0;
    }

    /**
     * 画在这个颜色上面的标记该用黑还是白（ARGB）
     *
     * @param rgb 底色
     * @return 0xFF000000 或 0xFFFFFFFF
     */
    public static int contrast(int rgb) {
        return luminance(rgb) > 0.55 ? 0xFF000000 : 0xFFFFFFFF;
    }
}
