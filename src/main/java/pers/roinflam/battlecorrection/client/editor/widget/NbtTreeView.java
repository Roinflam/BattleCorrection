package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.NbtType;
import pers.roinflam.battlecorrection.client.editor.tree.NbtTreeModel;
import pers.roinflam.battlecorrection.client.editor.tree.TreeNode;
import pers.roinflam.battlecorrection.client.editor.ui.AbstractWidget;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;
import pers.roinflam.battlecorrection.client.editor.ui.Ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * NBT 树视图：只负责"看"和"选"
 * <p>
 * 数据、展开、分页、选中都在 {@link NbtTreeModel} 里，这里按模型拍好的行来画，处理滚动、点击、键盘。
 * 改数据是页面的事。
 * <p>
 * 每帧只画能看见的那十几行；行上的文字（名字、截断后的值）第一次画时算好存在节点上，
 * 之后每帧直接拿来画，不再每帧逐行量宽度、截断。
 * <p>
 * 鼠标：点 +/- 或双击 = 展开 / 折叠；单击 = 选中；右键 = 选中并弹菜单；点分页行 = 再显示一页；
 * 点列表空白处 = 取消选中；滚动条可以拖。
 * 键盘（树有焦点时）：↑↓ 移动，PageUp / PageDown 翻页，Home / End 到头尾，← 折叠或回到父节点，
 * → 展开或进入第一个子节点，回车 / 空格展开折叠容器。其余按键先交给页面的钩子（删除、复制粘贴之类）。
 */
public class NbtTreeView extends AbstractWidget {

    /**
     * 行高
     */
    public static final int ROW_HEIGHT = 12;

    private static final int INDENT = 10;
    private static final int ARROW_WIDTH = 9;
    private static final int BADGE_WIDTH = 22;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int PADDING = 3;
    private static final long DOUBLE_CLICK_MS = 300L;
    private static final int WHEEL_ROWS = 3;
    private static final String ELLIPSIS = "…";

    private static final int HIT_BG = EditorTheme.alpha(EditorTheme.WARNING, 0.16F);
    private static final int HIT_EDGE = EditorTheme.alpha(EditorTheme.WARNING, 0.75F);

    /**
     * 右键菜单回调
     */
    @FunctionalInterface
    public interface ContextMenuHandler {

        /**
         * @param node   右键的节点（已选中）
         * @param mouseX 鼠标 X
         * @param mouseY 鼠标 Y
         */
        void open(@Nonnull TreeNode node, int mouseX, int mouseY);
    }

    private final NbtTreeModel model;
    @Nullable
    private Consumer<TreeNode> onSelect;
    @Nullable
    private ContextMenuHandler onContextMenu;
    @Nullable
    private TreeEditBox.KeyHook keyHook;
    private int hoveredIndex = -1;
    private long lastClickTime;
    private int lastClickIndex = -1;
    private boolean draggingScrollbar;

    /**
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param model  数据
     */
    public NbtTreeView(int x, int y, int width, int height, @Nonnull NbtTreeModel model) {
        super(x, y, width, height, Component.translatable("gui.battlecorrection.editor.tab.tree"));
        this.model = model;
    }

    /**
     * 选中变化时的回调（链式）
     *
     * @param onSelect 回调，参数可能为 null（清除选中）
     * @return this
     */
    @Nonnull
    public NbtTreeView onSelect(@Nonnull Consumer<TreeNode> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /**
     * 右键菜单（链式）
     *
     * @param handler 回调
     * @return this
     */
    @Nonnull
    public NbtTreeView onContextMenu(@Nonnull ContextMenuHandler handler) {
        this.onContextMenu = handler;
        return this;
    }

    /**
     * 有焦点时，导航键之前先交给钩子（链式）
     *
     * @param hook 钩子
     * @return this
     */
    @Nonnull
    public NbtTreeView onKey(@Nonnull TreeEditBox.KeyHook hook) {
        this.keyHook = hook;
        return this;
    }

    /**
     * 鼠标正悬停的节点（页面据此画提示框）
     *
     * @return 节点；没有或是分页行返回 null
     */
    @Nullable
    public TreeNode hoveredNode() {
        List<TreeNode> rows = model.rows();
        if (hoveredIndex < 0 || hoveredIndex >= rows.size()) {
            return null;
        }
        TreeNode node = rows.get(hoveredIndex);
        return node.isMore() ? null : node;
    }

    // 滚动

    private int visibleRows() {
        return Math.max(1, (getHeight() - 2) / ROW_HEIGHT);
    }

    private void clampScroll() {
        model.scroll = Mth.clamp(model.scroll, 0, Math.max(0, model.rows().size() - visibleRows()));
    }

    /**
     * 滚动到让某一行可见
     *
     * @param index  行号
     * @param center true = 不在视野里时把它放到上三分之一处（跳转、搜索用）；false = 最少滚动（键盘移动用）
     */
    public void scrollToRow(int index, boolean center) {
        if (index < 0) {
            return;
        }
        int visible = visibleRows();
        if (index < model.scroll || index >= model.scroll + visible) {
            model.scroll = center ? index - visible / 3 : (index < model.scroll ? index : index - visible + 1);
        }
        clampScroll();
    }

    /**
     * 让选中的节点可见（最少滚动）
     */
    public void scrollToSelection() {
        scrollToRow(model.rowOf(model.selectedForRender()), false);
    }

    private boolean hasScrollbar() {
        return model.rows().size() > visibleRows();
    }

    private int trackX() {
        return getX() + getWidth() - 1 - SCROLLBAR_WIDTH;
    }

    private void dragScrollTo(double mouseY) {
        int rows = model.rows().size();
        int visible = visibleRows();
        int trackHeight = getHeight() - 2;
        int thumbHeight = Math.max(8, trackHeight * visible / Math.max(1, rows));
        double k = (mouseY - getY() - 1 - thumbHeight / 2.0) / Math.max(1, trackHeight - thumbHeight);
        model.scroll = (int) Math.round(Mth.clamp(k, 0.0, 1.0) * (rows - visible));
        clampScroll();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() + 1 || mouseY >= getY() + getHeight() - 1) {
            return -1;
        }
        int index = model.scroll + (int) ((mouseY - getY() - 1) / ROW_HEIGHT);
        return index >= 0 && index < model.rows().size() ? index : -1;
    }

    // 选中

    private void selectRow(int index) {
        List<TreeNode> rows = model.rows();
        if (index < 0 || index >= rows.size()) {
            return;
        }
        TreeNode node = rows.get(index);
        if (node.isMore()) {
            return;
        }
        model.select(node.path);
        scrollToRow(index, false);
        if (onSelect != null) {
            onSelect.accept(node);
        }
    }

    private void clearSelection() {
        model.select(null);
        if (onSelect != null) {
            onSelect.accept(null);
        }
    }

    /**
     * 键盘移动选中：落到分页行上时先多显示一页，再选中顶上来的那个元素
     */
    private void moveSelection(int delta) {
        List<TreeNode> rows = model.rows();
        if (rows.isEmpty()) {
            return;
        }
        int current = model.rowOf(model.selectedForRender());
        int target;
        if (current < 0) {
            target = delta > 0 ? 0 : rows.size() - 1;
        } else {
            target = Mth.clamp(current + delta, 0, rows.size() - 1);
        }
        if (rows.get(target).isMore()) {
            if (delta > 0) {
                model.showMore(rows.get(target));
                rows = model.rows();
            } else {
                target = Math.max(0, target - 1);
            }
        }
        if (target < rows.size() && !rows.get(target).isMore()) {
            selectRow(target);
        }
    }

    // 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        // 松开鼠标时指针不在树上的话收不到 mouseReleased，拖动标记在下一次按下时清掉
        draggingScrollbar = false;
        if (button == 0 && hasScrollbar() && mouseX >= trackX() - 1) {
            draggingScrollbar = true;
            dragScrollTo(mouseY);
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (button == 1) {
            if (index >= 0 && !model.rows().get(index).isMore()) {
                selectRow(index);
                if (onContextMenu != null) {
                    onContextMenu.open(model.rows().get(index), (int) mouseX, (int) mouseY);
                }
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        playDownSound(Minecraft.getMinecraft().getSoundHandler());
        if (index < 0) {
            // 点在空白处：清掉选中
            clearSelection();
            return true;
        }
        TreeNode node = model.rows().get(index);
        if (node.isMore()) {
            model.showMore(node);
            return true;
        }
        long now = Ui.millis();
        boolean doubleClick = index == lastClickIndex && now - lastClickTime < DOUBLE_CLICK_MS;
        lastClickTime = now;
        lastClickIndex = index;

        int arrowX = getX() + 1 + PADDING + node.depth * INDENT;
        boolean onArrow = mouseX >= arrowX && mouseX < arrowX + ARROW_WIDTH;

        selectRow(index);
        if (node.isContainer() && (onArrow || doubleClick)) {
            model.toggle(node);
            // 双击之后的第三下不再算双击
            lastClickIndex = -1;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollTo(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            draggingScrollbar = false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int step = Screen.hasShiftDown() ? visibleRows() : WHEEL_ROWS;
        model.scroll -= (int) Math.signum(delta) * step;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) {
            return false;
        }
        if (keyHook != null && keyHook.handle(keyCode, modifiers)) {
            return true;
        }
        // 带 Ctrl / Alt 的方向键留给页面和界面（Alt+↑↓ 移动元素由钩子处理，走到这里说明钩子没要）
        if (Screen.hasControlDown() || Screen.hasAltDown()) {
            return false;
        }
        TreeNode selected = model.selectedForRender();
        switch (keyCode) {
            case Keys.KEY_UP:
                moveSelection(-1);
                return true;
            case Keys.KEY_DOWN:
                moveSelection(1);
                return true;
            case Keys.KEY_PAGE_UP:
                moveSelection(-visibleRows());
                return true;
            case Keys.KEY_PAGE_DOWN:
                moveSelection(visibleRows());
                return true;
            case Keys.KEY_HOME:
                selectRow(0);
                return true;
            case Keys.KEY_END: {
                List<TreeNode> rows = model.rows();
                int last = rows.size() - 1;
                if (last >= 0 && rows.get(last).isMore()) {
                    last--;
                }
                selectRow(last);
                return true;
            }
            case Keys.KEY_LEFT:
                if (selected == null) {
                    return true;
                }
                if (selected.isContainer() && selected.isExpanded() && !selected.isRoot()) {
                    model.toggle(selected);
                    scrollToSelection();
                } else if (selected.parent != null) {
                    selectRow(model.rowOf(selected.parent));
                }
                return true;
            case Keys.KEY_RIGHT:
                if (selected == null || !selected.isContainer()) {
                    return true;
                }
                if (!selected.isExpanded()) {
                    model.toggle(selected);
                } else if (selected.childCount() > 0) {
                    selectRow(model.rowOf(selected) + 1);
                }
                return true;
            case Keys.KEY_ENTER:
            case Keys.KEY_KP_ENTER:
            case Keys.KEY_SPACE:
                if (selected != null && selected.isContainer()) {
                    model.toggle(selected);
                    scrollToSelection();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    // 绘制

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        Font font = Font.get();
        List<TreeNode> rows = model.rows();

        EditorTheme.well(g, x, y, w, h, true);
        clampScroll();

        hoveredIndex = active ? rowAt(mouseX, mouseY) : -1;
        int visible = visibleRows();
        boolean scrollbar = rows.size() > visible;
        int innerRight = x + w - 1 - (scrollbar ? SCROLLBAR_WIDTH + 1 : 0);
        TreeNode selected = model.selectedForRender();
        int scroll = model.scroll;

        g.enableScissor(x + 1, y + 1, innerRight, y + h - 1);
        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            if (index >= rows.size()) {
                break;
            }
            TreeNode node = rows.get(index);
            int rowY = y + 1 + i * ROW_HEIGHT;
            int textY = rowY + (ROW_HEIGHT - 8) / 2;
            int cursorX = x + 1 + PADDING + node.depth * INDENT;

            if (node == selected) {
                g.fill(x + 1, rowY, innerRight, rowY + ROW_HEIGHT, EditorTheme.ACCENT_SOFT);
                g.fill(x + 1, rowY, x + 3, rowY + ROW_HEIGHT, node.searchMark() == 1 ? EditorTheme.WARNING : EditorTheme.ACCENT);
            } else if (node.searchMark() == 1) {
                g.fill(x + 1, rowY, innerRight, rowY + ROW_HEIGHT, HIT_BG);
                g.fill(x + 1, rowY, x + 2, rowY + ROW_HEIGHT, HIT_EDGE);
            }
            if (index == hoveredIndex && node != selected) {
                g.fill(x + 1, rowY, innerRight, rowY + ROW_HEIGHT, EditorTheme.ACCENT_GLOW);
            }

            if (node.isMore()) {
                // 分页行：缩进到子节点那一层，用强调色的小字
                int color = index == hoveredIndex ? EditorTheme.ACCENT_HI : EditorTheme.ACCENT_HOVER;
                g.drawString(font, node.nameText(), cursorX, textY, color, false);
                continue;
            }

            // 展开/折叠标记；折叠着但里面有搜索命中的，旁边点一个小黄点
            if (node.isContainer()) {
                boolean open = node.isExpanded();
                EditorTheme.chamfer(g, cursorX, rowY + 2, 8, ROW_HEIGHT - 4, 1, open ? EditorTheme.ACCENT_MID : EditorTheme.BUTTON_BORDER);
                g.drawString(font, open ? "-" : "+", cursorX + 2, textY, open ? EditorTheme.ACCENT_HI : EditorTheme.TEXT, false);
                if (node.searchMark() == 2) {
                    g.fill(cursorX + 8, rowY + 1, cursorX + 10, rowY + 3, EditorTheme.WARNING);
                }
            }
            cursorX += ARROW_WIDTH + 2;

            // 类型标记
            NbtType type = node.type();
            g.drawString(font, type == null ? "?" : type.badge(), cursorX, textY, badgeColor(type), false);
            cursorX += BADGE_WIDTH;

            // 名字 + 值（截断结果缓存在节点上，宽度不变就不重算）
            int available = innerRight - PADDING - cursorX;
            int nameLimit = Math.max(24, available * 2 / 3);
            if (node.renderName == null || node.renderNameLimit != nameLimit) {
                String full = node.nameText();
                String shown = clip(font, full, nameLimit);
                node.renderName = shown;
                node.renderNameLimit = nameLimit;
                node.renderNameWidth = font.width(shown);
                // 名字宽度变了，值的可用宽度跟着变
                node.renderValueLimit = -1;
            }
            g.drawString(font, node.renderName, cursorX, textY, EditorTheme.TEXT, false);
            cursorX += node.renderNameWidth;

            int valueLimit = innerRight - PADDING - cursorX;
            if (node.renderValue == null || node.renderValueLimit != valueLimit) {
                String full = node.valueText();
                String shown = clip(font, full, valueLimit);
                node.renderValue = shown;
                node.renderValueLimit = valueLimit;
                node.renderValueClipped = shown != full;
            }
            g.drawString(font, node.renderValue, cursorX, textY, EditorTheme.TEXT_MUTED, false);
        }
        g.disableScissor();

        if (scrollbar) {
            int trackX = trackX();
            int trackHeight = h - 2;
            g.fill(trackX, y + 1, trackX + SCROLLBAR_WIDTH, y + 1 + trackHeight, EditorTheme.ACCENT_FAINT);
            int thumbHeight = Math.max(8, trackHeight * visible / rows.size());
            int maxScroll = rows.size() - visible;
            int thumbY = y + 1 + (trackHeight - thumbHeight) * scroll / maxScroll;
            g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight,
                    draggingScrollbar ? EditorTheme.ACCENT_HOVER : EditorTheme.ACCENT);
            g.fill(trackX + 1, thumbY + 1, trackX + 2, thumbY + thumbHeight - 1, EditorTheme.ACCENT_HI);
        }
        if (isFocused()) {
            EditorTheme.wellOutline(g, x, y, w, h, EditorTheme.ACCENT_MID);
        }
    }

    /**
     * 按宽度截断，超出时末尾加省略号；没超出原样返回同一个对象（调用方靠 != 判断截没截）
     * <p>
     * 名字和值里的 § 码画的时候按格式解析（和截断前一样），量宽度、截断也按解析后的样子算：
     * 截出来的那段按样式重新写成 § 码，省略号接在后面。
     */
    @Nonnull
    private static String clip(@Nonnull Font font, @Nonnull String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int room = Math.max(0, maxWidth - font.width(ELLIPSIS));
        return font.substrByWidth(Component.literal(text), room).toLegacy() + ELLIPSIS;
    }

    /**
     * 类型标记的颜色
     */
    private static int badgeColor(@Nullable NbtType type) {
        if (type == null) {
            return EditorTheme.DANGER;
        }
        if (type.isContainer()) {
            return EditorTheme.ACCENT_HOVER;
        }
        if (type == NbtType.STRING) {
            return EditorTheme.SUCCESS;
        }
        if (type.isArray()) {
            return EditorTheme.TEXT_MUTED;
        }
        return EditorTheme.WARNING;
    }
}
