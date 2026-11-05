package pers.roinflam.battlecorrection.client.editor.widget;

import javax.annotation.Nonnull;

/**
 * 一排按钮的排版：一行放得下就一行，放不下按给定的断点分行，断点分出来的某一行还放不下就在各组里逐个往下折
 * （每组仍从新的一行开始，同组的按钮不会被拆到别的组那一行）
 * <p>
 * 每个按钮有两个宽度：舒服的宽度（文字两边留足空）和最小宽度（文字刚好不被裁成省略号）。
 * 一行按舒服的宽度放得下就照放；放不下先在最小宽度和舒服宽度之间按比例收，再不行把间距都缩成 {@link #TIGHT_GAP}；
 * 最小宽度都放不下才算这一行放不下。只算位置，不建控件。
 * <p>
 * 不在总宽超了时整排按比例缩：那样窄窗口下每个按钮都比文字窄一点，会全被裁成"…"，一个也认不出来。
 */
public final class ButtonRows {

    /**
     * 行与行之间的竖直间距
     */
    public static final int ROW_GAP = 2;
    /**
     * 挤的时候按钮之间至少留多少
     */
    public static final int TIGHT_GAP = 2;

    private final int[] row;
    private final int[] offset;
    private final int[] width;
    private int rows;

    private ButtonRows(int count) {
        this.row = new int[count];
        this.offset = new int[count];
        this.width = new int[count];
    }

    /**
     * FlatButton 能把文字完整画出来的最小宽度（六边轮廓两端各收 4 像素）
     *
     * @param textWidth 文字宽度
     * @return 最小宽度
     */
    public static int minWidthFor(int textWidth) {
        return textWidth + 8;
    }

    /**
     * 排版
     *
     * @param available 可用宽度
     * @param preferred 每个按钮舒服的宽度
     * @param minimum   每个按钮的最小宽度（不大于舒服的宽度）
     * @param gapAfter  每个按钮后面的间距（行尾的那个不算）
     * @param breaks    放不下一行时优先在这些下标前换行（升序）
     * @return 排好的结果
     */
    @Nonnull
    public static ButtonRows layout(int available, @Nonnull int[] preferred, @Nonnull int[] minimum,
                                    @Nonnull int[] gapAfter, @Nonnull int... breaks) {
        int count = preferred.length;
        ButtonRows result = new ButtonRows(count);
        if (count == 0) {
            return result;
        }
        if (result.placeRow(0, count, 0, available, preferred, minimum, gapAfter)) {
            result.rows = 1;
            return result;
        }
        if (breaks.length > 0) {
            int start = 0;
            int line = 0;
            boolean fits = true;
            for (int i = 0; i <= breaks.length && fits; i++) {
                int end = i < breaks.length ? Math.min(count, breaks[i]) : count;
                if (end <= start) {
                    continue;
                }
                fits = result.placeRow(start, end, line++, available, preferred, minimum, gapAfter);
                start = end;
            }
            if (fits) {
                result.rows = line;
                return result;
            }
        }
        // 断点分出来的行还放不下：每一组各自从新的一行开始，组内逐个往后放，放不下就折到下一行
        int line = 0;
        int groupStart = 0;
        for (int i = 0; i <= breaks.length; i++) {
            int groupEnd = i < breaks.length ? Math.min(count, breaks[i]) : count;
            if (groupEnd > groupStart) {
                line = result.flow(groupStart, groupEnd, line, available, preferred, minimum, gapAfter);
                groupStart = groupEnd;
            }
        }
        result.rows = line;
        return result;
    }

    /**
     * 把 [start, end) 从第 line 行开始逐个往后放，放不下就折到下一行
     *
     * @return 用完之后的下一行
     */
    private int flow(int start, int end, int line, int available, @Nonnull int[] preferred,
                     @Nonnull int[] minimum, @Nonnull int[] gapAfter) {
        while (start < end) {
            int rowEnd = start + 1;
            while (rowEnd < end && minimumTotal(start, rowEnd + 1, minimum) <= available) {
                rowEnd++;
            }
            if (!placeRow(start, rowEnd, line, available, preferred, minimum, gapAfter)) {
                // 单个按钮的最小宽度都比可用宽度大：只能给它整行，文字会被裁掉
                row[start] = line;
                offset[start] = 0;
                width[start] = Math.max(1, available);
            }
            line++;
            start = rowEnd;
        }
        return line;
    }

    /**
     * 最紧凑时 [start, end) 这些按钮占多宽
     */
    private static int minimumTotal(int start, int end, @Nonnull int[] minimum) {
        int total = (end - start - 1) * TIGHT_GAP;
        for (int i = start; i < end; i++) {
            total += minimum[i];
        }
        return total;
    }

    /**
     * 把 [start, end) 放进第 line 行
     *
     * @return false = 最紧凑也放不下（这时什么都没写）
     */
    private boolean placeRow(int start, int end, int line, int available, @Nonnull int[] preferred,
                             @Nonnull int[] minimum, @Nonnull int[] gapAfter) {
        int gaps = 0;
        int preferredTotal = 0;
        int minimumTotal = 0;
        for (int i = start; i < end; i++) {
            preferredTotal += preferred[i];
            minimumTotal += minimum[i];
            if (i < end - 1) {
                gaps += gapAfter[i];
            }
        }
        boolean tight = false;
        if (preferredTotal + gaps > available && minimumTotal + gaps > available) {
            gaps = (end - start - 1) * TIGHT_GAP;
            tight = true;
            if (minimumTotal + gaps > available) {
                return false;
            }
        }
        // 放不下舒服的宽度时，多出来的空间按各自"想多要多少"的比例分
        int slack = available - minimumTotal - gaps;
        int wanted = preferredTotal - minimumTotal;
        boolean squeeze = preferredTotal + gaps > available;
        int cursor = 0;
        for (int i = start; i < end; i++) {
            int w = preferred[i];
            if (squeeze) {
                int extra = preferred[i] - minimum[i];
                w = minimum[i] + (wanted <= 0 ? 0 : (int) ((long) extra * slack / wanted));
            }
            row[i] = line;
            offset[i] = cursor;
            width[i] = w;
            cursor += w + (tight ? TIGHT_GAP : gapAfter[i]);
        }
        return true;
    }

    /**
     * 一共几行
     */
    public int rows() {
        return rows;
    }

    /**
     * 第 index 个按钮在第几行（从 0 起）
     */
    public int row(int index) {
        return row[index];
    }

    /**
     * 第 index 个按钮离行首多远
     */
    public int offset(int index) {
        return offset[index];
    }

    /**
     * 第 index 个按钮的宽度
     */
    public int width(int index) {
        return width[index];
    }
}
