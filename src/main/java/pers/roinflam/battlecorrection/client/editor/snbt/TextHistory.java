package pers.roinflam.battlecorrection.client.editor.snbt;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * 文本级撤销 / 重做栈
 * <p>
 * 存的是"在哪儿、删了什么、插了什么"，不存整份快照：2MB 的文本打一个字只多存一个字符。
 * <p>
 * 合并规则：
 * <ul>
 *   <li>连续打字（每次都接在上一次的末尾）合并成一步；</li>
 *   <li>连续退格、连续 Delete 各自合并成一步；</li>
 *   <li>停顿超过 {@link #MERGE_WINDOW_MS}、移动光标、换行、粘贴、整段命令（格式化、缩进等）都会断开，各成一步。</li>
 * </ul>
 * 上限：最多 {@link #MAX_STEPS} 步，两个栈里存的字符总数不超过 {@link #MAX_CHARS}，超了从最老的开始丢；
 * 最新的一步无论多大都保留，不然对整篇做的格式化就撤不回来了。
 */
public final class TextHistory {

    /**
     * 最多保留的步数
     */
    public static final int MAX_STEPS = 200;

    /**
     * 两个栈合计最多存多少字符（约 16MB 内存）
     */
    public static final long MAX_CHARS = 8L * 1024 * 1024;

    /**
     * 打字停顿多久算新的一步
     */
    public static final long MERGE_WINDOW_MS = 800L;

    /**
     * 一步改动
     */
    public static final class Entry {
        int start;
        final StringBuilder removed;
        final StringBuilder inserted;
        final int anchorBefore;
        final int cursorBefore;
        int anchorAfter;
        int cursorAfter;
        final TextDocument.EditKind kind;
        long time;

        Entry(int start, @Nonnull String removed, @Nonnull String inserted, @Nonnull TextDocument.EditKind kind,
              int anchorBefore, int cursorBefore, int anchorAfter, int cursorAfter, long time) {
            this.start = start;
            this.removed = new StringBuilder(removed);
            this.inserted = new StringBuilder(inserted);
            this.kind = kind;
            this.anchorBefore = anchorBefore;
            this.cursorBefore = cursorBefore;
            this.anchorAfter = anchorAfter;
            this.cursorAfter = cursorAfter;
            this.time = time;
        }

        long size() {
            return (long) removed.length() + inserted.length();
        }
    }

    private final ArrayDeque<Entry> undo = new ArrayDeque<>();
    private final ArrayDeque<Entry> redo = new ArrayDeque<>();
    private long totalChars;
    private boolean sealed = true;

    /**
     * 记一步改动（可能并进上一步）
     */
    void record(int start, @Nonnull String removed, @Nonnull String inserted, @Nonnull TextDocument.EditKind kind,
                int anchorBefore, int cursorBefore, int anchorAfter, int cursorAfter, long now) {
        Entry last = undo.peekLast();
        if (!sealed && last != null && last.kind == kind && now - last.time <= MERGE_WINDOW_MS && tryMerge(last, start, removed, inserted)) {
            last.anchorAfter = anchorAfter;
            last.cursorAfter = cursorAfter;
            last.time = now;
            totalChars += removed.length() + inserted.length();
            trim();
            return;
        }
        clearRedo();
        Entry entry = new Entry(start, removed, inserted, kind, anchorBefore, cursorBefore, anchorAfter, cursorAfter, now);
        undo.addLast(entry);
        totalChars += entry.size();
        // 换行、粘贴、整段命令自成一步，后面的打字不再往里并
        sealed = kind != TextDocument.EditKind.TYPING
                && kind != TextDocument.EditKind.DELETE_BACKWARD
                && kind != TextDocument.EditKind.DELETE_FORWARD;
        trim();
    }

    private static boolean tryMerge(@Nonnull Entry last, int start, @Nonnull String removed, @Nonnull String inserted) {
        switch (last.kind) {
            case TYPING:
                if (removed.isEmpty() && start == last.start + last.inserted.length()) {
                    last.inserted.append(inserted);
                    return true;
                }
                return false;
            case DELETE_BACKWARD:
                if (inserted.isEmpty() && last.inserted.length() == 0 && start + removed.length() == last.start) {
                    last.removed.insert(0, removed);
                    last.start = start;
                    return true;
                }
                return false;
            case DELETE_FORWARD:
                if (inserted.isEmpty() && last.inserted.length() == 0 && start == last.start) {
                    last.removed.append(removed);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * 断开合并：下一次改动一定是新的一步
     */
    public void seal() {
        sealed = true;
    }

    /**
     * 取出要撤销的一步（同时挪进重做栈）
     *
     * @return 没得撤时返回 null
     */
    @Nullable
    Entry popUndo() {
        Entry entry = undo.pollLast();
        if (entry != null) {
            redo.addLast(entry);
        }
        sealed = true;
        return entry;
    }

    /**
     * 取出要重做的一步（同时挪回撤销栈）
     *
     * @return 没得重做时返回 null
     */
    @Nullable
    Entry popRedo() {
        Entry entry = redo.pollLast();
        if (entry != null) {
            undo.addLast(entry);
        }
        sealed = true;
        return entry;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /**
     * 撤销栈步数
     */
    public int undoSize() {
        return undo.size();
    }

    /**
     * 两个栈合计存的字符数
     */
    public long totalChars() {
        return totalChars;
    }

    /**
     * 清空
     */
    void clear() {
        undo.clear();
        redo.clear();
        totalChars = 0;
        sealed = true;
    }

    private void clearRedo() {
        for (Entry entry : redo) {
            totalChars -= entry.size();
        }
        redo.clear();
    }

    /**
     * 超上限时丢最老的；先丢重做栈里最远的那头，再丢撤销栈最底下的，最新的一步永远保留
     */
    private void trim() {
        while (undo.size() > MAX_STEPS) {
            totalChars -= undo.pollFirst().size();
        }
        while (totalChars > MAX_CHARS && !redo.isEmpty()) {
            totalChars -= redo.pollFirst().size();
        }
        Iterator<Entry> iterator = undo.iterator();
        while (totalChars > MAX_CHARS && undo.size() > 1 && iterator.hasNext()) {
            Entry oldest = iterator.next();
            iterator.remove();
            totalChars -= oldest.size();
        }
    }
}
