package pers.roinflam.battlecorrection.client.editor.snbt;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.LongSupplier;

/**
 * SNBT 页的文本模型：文本 + 光标 / 选区 + 行表 + 撤销栈 + 分词状态缓存
 * <p>
 * 和界面控件分开放是为了两件事：
 * <ul>
 *   <li>页面切走再回来、窗口大小变化时控件会重建，文本、光标、选区、滚动位置、撤销历史都还在这里，不会丢；</li>
 *   <li>所有编辑逻辑（自动缩进、括号补全、撤销合并……）都能脱离游戏单独测试。</li>
 * </ul>
 * 文本放在 StringBuilder 里，改动是原地替换；只有需要交给别的线程（后台校验）时才 {@link #snapshot()} 拷一份。
 * 所有改动都走 {@link #replace}，它负责更新行表、作废分词状态、记撤销、通知视图（增量排版）和页面（脏标记、校验）。
 * <p>
 * 只在主线程用。
 */
public final class TextDocument {

    /**
     * 改动的种类，决定撤销时怎么合并（见 {@link TextHistory}）
     */
    public enum EditKind {
        TYPING,
        NEWLINE,
        DELETE_BACKWARD,
        DELETE_FORWARD,
        PASTE,
        COMMAND
    }

    /**
     * 一次改动的描述，给视图做增量排版
     */
    public static final class Change {

        private final int start;
        private final int removedLength;
        private final int insertedLength;
        private final int removedBreaks;
        private final int insertedBreaks;
        private final long version;

        /**
         * @param start          改动起点
         * @param removedLength  删掉的长度
         * @param insertedLength 插入的长度
         * @param removedBreaks  删掉的换行符个数
         * @param insertedBreaks 插入的换行符个数
         * @param version        改动之后的版本号
         */
        public Change(int start, int removedLength, int insertedLength, int removedBreaks, int insertedBreaks,
                      long version) {
            this.start = start;
            this.removedLength = removedLength;
            this.insertedLength = insertedLength;
            this.removedBreaks = removedBreaks;
            this.insertedBreaks = insertedBreaks;
            this.version = version;
        }

        public int start() {
            return start;
        }

        public int removedLength() {
            return removedLength;
        }

        public int insertedLength() {
            return insertedLength;
        }

        public int removedBreaks() {
            return removedBreaks;
        }

        public int insertedBreaks() {
            return insertedBreaks;
        }

        public long version() {
            return version;
        }
    }

    /**
     * 文本改动的回调
     */
    @FunctionalInterface
    public interface Listener {
        void onChanged(@Nonnull Change change);
    }

    private final StringBuilder text = new StringBuilder();
    private final LineIndex lines = new LineIndex();
    private final TextHistory history = new TextHistory();
    private final LexerStates lexStates = new LexerStates();
    private final LongSupplier clock;

    private int cursor;
    private int anchor;
    private long version;
    private int viewTop;
    private int viewTopPixel;

    @Nullable
    private Listener view;
    @Nullable
    private Listener owner;

    public TextDocument() {
        this(System::currentTimeMillis);
    }

    /**
     * @param clock 毫秒时钟（撤销合并按它判断停顿；测试时可以换成假时钟）
     */
    public TextDocument(@Nonnull LongSupplier clock) {
        this.clock = clock;
    }

    // 读

    public int length() {
        return text.length();
    }

    public char charAt(int index) {
        return text.charAt(index);
    }

    /**
     * 文本本体（只读用，别强转回去改）
     */
    @Nonnull
    public CharSequence chars() {
        return text;
    }

    /**
     * 拷一份不可变的文本（交给后台线程、写剪贴板用）
     */
    @Nonnull
    public String snapshot() {
        return text.toString();
    }

    @Nonnull
    public String substring(int start, int end) {
        return text.substring(start, end);
    }

    /**
     * 内容是否和给定文本完全相同（长度不同时不逐字比较）
     */
    public boolean contentEquals(@Nonnull String other) {
        return other.length() == text.length() && other.contentEquals(text);
    }

    /**
     * 版本号：每次文本改动加一，光标移动不算
     */
    public long version() {
        return version;
    }

    public int lineCount() {
        return lines.count();
    }

    public int lineStart(int line) {
        return lines.start(line);
    }

    /**
     * 行内容终点（不含换行符）
     */
    public int lineEnd(int line) {
        return lines.end(line, text.length());
    }

    public int lineOf(int offset) {
        return lines.lineOf(offset);
    }

    /**
     * 第 line 行行首的分词状态
     */
    public int lexStateAtLine(int line) {
        return lexStates.stateAt(this, line);
    }

    /**
     * 任意位置的分词状态（主要用来判断"是不是在字符串里"）
     */
    public int lexStateAt(int offset) {
        int line = lineOf(offset);
        return SnbtLexer.scanState(text, lineStart(line), offset, lexStateAtLine(line));
    }

    // 光标 / 选区

    public int cursor() {
        return cursor;
    }

    public int anchor() {
        return anchor;
    }

    public int selectionStart() {
        return Math.min(cursor, anchor);
    }

    public int selectionEnd() {
        return Math.max(cursor, anchor);
    }

    public boolean hasSelection() {
        return cursor != anchor;
    }

    @Nonnull
    public String selectedText() {
        return text.substring(selectionStart(), selectionEnd());
    }

    /**
     * 设置选区（越界会被夹回去）；位置变了就断开撤销合并
     *
     * @param anchor 固定端
     * @param cursor 活动端（光标）
     */
    public void setSelection(int anchor, int cursor) {
        int length = text.length();
        int newAnchor = clamp(anchor, length);
        int newCursor = clamp(cursor, length);
        if (newAnchor != this.anchor || newCursor != this.cursor) {
            history.seal();
        }
        this.anchor = newAnchor;
        this.cursor = newCursor;
    }

    /**
     * 光标移到 pos
     *
     * @param pos    位置
     * @param extend true = 保留固定端（扩选），false = 清掉选区
     */
    public void moveCursor(int pos, boolean extend) {
        setSelection(extend ? anchor : pos, pos);
    }

    /**
     * 全选
     */
    public void selectAll() {
        setSelection(0, text.length());
    }

    // 改

    /**
     * 把 [start, end) 换成 insert，并设置新的选区
     *
     * @param start     起点
     * @param end       终点
     * @param insert    新文本（调用方负责过滤非法字符）
     * @param kind      改动种类（决定撤销合并）
     * @param newAnchor 改完之后的选区固定端
     * @param newCursor 改完之后的光标
     */
    public void replace(int start, int end, @Nonnull String insert, @Nonnull EditKind kind, int newAnchor, int newCursor) {
        replaceInternal(start, end, insert, kind, newAnchor, newCursor, true);
    }

    /**
     * 用 insert 替换选区，光标落在插入内容之后
     */
    public void replaceSelection(@Nonnull String insert, @Nonnull EditKind kind) {
        int start = selectionStart();
        int after = start + insert.length();
        replace(start, selectionEnd(), insert, kind, after, after);
    }

    private void replaceInternal(int start, int end, @Nonnull String insert, @Nonnull EditKind kind,
                                 int newAnchor, int newCursor, boolean record) {
        int length = text.length();
        if (start < 0 || end > length || start > end) {
            throw new IndexOutOfBoundsException("replace [" + start + ", " + end + ") of " + length);
        }
        if (start == end && insert.isEmpty()) {
            setSelection(newAnchor, newCursor);
            return;
        }
        String removed = text.substring(start, end);
        int firstLine = lines.lineOf(start);
        int removedBreaks = lines.replace(start, end, insert);
        text.replace(start, end, insert);
        lexStates.invalidateFrom(firstLine);
        version++;

        int anchorBefore = anchor;
        int cursorBefore = cursor;
        int newLength = text.length();
        anchor = clamp(newAnchor, newLength);
        cursor = clamp(newCursor, newLength);
        if (record) {
            history.record(start, removed, insert, kind, anchorBefore, cursorBefore, anchor, cursor, clock.getAsLong());
        }
        notifyChanged(new Change(start, end - start, insert.length(), removedBreaks, countBreaks(insert), version));
    }

    /**
     * 整体换掉文本，不记撤销（首次载入用）；光标回到开头
     *
     * @param value 新文本
     */
    public void setText(@Nonnull String value) {
        int oldLength = text.length();
        int oldBreaks = lines.count() - 1;
        text.setLength(0);
        text.append(value);
        lines.reset(text);
        lexStates.reset();
        history.clear();
        version++;
        cursor = 0;
        anchor = 0;
        viewTop = 0;
        viewTopPixel = 0;
        notifyChanged(new Change(0, oldLength, value.length(), oldBreaks, lines.count() - 1, version));
    }

    /**
     * 整体换成新文本，但只替换真正不同的那一段（掐头去尾），记一步撤销
     * <p>
     * 重新载入、格式化、颜色码转换都走这里：没变的部分不动，光标在没变的部分里就留在原处，
     * 撤销栈里也只存变了的那一段。
     *
     * @param value 新文本
     * @param kind  改动种类
     * @return true = 真的改了
     */
    public boolean replaceAll(@Nonnull String value, @Nonnull EditKind kind) {
        int oldLength = text.length();
        int newLength = value.length();
        int max = Math.min(oldLength, newLength);
        int prefix = 0;
        while (prefix < max && text.charAt(prefix) == value.charAt(prefix)) {
            prefix++;
        }
        if (prefix == oldLength && prefix == newLength) {
            return false;
        }
        int suffix = 0;
        while (suffix < max - prefix && text.charAt(oldLength - 1 - suffix) == value.charAt(newLength - 1 - suffix)) {
            suffix++;
        }
        int oldEnd = oldLength - suffix;
        int newEnd = newLength - suffix;
        replace(prefix, oldEnd, value.substring(prefix, newEnd), kind,
                mapThrough(anchor, prefix, oldEnd, newEnd), mapThrough(cursor, prefix, oldEnd, newEnd));
        return true;
    }

    /**
     * 位置跟着一次替换移动：在替换段之前不动，之后平移，落在段内的移到新段末尾
     */
    private static int mapThrough(int pos, int start, int oldEnd, int newEnd) {
        if (pos <= start) {
            return pos;
        }
        if (pos >= oldEnd) {
            return pos + (newEnd - oldEnd);
        }
        return newEnd;
    }

    // 撤销

    /**
     * 撤销一步
     *
     * @return true = 撤了
     */
    public boolean undo() {
        TextHistory.Entry entry = history.popUndo();
        if (entry == null) {
            return false;
        }
        replaceInternal(entry.start, entry.start + entry.inserted.length(), entry.removed.toString(), EditKind.COMMAND,
                entry.anchorBefore, entry.cursorBefore, false);
        return true;
    }

    /**
     * 重做一步
     *
     * @return true = 重做了
     */
    public boolean redo() {
        TextHistory.Entry entry = history.popRedo();
        if (entry == null) {
            return false;
        }
        replaceInternal(entry.start, entry.start + entry.removed.length(), entry.inserted.toString(), EditKind.COMMAND,
                entry.anchorAfter, entry.cursorAfter, false);
        return true;
    }

    /**
     * 断开撤销合并（应用、失去焦点之后调用）
     */
    public void sealHistory() {
        history.seal();
    }

    @Nonnull
    public TextHistory history() {
        return history;
    }

    // 视图状态 / 监听

    /**
     * 上次可视区域顶端那一行的起点（控件重建时据此恢复滚动位置）
     */
    public int viewTop() {
        return Math.min(viewTop, text.length());
    }

    /**
     * 顶端那一行被卷上去的像素数（滚动不一定停在整行上，例如滚到底时）
     */
    public int viewTopPixel() {
        return viewTopPixel;
    }

    public void setViewTop(int offset) {
        setViewTop(offset, 0);
    }

    /**
     * @param offset 顶端那一行的起点
     * @param pixel  这一行被卷上去的像素数
     */
    public void setViewTop(int offset, int pixel) {
        this.viewTop = offset;
        this.viewTopPixel = Math.max(0, pixel);
    }

    /**
     * 视图监听：同一时间只有一个控件在显示这份文档，新控件建起来时直接顶替旧的
     */
    public void setViewListener(@Nullable Listener listener) {
        this.view = listener;
    }

    /**
     * 页面监听
     */
    public void setOwnerListener(@Nullable Listener listener) {
        this.owner = listener;
    }

    private void notifyChanged(@Nonnull Change change) {
        if (view != null) {
            view.onChanged(change);
        }
        if (owner != null) {
            owner.onChanged(change);
        }
    }

    private static int countBreaks(@Nonnull String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static int clamp(int value, int length) {
        return value < 0 ? 0 : Math.min(value, length);
    }
}
