package pers.roinflam.battlecorrection.client.editor.snbt;

import javax.annotation.Nonnull;

/**
 * § 颜色码相关的纯逻辑：哪些字符是合法代码、对应什么颜色、& 和 § 互转
 * <p>
 * 1.12.2 的字体渲染器只认 0-9 a-f k-o r 这 22 个代码（不分大小写），没有十六进制颜色，这里也只认这些。
 * 模组自己往 TextFormatting 里塞的代码不在 SNBT 页处理范围里：SNBT 里改的是原始文本，
 * 转换按钮要是把 {@code &!} 这种也转了，玩家很难预料结果。
 */
public final class ColorCodes {

    /**
     * 原版颜色码前缀（写成数值，源码里不放这个字符本身）
     */
    public static final char SECTION = (char) 0xA7;

    /**
     * 常用的替代前缀
     */
    public static final char AMPERSAND = '&';

    /**
     * 0-f 十六种颜色的 RGB，和 1.12.2 FontRenderer 算出来的颜色表一致
     */
    private static final int[] RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private ColorCodes() {
    }

    /**
     * 是否是合法的代码字符：0-9 a-f k-o r，不分大小写
     *
     * @param c 字符
     * @return true = 合法
     */
    public static boolean isLegalCode(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || (c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O')
                || c == 'r' || c == 'R';
    }

    /**
     * 颜色代码的序号
     *
     * @param c 代码字符
     * @return 0-15；格式代码（k-o、r）和非法字符返回 -1
     */
    public static int colorIndex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    /**
     * 是不是重置码 r
     */
    public static boolean isReset(char c) {
        return c == 'r' || c == 'R';
    }

    /**
     * 颜色序号对应的 RGB（不带透明度）
     *
     * @param index 0-15
     * @return RGB
     */
    public static int rgb(int index) {
        return RGB[index];
    }

    /**
     * 把 [from, to) 里的"前缀 + 合法代码"换成"新前缀 + 同一个代码"
     * <p>
     * 只看前缀字符在不在范围里；代码字符可以在范围外一格（选区刚好切在两个字符中间时，本意还是要转这一处）。
     * 代码字符本身不动，大小写原样保留。
     *
     * @param text  全文
     * @param from  起点
     * @param to    终点
     * @param from1 原前缀（& 或 §）
     * @param to1   新前缀
     * @param count 出参：count[0] = 转换了几处
     * @return [from, to) 转换后的文本
     */
    @Nonnull
    public static String convert(@Nonnull CharSequence text, int from, int to, char from1, char to1, @Nonnull int[] count) {
        StringBuilder out = new StringBuilder(to - from);
        int length = text.length();
        int converted = 0;
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c == from1 && i + 1 < length && isLegalCode(text.charAt(i + 1))) {
                out.append(to1);
                converted++;
            } else {
                out.append(c);
            }
        }
        count[0] = converted;
        return out.toString();
    }
}
