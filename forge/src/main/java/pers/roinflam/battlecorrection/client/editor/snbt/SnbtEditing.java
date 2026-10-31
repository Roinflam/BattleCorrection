package pers.roinflam.battlecorrection.client.editor.snbt;

import javax.annotation.Nonnull;

/**
 * SNBT 编辑辅助：打字时的括号 / 引号补全、回车自动缩进、Tab 缩进、退格、整行操作、按词移动
 * <p>
 * 全部是对 {@link TextDocument} 的纯操作，不碰界面；控件只负责把按键翻译成这里的调用。
 * 每个操作只调用一次 {@link TextDocument#replace}，所以每个操作在撤销栈里就是一步（打字按规则合并）。
 */
public final class SnbtEditing {

    /**
     * 一级缩进的空格数
     */
    public static final int INDENT = SnbtFormatter.INDENT.length();

    private SnbtEditing() {
    }

    // 输入过滤

    /**
     * 整理外来文本（粘贴）：换行统一成 \n，Tab 换成空格，去掉其它控制字符；§ 原样保留
     * <p>
     * 原版的 SharedConstants.filterText 会把 § 当非法字符删掉，粘贴带颜色码的 SNBT 时 §c 就只剩一个孤零零的 c，
     * 所以这里自己过滤，只删真正不可见的控制字符。
     *
     * @param text 原文
     * @return 整理后的文本
     */
    @Nonnull
    public static String normalizeInput(@Nonnull String text) {
        StringBuilder out = null;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            boolean keep = c == '\n' || (c >= 0x20 && c != 0x7F);
            if (keep && out == null) {
                continue;
            }
            if (out == null) {
                out = new StringBuilder(length);
                out.append(text, 0, i);
            }
            if (keep) {
                out.append(c);
            } else if (c == '\r') {
                // \r\n 只留一个 \n；单独的 \r（老 Mac 换行）也当换行
                if (i + 1 >= length || text.charAt(i + 1) != '\n') {
                    out.append('\n');
                }
            } else if (c == '\t') {
                out.append(SnbtFormatter.INDENT);
            }
        }
        return out == null ? text : out.toString();
    }

    /**
     * 键入的单个字符能不能进文本（控制字符不行，§ 可以）
     */
    public static boolean isTypeable(char c) {
        return c >= 0x20 && c != 0x7F;
    }

    // 打字

    /**
     * 键入一个字符，带括号 / 引号补全
     * <ul>
     *   <li>有选区时键入 { [ " ' ：把选区包起来；</li>
     *   <li>键入 { [ 且后面是空白、行尾或右括号逗号冒号：补上右半边，光标停在中间；</li>
     *   <li>字符串外键入 " '，前后都不挨着文字：补成一对；</li>
     *   <li>键入右括号或闭引号时，下一个字符正好就是它：只把光标挪过去（不重复）；</li>
     *   <li>字符串里面除了闭引号以外一律原样插入。</li>
     * </ul>
     *
     * @param doc 文档
     * @param c   字符
     */
    public static void typeChar(@Nonnull TextDocument doc, char c) {
        CharSequence text = doc.chars();
        int start = doc.selectionStart();
        int end = doc.selectionEnd();
        boolean pairable = c == '{' || c == '[' || c == '"' || c == '\'';

        if (doc.hasSelection() && pairable) {
            char close = c == '{' ? '}' : c == '[' ? ']' : c;
            String inner = doc.substring(start, end);
            doc.replace(start, end, c + inner + close, TextDocument.EditKind.COMMAND, start + 1, end + 1);
            return;
        }
        if (doc.hasSelection()) {
            insertTyped(doc, String.valueOf(c));
            return;
        }

        int pos = doc.cursor();
        char next = pos < text.length() ? text.charAt(pos) : 0;
        boolean inString = SnbtLexer.inString(doc.lexStateAt(pos));

        if (inString) {
            // 字符串里：只有"正好打到闭引号上"才跳过，其余照常插入
            if ((c == '"' || c == '\'') && next == c && SnbtLexer.quoteOf(doc.lexStateAt(pos)) == c) {
                doc.moveCursor(pos + 1, false);
                return;
            }
            insertTyped(doc, String.valueOf(c));
            return;
        }

        if ((c == '}' || c == ']') && next == c) {
            doc.moveCursor(pos + 1, false);
            return;
        }
        if ((c == '{' || c == '[') && boundaryAfter(next)) {
            insertPair(doc, pos, c, c == '{' ? '}' : ']');
            return;
        }
        if (c == '"' || c == '\'') {
            char previous = pos > 0 ? text.charAt(pos - 1) : 0;
            if (boundaryAfter(next) && !SnbtLexer.isUnquotedChar(previous) && previous != '"' && previous != '\'') {
                insertPair(doc, pos, c, c);
                return;
            }
        }
        insertTyped(doc, String.valueOf(c));
    }

    /**
     * 键入一段文字（输入法一次提交多个字时也走这里），替换选区
     */
    public static void insertTyped(@Nonnull TextDocument doc, @Nonnull String value) {
        doc.replaceSelection(value, TextDocument.EditKind.TYPING);
    }

    /**
     * 粘贴：整理后替换选区，单独一步撤销
     */
    public static void paste(@Nonnull TextDocument doc, @Nonnull String clipboard) {
        String value = normalizeInput(clipboard);
        if (value.isEmpty() && !doc.hasSelection()) {
            return;
        }
        doc.replaceSelection(value, TextDocument.EditKind.PASTE);
    }

    private static void insertPair(@Nonnull TextDocument doc, int pos, char open, char close) {
        doc.replace(pos, pos, String.valueOf(open) + close, TextDocument.EditKind.TYPING, pos + 1, pos + 1);
    }

    private static boolean boundaryAfter(char next) {
        return next == 0 || Character.isWhitespace(next) || next == '}' || next == ']' || next == ',' || next == ':';
    }

    // 回车

    /**
     * 回车：沿用当前行的缩进；前面是 { 或 [ 时多缩一级；光标夹在一对空括号中间时把右括号送到再下一行；
     * 后面紧跟右括号时，右括号那一行少缩一级。字符串里只插一个换行（缩进会变成字符串内容）。
     * 换行前光标左边的行尾空白、右边的行首空白一并去掉。
     *
     * @param doc 文档
     */
    public static void newline(@Nonnull TextDocument doc) {
        CharSequence text = doc.chars();
        int start = doc.selectionStart();
        int end = doc.selectionEnd();
        if (SnbtLexer.inString(doc.lexStateAt(start))) {
            doc.replace(start, end, "\n", TextDocument.EditKind.NEWLINE, start + 1, start + 1);
            return;
        }
        int line = doc.lineOf(start);
        int lineStart = doc.lineStart(line);
        int indent = leadingSpaces(text, lineStart, start);

        int before = start;
        while (before > lineStart && text.charAt(before - 1) == ' ') {
            before--;
        }
        int afterLineEnd = doc.lineEnd(doc.lineOf(end));
        int after = end;
        while (after < afterLineEnd && text.charAt(after) == ' ') {
            after++;
        }
        char previous = before > lineStart ? text.charAt(before - 1) : 0;
        char next = after < afterLineEnd ? text.charAt(after) : 0;
        boolean opens = (previous == '{' || previous == '[') && BracketMatcher.isStructural(doc, before - 1);
        boolean closes = (next == '}' || next == ']') && BracketMatcher.isStructural(doc, after);

        String base = spaces(indent);
        String insert;
        int caret;
        if (opens && closes && next == BracketMatcher.partner(previous)) {
            String inner = spaces(indent + INDENT);
            insert = "\n" + inner + "\n" + base;
            caret = before + 1 + inner.length();
        } else if (opens) {
            insert = "\n" + spaces(indent + INDENT);
            caret = before + insert.length();
        } else if (closes) {
            insert = "\n" + spaces(Math.max(0, indent - INDENT));
            caret = before + insert.length();
        } else {
            insert = "\n" + base;
            caret = before + insert.length();
        }
        doc.replace(before, after, insert, TextDocument.EditKind.NEWLINE, caret, caret);
    }

    // 删除

    /**
     * 退格
     * <ul>
     *   <li>有选区：删选区；</li>
     *   <li>Ctrl：删到上一个词首；</li>
     *   <li>光标夹在一对空的 {} [] "" '' 中间：两边一起删；</li>
     *   <li>光标左边全是缩进空格：退一级缩进；</li>
     *   <li>否则删一个字符（两个码元组成的字符整个删）。</li>
     * </ul>
     *
     * @param doc  文档
     * @param word 是否按词删（Ctrl）
     */
    public static void backspace(@Nonnull TextDocument doc, boolean word) {
        if (doc.hasSelection()) {
            deleteSelection(doc, TextDocument.EditKind.DELETE_BACKWARD);
            return;
        }
        CharSequence text = doc.chars();
        int pos = doc.cursor();
        if (pos == 0) {
            return;
        }
        int from;
        int to = pos;
        if (word) {
            from = previousWordBoundary(text, pos);
        } else {
            char previous = text.charAt(pos - 1);
            char next = pos < text.length() ? text.charAt(pos) : 0;
            int lineStart = doc.lineStart(doc.lineOf(pos));
            int indent = leadingSpaces(text, lineStart, pos);
            if (isEmptyPair(doc, pos, previous, next)) {
                from = pos - 1;
                to = pos + 1;
            } else if (indent == pos - lineStart && indent > 0) {
                int remove = (indent - 1) % INDENT + 1;
                from = pos - remove;
            } else {
                from = pos - 1;
                if (Character.isLowSurrogate(previous) && from > 0 && Character.isHighSurrogate(text.charAt(from - 1))) {
                    from--;
                }
            }
        }
        doc.replace(from, to, "", TextDocument.EditKind.DELETE_BACKWARD, from, from);
    }

    /**
     * Delete 键：有选区删选区，Ctrl 删到下一个词边界，否则删光标右边一个字符
     *
     * @param doc  文档
     * @param word 是否按词删
     */
    public static void deleteForward(@Nonnull TextDocument doc, boolean word) {
        if (doc.hasSelection()) {
            deleteSelection(doc, TextDocument.EditKind.DELETE_FORWARD);
            return;
        }
        CharSequence text = doc.chars();
        int pos = doc.cursor();
        if (pos >= text.length()) {
            return;
        }
        int to;
        if (word) {
            to = nextWordBoundary(text, pos);
        } else {
            to = pos + 1;
            if (Character.isHighSurrogate(text.charAt(pos)) && to < text.length() && Character.isLowSurrogate(text.charAt(to))) {
                to++;
            }
        }
        doc.replace(pos, to, "", TextDocument.EditKind.DELETE_FORWARD, pos, pos);
    }

    /**
     * 删除选区
     */
    public static void deleteSelection(@Nonnull TextDocument doc, @Nonnull TextDocument.EditKind kind) {
        int start = doc.selectionStart();
        doc.replace(start, doc.selectionEnd(), "", kind, start, start);
    }

    private static boolean isEmptyPair(@Nonnull TextDocument doc, int pos, char previous, char next) {
        if (next == 0) {
            return false;
        }
        if ((previous == '{' && next == '}') || (previous == '[' && next == ']')) {
            return BracketMatcher.isStructural(doc, pos - 1);
        }
        if ((previous == '"' || previous == '\'') && next == previous) {
            // 左边那个引号必须是开引号（它前面不在字符串里），否则是 "abc"|" 这种，不能连着删
            return !SnbtLexer.inString(doc.lexStateAt(pos - 1));
        }
        return false;
    }

    // 缩进

    /**
     * Tab：没有选区时在光标处补空格到下一个缩进位；有选区时把选区碰到的每一行都缩进一级（空行不加）
     *
     * @param doc 文档
     */
    public static void indent(@Nonnull TextDocument doc) {
        CharSequence text = doc.chars();
        if (!doc.hasSelection()) {
            int pos = doc.cursor();
            int column = pos - doc.lineStart(doc.lineOf(pos));
            String pad = spaces(INDENT - column % INDENT);
            doc.replace(pos, pos, pad, TextDocument.EditKind.TYPING, pos + pad.length(), pos + pad.length());
            return;
        }
        int firstLine = doc.lineOf(doc.selectionStart());
        int lastLine = lastSelectedLine(doc);
        int blockStart = doc.lineStart(firstLine);
        int blockEnd = doc.lineEnd(lastLine);
        StringBuilder block = new StringBuilder(blockEnd - blockStart + (lastLine - firstLine + 1) * INDENT);
        int[] added = new int[lastLine - firstLine + 1];
        for (int line = firstLine; line <= lastLine; line++) {
            int ls = doc.lineStart(line);
            int le = doc.lineEnd(line);
            if (le > ls) {
                block.append(SnbtFormatter.INDENT);
                added[line - firstLine] = INDENT;
            }
            block.append(text, ls, le);
            if (line < lastLine) {
                block.append('\n');
            }
        }
        int newAnchor = shiftForIndent(doc, doc.anchor(), firstLine, added, true);
        int newCursor = shiftForIndent(doc, doc.cursor(), firstLine, added, true);
        doc.replace(blockStart, blockEnd, block.toString(), TextDocument.EditKind.COMMAND, newAnchor, newCursor);
    }

    /**
     * Shift+Tab：把光标所在行（有选区时是选区碰到的每一行）的行首空格去掉一级（不够一级就去掉全部）
     *
     * @param doc 文档
     */
    public static void outdent(@Nonnull TextDocument doc) {
        CharSequence text = doc.chars();
        int firstLine = doc.lineOf(doc.selectionStart());
        int lastLine = doc.hasSelection() ? lastSelectedLine(doc) : firstLine;
        int blockStart = doc.lineStart(firstLine);
        int blockEnd = doc.lineEnd(lastLine);
        StringBuilder block = new StringBuilder(blockEnd - blockStart);
        int[] removed = new int[lastLine - firstLine + 1];
        boolean any = false;
        for (int line = firstLine; line <= lastLine; line++) {
            int ls = doc.lineStart(line);
            int le = doc.lineEnd(line);
            int remove = Math.min(INDENT, leadingSpaces(text, ls, le));
            removed[line - firstLine] = -remove;
            any |= remove > 0;
            block.append(text, ls + remove, le);
            if (line < lastLine) {
                block.append('\n');
            }
        }
        if (!any) {
            return;
        }
        int newAnchor = shiftForIndent(doc, doc.anchor(), firstLine, removed, false);
        int newCursor = shiftForIndent(doc, doc.cursor(), firstLine, removed, false);
        doc.replace(blockStart, blockEnd, block.toString(), TextDocument.EditKind.COMMAND, newAnchor, newCursor);
    }

    /**
     * 缩进 / 反缩进之后，一个位置该挪到哪里
     *
     * @param doc        改动前的文档
     * @param pos        改动前的位置
     * @param firstLine  第一行
     * @param delta      每行行首增减的字符数
     * @param keepAtHome true = 正好在行首的位置不跟着本行的缩进走（缩进时选区起点留在行首，整行仍被选中）
     * @return 改动后的位置
     */
    private static int shiftForIndent(@Nonnull TextDocument doc, int pos, int firstLine, @Nonnull int[] delta,
                                      boolean keepAtHome) {
        int line = doc.lineOf(pos);
        int lineStart = doc.lineStart(line);
        int shift = 0;
        int lastIndex = Math.min(line - firstLine, delta.length) - 1;
        for (int k = 0; k <= lastIndex; k++) {
            shift += delta[k];
        }
        int own = line - firstLine;
        if (own >= 0 && own < delta.length) {
            int d = delta[own];
            int column = pos - lineStart;
            if (d > 0) {
                if (!(keepAtHome && column == 0)) {
                    shift += d;
                }
            } else {
                shift += Math.max(d, -column);
            }
        }
        return pos + shift;
    }

    /**
     * 选区碰到的最后一行：选区终点正好在某行行首时那一行不算（拖选整行时终点总是落在下一行行首）
     */
    private static int lastSelectedLine(@Nonnull TextDocument doc) {
        int end = doc.selectionEnd();
        int line = doc.lineOf(end);
        if (line > doc.lineOf(doc.selectionStart()) && end == doc.lineStart(line)) {
            line--;
        }
        return line;
    }

    // 整行操作

    /**
     * Ctrl+D：把当前行（有选区时是选区碰到的各行）复制一份放在下面，光标跟到复制出来的那份上
     *
     * @param doc 文档
     */
    public static void duplicateLines(@Nonnull TextDocument doc) {
        int firstLine = doc.lineOf(doc.selectionStart());
        int lastLine = doc.hasSelection() ? lastSelectedLine(doc) : firstLine;
        int blockStart = doc.lineStart(firstLine);
        int blockEnd = doc.lineEnd(lastLine);
        String block = doc.substring(blockStart, blockEnd);
        int shift = block.length() + 1;
        doc.replace(blockEnd, blockEnd, "\n" + block, TextDocument.EditKind.COMMAND,
                doc.anchor() + shift, doc.cursor() + shift);
    }

    /**
     * Alt+上 / 下：把当前行（或选区碰到的各行）和上一行 / 下一行对调
     *
     * @param doc 文档
     * @param up  true = 往上
     */
    public static void moveLines(@Nonnull TextDocument doc, boolean up) {
        int firstLine = doc.lineOf(doc.selectionStart());
        int lastLine = doc.hasSelection() ? lastSelectedLine(doc) : firstLine;
        if (up ? firstLine == 0 : lastLine == doc.lineCount() - 1) {
            return;
        }
        String block = doc.substring(doc.lineStart(firstLine), doc.lineEnd(lastLine));
        if (up) {
            int otherStart = doc.lineStart(firstLine - 1);
            String other = doc.substring(otherStart, doc.lineEnd(firstLine - 1));
            int shift = -(other.length() + 1);
            doc.replace(otherStart, doc.lineEnd(lastLine), block + "\n" + other, TextDocument.EditKind.COMMAND,
                    doc.anchor() + shift, doc.cursor() + shift);
        } else {
            int blockStart = doc.lineStart(firstLine);
            String other = doc.substring(doc.lineStart(lastLine + 1), doc.lineEnd(lastLine + 1));
            int shift = other.length() + 1;
            doc.replace(blockStart, doc.lineEnd(lastLine + 1), other + "\n" + block, TextDocument.EditKind.COMMAND,
                    doc.anchor() + shift, doc.cursor() + shift);
        }
    }

    /**
     * 选中从 fromLine 到 toLine 的整行（含行尾换行，最后一行到全文末尾）；toLine 在上面时光标停在上端
     *
     * @param doc      文档
     * @param fromLine 起始行（选区固定端所在行）
     * @param toLine   结束行（光标所在行，含）
     */
    public static void selectLines(@Nonnull TextDocument doc, int fromLine, int toLine) {
        int first = Math.min(fromLine, toLine);
        int last = Math.max(fromLine, toLine);
        int start = doc.lineStart(first);
        int end = last + 1 < doc.lineCount() ? doc.lineStart(last + 1) : doc.length();
        if (toLine < fromLine) {
            doc.setSelection(end, start);
        } else {
            doc.setSelection(start, end);
        }
    }

    /**
     * Home：先到行首第一个非空白字符，已经在那里了就到真正的行首
     *
     * @param doc 文档
     * @param pos 当前位置
     * @return 目标位置
     */
    public static int smartHome(@Nonnull TextDocument doc, int pos) {
        int lineStart = doc.lineStart(doc.lineOf(pos));
        int firstText = lineStart + leadingSpaces(doc.chars(), lineStart, doc.lineEnd(doc.lineOf(pos)));
        return pos == firstText ? lineStart : firstText;
    }

    // 按词

    /**
     * 字符分类：0 = 空白，1 = 词（字母数字、下划线、- . +、汉字等），2 = 符号
     */
    private static int charClass(char c) {
        if (Character.isWhitespace(c)) {
            return 0;
        }
        if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '+' || Character.isSurrogate(c)) {
            return 1;
        }
        return 2;
    }

    /**
     * Ctrl+左：先跳过左边的空白，再跳过一段同类字符
     */
    public static int previousWordBoundary(@Nonnull CharSequence text, int pos) {
        int i = pos;
        while (i > 0 && charClass(text.charAt(i - 1)) == 0) {
            i--;
        }
        if (i == 0) {
            return 0;
        }
        int cls = charClass(text.charAt(i - 1));
        while (i > 0 && charClass(text.charAt(i - 1)) == cls) {
            i--;
        }
        return i;
    }

    /**
     * Ctrl+右：先跳过一段同类字符，再跳过后面的空白
     */
    public static int nextWordBoundary(@Nonnull CharSequence text, int pos) {
        int length = text.length();
        int i = pos;
        if (i < length && charClass(text.charAt(i)) != 0) {
            int cls = charClass(text.charAt(i));
            while (i < length && charClass(text.charAt(i)) == cls) {
                i++;
            }
        }
        while (i < length && charClass(text.charAt(i)) == 0 && text.charAt(i) != '\n') {
            i++;
        }
        if (i == pos && i < length) {
            // 正好停在换行上：至少前进一格
            i++;
        }
        return i;
    }

    /**
     * 双击选词：offset 处（或它左边）那一段同类字符的范围
     *
     * @param text   文本
     * @param offset 位置
     * @return {起点, 终点}
     */
    @Nonnull
    public static int[] wordAt(@Nonnull CharSequence text, int offset) {
        int length = text.length();
        if (length == 0) {
            return new int[]{0, 0};
        }
        int probe = offset < length && text.charAt(offset) != '\n' ? offset : offset - 1;
        if (probe < 0 || text.charAt(probe) == '\n') {
            return new int[]{offset, offset};
        }
        int cls = charClass(text.charAt(probe));
        if (cls == 2) {
            return new int[]{probe, probe + 1};
        }
        int start = probe;
        while (start > 0 && charClass(text.charAt(start - 1)) == cls && text.charAt(start - 1) != '\n') {
            start--;
        }
        int end = probe + 1;
        while (end < length && charClass(text.charAt(end)) == cls && text.charAt(end) != '\n') {
            end++;
        }
        return new int[]{start, end};
    }

    // 颜色码

    /**
     * & ↔ § 转换：有选区作用于选区，没有就作用于全文；转换后保持原来的选区
     *
     * @param doc       文档
     * @param toSection true = & → §，false = § → &
     * @return 转换了几处
     */
    public static int convertCodes(@Nonnull TextDocument doc, boolean toSection) {
        boolean selection = doc.hasSelection();
        int start = selection ? doc.selectionStart() : 0;
        int end = selection ? doc.selectionEnd() : doc.length();
        int[] count = new int[1];
        char from = toSection ? ColorCodes.AMPERSAND : ColorCodes.SECTION;
        char to = toSection ? ColorCodes.SECTION : ColorCodes.AMPERSAND;
        String converted = ColorCodes.convert(doc.chars(), start, end, from, to, count);
        if (count[0] > 0) {
            // 长度不变：只替换第一处到最后一处之间那段，撤销栈里不用存整篇；选区原样保留
            CharSequence text = doc.chars();
            int first = 0;
            while (text.charAt(start + first) == converted.charAt(first)) {
                first++;
            }
            int last = converted.length() - 1;
            while (text.charAt(start + last) == converted.charAt(last)) {
                last--;
            }
            doc.replace(start + first, start + last + 1, converted.substring(first, last + 1),
                    TextDocument.EditKind.COMMAND, doc.anchor(), doc.cursor());
        }
        return count[0];
    }

    // 工具

    /**
     * [from, to) 开头连续空格的个数
     */
    public static int leadingSpaces(@Nonnull CharSequence text, int from, int to) {
        int i = from;
        while (i < to && text.charAt(i) == ' ') {
            i++;
        }
        return i - from;
    }

    @Nonnull
    private static String spaces(int count) {
        return " ".repeat(Math.max(0, count));
    }
}
