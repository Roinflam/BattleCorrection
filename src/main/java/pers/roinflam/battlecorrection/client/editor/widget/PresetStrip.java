package pers.roinflam.battlecorrection.client.editor.widget;

import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 预设渐变：一格一条小色带，宽度放不下就换行，悬停显示名字，点一下换成这组色标
 * <p>
 * 色带和调色盘的渐变条一样，画的是换成原版颜色后的样子（{@link GradientBar.Bands}，每格一份缓存）。
 * 列数按宽度算（每格至少 {@link #MIN_CELL} 宽，剩下的宽度平分），所以窄窗口里自动排成两行，宽窗口里一行拉开。
 * 有焦点时方向键换预设（上下跨行），换到哪个就立刻用哪个，边按边看下面的预览；回车留给界面应用渐变。
 */
public class PresetStrip extends PickerWidget {

    /**
     * 一格的最小宽度
     */
    public static final int MIN_CELL = 22;
    /**
     * 一格的高度
     */
    public static final int CELL_HEIGHT = 8;
    private static final int GAP = 3;

    private final int[][] presets;
    private final Component[] names;
    private final IntConsumer onPick;
    private final int columns;
    private final int cellWidth;
    private final GradientBar.Bands[] bands;
    private int cursor = -1;
    private int tipIndex = -1;
    @Nullable
    private List<Component> tipLines;

    /**
     * @param x       左
     * @param y       上
     * @param width   可用宽度
     * @param message 控件的名字
     * @param presets 每个预设的色标
     * @param names   每个预设的名字
     * @param onPick  用户选了第 i 个
     */
    public PresetStrip(int x, int y, int width, @Nonnull Component message, @Nonnull int[][] presets,
                       @Nonnull Component[] names, @Nonnull IntConsumer onPick) {
        super(x, y, width, heightFor(width, presets.length), message, true);
        this.presets = presets;
        this.names = names;
        this.onPick = onPick;
        this.columns = columnsFor(width, presets.length);
        this.cellWidth = (width - (columns - 1) * GAP) / columns;
        this.bands = new GradientBar.Bands[presets.length];
        for (int i = 0; i < bands.length; i++) {
            bands[i] = new GradientBar.Bands();
        }
    }

    /**
     * 这么宽放得下几列
     *
     * @param width 宽
     * @param count 预设数
     * @return 列数（至少 1）
     */
    public static int columnsFor(int width, int count) {
        return Math.max(1, Math.min(count, (width + GAP) / (MIN_CELL + GAP)));
    }

    /**
     * 这么宽要占多高
     *
     * @param width 宽
     * @param count 预设数
     * @return 高
     */
    public static int heightFor(int width, int count) {
        int columns = columnsFor(width, count);
        int rows = (count + columns - 1) / columns;
        return rows * CELL_HEIGHT + Math.max(0, rows - 1) * GAP;
    }

    /**
     * 第 index 格的左上角
     *
     * @param index 下标
     * @return {x, y}
     */
    public int[] cellOrigin(int index) {
        return new int[]{getX() + (index % columns) * (cellWidth + GAP), getY() + (index / columns) * (CELL_HEIGHT + GAP)};
    }

    /**
     * 坐标落在哪一格（缝里返回 -1）
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
        int column = dx / (cellWidth + GAP);
        int row = dy / (CELL_HEIGHT + GAP);
        if (column >= columns || dx - column * (cellWidth + GAP) >= cellWidth || dy - row * (CELL_HEIGHT + GAP) >= CELL_HEIGHT) {
            return -1;
        }
        int index = row * columns + column;
        return index < presets.length ? index : -1;
    }

    @Override
    protected void pick(double mouseX, double mouseY, boolean press) {
        int index = cellAt(mouseX, mouseY);
        if (index >= 0 && (press || index != cursor)) {
            cursor = index;
            onPick.accept(index);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) {
            return false;
        }
        int current = cursor < 0 ? 0 : cursor;
        int next;
        switch (keyCode) {
            case Keys.KEY_LEFT:
                next = current - 1;
                break;
            case Keys.KEY_RIGHT:
                next = current + 1;
                break;
            case Keys.KEY_UP:
                next = current - columns;
                break;
            case Keys.KEY_DOWN:
                next = current + columns;
                break;
            default:
                return false;
        }
        if (cursor < 0) {
            // 第一次按方向键：先落在第一个上
            next = 0;
        }
        if (next >= 0 && next < presets.length && next != cursor) {
            cursor = next;
            onPick.accept(next);
        }
        return true;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int hovered = isHovered() ? cellAt(mouseX, mouseY) : -1;
        renderFocusRing(g);
        for (int i = 0; i < presets.length; i++) {
            int x = getX() + (i % columns) * (cellWidth + GAP);
            int y = getY() + (i / columns) * (CELL_HEIGHT + GAP);
            boolean lit = i == hovered || (isFocused() && i == cursor);
            EditorTheme.rect(g, x, y, cellWidth, CELL_HEIGHT, lit ? EditorTheme.ACCENT_HI : EditorTheme.GROOVE);
            bands[i].fill(g, x + 1, y + 1, cellWidth - 2, CELL_HEIGHT - 2, presets[i]);
        }
        if (hovered >= 0 && !isDragging()) {
            if (hovered != tipIndex || tipLines == null) {
                tipIndex = hovered;
                tipLines = Font.get().split(names[hovered], 200);
            }
            showTooltip(tipLines);
        }
    }
}
