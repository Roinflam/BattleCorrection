package pers.roinflam.battlecorrection.editor;

import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * 颜色码文本的语法层：编辑器里用 {@code &a} 这种写法，物品里存的是 {@code §a}
 * <p>
 * 为什么不直接编辑 §：原版输入框会把 § 当非法字符过滤掉（ChatAllowedCharacters.isAllowedCharacter），
 * 打不进也粘不进。用 & 代替是 Bukkit 那边的老习惯，服主都熟。
 * <p>
 * 编辑文本（editable）的写法：
 * <ul>
 *   <li>{@code &} + 代码字符：颜色 / 格式 / 重置（0-9 a-f k-o r 不分大小写）</li>
 *   <li>{@code &&}：一个字面的 &。只在 & 后面紧跟着代码字符或 & 时才需要，单独的 & 本来就原样显示</li>
 * </ul>
 * 1.12.2 的物品名和 Lore 就是带 § 的字符串，只有 16 种颜色，没有十六进制颜色：编辑文本和存进物品的字符串一一对应，
 * {@link #toSection} 转出来就是物品里的样子，预览画的也就是提示框里的样子。
 * <p>
 * 字体渲染器（FontRenderer#renderStringAtPos）对 § 的处理和 1.20.1 不一样，这里照它来：
 * <ul>
 *   <li>§ 后面跟着任何字符都会连那个字符一起吞掉；认不出的代码按白色处理，并关掉粗体等格式</li>
 *   <li>只有一行最末尾的 § 会当普通字符画出来</li>
 * </ul>
 * 认不出的代码（{@code §z} 这类）在编辑文本里原样保留成 {@code §z}：它没有对应的 & 写法（{@code &z} 是普通文字），
 * 而且有的模组用自己的字体给它另配了效果，转成别的就丢了。输入框里打不出 §，所以这类代码只会从物品里读进来。
 * <p>
 * 这里只管字符串层面的事：切记号、§ 与 & 两种写法互转、粘贴文本的清洗。
 * 编辑文本加上基础样式之后在提示框里长什么样在 {@link LegacyComponents}，选中文字做渐变这类改写在 {@link LegacyEdits}。
 */
public final class LegacyText {

    /**
     * 原版颜色码前缀
     */
    public static final char SECTION = (char) 0xA7;

    /**
     * 编辑器里代替 § 的字符
     */
    public static final char AMPERSAND = '&';

    /**
     * 字体渲染器认的代码字符（小写），顺序和 FontRenderer 里那张表一致
     */
    private static final String CODE_CHARS = "0123456789abcdefklmnor";

    /**
     * 原版的 16 种颜色，按 &0 ~ &f 的顺序
     */
    public static final TextFormatting[] VANILLA_COLORS = {
            TextFormatting.BLACK, TextFormatting.DARK_BLUE, TextFormatting.DARK_GREEN, TextFormatting.DARK_AQUA,
            TextFormatting.DARK_RED, TextFormatting.DARK_PURPLE, TextFormatting.GOLD, TextFormatting.GRAY,
            TextFormatting.DARK_GRAY, TextFormatting.BLUE, TextFormatting.GREEN, TextFormatting.AQUA,
            TextFormatting.RED, TextFormatting.LIGHT_PURPLE, TextFormatting.YELLOW, TextFormatting.WHITE
    };

    /**
     * 代码字符在 CODE_CHARS 里的位置 → TextFormatting（0~15 颜色、16~20 格式、21 重置）
     */
    private static final TextFormatting[] BY_INDEX = {
            TextFormatting.BLACK, TextFormatting.DARK_BLUE, TextFormatting.DARK_GREEN, TextFormatting.DARK_AQUA,
            TextFormatting.DARK_RED, TextFormatting.DARK_PURPLE, TextFormatting.GOLD, TextFormatting.GRAY,
            TextFormatting.DARK_GRAY, TextFormatting.BLUE, TextFormatting.GREEN, TextFormatting.AQUA,
            TextFormatting.RED, TextFormatting.LIGHT_PURPLE, TextFormatting.YELLOW, TextFormatting.WHITE,
            TextFormatting.OBFUSCATED, TextFormatting.BOLD, TextFormatting.STRIKETHROUGH, TextFormatting.UNDERLINE,
            TextFormatting.ITALIC, TextFormatting.RESET
    };

    /**
     * ASCII 范围内代码字符 → TextFormatting 的速查表（大小写都填）
     * <p>
     * 切记号时每个 & 都要查一次，ASCII 先查表；表外的字符照字体渲染器的写法转小写再找（开尔文符号 U+212A 转小写正好是 k，
     * 原版会把它当乱码处理，这里保持一致）。
     */
    private static final TextFormatting[] BY_CHAR = new TextFormatting[128];

    static {
        for (int i = 0; i < CODE_CHARS.length(); i++) {
            char c = CODE_CHARS.charAt(i);
            BY_CHAR[c] = BY_INDEX[i];
            BY_CHAR[Character.toUpperCase(c)] = BY_INDEX[i];
        }
    }

    private LegacyText() {
    }

    // 字符判断

    /**
     * 是否是字体渲染器认的颜色/格式代码字符
     *
     * @param c 字符
     * @return true = 合法
     */
    public static boolean isCodeChar(char c) {
        return codeOf(c) != null;
    }

    /**
     * 代码字符对应的 TextFormatting（规则同 FontRenderer：转小写后在 0-9 a-f k-o r 里找）
     *
     * @param c 字符
     * @return 不是代码字符时返回 null
     */
    @Nullable
    public static TextFormatting codeOf(char c) {
        if (c < 128) {
            return BY_CHAR[c];
        }
        int index = CODE_CHARS.indexOf(String.valueOf(c).toLowerCase(Locale.ROOT).charAt(0));
        return index < 0 ? null : BY_INDEX[index];
    }

    /**
     * 格式的代码字符（{@code §c} 里的 c）
     *
     * @param formatting 格式
     * @return 小写字符
     */
    public static char codeChar(@Nonnull TextFormatting formatting) {
        return formatting.toString().charAt(1);
    }

    /**
     * 是否是十六进制数字（原版 16 色的代码字符正好就是一位十六进制数）
     *
     * @param c 字符
     * @return true = 0-9 a-f A-F
     */
    public static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * 颜色值对应的原版 16 色
     *
     * @param rgb 颜色（只看低 24 位）
     * @return 不是 16 色之一时返回 null
     */
    @Nullable
    public static TextFormatting vanillaColor(int rgb) {
        return ColorMath.exactLegacy(rgb);
    }

    /**
     * 颜色的编辑器代码：16 色之一就是它自己的代码，别的颜色换成最接近的原版颜色（{@link ColorMath#nearestLegacy}）
     *
     * @param rgb 颜色
     * @return 例如 {@code &6}
     */
    @Nonnull
    public static String colorCode(int rgb) {
        return code(ColorMath.nearestLegacy(rgb));
    }

    /**
     * 颜色 / 格式的编辑器代码
     *
     * @param formatting 格式
     * @return 例如 {@code &l}
     */
    @Nonnull
    public static String code(@Nonnull TextFormatting formatting) {
        return String.valueOf(AMPERSAND) + codeChar(formatting);
    }

    // 切记号

    /**
     * 记号类型
     */
    public enum TokenType {
        /**
         * 一个普通字符
         */
        TEXT,
        /**
         * {@code &&}：一个字面的 &（只在编辑文本里有）
         */
        ESCAPE,
        /**
         * 原版 16 色
         */
        COLOR,
        /**
         * 十六进制颜色。1.12.2 的文字没有这种颜色，扫描器不会给出这一类；留着是为了和 1.20.1 的代码一一对应
         */
        HEX,
        /**
         * 粗体、斜体、下划线、删除线、乱码
         */
        FORMAT,
        /**
         * 重置：白色、关掉所有格式（字体渲染器回到画字时给的颜色，提示框里就是白色）
         */
        RESET,
        /**
         * 认不出的代码（§ 加 0-9 a-f k-o r 以外的字符）：原版按白色处理并关掉格式，两个字符都不显示；原样保留
         */
        CUSTOM,
        /**
         * 编辑文本末尾落单的 §：留在末尾时原版把它当普通字符画出来，后面再接字就和那个字拼成代码
         */
        STRAY
    }

    /**
     * 一个记号（列表形式，给不在每帧路径上的地方用）
     */
    public static final class Token {
        @Nonnull
        private final TokenType type;
        private final int start;
        private final int end;
        private final char ch;
        private final int rgb;
        @Nullable
        private final TextFormatting format;

        /**
         * @param type   类型
         * @param start  在原文里的起点
         * @param end    在原文里的终点（不含）
         * @param ch     TEXT/ESCAPE：显示的字符；COLOR/FORMAT/RESET/CUSTOM：代码字符（保留原大小写）
         * @param rgb    COLOR/RESET/CUSTOM：按原版效果的颜色
         * @param format COLOR/FORMAT/RESET：对应的 TextFormatting
         */
        public Token(@Nonnull TokenType type, int start, int end, char ch, int rgb, @Nullable TextFormatting format) {
            this.type = type;
            this.start = start;
            this.end = end;
            this.ch = ch;
            this.rgb = rgb;
            this.format = format;
        }

        @Nonnull
        public TokenType type() {
            return type;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public char ch() {
            return ch;
        }

        public int rgb() {
            return rgb;
        }

        @Nullable
        public TextFormatting format() {
            return format;
        }

        /**
         * 是不是会显示出来的字符
         */
        public boolean visible() {
            return type == TokenType.TEXT || type == TokenType.ESCAPE;
        }

        /**
         * 是不是颜色 / 重置这类会清掉之前格式的代码
         */
        public boolean resetsFormats() {
            return type == TokenType.COLOR || type == TokenType.RESET || type == TokenType.CUSTOM;
        }
    }

    /**
     * 逐个读记号的扫描器（不分配记号对象，切大段文本时用）
     * <p>
     * 两种模式：
     * <ul>
     *   <li>编辑文本：& 和 § 都是代码前缀，{@code &&} 是转义，& 后面不成代码就是普通字符，§ 后面不成代码就是 CUSTOM</li>
     *   <li>§ 文本：只有 § 是代码前缀，& 是普通字符；§ 后面不管跟着什么都一起吞掉，只有最末尾的 § 是普通字符（同字体渲染器）</li>
     * </ul>
     */
    public static final class Scanner {
        private final String text;
        private final boolean editable;
        private int pos;

        private TokenType type = TokenType.TEXT;
        private int start;
        private int end;
        private char ch;
        private int rgb;
        @Nullable
        private TextFormatting format;

        private Scanner(@Nonnull String text, boolean editable) {
            this.text = text;
            this.editable = editable;
        }

        /**
         * 读下一个记号
         *
         * @return false = 读完了
         */
        public boolean next() {
            int length = text.length();
            if (pos >= length) {
                return false;
            }
            start = pos;
            format = null;
            char c = text.charAt(pos);
            boolean intro = c == SECTION || (editable && c == AMPERSAND);
            if (!intro) {
                type = TokenType.TEXT;
                ch = c;
                end = pos + 1;
            } else if (editable && c == AMPERSAND && pos + 1 < length && text.charAt(pos + 1) == AMPERSAND) {
                type = TokenType.ESCAPE;
                ch = AMPERSAND;
                end = pos + 2;
            } else if (pos + 1 >= length) {
                // 末尾孤零零的前缀：& 是普通字符；§ 原版当普通字符画，编辑文本里标成落单（后面一接字就成了代码）
                type = c == AMPERSAND || !editable ? TokenType.TEXT : TokenType.STRAY;
                ch = c;
                end = length;
            } else {
                char code = text.charAt(pos + 1);
                TextFormatting formatting = codeOf(code);
                if (formatting != null) {
                    format = formatting;
                    ch = code;
                    end = pos + 2;
                    if (formatting == TextFormatting.RESET) {
                        type = TokenType.RESET;
                        rgb = 0xFFFFFF;
                    } else if (formatting.isColor()) {
                        type = TokenType.COLOR;
                        rgb = ColorMath.legacyRgb(formatting);
                    } else {
                        type = TokenType.FORMAT;
                    }
                } else if (c == AMPERSAND) {
                    type = TokenType.TEXT;
                    ch = AMPERSAND;
                    end = pos + 1;
                } else {
                    // § 加一个认不出的字符：字体渲染器照样把两个字符都吞掉，按白色处理
                    type = TokenType.CUSTOM;
                    ch = code;
                    rgb = 0xFFFFFF;
                    end = pos + 2;
                }
            }
            pos = end;
            return true;
        }

        @Nonnull
        public TokenType type() {
            return type;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        /**
         * TEXT/ESCAPE：显示的字符；COLOR/FORMAT/RESET/CUSTOM：代码字符（保留原大小写）
         */
        public char ch() {
            return ch;
        }

        /**
         * COLOR/RESET/CUSTOM：按原版效果的颜色
         */
        public int rgb() {
            return rgb;
        }

        @Nullable
        public TextFormatting format() {
            return format;
        }

        /**
         * 当前记号转成 {@link Token}
         */
        @Nonnull
        public Token token() {
            return new Token(type, start, end, ch, rgb, format);
        }
    }

    /**
     * 扫描编辑文本
     *
     * @param editable 编辑文本
     * @return 扫描器
     */
    @Nonnull
    public static Scanner scanEditable(@Nonnull String editable) {
        return new Scanner(editable, true);
    }

    /**
     * 扫描 § 文本
     *
     * @param text § 文本
     * @return 扫描器
     */
    @Nonnull
    public static Scanner scanSection(@Nonnull String text) {
        return new Scanner(text, false);
    }

    /**
     * 编辑文本切成记号列表
     *
     * @param editable 编辑文本
     * @return 记号
     */
    @Nonnull
    public static List<Token> tokenize(@Nonnull String editable) {
        List<Token> tokens = new ArrayList<>();
        Scanner scanner = scanEditable(editable);
        while (scanner.next()) {
            tokens.add(scanner.token());
        }
        return tokens;
    }

    // § 与 & 互转

    /**
     * editable → section：{@code &x} 转成 {@code §x}，{@code &&} 转成 &，原样保留的 § 代码照抄
     * <p>
     * 代码字符保留原大小写，这样 {@link #fromStored} 能原样转回来。
     *
     * @param editable 编辑器文本
     * @return 带 § 的文本
     */
    @Nonnull
    public static String toSection(@Nonnull String editable) {
        if (editable.indexOf(AMPERSAND) < 0) {
            return editable;
        }
        StringBuilder result = new StringBuilder(editable.length());
        Scanner scanner = scanEditable(editable);
        while (scanner.next()) {
            switch (scanner.type()) {
                case TEXT:
                case ESCAPE:
                    result.append(scanner.ch());
                    break;
                case COLOR:
                case FORMAT:
                case RESET:
                    result.append(SECTION).append(scanner.ch());
                    break;
                default:
                    // CUSTOM / STRAY：本来就是 § 写法，照抄
                    result.append(editable, scanner.start(), scanner.end());
                    break;
            }
        }
        return result.toString();
    }

    /**
     * 剪贴板里的文本 → 可以插进编辑框的编辑文本
     * <p>
     * 带 § 的当成 § 文本整段转换（§ 码变 & 码，原文的 & 按需转义），认不出的 § 代码和末尾的 § 换成字面的 &，
     * 不吞字符（输入框收不下 §，留着也会被过滤掉）；不带 § 的当成已经是 & 写法，原样返回。
     * 控制字符（换行、制表符等）去掉，和原版输入框一致。
     *
     * @param text      剪贴板文本（单行）
     * @param following 插入点后面紧跟着的编辑文本（决定末尾的 & 要不要转义），没有就传空串
     * @return 编辑文本
     */
    @Nonnull
    public static String fromClipboard(@Nonnull String text, @Nonnull String following) {
        if (text.indexOf(SECTION) < 0) {
            return dropControlChars(text);
        }
        return convertSection(text, true, following);
    }

    /**
     * 物品里存着的 § 文本 → 编辑文本
     * <p>
     * 和 {@link #fromClipboard} 的区别：
     * <ul>
     *   <li>不管里面有没有 §，一律按 § 文本转换。物品里的原文本来就是 § 写法，里面的 & 只是普通字符
     *   （{@code Made by R&D} 这类），后面紧跟着代码字符时要写成 {@code &&}，否则 {@code &D} 会被读成粉色，"&D" 两个字就没了。
     *   剪贴板不一样：不带 § 的文本多半是照着 & 写法打的，所以那边原样收下</li>
     *   <li>认不出的 § 代码、末尾的 § 原样留在编辑文本里（{@link TokenType#CUSTOM} / {@link TokenType#STRAY}），
     *   写回去还是原来那几个字符。它们是用 setValue 放进输入框的，不经过字符过滤</li>
     * </ul>
     * 控制字符去掉（输入框放不下）；页面会把这种行标出来，没改动时写回原文，见 {@link LegacyComponents.Lossy#CONTROL}。
     * § 后面紧跟着控制字符时，字体渲染器把这两个字符当成一个认不出的代码（白色、清掉格式），换成效果完全相同的 {@code &f}，
     * 不能只删控制字符：剩下的 § 会和再后面的字拼成别的代码。
     *
     * @param text § 文本
     * @return 编辑文本
     */
    @Nonnull
    public static String fromStored(@Nonnull String text) {
        if (text.indexOf(SECTION) < 0 && text.indexOf(AMPERSAND) < 0) {
            return dropControlChars(text);
        }
        return convertSection(text, false, "");
    }

    /**
     * 文本里有没有输入框收不下的控制字符（换行、制表符、DEL 这些）
     *
     * @param text 文本
     * @return true = 有
     */
    public static boolean hasControlChars(@Nonnull String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != SECTION && !ChatAllowedCharacters.isAllowedCharacter(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去掉输入框收不下的字符（换行、制表符等控制字符）；只给不带 § 的文本用，带 § 的在转换时逐个记号处理
     *
     * @param text 文本
     * @return 去掉之后的文本；一个都没去掉时返回原对象
     */
    @Nonnull
    private static String dropControlChars(@Nonnull String text) {
        StringBuilder filtered = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean keep = c == SECTION || ChatAllowedCharacters.isAllowedCharacter(c);
            if (!keep && filtered == null) {
                filtered = new StringBuilder(text.length());
                filtered.append(text, 0, i);
            } else if (keep && filtered != null) {
                filtered.append(c);
            }
        }
        return filtered == null ? text : filtered.toString();
    }

    /**
     * 往 before 和 after 之间插入一段编辑文本时，保证 before 末尾那个单独的 & 仍然显示为 &
     * <p>
     * 例如输入框里已经有 {@code R&}，光标在末尾，再点色块插入 {@code &a}：直接拼成 {@code R&&a} 会被读成转义（显示 "R&a"），
     * 这里会改成插入 {@code &&a}，结果 {@code R&&&a} = "R&" + 绿色。只给工具条插代码、粘贴 § 文本用，正常打字不经过这里。
     *
     * @param before   插入点前面的编辑文本
     * @param inserted 要插入的编辑文本
     * @param after    插入点后面的编辑文本（1.12.2 的 & 只看后面一个字符，插入的文本不空就用不上它，留着和 1.20.1 一致）
     * @return 实际该插入的文本
     */
    @Nonnull
    public static String guardInsertion(@Nonnull String before, @Nonnull String inserted, @Nonnull String after) {
        if (before.isEmpty() || before.charAt(before.length() - 1) != AMPERSAND || inserted.isEmpty()) {
            return inserted;
        }
        // before 末尾的 & 是不是单独成字（不是 && 或代码的一部分）
        Scanner scanner = scanEditable(before);
        boolean loneAmpersand = false;
        while (scanner.next()) {
            loneAmpersand = scanner.type() == TokenType.TEXT && scanner.end() == before.length();
        }
        if (!loneAmpersand) {
            return inserted;
        }
        return needsEscape(inserted, 0, inserted.length()) ? AMPERSAND + inserted : inserted;
    }

    /**
     * 编辑文本里能看见的文字（去掉所有代码，转义还原成 &）
     *
     * @param editable 编辑文本
     * @return 纯文字
     */
    @Nonnull
    public static String plain(@Nonnull String editable) {
        if (editable.indexOf(AMPERSAND) < 0 && editable.indexOf(SECTION) < 0) {
            return editable;
        }
        StringBuilder result = new StringBuilder(editable.length());
        Scanner scanner = scanEditable(editable);
        while (scanner.next()) {
            if (scanner.type() == TokenType.TEXT || scanner.type() == TokenType.ESCAPE) {
                result.append(scanner.ch());
            }
        }
        return result.toString();
    }

    /**
     * § 文本 → 编辑文本
     *
     * @param text      § 文本
     * @param pasting   是不是粘贴：认不出的 § 代码、末尾的 § 换成字面的 &（输入框收不下 §）；否则原样保留
     * @param following 后面紧跟着的编辑文本
     * @return 编辑文本
     */
    @Nonnull
    private static String convertSection(@Nonnull String text, boolean pasting, @Nonnull String following) {
        Writer writer = new Writer(text.length() + 8);
        Scanner scanner = scanSection(text);
        while (scanner.next()) {
            switch (scanner.type()) {
                case TEXT:
                case ESCAPE:
                    if (scanner.ch() != SECTION) {
                        // 控制字符输入框放不下，去掉
                        if (ChatAllowedCharacters.isAllowedCharacter(scanner.ch())) {
                            writer.literal(scanner.ch());
                        }
                    } else if (pasting) {
                        // 末尾的 §：原版当普通字符画。粘贴时换成 &，读物品时原样留着
                        writer.literal(AMPERSAND);
                    } else {
                        writer.raw(String.valueOf(SECTION));
                    }
                    break;
                case COLOR:
                case FORMAT:
                case RESET:
                    writer.code(AMPERSAND, scanner.ch());
                    break;
                case CUSTOM:
                    if (scanner.ch() != SECTION && !ChatAllowedCharacters.isAllowedCharacter(scanner.ch())) {
                        // § 加控制字符：原版按白色处理并清掉格式，和 &f 一模一样；粘贴时同样只留一个字面的 &
                        if (pasting) {
                            writer.literal(AMPERSAND);
                        } else {
                            writer.code(AMPERSAND, 'f');
                        }
                    } else if (pasting) {
                        // 被它带走的那个字符按普通字符写：它可能是 &，原样拼回去会和后面的字粘成转义或代码
                        writer.literal(AMPERSAND);
                        writer.literal(scanner.ch() == SECTION ? AMPERSAND : scanner.ch());
                    } else {
                        writer.raw(text.substring(scanner.start(), scanner.end()));
                    }
                    break;
                default:
                    break;
            }
        }
        return writer.build(following);
    }

    /**
     * 在编辑文本的 from 位置放一个字面的 & 时，要不要写成 {@code &&}
     * <p>
     * 判断规则和 {@link Scanner} 的编辑模式一一对应：& 后面是 & 或代码字符时会被读成别的东西。
     *
     * @param text 紧跟在 & 后面的文本
     * @param from 起点
     * @param to   终点（不含）
     * @return true = 要转义
     */
    private static boolean needsEscape(@Nonnull CharSequence text, int from, int to) {
        if (from >= to) {
            return false;
        }
        char first = text.charAt(from);
        return first == AMPERSAND || isCodeChar(first);
    }

    // 写编辑文本

    /**
     * 拼编辑文本：按顺序给出字面字符和代码，最后统一决定哪些字面的 & 要写成 {@code &&}
     * <p>
     * 一个 & 要不要转义取决于它后面最终跟着什么（可能是下一个代码，也可能是后面的字面字符），
     * 所以 {@link #build} 从右往左拼：轮到某个 & 时，它右边的结果已经定了，判断是精确的，只在必要时转义。
     * 可以打标记（{@link #mark}），拼完后换算成在结果里的位置，用来放回光标 / 选区。
     */
    public static final class Writer {
        private final StringBuilder forward;
        private final BitSet ampersands = new BitSet();

        /**
         * @param capacity 预估长度
         */
        public Writer(int capacity) {
            this.forward = new StringBuilder(Math.max(16, capacity));
        }

        /**
         * 一个要显示出来的字符
         *
         * @param c 字符
         * @return this
         */
        @Nonnull
        public Writer literal(char c) {
            if (c == AMPERSAND) {
                ampersands.set(forward.length());
            }
            forward.append(c);
            return this;
        }

        /**
         * 一段代码（原样写入，例如 {@code &6&l}）
         *
         * @param code 代码
         * @return this
         */
        @Nonnull
        public Writer code(@Nonnull CharSequence code) {
            forward.append(code);
            return this;
        }

        /**
         * 一个两字符代码
         *
         * @param intro 前缀
         * @param c     代码字符
         * @return this
         */
        @Nonnull
        public Writer code(char intro, char c) {
            forward.append(intro).append(c);
            return this;
        }

        /**
         * 原样写入、不参与转义判断的一段（例如保留下来的 § 代码）
         *
         * @param text 文本
         * @return this
         */
        @Nonnull
        public Writer raw(@Nonnull CharSequence text) {
            forward.append(text);
            return this;
        }

        /**
         * 当前位置的标记
         *
         * @return 标记，传给 {@link #build(String, int[])} 换算
         */
        public int mark() {
            return forward.length();
        }

        /**
         * 拼出结果
         *
         * @return 编辑文本
         */
        @Nonnull
        public String build() {
            return build("", null);
        }

        /**
         * 拼出结果
         *
         * @param following 结果后面紧跟着的文本（只用来判断末尾的 & 要不要转义，不会拼进去）
         * @return 编辑文本
         */
        @Nonnull
        public String build(@Nonnull String following) {
            return build(following, null);
        }

        /**
         * 拼出结果，并把标记换算成在结果里的位置
         *
         * @param following 结果后面紧跟着的文本
         * @param marks     {@link #mark()} 拿到的标记，原地换算；可为 null
         * @return 编辑文本
         */
        @Nonnull
        public String build(@Nonnull String following, @Nullable int[] marks) {
            int length = forward.length();
            if (ampersands.isEmpty()) {
                return forward.toString();
            }
            // 1.12.2 的 & 要不要转义只看后面一个字符，following 带上头一个字符就够了
            int context = Math.min(following.length(), 1);
            char[] buffer = new char[2 * length + context];
            int pos = buffer.length;
            for (int i = context - 1; i >= 0; i--) {
                buffer[--pos] = following.charAt(i);
            }
            int end = pos;
            int[] positions = marks == null ? null : new int[marks.length];
            if (positions != null) {
                for (int m = 0; m < marks.length; m++) {
                    if (marks[m] >= length) {
                        positions[m] = end;
                    }
                }
            }
            CharArray view = new CharArray(buffer);
            for (int i = length - 1; i >= 0; i--) {
                char c = forward.charAt(i);
                if (ampersands.get(i) && needsEscape(view, pos, buffer.length)) {
                    buffer[--pos] = AMPERSAND;
                }
                buffer[--pos] = c;
                if (positions != null) {
                    for (int m = 0; m < marks.length; m++) {
                        if (marks[m] == i) {
                            positions[m] = pos;
                        }
                    }
                }
            }
            if (marks != null) {
                for (int m = 0; m < marks.length; m++) {
                    marks[m] = positions[m] - pos;
                }
            }
            return new String(buffer, pos, end - pos);
        }
    }

    /**
     * 把 char[] 包成 CharSequence 给 {@link #needsEscape} 用，省得每次判断都复制
     */
    private static final class CharArray implements CharSequence {
        private final char[] data;

        CharArray(@Nonnull char[] data) {
            this.data = data;
        }

        @Override
        public int length() {
            return data.length;
        }

        @Override
        public char charAt(int index) {
            return data[index];
        }

        @Nonnull
        @Override
        public CharSequence subSequence(int start, int end) {
            return new String(data, start, end - start);
        }

        @Nonnull
        @Override
        public String toString() {
            return new String(data);
        }
    }
}
