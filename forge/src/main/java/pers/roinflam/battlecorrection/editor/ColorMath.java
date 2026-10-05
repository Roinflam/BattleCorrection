package pers.roinflam.battlecorrection.editor;

import javax.annotation.Nonnull;

/**
 * 颜色运算：RGB 与 HSV 互转、色标插值、十六进制读写
 * <p>
 * 纯计算，不碰界面，调色盘和渐变改写共用。颜色一律是 {@code 0xRRGGBB}（只看低 24 位）；
 * HSV 用 double：色相 0~360（360 和 0 是同一个红），饱和度和明度 0~1。
 * 用 double 而不是 float：RGB → HSV → RGB 要逐位还原（全部 1677 万种颜色都对），float 的精度在边界上会差一。
 */
public final class ColorMath {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

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
     * 三个通道拼成颜色（每个通道先夹到 0~255）
     *
     * @param r 红
     * @param g 绿
     * @param b 蓝
     * @return RGB
     */
    public static int rgb(int r, int g, int b) {
        return (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
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

    /**
     * 换掉一个通道
     *
     * @param rgb     颜色
     * @param channel 0 = 红，1 = 绿，2 = 蓝
     * @param value   新值（夹到 0~255）
     * @return 新颜色
     */
    public static int withChannel(int rgb, int channel, int value) {
        int shift = 16 - 8 * channel;
        return (rgb & 0xFFFFFF & ~(0xFF << shift)) | (clamp8(value) << shift);
    }

    // ═══════════════════════════════════════════════════════════════
    // HSV
    // ═══════════════════════════════════════════════════════════════

    /**
     * HSV → RGB
     *
     * @param hue        色相（度，超出 0~360 的按周期折回）
     * @param saturation 饱和度 0~1
     * @param value      明度 0~1
     * @return RGB
     */
    public static int hsvToRgb(double hue, double saturation, double value) {
        double s = clamp01(saturation);
        double v = clamp01(value);
        double h = hue % 360.0;
        if (h < 0) {
            h += 360.0;
        }
        double sector = h / 60.0;
        int index = (int) Math.floor(sector);
        double f = sector - index;
        double p = v * (1.0 - s);
        double q = v * (1.0 - s * f);
        double t = v * (1.0 - s * (1.0 - f));
        double r;
        double g;
        double b;
        switch (index) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return ((int) Math.round(r * 255.0) << 16) | ((int) Math.round(g * 255.0) << 8) | (int) Math.round(b * 255.0);
    }

    /**
     * RGB → HSV，结果写进调用方给的数组（拖动取色时每次鼠标移动都要算，不分配）
     * <p>
     * 灰色（饱和度 0）没有色相，这里给 0；黑色没有饱和度，给 0。调色盘拖到灰色 / 黑色时要保住原来的色相和饱和度，
     * 由调用方用 {@link #hasHue} / 明度判断要不要沿用旧值。
     *
     * @param rgb 颜色
     * @param out 长度至少 3：{色相 0~360（不含 360）, 饱和度, 明度}
     */
    public static void rgbToHsv(int rgb, @Nonnull double[] out) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        double hue;
        if (delta <= 0) {
            hue = 0;
        } else if (max == r) {
            hue = 60.0 * ((g - b) / delta);
        } else if (max == g) {
            hue = 60.0 * ((b - r) / delta + 2.0);
        } else {
            hue = 60.0 * ((r - g) / delta + 4.0);
        }
        if (hue < 0) {
            hue += 360.0;
        }
        if (hue >= 360.0) {
            hue -= 360.0;
        }
        out[0] = hue;
        out[1] = max <= 0 ? 0 : delta / max;
        out[2] = max;
    }

    /**
     * 这个颜色有没有确定的色相（不是灰色 / 黑白）
     *
     * @param rgb 颜色
     * @return true = 有
     */
    public static boolean hasHue(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r != g || g != b;
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
     * <p>
     * 只认规整的写法；粘贴进来的各种杂七杂八的格式（{@code §x§f§f...} 之类）交给 HexColorField 的粘贴识别。
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
