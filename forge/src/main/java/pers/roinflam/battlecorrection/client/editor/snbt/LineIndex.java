package pers.roinflam.battlecorrection.client.editor.snbt;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * 逻辑行（按换行符分的行）的起点表
 * <p>
 * 文本改动时只重算被改到的那几行，后面各行的起点整体平移：5 万行的文本打一个字，只是一次数组尾部搬移加平移，
 * 不用重新扫一遍全文找换行。
 * <p>
 * 约定：第 i 行的内容是 [start(i), end(i))，不含行尾的换行符；文本为空时也有一行（空行）。
 */
public final class LineIndex {

    private int[] starts = new int[64];
    private int count = 1;

    /**
     * 按整段文本重建
     *
     * @param text 文本
     */
    public void reset(@Nonnull CharSequence text) {
        count = 1;
        starts[0] = 0;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == '\n') {
                ensureCapacity(count + 1);
                starts[count++] = i + 1;
            }
        }
    }

    /**
     * 行数（至少 1）
     */
    public int count() {
        return count;
    }

    /**
     * 第 line 行的起点
     */
    public int start(int line) {
        return starts[line];
    }

    /**
     * 第 line 行内容的终点（不含换行符）
     *
     * @param line       行号（从 0 起）
     * @param textLength 全文长度
     * @return 终点下标
     */
    public int end(int line, int textLength) {
        return line + 1 < count ? starts[line + 1] - 1 : textLength;
    }

    /**
     * 下标所在的行：起点不大于它的最后一行（二分）
     *
     * @param offset 下标，越界会被夹到首尾
     * @return 行号
     */
    public int lineOf(int offset) {
        int low = 0;
        int high = count - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (starts[mid] <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * 文本里 [start, end) 被换成 inserted 之后更新起点表
     * <p>
     * 必须在文本真正改动之前或之后都只调一次；这里只用到"旧的起点表"和 inserted，不读文本本身。
     *
     * @param start    改动起点
     * @param end      旧文本里被替换部分的终点
     * @param inserted 新插入的文本
     * @return 被删掉的换行符个数
     */
    public int replace(int start, int end, @Nonnull CharSequence inserted) {
        int first = lineOf(start);
        int last = lineOf(end);
        int removed = last - first;
        int added = 0;
        int insertedLength = inserted.length();
        for (int i = 0; i < insertedLength; i++) {
            if (inserted.charAt(i) == '\n') {
                added++;
            }
        }
        int delta = insertedLength - (end - start);
        int newCount = count - removed + added;
        ensureCapacity(newCount);

        // 旧的 last 之后各行整体挪到新位置并平移；arraycopy 自己处理重叠
        int tailFrom = last + 1;
        int tailLength = count - tailFrom;
        int tailTo = first + 1 + added;
        if (tailLength > 0 && tailFrom != tailTo) {
            System.arraycopy(starts, tailFrom, starts, tailTo, tailLength);
        }
        if (delta != 0) {
            for (int i = tailTo; i < tailTo + tailLength; i++) {
                starts[i] += delta;
            }
        }
        int k = first + 1;
        for (int i = 0; i < insertedLength; i++) {
            if (inserted.charAt(i) == '\n') {
                starts[k++] = start + i + 1;
            }
        }
        count = newCount;
        return removed;
    }

    private void ensureCapacity(int size) {
        if (size > starts.length) {
            starts = Arrays.copyOf(starts, Math.max(size, starts.length + (starts.length >> 1)));
        }
    }
}
