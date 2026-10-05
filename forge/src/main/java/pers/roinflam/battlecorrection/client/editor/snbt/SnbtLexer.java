package pers.roinflam.battlecorrection.client.editor.snbt;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import javax.annotation.Nonnull;

/**
 * SNBT 分词器（只用于高亮、括号匹配和编辑辅助，不负责校验——校验交给原版 TagParser）
 * <p>
 * 按行工作：行首状态记录"是不是还在一个没闭合的字符串里"（原版 SNBT 的字符串可以跨行，SnbtPrinterTagVisitor
 * 遇到带换行的字符串也是原样输出的），以及字符串里当前生效的 § 颜色。给出行首状态就能独立地分出这一行的词，
 * 所以界面只需要给可视的那几行分词。
 * <p>
 * 状态编码：低 2 位 = 引号（0 不在字符串里，1 双引号，2 单引号），第 2-6 位 = 当前 § 颜色序号 + 1（0 = 没有）。
 * <p>
 * 词的输出格式：每个词三个 int —— 起点、终点（全文下标）、{@link #pack 打包}的种类和参数。空白不出词。
 * 数字的判定和 TagParser 完全一致（同一套正则的手写版本，含越界退化成字符串的规则），见 {@link #classifyNumber}。
 */
public final class SnbtLexer {

    /** 普通文字（没有专门颜色） */
    public static final int PLAIN = 0;
    /** 键名 */
    public static final int KEY = 1;
    /** 字符串值（含引号） */
    public static final int STRING = 2;
    /** 字符串里的转义 \" \\ */
    public static final int ESCAPE = 3;
    /** 数字主体 */
    public static final int NUMBER = 4;
    /** 数字类型后缀 b s L f d */
    public static final int SUFFIX = 5;
    /** true / false */
    public static final int BOOL = 6;
    /** 花括号 */
    public static final int BRACE = 7;
    /** 方括号 */
    public static final int BRACKET = 8;
    /** 冒号 */
    public static final int COLON = 9;
    /** 逗号 */
    public static final int COMMA = 10;
    /** 数组类型前缀 B; I; L; */
    public static final int ARRAY_TYPE = 11;
    /** 字符串里 § 颜色码的 § 本身，参数 = 颜色序号 / {@link #ARG_FORMAT} / -1（重置） */
    public static final int SECTION = 12;
    /** 字符串里 § 后面的代码字符，参数同上 */
    public static final int SECTION_CODE = 13;
    /** 被 § 颜色码染色的字符串内容，参数 = 颜色序号 */
    public static final int TINTED = 14;
    /** 不该出现在这里的字符（中文、= 之类，写在字符串外面就是错的） */
    public static final int INVALID = 15;
    /** 种类总数 */
    public static final int KIND_COUNT = 16;

    /**
     * 格式代码（k-o）的参数值
     */
    public static final int ARG_FORMAT = 16;

    private static final int QUOTE_NONE = 0;
    private static final int QUOTE_DOUBLE = 1;
    private static final int QUOTE_SINGLE = 2;

    private SnbtLexer() {
    }

    // ═══════════════════════════════════════════════════════════════
    // 状态与打包
    // ═══════════════════════════════════════════════════════════════

    /**
     * 状态是否在字符串里
     */
    public static boolean inString(int state) {
        return (state & 3) != QUOTE_NONE;
    }

    /**
     * 状态里的引号字符
     *
     * @return '"'、'\'' 或 0
     */
    public static char quoteOf(int state) {
        int quote = state & 3;
        return quote == QUOTE_DOUBLE ? '"' : quote == QUOTE_SINGLE ? '\'' : 0;
    }

    private static int makeState(char quote, int tint) {
        int q = quote == '"' ? QUOTE_DOUBLE : quote == '\'' ? QUOTE_SINGLE : QUOTE_NONE;
        return q | ((tint + 1) << 2);
    }

    private static int tintOf(int state) {
        return ((state >>> 2) & 31) - 1;
    }

    /**
     * 打包种类和参数
     *
     * @param kind 种类
     * @param arg  参数（-1 ~ 254）
     * @return 打包值
     */
    public static int pack(int kind, int arg) {
        return kind | ((arg + 1) << 8);
    }

    /**
     * 打包值里的种类
     */
    public static int kindOf(int packed) {
        return packed & 0xFF;
    }

    /**
     * 打包值里的参数
     */
    public static int argOf(int packed) {
        return (packed >>> 8) - 1;
    }

    /**
     * 不加引号的字符串允许的字符（和 brigadier StringReader.isAllowedInUnquotedString 一致）
     */
    public static boolean isUnquotedChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || c == '_' || c == '-' || c == '.' || c == '+';
    }

    /**
     * 是不是结构括号字符
     */
    public static boolean isBracket(char c) {
        return c == '{' || c == '}' || c == '[' || c == ']';
    }

    // ═══════════════════════════════════════════════════════════════
    // 只算状态（不出词），用于推算各行行首状态
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从 state 出发扫过 [from, to)，返回扫完后的状态
     * <p>
     * to 停在行尾时，行尾的换行要么在字符串里（状态不变），要么在字符串外（也不影响状态），所以可以直接接着算下一行。
     * 字符串里最后一个字符是反斜杠时，它转义的是换行本身，下一行照常从字符串内开始。
     *
     * @param text  全文
     * @param from  起点
     * @param to    终点
     * @param state 起点处的状态
     * @return 终点处的状态
     */
    public static int scanState(@Nonnull CharSequence text, int from, int to, int state) {
        char quote = quoteOf(state);
        int tint = tintOf(state);
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            if (quote == 0) {
                if (c == '"' || c == '\'') {
                    quote = c;
                    tint = -1;
                }
                i++;
                continue;
            }
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                quote = 0;
                tint = -1;
                i++;
                continue;
            }
            if (c == ColorCodes.SECTION && i + 1 < to && ColorCodes.isLegalCode(text.charAt(i + 1))) {
                char code = text.charAt(i + 1);
                int index = ColorCodes.colorIndex(code);
                if (index >= 0) {
                    tint = index;
                } else if (ColorCodes.isReset(code)) {
                    tint = -1;
                }
                i += 2;
                continue;
            }
            i++;
        }
        return quote == 0 ? 0 : makeState(quote, tint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 分词
    // ═══════════════════════════════════════════════════════════════

    /**
     * 给一行分词
     *
     * @param text  全文
     * @param from  行起点
     * @param to    行终点（不含换行）
     * @param state 行首状态
     * @param out   输出（三个 int 一组，追加在末尾）
     * @return 行尾状态
     */
    public static int tokenizeLine(@Nonnull CharSequence text, int from, int to, int state, @Nonnull IntArrayList out) {
        int i = from;
        if (inString(state)) {
            int firstToken = out.size();
            long result = lexString(text, i, to, quoteOf(state), tintOf(state), out);
            i = (int) result;
            if (!closed(result)) {
                return makeState(quoteOf(state), resultTint(result));
            }
            if (followedByColon(text, i, to)) {
                markKey(out, firstToken);
            }
        }
        while (i < to) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            switch (c) {
                case '{', '}' -> {
                    emit(out, i, i + 1, BRACE, -1);
                    i++;
                }
                case '[' -> {
                    emit(out, i, i + 1, BRACKET, -1);
                    // [B; [I; [L; —— 和 TagParser.readList 的判定一样：第二个字符不是引号、第三个字符是分号
                    if (i + 2 < to && text.charAt(i + 2) == ';' && text.charAt(i + 1) != '"' && text.charAt(i + 1) != '\'') {
                        char type = text.charAt(i + 1);
                        emit(out, i + 1, i + 3, type == 'B' || type == 'I' || type == 'L' ? ARRAY_TYPE : INVALID, -1);
                        i += 3;
                    } else {
                        i++;
                    }
                }
                case ']' -> {
                    emit(out, i, i + 1, BRACKET, -1);
                    i++;
                }
                case ':' -> {
                    emit(out, i, i + 1, COLON, -1);
                    i++;
                }
                case ',' -> {
                    emit(out, i, i + 1, COMMA, -1);
                    i++;
                }
                case '"', '\'' -> {
                    int firstToken = out.size();
                    emit(out, i, i + 1, STRING, -1);
                    long result = lexString(text, i + 1, to, c, -1, out);
                    i = (int) result;
                    if (!closed(result)) {
                        return makeState(c, resultTint(result));
                    }
                    if (followedByColon(text, i, to)) {
                        markKey(out, firstToken);
                    }
                }
                default -> {
                    if (isUnquotedChar(c)) {
                        int end = i + 1;
                        while (end < to && isUnquotedChar(text.charAt(end))) {
                            end++;
                        }
                        if (followedByColon(text, end, to)) {
                            emit(out, i, end, KEY, -1);
                        } else {
                            int suffix = classifyNumber(text, i, end);
                            if (suffix >= 0) {
                                emit(out, i, suffix, NUMBER, -1);
                                if (suffix < end) {
                                    emit(out, suffix, end, SUFFIX, -1);
                                }
                            } else if (isBoolean(text, i, end)) {
                                emit(out, i, end, BOOL, -1);
                            } else {
                                emit(out, i, end, STRING, -1);
                            }
                        }
                        i = end;
                    } else {
                        int length = Character.isHighSurrogate(c) && i + 1 < to
                                && Character.isLowSurrogate(text.charAt(i + 1)) ? 2 : 1;
                        emit(out, i, i + length, INVALID, -1);
                        i += length;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * 字符串内容（从开引号之后开始）
     *
     * @return 低 32 位 = 结束位置（闭引号之后，或行尾）；第 32 位 = 是否闭合；第 33 位起 = 结束时的颜色序号 + 1
     */
    private static long lexString(@Nonnull CharSequence text, int from, int to, char quote, int tint,
                                  @Nonnull IntArrayList out) {
        int i = from;
        int segment = from;
        while (i < to) {
            char c = text.charAt(i);
            if (c == '\\') {
                flush(out, segment, i, tint);
                int end = Math.min(i + 2, to);
                emit(out, i, end, ESCAPE, -1);
                i = end;
                segment = i;
                continue;
            }
            if (c == quote) {
                flush(out, segment, i, tint);
                emit(out, i, i + 1, STRING, -1);
                return (i + 1) | (1L << 32);
            }
            if (c == ColorCodes.SECTION && i + 1 < to && ColorCodes.isLegalCode(text.charAt(i + 1))) {
                flush(out, segment, i, tint);
                char code = text.charAt(i + 1);
                int index = ColorCodes.colorIndex(code);
                int arg = index >= 0 ? index : ColorCodes.isReset(code) ? -1 : ARG_FORMAT;
                emit(out, i, i + 1, SECTION, arg);
                emit(out, i + 1, i + 2, SECTION_CODE, arg);
                if (index >= 0) {
                    tint = index;
                } else if (ColorCodes.isReset(code)) {
                    tint = -1;
                }
                i += 2;
                segment = i;
                continue;
            }
            i++;
        }
        flush(out, segment, to, tint);
        return to | ((long) (tint + 1) << 33);
    }

    private static boolean closed(long result) {
        return (result & (1L << 32)) != 0;
    }

    private static int resultTint(long result) {
        return (int) (result >>> 33) - 1;
    }

    private static void flush(@Nonnull IntArrayList out, int from, int to, int tint) {
        if (from < to) {
            if (tint >= 0) {
                emit(out, from, to, TINTED, tint);
            } else {
                emit(out, from, to, STRING, -1);
            }
        }
    }

    private static void emit(@Nonnull IntArrayList out, int from, int to, int kind, int arg) {
        out.add(from);
        out.add(to);
        out.add(pack(kind, arg));
    }

    /**
     * 刚闭合的字符串后面跟着冒号 = 它是键名：把这个字符串的 STRING 词改成 KEY（染色、转义保持不变）
     */
    private static void markKey(@Nonnull IntArrayList out, int firstToken) {
        for (int t = firstToken; t + 2 < out.size(); t += 3) {
            if (kindOf(out.getInt(t + 2)) == STRING) {
                out.set(t + 2, pack(KEY, -1));
            }
        }
    }

    private static boolean followedByColon(@Nonnull CharSequence text, int from, int to) {
        int i = from;
        while (i < to && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return i < to && text.charAt(i) == ':';
    }

    private static boolean isBoolean(@Nonnull CharSequence text, int from, int to) {
        int length = to - from;
        if (length == 4) {
            return regionEqualsIgnoreCase(text, from, "true");
        }
        if (length == 5) {
            return regionEqualsIgnoreCase(text, from, "false");
        }
        return false;
    }

    private static boolean regionEqualsIgnoreCase(@Nonnull CharSequence text, int from, @Nonnull String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.toLowerCase(text.charAt(from + i)) != word.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 数字
    // ═══════════════════════════════════════════════════════════════

    /**
     * 判断一个不带引号的词会不会被 TagParser 解析成数字
     * <p>
     * 和 TagParser.type 的顺序、正则一一对应：float → byte → long → short → int → double（带 d）→ double（不带后缀但必须有小数点）。
     * 整数类超出范围时 TagParser 会吞掉 NumberFormatException 退化成字符串，这里同样返回 -1。
     * 不用正则是因为分词在每次编辑后都会跑，正则匹配要分配对象。
     *
     * @param text 全文
     * @param from 词起点
     * @param to   词终点
     * @return 是数字时返回后缀的起点（没有后缀就等于 to）；不是数字返回 -1
     */
    public static int classifyNumber(@Nonnull CharSequence text, int from, int to) {
        int p = from;
        boolean negative = false;
        if (p < to && (text.charAt(p) == '+' || text.charAt(p) == '-')) {
            negative = text.charAt(p) == '-';
            p++;
        }
        int integerStart = p;
        while (p < to && isDigit(text.charAt(p))) {
            p++;
        }
        int integerDigits = p - integerStart;
        boolean dot = false;
        if (p < to && text.charAt(p) == '.') {
            dot = true;
            p++;
        }
        int fractionStart = p;
        while (p < to && isDigit(text.charAt(p))) {
            p++;
        }
        int fractionDigits = p - fractionStart;
        boolean exponent = false;
        if (p < to && (text.charAt(p) == 'e' || text.charAt(p) == 'E')) {
            int q = p + 1;
            if (q < to && (text.charAt(q) == '+' || text.charAt(q) == '-')) {
                q++;
            }
            int exponentStart = q;
            while (q < to && isDigit(text.charAt(q))) {
                q++;
            }
            if (q == exponentStart) {
                // 有 e 却没有指数数字：哪条正则都不匹配
                return -1;
            }
            exponent = true;
            p = q;
        }
        boolean hasMantissa = integerDigits + fractionDigits > 0;
        boolean integerForm = !dot && !exponent && integerDigits > 0
                && (integerDigits == 1 || text.charAt(integerStart) != '0');
        int rest = to - p;
        if (rest == 0) {
            if (integerForm) {
                return fitsRange(text, integerStart, p, negative, Integer.MAX_VALUE) ? to : -1;
            }
            return dot && hasMantissa ? to : -1;
        }
        if (rest != 1) {
            return -1;
        }
        char suffix = Character.toLowerCase(text.charAt(p));
        switch (suffix) {
            case 'f':
            case 'd':
                return hasMantissa ? p : -1;
            case 'b':
                return integerForm && fitsRange(text, integerStart, p, negative, Byte.MAX_VALUE) ? p : -1;
            case 's':
                return integerForm && fitsRange(text, integerStart, p, negative, Short.MAX_VALUE) ? p : -1;
            case 'l':
                return integerForm && fitsRange(text, integerStart, p, negative, Long.MAX_VALUE) ? p : -1;
            default:
                return -1;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * 十进制数字串（不带符号、没有多余的前导 0）的绝对值是否落在范围里：正数到 max，负数到 max + 1
     * <p>
     * 不分配对象、不抛异常：18 位以内直接累加不会溢出；19 位只可能是 long，按无符号数比较边界。
     */
    private static boolean fitsRange(@Nonnull CharSequence text, int from, int to, boolean negative, long max) {
        int digits = to - from;
        if (digits > 19) {
            return false;
        }
        if (digits == 19) {
            if (max != Long.MAX_VALUE) {
                return false;
            }
            long value = Long.parseUnsignedLong(text, from, to, 10);
            return Long.compareUnsigned(value, negative ? Long.MIN_VALUE : Long.MAX_VALUE) <= 0;
        }
        if (max == Long.MAX_VALUE) {
            return true;
        }
        long value = 0;
        for (int i = from; i < to; i++) {
            value = value * 10 + (text.charAt(i) - '0');
        }
        return value <= (negative ? max + 1 : max);
    }
}
