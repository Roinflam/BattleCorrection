package pers.roinflam.battlecorrection.client.editor.widget;

import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.editor.ColorMath;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * 一组小色块（调色盘的 16 色色板、"最近使用"和"原色 / 新色"对比）：点一个就取它的颜色
 * <p>
 * 格子数固定，可以排成几行（{@code columns}），颜色每帧从外面读，-1 = 空格子（画成暗槽，点了没反应）。
 * 按住在一行上拖动会一路取过去。有焦点时方向键在有颜色的格子间移动并取色（上下跨行）。
 * 可以标出"当前选中"的那一格（{@link #marked}），描亮边、右下角一个小点。
 * 悬停的格子显示说明（按格子缓存折好的行，鼠标不动时每帧不分配）。
 */
public class SwatchStrip extends PickerWidget {

    private static final int TIP_WIDTH = 200;

    private final int cells;
    private final int columns;
    private final int cell;
    private final int gap;
    private final IntUnaryOperator colorAt;
    private final IntConsumer onPick;
    @Nullable
    private final IntFunction<Component> tooltipAt;
    @Nullable
    private IntSupplier marked;
    /**
     * 键盘光标停在哪一格（-1 = 还没动过）
     */
    private int cursor = -1;
    private int tipIndex = -1;
    private int tipColor;
    @Nullable
    private List<Component> tipLines;

    /**
     * 排成一行
     *
     * @param x         左
     * @param y         上
     * @param cells     格子数
     * @param cell      格子边长
     * @param gap       格子间距
     * @param message   控件的名字
     * @param colorAt   第 i 格的颜色（RGB；-1 = 空）
     * @param tooltipAt 第 i 格的悬停说明（可为 null = 不显示）
     * @param onPick    用户选了第 i 格
     */
    public SwatchStrip(int x, int y, int cells, int cell, int gap, @Nonnull Component message,
                       @Nonnull IntUnaryOperator colorAt, @Nullable IntFunction<Component> tooltipAt,
                       @Nonnull IntConsumer onPick) {
        this(x, y, cells, cells, cell, gap, message, colorAt, tooltipAt, onPick);
    }

    /**
     * 排成几行
     *
     * @param x         左
     * @param y         上
     * @param cells     格子数
     * @param columns   每行几格
     * @param cell      格子边长
     * @param gap       格子间距（横竖一样）
     * @param message   控件的名字
     * @param colorAt   第 i 格的颜色（RGB；-1 = 空）
     * @param tooltipAt 第 i 格的悬停说明（可为 null = 不显示）
     * @param onPick    用户选了第 i 格
     */
    public SwatchStrip(int x, int y, int cells, int columns, int cell, int gap, @Nonnull Component message,
                       @Nonnull IntUnaryOperator colorAt, @Nullable IntFunction<Component> tooltipAt,
                       @Nonnull IntConsumer onPick) {
        super(x, y, widthOf(Math.min(cells, Math.max(1, columns)), cell, gap),
                heightOf(cells, Math.max(1, columns), cell, gap), message, true);
        this.cells = cells;
        this.columns = Math.max(1, columns);
        this.cell = cell;
        this.gap = gap;
        this.colorAt = colorAt;
        this.tooltipAt = tooltipAt;
        this.onPick = onPick;
    }

    /**
     * 标出当前选中的格子（链式）
     *
     * @param marked 选中的下标（-1 = 没有）
     * @return this
     */
    @Nonnull
    public SwatchStrip marked(@Nonnull IntSupplier marked) {
        this.marked = marked;
        return this;
    }

    /**
     * 一排格子的总宽
     *
     * @param cells 格子数
     * @param cell  格子边长
     * @param gap   间距
     * @return 宽
     */
    public static int widthOf(int cells, int cell, int gap) {
        return cells * cell + Math.max(0, cells - 1) * gap;
    }

    /**
     * 排成几行时的总高
     *
     * @param cells   格子数
     * @param columns 每行几格
     * @param cell    格子边长
     * @param gap     间距
     * @return 高
     */
    public static int heightOf(int cells, int columns, int cell, int gap) {
        int rows = Math.max(1, (cells + columns - 1) / columns);
        return rows * cell + (rows - 1) * gap;
    }

    /**
     * 坐标落在哪一格（落在格子间的缝里返回 -1）
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 下标或 -1
     */
    public int cellAt(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseY < getY()) {
            return -1;
        }
        int dx = (int) Math.floor(mouseX - getX());
        int dy = (int) Math.floor(mouseY - getY());
        int column = dx / (cell + gap);
        int row = dy / (cell + gap);
        if (column >= columns || dx - column * (cell + gap) >= cell || dy - row * (cell + gap) >= cell) {
            return -1;
        }
        int index = row * columns + column;
        return index < cells ? index : -1;
    }

    /**
     * 第 index 格的左边
     *
     * @param index 下标
     * @return X
     */
    public int cellX(int index) {
        return getX() + (index % columns) * (cell + gap);
    }

    /**
     * 第 index 格的上边
     *
     * @param index 下标
     * @return Y
     */
    public int cellY(int index) {
        return getY() + (index / columns) * (cell + gap);
    }

    @Override
    protected void pick(double mouseX, double mouseY, boolean press) {
        // 拖动时只在按下的那一行里认格子：横着拖出去不算，免得拖到别处时还在乱取色
        int rowY = press || cursor < 0 ? (int) Math.floor(mouseY) : cellY(cursor);
        int index = cellAt(mouseX, rowY);
        if (index >= 0 && colorAt.applyAsInt(index) >= 0 && (press || index != cursor)) {
            cursor = index;
            onPick.accept(index);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) {
            return false;
        }
        int step;
        switch (keyCode) {
            case Keys.KEY_LEFT:
                step = -1;
                break;
            case Keys.KEY_RIGHT:
                step = 1;
                break;
            case Keys.KEY_UP:
                step = columns < cells ? -columns : -1;
                break;
            case Keys.KEY_DOWN:
                step = columns < cells ? columns : 1;
                break;
            default:
                return false;
        }
        int from = cursor >= 0 ? cursor : marked != null ? marked.getAsInt() : -1;
        if (from < 0) {
            from = step > 0 ? -1 : cells;
            step = step > 0 ? 1 : -1;
        }
        for (int i = from + step; i >= 0 && i < cells; i += step) {
            if (colorAt.applyAsInt(i) >= 0) {
                cursor = i;
                onPick.accept(i);
                break;
            }
        }
        return true;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int hovered = isHovered() ? cellAt(mouseX, mouseY) : -1;
        int selected = marked != null ? marked.getAsInt() : -1;
        renderFocusRing(g);
        for (int i = 0; i < cells; i++) {
            int x = cellX(i);
            int y = cellY(i);
            int rgb = colorAt.applyAsInt(i);
            boolean lit = i == hovered || i == selected || (isFocused() && i == cursor);
            if (rgb < 0) {
                EditorTheme.rect(g, x, y, cell, cell, EditorTheme.GROOVE);
                EditorTheme.rect(g, x + 1, y + 1, cell - 2, cell - 2, EditorTheme.WELL);
                continue;
            }
            EditorTheme.chamfer(g, x, y, cell, cell, cell >= 8 ? 2 : 1, lit ? EditorTheme.ACCENT_HI : EditorTheme.BUTTON_BORDER);
            EditorTheme.rect(g, x + 1, y + 1, cell - 2, cell - 2, 0xFF000000 | rgb);
            if (i == selected && cell >= 8) {
                // 选中标记：右下角一个和底色对比明显的小方点
                EditorTheme.rect(g, x + cell - 4, y + cell - 4, 2, 2, ColorMath.contrast(rgb));
            }
        }
        if (hovered >= 0 && tooltipAt != null && !isDragging()) {
            int rgb = colorAt.applyAsInt(hovered);
            if (hovered != tipIndex || rgb != tipColor || tipLines == null) {
                tipIndex = hovered;
                tipColor = rgb;
                Component tip = tooltipAt.apply(hovered);
                tipLines = tip == null ? null : Font.get().split(tip, TIP_WIDTH);
            }
            if (tipLines != null) {
                showTooltip(tipLines);
            }
        }
    }
}
