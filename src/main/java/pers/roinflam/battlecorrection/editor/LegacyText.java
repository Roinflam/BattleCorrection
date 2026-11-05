package pers.roinflam.battlecorrection.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.util.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * 颜色码文本的语法层：编辑器里用 {@code &a} 这种写法，原版文本里是 {@code §a}
 * <p>
 * 为什么不直接编辑 §：原版的 EditBox 会把 § 当非法字符过滤掉（StringUtil.isAllowedChatCharacter），
 * 打不进也粘不进。用 & 代替是 Bukkit 那边的老习惯，服主都熟。
 * <p>
 * 编辑文本（editable）的写法：
 * <ul>
 *   <li>{@code &} + 代码字符：颜色 / 格式 / 重置（0-9 a-f k-o r 不分大小写，以及模组注册的自定义代码如 {@code &!}）</li>
 *   <li>{@code &#RRGGBB}：任意颜色；读取时也认 Bukkit 的旧写法 {@code &x&R&R&G&G&B&B}</li>
 *   <li>{@code &&}：一个字面的 &。只在 & 后面紧跟着会被当成代码的东西时才需要，单独的 & 本来就原样显示</li>
 * </ul>
 * 这里只管字符串层面的事：切记号、§ 与 & 两种写法互转、粘贴文本的清洗。
 * 带样式的组件和编辑文本之间的转换在 {@link LegacyComponents}，选中文字做渐变这类改写在 {@link LegacyEdits}。
 */
public final class LegacyText {

    /**
     * 原版颜色码前缀
     */
    public static final char SECTION = '§';

    /**
     * 编辑器里代替 § 的字符
     */
    public static final char AMPERSAND = '&';

    /**
     * 十六进制颜色写法 {@code &#RRGGBB} 里的 #
     */
    public static final char HEX_MARK = '#';

    /**
     * 原版的代码字符（小写）
     */
    private static final String CODE_CHARS = "0123456789abcdefklmnor";

    /**
     * 原版的 16 种颜色，按 &0 ~ &f 的顺序
     * <p>
     * 不用 ChatFormatting.values()：有些模组会往这个枚举里塞自己的颜色，那些没有 § 码。
     */
    public static final ChatFormatting[] VANILLA_COLORS = {
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE
    };

    /**
     * ASCII 范围内代码字符 → ChatFormatting 的速查表（大小写都填）
     * <p>
     * ChatFormatting.getByCode 每次都要转小写字符串再遍历枚举，切记号时每个 & 都要查一次，这里先查表，表里没有的再交给它（模组代码）。
     */
    private static final ChatFormatting[] BY_CHAR = new ChatFormatting[128];

    static {
        for (int i = 0; i < CODE_CHARS.length(); i++) {
            char c = CODE_CHARS.charAt(i);
            ChatFormatting formatting = ChatFormatting.getByCode(c);
            BY_CHAR[c] = formatting;
            BY_CHAR[Character.toUpperCase(c)] = formatting;
        }
    }

    private LegacyText() {
    }

    // 字符判断

    /**
     * 是否是合法的颜色/格式代码字符
     * <p>
     * 原版的 0-9 a-f k-o r 之外，模组往 ChatFormatting 里塞的自定义代码也算（例如某些模组用 {@code §!} 做流光/动态文字）：
     * 原版渲染时是按 ChatFormatting.getByCode 认的，这里保持一致，否则这类名字一经编辑 {@code §!} 就会变成普通字符，特效消失。
     *
     * @param c 字符
     * @return true = 合法
     */
    public static boolean isCodeChar(char c) {
        return codeOf(c) != null;
    }

    /**
     * 是否是原版自带的代码字符（0-9 a-f k-o r，不分大小写）
     *
     * @param c 字符
     * @return true = 原版代码
     */
    public static boolean isVanillaCode(char c) {
        return c < 128 && BY_CHAR[c] != null;
    }

    /**
     * 是否是原版自带的那 22 种格式（不是模组塞进枚举的）
     * <p>
     * 不能只看代码字符是不是 ASCII：ChatFormatting.getByCode 先把字符转小写再比，开尔文符号 U+212A 转小写正好是 k，
     * 原版会把 {@code §K}（开尔文）当乱码处理。这里按查到的枚举本身判断，和原版渲染保持一致。
     *
     * @param formatting 格式
     * @return true = 原版格式
     */
    private static boolean isVanillaFormat(@Nonnull ChatFormatting formatting) {
        char code = formatting.getChar();
        return code < 128 && BY_CHAR[code] == formatting;
    }

    /**
     * 代码字符对应的 ChatFormatting
     *
     * @param c 字符
     * @return 不是代码字符时返回 null
     */
    @Nullable
    public static ChatFormatting codeOf(char c) {
        if (c < 128) {
            ChatFormatting formatting = BY_CHAR[c];
            if (formatting != null) {
                return formatting;
            }
        }
        return ChatFormatting.getByCode(c);
    }

    /**
     * 是否是十六进制数字
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
    public static ChatFormatting vanillaColor(int rgb) {
        int value = rgb & 0xFFFFFF;
        for (ChatFormatting formatting : VANILLA_COLORS) {
            Integer color = formatting.getColor();
            if (color != null && color == value) {
                return formatting;
            }
        }
        return null;
    }

    /**
     * 颜色值 → 6 位大写十六进制（不带 #）
     *
     * @param rgb 颜色
     * @return 例如 FFAA00
     */
    @Nonnull
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    /**
     * 颜色对应的编辑器代码：16 色用 {@code &6} 这种短写法，其他用 {@code &#RRGGBB}
     *
     * @param rgb 颜色
     * @return 代码
     */
    @Nonnull
    public static String colorCode(int rgb) {
        ChatFormatting formatting = vanillaColor(rgb);
        return formatting != null
                ? String.valueOf(AMPERSAND) + formatting.getChar()
                : String.valueOf(AMPERSAND) + HEX_MARK + hex(rgb);
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
         * 十六进制颜色
         */
        HEX,
        /**
         * 粗体、斜体、下划线、删除线、乱码
         */
        FORMAT,
        /**
         * 重置
         */
        RESET,
        /**
         * 模组注册的自定义代码：样式上不认识，原样保留
         */
        CUSTOM,
        /**
         * 不成代码的 §：原版渲染时不显示
         */
        STRAY
    }

    /**
     * 一个记号（列表形式，给不在每帧路径上的地方用）
     *
     * @param type   类型
     * @param start  在原文里的起点
     * @param end    在原文里的终点（不含）
     * @param ch     TEXT/ESCAPE：显示的字符；COLOR/FORMAT/RESET/CUSTOM：代码字符（保留原大小写）
     * @param rgb    COLOR/HEX：颜色
     * @param format COLOR/FORMAT/RESET/CUSTOM：对应的 ChatFormatting
     */
    public record Token(@Nonnull TokenType type, int start, int end, char ch, int rgb, @Nullable ChatFormatting format) {

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
            return type == TokenType.COLOR || type == TokenType.HEX || type == TokenType.RESET;
        }
    }

    /**
     * 逐个读记号的扫描器（不分配记号对象，切大段文本时用）
     * <p>
     * 两种模式：
     * <ul>
     *   <li>编辑文本：& 和 § 都是代码前缀，{@code &&} 是转义，& 后面不成代码就是普通字符</li>
     *   <li>§ 文本：只有 § 是代码前缀，& 是普通字符；不成代码的 § 和它后面那个字符一起吞掉（和原版 StringDecomposer 一致）</li>
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
        private ChatFormatting format;
        private int hexAt;
        private int hexStep;
        private char xChar;

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
                // 末尾孤零零的前缀：& 是普通字符，§ 原版直接不显示
                type = c == AMPERSAND ? TokenType.TEXT : TokenType.STRAY;
                ch = c;
                end = length;
            } else if (editable && c == AMPERSAND && text.charAt(pos + 1) == HEX_MARK && hexRun(pos + 2)) {
                // #RRGGBB 只是编辑器的写法，§# 在原版里不是代码，不认
                type = TokenType.HEX;
                hexAt = pos + 2;
                hexStep = 1;
                xChar = 'x';
                rgb = parseHex();
                end = pos + 8;
            } else if (isX(text.charAt(pos + 1)) && xRun(pos + 2)) {
                type = TokenType.HEX;
                hexAt = pos + 3;
                hexStep = 2;
                xChar = text.charAt(pos + 1);
                rgb = parseHex();
                end = pos + 14;
            } else {
                char code = text.charAt(pos + 1);
                ChatFormatting formatting = codeOf(code);
                if (formatting != null) {
                    format = formatting;
                    ch = code;
                    end = pos + 2;
                    if (formatting == ChatFormatting.RESET) {
                        type = TokenType.RESET;
                    } else if (!isVanillaFormat(formatting)) {
                        type = TokenType.CUSTOM;
                    } else if (formatting.isColor()) {
                        type = TokenType.COLOR;
                        Integer value = formatting.getColor();
                        rgb = value == null ? 0xFFFFFF : value;
                    } else {
                        type = TokenType.FORMAT;
                    }
                } else if (c == AMPERSAND) {
                    type = TokenType.TEXT;
                    ch = AMPERSAND;
                    end = pos + 1;
                } else {
                    // 编辑文本里不成代码的 § 只去掉它自己；§ 文本里按原版连同后一个字符一起吞掉
                    type = TokenType.STRAY;
                    ch = c;
                    end = editable ? pos + 1 : pos + 2;
                }
            }
            pos = end;
            return true;
        }

        private static boolean isX(char c) {
            return c == 'x' || c == 'X';
        }

        private boolean isIntro(char c) {
            return c == SECTION || (editable && c == AMPERSAND);
        }

        private boolean hexRun(int from) {
            if (from + 6 > text.length()) {
                return false;
            }
            for (int i = 0; i < 6; i++) {
                if (!isHexDigit(text.charAt(from + i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean xRun(int from) {
            if (from + 12 > text.length()) {
                return false;
            }
            for (int i = 0; i < 6; i++) {
                if (!isIntro(text.charAt(from + 2 * i)) || !isHexDigit(text.charAt(from + 2 * i + 1))) {
                    return false;
                }
            }
            return true;
        }

        private int parseHex() {
            int value = 0;
            for (int i = 0; i < 6; i++) {
                value = (value << 4) | Character.digit(hexDigit(i), 16);
            }
            return value;
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
         * COLOR/HEX：颜色
         */
        public int rgb() {
            return rgb;
        }

        @Nullable
        public ChatFormatting format() {
            return format;
        }

        /**
         * HEX：第 i 位十六进制数字（保留原文的大小写）
         *
         * @param i 0 ~ 5
         * @return 数字字符
         */
        public char hexDigit(int i) {
            return text.charAt(hexAt + i * hexStep);
        }

        /**
         * HEX：写成 §x 形式时用的 x（原文是 &X... 就保留大写）
         */
        public char xChar() {
            return xChar;
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
     * editable → section：{@code &x} 转成 {@code §x}，{@code &#RRGGBB} 转成 {@code §x§R§R§G§G§B§B}，{@code &&} 转成 &
     * <p>
     * 代码字符保留原大小写，这样 {@link #fromSection} 能原样转回来。
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
                case TEXT, ESCAPE -> result.append(scanner.ch());
                case COLOR, FORMAT, RESET, CUSTOM -> result.append(SECTION).append(scanner.ch());
                case HEX -> {
                    result.append(SECTION).append(scanner.xChar());
                    for (int i = 0; i < 6; i++) {
                        result.append(SECTION).append(scanner.hexDigit(i));
                    }
                }
                case STRAY -> result.append(editable, scanner.start(), scanner.end());
            }
        }
        return result.toString();
    }

    /**
     * section → editable：合法的 {@code §x} 转成 {@code &x}，{@code §x§R§R§G§G§B§B} 转成 {@code &#RRGGBB}
     * <p>
     * 后面不是合法代码的 § 原样留着，不改写。原文里本来就有的 &，如果紧跟着会被当成代码的东西（例如 {@code R&D}），
     * 写成 {@code &&} 转义，保证 {@link #toSection} 能原样转回来。
     *
     * @param text 带 § 的文本
     * @return 编辑器文本
     */
    @Nonnull
    public static String fromSection(@Nonnull String text) {
        if (text.indexOf(SECTION) < 0 && text.indexOf(AMPERSAND) < 0) {
            return text;
        }
        return convertSection(text, false, "");
    }

    /**
     * 剪贴板里的文本 → 可以插进编辑框的编辑文本
     * <p>
     * 带 § 的当成 § 文本整段转换（§ 码变 & 码，§x 十六进制变 {@code &#RRGGBB}，原文的 & 按需转义），
     * 不成代码的 § 也换成字面的 &，不吞字符；不带 § 的当成已经是 & 写法，原样返回。
     * 控制字符（换行、制表符等）去掉，和原版输入框一致。
     *
     * @param text      剪贴板文本（单行）
     * @param following 插入点后面紧跟着的编辑文本（决定末尾的 & 要不要转义），没有就传空串
     * @return 编辑文本
     */
    @Nonnull
    public static String fromClipboard(@Nonnull String text, @Nonnull String following) {
        String clean = dropControlChars(text);
        if (clean.indexOf(SECTION) < 0) {
            return clean;
        }
        return convertSection(clean, true, following);
    }

    /**
     * 物品里存着的 § 文本 → 编辑文本（Lore 里不是合法 JSON、只能按纯文本读的行）
     * <p>
     * 和 {@link #fromClipboard} 的区别：不管里面有没有 §，一律按 § 文本转换。物品里的原文本来就是 § 写法，
     * 里面的 & 只是普通字符（{@code /give} 出来的 {@code Made by R&D} 这类），后面紧跟着会被当成代码的东西时要写成 {@code &&}，
     * 否则 {@code &D} 会被读成淡紫色，"&D" 两个字就没了。剪贴板不一样：不带 § 的文本多半是照着 & 写法打的，所以那边原样收下。
     * 其余和粘贴一致：不成代码的 § 换成字面的 &，控制字符去掉。
     *
     * @param text § 文本
     * @return 编辑文本
     */
    @Nonnull
    public static String fromStored(@Nonnull String text) {
        String clean = dropControlChars(text);
        if (clean.indexOf(SECTION) < 0 && clean.indexOf(AMPERSAND) < 0) {
            return clean;
        }
        return convertSection(clean, true, "");
    }

    /**
     * 去掉输入框收不下的字符（换行、制表符等控制字符），§ 留着交给后面转换
     *
     * @param text 文本
     * @return 去掉之后的文本；一个都没去掉时返回原对象
     */
    @Nonnull
    private static String dropControlChars(@Nonnull String text) {
        StringBuilder filtered = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean keep = c == SECTION || StringUtil.isAllowedChatCharacter(c);
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
     * @param after    插入点后面的编辑文本
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
        String joined = inserted.length() >= 14 ? inserted : inserted + after.substring(0, Math.min(after.length(), 14));
        return needsEscape(joined, 0, joined.length()) ? AMPERSAND + inserted : inserted;
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
     * @param text         § 文本
     * @param strayAsText  不成代码的 § 是否当成字面的 &（粘贴时不吞字符），否则原样保留
     * @param following    后面紧跟着的编辑文本
     * @return 编辑文本
     */
    @Nonnull
    private static String convertSection(@Nonnull String text, boolean strayAsText, @Nonnull String following) {
        Writer writer = new Writer(text.length() + 8);
        Scanner scanner = scanSection(text);
        while (scanner.next()) {
            switch (scanner.type()) {
                case TEXT, ESCAPE -> writer.literal(scanner.ch());
                case COLOR, FORMAT, RESET, CUSTOM -> writer.code(AMPERSAND, scanner.ch());
                case HEX -> {
                    StringBuilder code = new StringBuilder(8).append(AMPERSAND).append(HEX_MARK);
                    for (int i = 0; i < 6; i++) {
                        code.append(scanner.hexDigit(i));
                    }
                    writer.code(code);
                }
                case STRAY -> {
                    // 不成代码的 § 本身原样保留（粘贴时换成字面的 &），被它带走的那个字符按普通字符写：
                    // 它可能是 &，原样拼回去会和后面的字粘成转义或代码
                    writer.literal(strayAsText ? AMPERSAND : SECTION);
                    if (scanner.end() - scanner.start() > 1) {
                        char eaten = text.charAt(scanner.start() + 1);
                        writer.literal(strayAsText && eaten == SECTION ? AMPERSAND : eaten);
                    }
                }
            }
        }
        return writer.build(following);
    }

    /**
     * 在编辑文本的 from 位置放一个字面的 & 时，要不要写成 {@code &&}
     * <p>
     * 判断规则和 {@link Scanner} 的编辑模式一一对应：& 后面是 &、代码字符、{@code #RRGGBB}、{@code x&R&R&G&G&B&B} 时会被读成别的东西。
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
        if (first == AMPERSAND || isCodeChar(first)) {
            return true;
        }
        if (first == HEX_MARK) {
            if (to - from < 7) {
                return false;
            }
            for (int i = 1; i <= 6; i++) {
                if (!isHexDigit(text.charAt(from + i))) {
                    return false;
                }
            }
            return true;
        }
        if (first == 'x' || first == 'X') {
            if (to - from < 13) {
                return false;
            }
            for (int i = 0; i < 6; i++) {
                char intro = text.charAt(from + 1 + 2 * i);
                if ((intro != AMPERSAND && intro != SECTION) || !isHexDigit(text.charAt(from + 2 + 2 * i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
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
         * 原样写入、不参与转义判断的一段（例如保留下来的孤立 §）
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
            int context = Math.min(following.length(), 14);
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
            for (int i = length - 1; i >= 0; i--) {
                char c = forward.charAt(i);
                if (ampersands.get(i) && needsEscape(new CharArray(buffer), pos, buffer.length)) {
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
    private record CharArray(char[] data) implements CharSequence {
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
