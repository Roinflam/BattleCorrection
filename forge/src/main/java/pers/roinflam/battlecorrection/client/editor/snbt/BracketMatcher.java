package pers.roinflam.battlecorrection.client.editor.snbt;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import javax.annotation.Nonnull;

/**
 * 括号配对：光标停在括号上时找它的另一半
 * <p>
 * 字符串里的括号不算（"a{b" 里的 { 是文字）。往后找时从括号处顺着扫、边扫边跟踪引号；
 * 往前找时不能倒着判断引号（分不清开引号闭引号），所以按行往前：每行从行首状态正着扫一遍，收集这一行的结构括号，再倒序处理。
 * 两个方向都只扫括号之间的那段，扫描长度有上限，超了就当没找到。
 */
public final class BracketMatcher {

    /**
     * 最多扫描多少个字符
     */
    public static final int SCAN_LIMIT = 4 * 1024 * 1024;

    private static final int NOT_FOUND = -1;

    private BracketMatcher() {
    }

    /**
     * 是不是开括号
     */
    public static boolean isOpen(char c) {
        return c == '{' || c == '[';
    }

    /**
     * 另一半
     */
    public static char partner(char c) {
        return switch (c) {
            case '{' -> '}';
            case '}' -> '{';
            case '[' -> ']';
            case ']' -> '[';
            default -> 0;
        };
    }

    /**
     * offset 处是不是字符串外面的结构括号
     *
     * @param doc    文档
     * @param offset 下标
     * @return true = 是
     */
    public static boolean isStructural(@Nonnull TextDocument doc, int offset) {
        if (offset < 0 || offset >= doc.length() || !SnbtLexer.isBracket(doc.charAt(offset))) {
            return false;
        }
        return !SnbtLexer.inString(doc.lexStateAt(offset));
    }

    /**
     * 找配对的括号
     * <p>
     * 按嵌套深度配对，不看括号种类；种类对不上（{ 配到了 ]）由调用方用 {@link #partner} 判断后标红。
     *
     * @param doc    文档
     * @param offset 结构括号的下标（调用方先用 {@link #isStructural} 确认）
     * @return 另一半的下标，找不到返回 -1
     */
    public static int findMatch(@Nonnull TextDocument doc, int offset) {
        char c = doc.charAt(offset);
        return isOpen(c) ? scanForward(doc.chars(), offset) : scanBackward(doc, offset);
    }

    private static int scanForward(@Nonnull CharSequence text, int offset) {
        int length = text.length();
        int limit = (int) Math.min(length, (long) offset + SCAN_LIMIT);
        int depth = 0;
        char quote = 0;
        for (int i = offset; i < limit; i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return NOT_FOUND;
    }

    private static int scanBackward(@Nonnull TextDocument doc, int offset) {
        CharSequence text = doc.chars();
        IntArrayList brackets = new IntArrayList();
        int depth = 0;
        int scanned = 0;
        int line = doc.lineOf(offset);
        int end = offset + 1;
        while (line >= 0) {
            int start = doc.lineStart(line);
            collectBrackets(text, start, end, doc.lexStateAtLine(line), brackets);
            for (int k = brackets.size() - 1; k >= 0; k--) {
                int pos = brackets.getInt(k);
                char c = text.charAt(pos);
                if (c == '}' || c == ']') {
                    depth++;
                } else {
                    depth--;
                    if (depth == 0) {
                        return pos;
                    }
                }
            }
            scanned += end - start;
            if (scanned > SCAN_LIMIT) {
                return NOT_FOUND;
            }
            line--;
            if (line >= 0) {
                end = doc.lineEnd(line);
            }
        }
        return NOT_FOUND;
    }

    /**
     * 收集 [from, to) 里字符串外面的括号位置
     */
    private static void collectBrackets(@Nonnull CharSequence text, int from, int to, int state,
                                        @Nonnull IntArrayList out) {
        out.clear();
        char quote = SnbtLexer.quoteOf(state);
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (SnbtLexer.isBracket(c)) {
                out.add(i);
            }
        }
    }
}
