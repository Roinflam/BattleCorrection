package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/**
 * 一排小色块（调色盘的"最近使用"和"原色 / 新色"对比）：点一个就取它的颜色
 * <p>
 * 格子数固定，颜色每帧从外面读，-1 = 空格子（画成暗槽，点了没反应）。按住在一排上拖动会一路取过去。
 * 有焦点时左右方向键在有颜色的格子间移动并取色。悬停的格子显示说明（按格子缓存折好的行，鼠标不动时每帧不分配）。
 */
public class SwatchStrip extends PickerWidget {

    private final int cells;
    private final int cell;
    private final int gap;
    private final IntUnaryOperator colorAt;
    private final IntConsumer onPick;
    @Nullable
    private final IntFunction<Component> tooltipAt;
    /**
     * 键盘光标停在哪一格（-1 = 还没动过）
     */
    private int cursor = -1;
    private int tipIndex = -1;
    private int tipColor;
    @Nullable
    private List<FormattedCharSequence> tipLines;

    /**
     * @param x         左
     * @param y         上
     * @param cells     格子数
     * @param cell      格子边长
     * @param gap       格子间距
     * @param message   朗读用的名字
     * @param colorAt   第 i 格的颜色（RGB；-1 = 空）
     * @param tooltipAt 第 i 格的悬停说明（可为 null = 不显示）
     * @param onPick    用户选了第 i 格
     */
    public SwatchStrip(int x, int y, int cells, int cell, int gap, @Nonnull Component message,
                       @Nonnull IntUnaryOperator colorAt, @Nullable IntFunction<Component> tooltipAt,
                       @Nonnull IntConsumer onPick) {
        super(x, y, widthOf(cells, cell, gap), cell, message, true);
        this.cells = cells;
        this.cell = cell;
        this.gap = gap;
        this.colorAt = colorAt;
        this.tooltipAt = tooltipAt;
        this.onPick = onPick;
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
     * 坐标落在哪一格（落在格子间的缝里返回 -1）
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 下标或 -1
     */
    public int cellAt(double mouseX, double mouseY) {
        if (mouseY < getY() || mouseY >= getY() + cell || mouseX < getX()) {
            return -1;
        }
        int offset = (int) Math.floor(mouseX - getX());
        int index = offset / (cell + gap);
        if (index >= cells || offset - index * (cell + gap) >= cell) {
            return -1;
        }
        return index;
    }

    /**
     * 第 index 格的左边
     *
     * @param index 下标
     * @return X
     */
    public int cellX(int index) {
        return getX() + index * (cell + gap);
    }

    @Override
    protected void pick(double mouseX, double mouseY, boolean press) {
        // 拖动时只在一排的高度里认格子：横着拖出去不算，免得拖到别处时还在乱取色
        int index = cellAt(mouseX, press ? mouseY : getY());
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
        int direction = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> -1;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> 1;
            default -> 0;
        };
        if (direction == 0) {
            return false;
        }
        int index = cursor < 0 ? (direction > 0 ? -1 : cells) : cursor;
        for (int i = index + direction; i >= 0 && i < cells; i += direction) {
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
        renderFocusRing(g);
        for (int i = 0; i < cells; i++) {
            int x = cellX(i);
            int y = getY();
            int rgb = colorAt.applyAsInt(i);
            boolean lit = i == hovered || (isFocused() && i == cursor);
            if (rgb < 0) {
                EditorTheme.rect(g, x, y, cell, cell, EditorTheme.GROOVE);
                EditorTheme.rect(g, x + 1, y + 1, cell - 2, cell - 2, EditorTheme.WELL);
                continue;
            }
            EditorTheme.chamfer(g, x, y, cell, cell, cell >= 8 ? 2 : 1, lit ? EditorTheme.ACCENT_HI : EditorTheme.BUTTON_BORDER);
            EditorTheme.rect(g, x + 1, y + 1, cell - 2, cell - 2, 0xFF000000 | rgb);
        }
        if (hovered >= 0 && tooltipAt != null && !isDragging()) {
            int rgb = colorAt.applyAsInt(hovered);
            if (hovered != tipIndex || rgb != tipColor || tipLines == null) {
                tipIndex = hovered;
                tipColor = rgb;
                Component tip = tooltipAt.apply(hovered);
                tipLines = tip == null ? null : Minecraft.getInstance().font.split(tip, 200);
            }
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null && tipLines != null) {
                screen.setTooltipForNextRenderPass(tipLines);
            }
        }
    }
}
