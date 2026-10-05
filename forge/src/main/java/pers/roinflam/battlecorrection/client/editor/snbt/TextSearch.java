package pers.roinflam.battlecorrection.client.editor.snbt;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * 查找：算出全部命中的位置，以及上一个 / 下一个的定位
 * <p>
 * 命中不重叠（找到一处就从它末尾接着找），按起点升序存在 int 数组里，界面画高亮时二分出可视范围内的那几个。
 * 命中太多（比如搜一个字母）只记前 {@link #MAX_HITS} 个，计数显示成"N+"。
 * 只读传入的文本，可以在后台线程对快照调用。
 */
public final class TextSearch {

    /**
     * 最多记多少个命中
     */
    public static final int MAX_HITS = 100_000;

    /**
     * 一次查找的结果
     *
     * @param hits      命中起点（升序，长度可能大于 count）
     * @param count     命中个数
     * @param length    查找词长度
     * @param truncated 是否因为太多被截断
     * @param version   查找时的文档版本
     */
    public record Result(@Nonnull int[] hits, int count, int length, boolean truncated, long version) {

        /**
         * 第一个起点 >= offset 的命中序号（可能等于 count）
         */
        public int lowerBound(int offset) {
            int low = 0;
            int high = count;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (hits[mid] < offset) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            return low;
        }

        /**
         * 恰好从 start 开始、长度为 length 的命中的序号
         *
         * @return 没有返回 -1
         */
        public int indexOf(int start, int end) {
            if (end - start != length) {
                return -1;
            }
            int index = lowerBound(start);
            return index < count && hits[index] == start ? index : -1;
        }

        /**
         * 下一个命中：起点 >= from 的第一个，没有就绕回第一个
         *
         * @return 序号，没有命中返回 -1
         */
        public int next(int from) {
            if (count == 0) {
                return -1;
            }
            int index = lowerBound(from);
            return index < count ? index : 0;
        }

        /**
         * 上一个命中：起点 < before 的最后一个，没有就绕到最后一个
         *
         * @return 序号，没有命中返回 -1
         */
        public int previous(int before) {
            if (count == 0) {
                return -1;
            }
            int index = lowerBound(before) - 1;
            return index >= 0 ? index : count - 1;
        }
    }

    private TextSearch() {
    }

    /**
     * 找出全部命中
     *
     * @param text          文本
     * @param needle        查找词（非空）
     * @param caseSensitive 是否区分大小写
     * @param version       文档版本（原样记进结果）
     * @return 结果
     */
    @Nonnull
    public static Result findAll(@Nonnull CharSequence text, @Nonnull String needle, boolean caseSensitive, long version) {
        int[] hits = new int[16];
        int count = 0;
        boolean truncated = false;
        int needleLength = needle.length();
        int length = text.length();
        if (needleLength == 0 || needleLength > length) {
            return new Result(hits, 0, needleLength, false, version);
        }
        if (caseSensitive) {
            int from = 0;
            while (true) {
                int found = indexOf(text, needle, from);
                if (found < 0) {
                    break;
                }
                if (count == MAX_HITS) {
                    truncated = true;
                    break;
                }
                if (count == hits.length) {
                    hits = Arrays.copyOf(hits, count * 2);
                }
                hits[count++] = found;
                from = found + needleLength;
            }
        } else {
            char[] folded = new char[needleLength];
            for (int i = 0; i < needleLength; i++) {
                folded[i] = fold(needle.charAt(i));
            }
            char first = folded[0];
            int last = length - needleLength;
            int i = 0;
            outer:
            while (i <= last) {
                if (fold(text.charAt(i)) != first) {
                    i++;
                    continue;
                }
                for (int k = 1; k < needleLength; k++) {
                    if (fold(text.charAt(i + k)) != folded[k]) {
                        i++;
                        continue outer;
                    }
                }
                if (count == MAX_HITS) {
                    truncated = true;
                    break;
                }
                if (count == hits.length) {
                    hits = Arrays.copyOf(hits, count * 2);
                }
                hits[count++] = i;
                i += needleLength;
            }
        }
        return new Result(hits, count, needleLength, truncated, version);
    }

    /**
     * 不分大小写比较时的折叠（和 String.regionMatches(true, ...) 一样先转大写再转小写，逐字符、不改长度）
     */
    private static char fold(char c) {
        if (c < 128) {
            return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
        }
        return Character.toLowerCase(Character.toUpperCase(c));
    }

    private static int indexOf(@Nonnull CharSequence text, @Nonnull String needle, int from) {
        if (text instanceof StringBuilder builder) {
            return builder.indexOf(needle, from);
        }
        if (text instanceof String string) {
            return string.indexOf(needle, from);
        }
        int needleLength = needle.length();
        int last = text.length() - needleLength;
        char first = needle.charAt(0);
        outer:
        for (int i = from; i <= last; i++) {
            if (text.charAt(i) != first) {
                continue;
            }
            for (int k = 1; k < needleLength; k++) {
                if (text.charAt(i + k) != needle.charAt(k)) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
