package pers.roinflam.battlecorrection.client.editor.snbt;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * 自动换行排版：把逻辑行切成一个个显示行（行宽超了就折到下一行）
 * <p>
 * 只存每个显示行的起点（int 数组，严格递增）。文本改动时从改动点附近开始重排，排到和旧结果对齐（收敛）就停，
 * 后面的显示行起点整体平移：5 万行的文本、或者压成一行的 2MB 文本，打一个字都只重排光标附近几行，再加一次数组尾部搬移。
 * 只有宽度变化（窗口大小变了、行号栏变宽）才整篇重排。
 * <p>
 * 折行规则：尽量在空格或逗号后面折（SNBT 里元素之间就是 ", "），一整段都没有可折点时按字符硬折；
 * 两个码元组成的字符（surrogate pair）不拆开；一行最多 {@link #MAX_ROW_CHARS} 个字符，防止一长串零宽字符挤在一行里让每帧的测宽变慢。
 * <p>
 * 光标位置约定：下标恰好落在软折行处时算下一行的行首。
 */
public final class WrapLayout {

    /**
     * 每个显示行最多多少个字符
     */
    public static final int MAX_ROW_CHARS = 1024;

    private final CharWidths widths;
    private final IntArrayList scratch = new IntArrayList();
    private int[] rows = new int[64];
    private int rowCount = 1;
    private float maxWidth = 100;
    private long version = Long.MIN_VALUE;

    /**
     * @param widths 字符宽度
     */
    public WrapLayout(@Nonnull CharWidths widths) {
        this.widths = widths;
    }

    /**
     * 整篇重排
     *
     * @param doc      文档
     * @param maxWidth 行宽（像素）
     */
    public void rebuild(@Nonnull TextDocument doc, float maxWidth) {
        this.maxWidth = Math.max(1, maxWidth);
        CharSequence text = doc.chars();
        int lineCount = doc.lineCount();
        scratch.clear();
        for (int line = 0; line < lineCount; line++) {
            wrapLine(text, doc.lineStart(line), doc.lineEnd(line), scratch);
        }
        rowCount = scratch.size();
        ensureCapacity(rowCount);
        scratch.getElements(0, rows, 0, rowCount);
        version = doc.version();
    }

    /**
     * 文本改动之后增量重排；如果漏了中间的改动（版本号对不上）就整篇重排
     *
     * @param doc    文档（已经是改动之后的状态）
     * @param change 改动
     */
    public void update(@Nonnull TextDocument doc, @Nonnull TextDocument.Change change) {
        if (change.version() != version + 1) {
            rebuild(doc, maxWidth);
            return;
        }
        CharSequence text = doc.chars();
        int start = change.start();
        int delta = change.insertedLength() - change.removedLength();
        int newEnd = start + change.insertedLength();
        int firstLine = doc.lineOf(start);
        int lastLine = firstLine + change.insertedBreaks();

        // 从改动所在显示行往上数两行开始重排：第 r 行在哪折，取决于从它行首到"第一个放不下的字符"为止的文本，
        // 而那个字符最远是第 r+2 行的行首（第 r+1 行至少装得下折过去的那段），所以改动点往上两行可能被波及，再往上不会。
        // 此时 rows 还是旧排版，但改动点之前的起点新旧一致，可以直接二分。
        int lineFirstRow = lowerBound(doc.lineStart(firstLine));
        int firstRow = Math.max(lineFirstRow, rowOf(start) - 2);

        scratch.clear();
        int converged = -1;
        for (int line = firstLine; line <= lastLine && converged < 0; line++) {
            int from = line == firstLine ? rows[firstRow] : doc.lineStart(line);
            converged = wrapLine(text, from, doc.lineEnd(line), scratch, newEnd, delta);
        }
        int endRow;
        if (converged >= 0) {
            endRow = converged;
        } else {
            // 最后一个受影响逻辑行的下一行：旧起点 = 新起点 - delta
            endRow = lastLine + 1 < doc.lineCount() ? lowerBound(doc.lineStart(lastLine + 1) - delta) : rowCount;
        }
        splice(firstRow, endRow, delta);
        version = change.version();
    }

    /**
     * 排版对应的文档版本
     */
    public long version() {
        return version;
    }

    /**
     * 当前行宽
     */
    public float maxWidth() {
        return maxWidth;
    }

    public int rowCount() {
        return rowCount;
    }

    public int rowStart(int row) {
        return rows[row];
    }

    /**
     * 显示行内容的终点（不含换行符）
     *
     * @param text 全文
     * @param row  显示行
     * @return 终点下标
     */
    public int rowEnd(@Nonnull CharSequence text, int row) {
        int limit = row + 1 < rowCount ? rows[row + 1] : text.length();
        if (limit > rows[row] && text.charAt(limit - 1) == '\n') {
            limit--;
        }
        return limit;
    }

    /**
     * 这个显示行是不是所在逻辑行的最后一行（后面紧跟换行符或全文结束）
     */
    public boolean endsLine(@Nonnull CharSequence text, int row) {
        return row + 1 >= rowCount || text.charAt(rows[row + 1] - 1) == '\n';
    }

    /**
     * 下标所在的显示行（二分）
     *
     * @param offset 下标
     * @return 显示行
     */
    public int rowOf(int offset) {
        int low = 0;
        int high = rowCount - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (rows[mid] <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * [from, to) 的显示宽度
     */
    public float measure(@Nonnull CharSequence text, int from, int to) {
        float width = 0;
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < to && Character.isLowSurrogate(text.charAt(i + 1))) {
                width += widths.width(Character.toCodePoint(c, text.charAt(i + 1)));
                i += 2;
            } else {
                width += widths.width(Character.isSurrogate(c) ? 0xFFFD : c);
                i++;
            }
        }
        return width;
    }

    /**
     * 显示行里离 x 最近的字符边界
     *
     * @param text 全文
     * @param from 行起点
     * @param to   行终点
     * @param x    相对行首的横坐标
     * @return 下标
     */
    public int offsetAt(@Nonnull CharSequence text, int from, int to, double x) {
        float width = 0;
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            int length = 1;
            float charWidth;
            if (Character.isHighSurrogate(c) && i + 1 < to && Character.isLowSurrogate(text.charAt(i + 1))) {
                charWidth = widths.width(Character.toCodePoint(c, text.charAt(i + 1)));
                length = 2;
            } else {
                charWidth = widths.width(Character.isSurrogate(c) ? 0xFFFD : c);
            }
            if (x < width + charWidth / 2) {
                return i;
            }
            width += charWidth;
            i += length;
        }
        return to;
    }

    /**
     * 点击 / 上下移动时，显示行 row 里横坐标 x 处的光标位置
     * <p>
     * 和 {@link #offsetAt} 的区别在软折行的行末：那个位置按约定算下一行的行首，光标放过去会画在下一行开头，
     * 看起来像是跳到了下一行，再按一次下方向键还会越过一行。所以软折行里最远只到折行点前一个字符（两个码元组成的字符不拆）。
     * 以换行符或全文结尾收尾的行不受影响。拖选时要能选到行末，用 {@link #offsetAt}。
     *
     * @param text 全文
     * @param row  显示行
     * @param x    相对行首的横坐标
     * @return 下标
     */
    public int caretOffsetAt(@Nonnull CharSequence text, int row, double x) {
        int from = rows[row];
        int to = rowEnd(text, row);
        int pos = offsetAt(text, from, to, x);
        if (pos == to && to > from && row + 1 < rowCount && rows[row + 1] == to) {
            pos = to - 1;
            if (pos > from && Character.isLowSurrogate(text.charAt(pos)) && Character.isHighSurrogate(text.charAt(pos - 1))) {
                pos--;
            }
        }
        return pos;
    }

    /**
     * 整篇重排用：不做收敛检查
     */
    private void wrapLine(@Nonnull CharSequence text, int from, int to, @Nonnull IntArrayList out) {
        wrapLine(text, from, to, out, Integer.MAX_VALUE, 0);
    }

    /**
     * 从一个显示行行首 from 开始，把逻辑行剩下的部分切成显示行，起点依次追加到 out（第一个就是 from）
     * <p>
     * 收敛检查：新切出的行首落在改动之后（>= convergeAfter），而且旧排版里正好也有一行从对应位置（减去 delta）开始，
     * 那么从这里往后两边面对的是同一段文本、同样的初始状态，排出来一定一样，后面不用再排了。
     * 2MB 压成一行的文本里打字，靠这个只重排光标附近的几行，而不是整行重排。
     *
     * @param convergeAfter 改动之后的新文本终点；整篇重排时传 Integer.MAX_VALUE
     * @param delta         新旧长度差
     * @return 收敛到的旧显示行序号（这一行本身不追加）；没收敛返回 -1
     */
    private int wrapLine(@Nonnull CharSequence text, int from, int to, @Nonnull IntArrayList out, int convergeAfter,
                         int delta) {
        out.add(from);
        int rowStart = from;
        float width = 0;
        int lastBreak = -1;
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            int length = 1;
            float charWidth;
            if (Character.isHighSurrogate(c) && i + 1 < to && Character.isLowSurrogate(text.charAt(i + 1))) {
                charWidth = widths.width(Character.toCodePoint(c, text.charAt(i + 1)));
                length = 2;
            } else {
                charWidth = widths.width(Character.isSurrogate(c) ? 0xFFFD : c);
            }
            if (i > rowStart && (width + charWidth > maxWidth || i - rowStart >= MAX_ROW_CHARS)) {
                // 折在最近的空格 / 逗号后面；这一行里没有可折点就在当前字符前硬折
                int brk = lastBreak > rowStart ? lastBreak : i;
                if (brk >= convergeAfter) {
                    int old = lowerBound(brk - delta);
                    if (old < rowCount && rows[old] == brk - delta) {
                        return old;
                    }
                }
                out.add(brk);
                // 从新行首重新量：每一行的结果只取决于从它行首开始的文本，增量重排和收敛判断都靠这一点
                rowStart = brk;
                i = brk;
                width = 0;
                lastBreak = -1;
                continue;
            }
            width += charWidth;
            i += length;
            if (c == ' ' || c == ',') {
                lastBreak = i;
            }
        }
        return -1;
    }

    /**
     * 第一个起点 >= value 的显示行
     */
    private int lowerBound(int value) {
        int low = 0;
        int high = rowCount;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (rows[mid] < value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * 用 scratch 里的新行替换 [from, to)，后面的行起点平移 delta
     */
    private void splice(int from, int to, int delta) {
        int inserted = scratch.size();
        int tail = rowCount - to;
        int newCount = from + inserted + tail;
        if (newCount > rows.length) {
            int[] grown = new int[Math.max(newCount, rows.length + (rows.length >> 1))];
            System.arraycopy(rows, 0, grown, 0, from);
            System.arraycopy(rows, to, grown, from + inserted, tail);
            rows = grown;
        } else if (tail > 0 && to != from + inserted) {
            System.arraycopy(rows, to, rows, from + inserted, tail);
        }
        if (delta != 0) {
            for (int i = from + inserted; i < newCount; i++) {
                rows[i] += delta;
            }
        }
        scratch.getElements(0, rows, from, inserted);
        rowCount = newCount;
    }

    private void ensureCapacity(int size) {
        if (size > rows.length) {
            rows = Arrays.copyOf(rows, Math.max(size, rows.length + (rows.length >> 1)));
        }
    }
}
