package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可滚动、可单选的列表
 * <p>
 * 整个列表是一个控件，行是自己画的，不给每一行建子控件——附魔和属性列表有几百行，
 * 建几百个控件既慢又难管。行的内容由 {@link Row} 接口提供：左边主文字、右边灰色副文字。
 * <p>
 * 鼠标滚轮滚动、左键选中并回调、右键另有回调（不改选中）；悬停的行由 {@link #hoveredItem()} 暴露出去，
 * 页面在 renderForeground 里据此画提示框（画在所有控件之上）。
 *
 * @param <T> 行类型
 */
public class ScrollList<T extends ScrollList.Row> extends AbstractWidget {

    /**
     * 行高
     */
    public static final int ROW_HEIGHT = 12;

    private static final int SCROLLBAR_WIDTH = 3;
    private static final int PADDING = 4;

    /**
     * 一行要显示的内容
     */
    public interface Row {

        /**
         * 主文字
         *
         * @return 组件
         */
        @Nonnull
        Component label();

        /**
         * 右侧副文字
         *
         * @return 组件，可为 null
         */
        @Nullable
        default Component secondary() {
            return null;
        }

        /**
         * 主文字颜色
         *
         * @return ARGB
         */
        default int color() {
            return EditorTheme.TEXT;
        }
    }

    private final List<T> items = new ArrayList<>();
    private final Component emptyText;
    @Nullable
    private Consumer<T> onSelect;
    @Nullable
    private Consumer<T> onRightClick;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private int scrollRows;

    /**
     * @param x         左
     * @param y         上
     * @param width     宽
     * @param height    高
     * @param emptyText 没有任何行时显示的灰字
     */
    public ScrollList(int x, int y, int width, int height, @Nonnull Component emptyText) {
        super(x, y, width, height, emptyText);
        this.emptyText = emptyText;
    }

    /**
     * 点击选中时的回调（链式）
     *
     * @param onSelect 回调
     * @return this
     */
    @Nonnull
    public ScrollList<T> onSelect(@Nonnull Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /**
     * 右键某一行时的回调（链式），不改变选中
     *
     * @param onRightClick 回调
     * @return this
     */
    @Nonnull
    public ScrollList<T> onRightClick(@Nonnull Consumer<T> onRightClick) {
        this.onRightClick = onRightClick;
        return this;
    }

    /**
     * 某一行当前画在哪个纵坐标（用于在它旁边弹东西）
     *
     * @param item 行
     * @return 行的上边缘 Y；不在可见范围内返回 -1
     */
    public int rowTop(@Nonnull T item) {
        int index = items.indexOf(item);
        if (index < 0 || index < scrollRows || index >= scrollRows + visibleRows()) {
            return -1;
        }
        return getY() + 1 + (index - scrollRows) * ROW_HEIGHT;
    }

    /**
     * 替换全部行；原来选中的对象如果还在就保持选中
     *
     * @param newItems 新的行
     */
    public void setItems(@Nonnull List<T> newItems) {
        T previous = selectedItem();
        items.clear();
        items.addAll(newItems);
        selectedIndex = previous == null ? -1 : items.indexOf(previous);
        clampScroll();
    }

    /**
     * 全部行（只读）
     *
     * @return 行列表
     */
    @Nonnull
    public List<T> items() {
        return Collections.unmodifiableList(items);
    }

    /**
     * 当前选中的行
     *
     * @return 行，没有选中返回 null
     */
    @Nullable
    public T selectedItem() {
        return selectedIndex >= 0 && selectedIndex < items.size() ? items.get(selectedIndex) : null;
    }

    /**
     * 选中某一行（不触发回调），并滚动到可见
     *
     * @param item 行；null = 清除选中
     */
    public void select(@Nullable T item) {
        selectedIndex = item == null ? -1 : items.indexOf(item);
        if (selectedIndex >= 0) {
            scrollTo(selectedIndex);
        }
    }

    /**
     * 鼠标悬停的行
     *
     * @return 行，没有返回 null
     */
    @Nullable
    public T hoveredItem() {
        return hoveredIndex >= 0 && hoveredIndex < items.size() ? items.get(hoveredIndex) : null;
    }

    /**
     * 能同时显示几行
     *
     * @return 行数
     */
    private int visibleRows() {
        return Math.max(1, (getHeight() - 2) / ROW_HEIGHT);
    }

    /**
     * 把滚动位置限制在合法范围
     */
    private void clampScroll() {
        scrollRows = Mth.clamp(scrollRows, 0, Math.max(0, items.size() - visibleRows()));
    }

    /**
     * 滚动到让某一行可见
     *
     * @param index 行下标
     */
    private void scrollTo(int index) {
        int visible = visibleRows();
        if (index < scrollRows) {
            scrollRows = index;
        } else if (index >= scrollRows + visible) {
            scrollRows = index - visible + 1;
        }
        clampScroll();
    }

    /**
     * 鼠标位置对应的行下标
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 下标；不在任何行上返回 -1
     */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() + 1 || mouseY >= getY() + getHeight() - 1) {
            return -1;
        }
        int index = scrollRows + (int) ((mouseY - getY() - 1) / ROW_HEIGHT);
        return index >= 0 && index < items.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        T item = items.get(index);
        if (button == 1) {
            if (onRightClick == null) {
                return false;
            }
            playDownSound(Minecraft.getInstance().getSoundManager());
            onRightClick.accept(item);
            return true;
        }
        if (button != 0) {
            return false;
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        selectedIndex = index;
        if (onSelect != null) {
            onSelect.accept(item);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scrollRows -= (int) Math.signum(delta);
        clampScroll();
        return true;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        Font font = Minecraft.getInstance().font;

        EditorTheme.well(g, x, y, w, h, true);

        hoveredIndex = active ? rowAt(mouseX, mouseY) : -1;

        if (items.isEmpty()) {
            g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
            g.drawWordWrap(font, emptyText, x + PADDING, y + PADDING, w - 2 * PADDING, EditorTheme.TEXT_DIM);
            g.disableScissor();
            return;
        }

        int visible = visibleRows();
        boolean scrollbar = items.size() > visible;
        int innerRight = x + w - 1 - (scrollbar ? SCROLLBAR_WIDTH + 1 : 0);

        g.enableScissor(x + 1, y + 1, innerRight, y + h - 1);
        for (int i = 0; i < visible; i++) {
            int index = scrollRows + i;
            if (index >= items.size()) {
                break;
            }
            T item = items.get(index);
            int rowY = y + 1 + i * ROW_HEIGHT;

            if (index == selectedIndex) {
                g.fill(x + 1, rowY, innerRight, rowY + ROW_HEIGHT, EditorTheme.ACCENT_SOFT);
                g.fill(x + 1, rowY, x + 3, rowY + ROW_HEIGHT, EditorTheme.ACCENT);
                g.fill(x + 3, rowY, innerRight, rowY + 1, EditorTheme.ACCENT_GLOW);
            } else if (index == hoveredIndex) {
                g.fill(x + 1, rowY, innerRight, rowY + ROW_HEIGHT, EditorTheme.ACCENT_GLOW);
                g.fill(x + 1, rowY, x + 2, rowY + ROW_HEIGHT, EditorTheme.ACCENT_MID);
            }

            int textY = rowY + (ROW_HEIGHT - 8) / 2;
            int labelRight = innerRight - PADDING;
            Component secondary = item.secondary();
            if (secondary != null) {
                int secondaryWidth = font.width(secondary);
                int secondaryX = innerRight - PADDING - secondaryWidth;
                g.drawString(font, secondary, secondaryX, textY, EditorTheme.TEXT_MUTED, false);
                labelRight = secondaryX - PADDING;
            }
            // 主文字单独裁一次，太长时不会盖到副文字上
            g.enableScissor(x + 1, rowY, Math.max(x + 1, labelRight), rowY + ROW_HEIGHT);
            g.drawString(font, item.label(), x + PADDING + 1, textY, item.color(), false);
            g.disableScissor();
        }
        g.disableScissor();

        if (scrollbar) {
            int trackX = x + w - 1 - SCROLLBAR_WIDTH;
            int trackHeight = h - 2;
            g.fill(trackX, y + 1, trackX + SCROLLBAR_WIDTH, y + 1 + trackHeight, EditorTheme.ACCENT_FAINT);
            int thumbHeight = Math.max(8, trackHeight * visible / items.size());
            int maxScroll = items.size() - visible;
            int thumbY = y + 1 + (trackHeight - thumbHeight) * scrollRows / maxScroll;
            g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, EditorTheme.ACCENT);
            g.fill(trackX + 1, thumbY + 1, trackX + 2, thumbY + thumbHeight - 1, EditorTheme.ACCENT_HI);
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
