package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.NbtType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * NBT 树视图
 * <p>
 * 只负责"看"和"选"：把一份 CompoundTag 展开成节点树，按展开状态拍平成行来画，
 * 处理滚动、点击选中、展开折叠。改数据是页面的事，页面改完调 {@link #rebuild()} 重新拍平。
 * <p>
 * 节点用"路径"标识（{@code /tag/display/Lore/0} 这种），展开状态和选中状态都按路径记，
 * 所以重建之后展开的还是展开的、选中的还是选中的。复合标签的子节点按键名排序显示，
 * 原版 CompoundTag 内部是 HashMap，本来就没有稳定顺序。
 * <p>
 * 点击行首的 +/- 或双击一行 = 展开/折叠；单击 = 选中。
 */
public class NbtTreeView extends AbstractWidget {

    /**
     * 行高
     */
    public static final int ROW_HEIGHT = 12;

    private static final int INDENT = 10;
    private static final int ARROW_WIDTH = 9;
    private static final int BADGE_WIDTH = 22;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int PADDING = 3;
    private static final int MAX_STRING_PREVIEW = 48;
    private static final long DOUBLE_CLICK_MS = 300L;

    /**
     * 树上的一个节点
     */
    public static final class Node {
        @Nullable
        public final Node parent;
        /**
         * 在父复合标签里的键；列表子节点和根为空串
         */
        public final String key;
        /**
         * 在父列表里的下标；非列表子节点为 -1
         */
        public final int index;
        public final Tag tag;
        public final int depth;
        public final String path;
        public final List<Node> children = new ArrayList<>();

        Node(@Nullable Node parent, @Nonnull String key, int index, @Nonnull Tag tag, int depth, @Nonnull String path) {
            this.parent = parent;
            this.key = key;
            this.index = index;
            this.tag = tag;
            this.depth = depth;
            this.path = path;
        }

        public boolean isRoot() {
            return parent == null;
        }

        public boolean isContainer() {
            return tag instanceof CompoundTag || tag instanceof ListTag;
        }

        public boolean isListChild() {
            return index >= 0;
        }

        /**
         * 行上显示的名字
         *
         * @return 组件
         */
        @Nonnull
        public Component displayName() {
            if (isRoot()) {
                return Component.translatable("gui.battlecorrection.editor.tree.root");
            }
            if (isListChild()) {
                return Component.literal("[" + index + "]");
            }
            return Component.literal(key);
        }
    }

    @Nullable
    private CompoundTag root;
    @Nullable
    private Node rootNode;
    private final Set<String> expanded = new HashSet<>();
    private final List<Node> rows = new ArrayList<>();
    @Nullable
    private String selectedPath;
    @Nullable
    private Consumer<Node> onSelect;
    private int scrollRows;
    private int hoveredIndex = -1;
    private long lastClickTime;
    private int lastClickIndex = -1;

    /**
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     */
    public NbtTreeView(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("gui.battlecorrection.editor.tab.tree"));
        expanded.add("");
    }

    /**
     * 选中变化时的回调（链式）
     *
     * @param onSelect 回调，参数可能为 null（清除选中）
     * @return this
     */
    @Nonnull
    public NbtTreeView onSelect(@Nonnull Consumer<Node> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /**
     * 设置要显示的根标签并重建
     *
     * @param root 根
     */
    public void setRoot(@Nonnull CompoundTag root) {
        this.root = root;
        rebuild();
    }

    /**
     * 数据改了之后重新拍平（展开和选中状态按路径保留）
     */
    public void rebuild() {
        rows.clear();
        rootNode = null;
        if (root == null) {
            return;
        }
        rootNode = build(null, "", -1, root, 0, "");
        flatten(rootNode);
        clampScroll();
    }

    /**
     * 递归建节点
     */
    @Nonnull
    private Node build(@Nullable Node parent, @Nonnull String key, int index, @Nonnull Tag tag, int depth,
                       @Nonnull String path) {
        Node node = new Node(parent, key, index, tag, depth, path);
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            for (String childKey : keys) {
                Tag child = compound.get(childKey);
                if (child != null) {
                    node.children.add(build(node, childKey, -1, child, depth + 1, path + "/" + childKey));
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                node.children.add(build(node, "", i, list.get(i), depth + 1, path + "/" + i));
            }
        }
        return node;
    }

    /**
     * 按展开状态拍平成行
     */
    private void flatten(@Nonnull Node node) {
        rows.add(node);
        if (node.isContainer() && expanded.contains(node.path)) {
            for (Node child : node.children) {
                flatten(child);
            }
        }
    }

    /**
     * 当前选中的节点（就算它的父节点折叠了也能拿到）
     *
     * @return 节点，没有返回 null
     */
    @Nullable
    public Node selected() {
        return selectedPath == null || rootNode == null ? null : find(rootNode, selectedPath);
    }

    @Nullable
    private static Node find(@Nonnull Node node, @Nonnull String path) {
        if (node.path.equals(path)) {
            return node;
        }
        if (!path.startsWith(node.path + "/") && !node.isRoot()) {
            return null;
        }
        for (Node child : node.children) {
            Node found = find(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 按路径选中（不触发回调），并把它的祖先全部展开、滚动到可见
     *
     * @param path 路径；null = 清除选中
     */
    public void select(@Nullable String path) {
        selectedPath = path;
        if (path == null) {
            return;
        }
        // 展开所有祖先
        String current = path;
        while (current.contains("/")) {
            current = current.substring(0, current.lastIndexOf('/'));
            expanded.add(current);
        }
        rebuild();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).path.equals(path)) {
                scrollTo(i);
                return;
            }
        }
    }

    /**
     * 展开或折叠一个容器节点
     *
     * @param node 节点
     */
    public void toggle(@Nonnull Node node) {
        if (!node.isContainer()) {
            return;
        }
        if (!expanded.remove(node.path)) {
            expanded.add(node.path);
        }
        rebuild();
    }

    /**
     * 确保某个路径是展开的（新建子节点后用）
     *
     * @param path 路径
     */
    public void expand(@Nonnull String path) {
        expanded.add(path);
    }

    /**
     * 全部展开
     */
    public void expandAll() {
        if (rootNode == null) {
            return;
        }
        collectContainers(rootNode, expanded);
        rebuild();
    }

    /**
     * 全部折叠（根保持展开，不然什么都看不见）
     */
    public void collapseAll() {
        expanded.clear();
        expanded.add("");
        rebuild();
    }

    private static void collectContainers(@Nonnull Node node, @Nonnull Set<String> into) {
        if (node.isContainer()) {
            into.add(node.path);
            for (Node child : node.children) {
                collectContainers(child, into);
            }
        }
    }

    private int visibleRows() {
        return Math.max(1, (getHeight() - 2) / ROW_HEIGHT);
    }

    private void clampScroll() {
        scrollRows = Mth.clamp(scrollRows, 0, Math.max(0, rows.size() - visibleRows()));
    }

    private void scrollTo(int index) {
        int visible = visibleRows();
        if (index < scrollRows) {
            scrollRows = index;
        } else if (index >= scrollRows + visible) {
            scrollRows = index - visible + 1;
        }
        clampScroll();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() + 1 || mouseY >= getY() + getHeight() - 1) {
            return -1;
        }
        int index = scrollRows + (int) ((mouseY - getY() - 1) / ROW_HEIGHT);
        return index >= 0 && index < rows.size() ? index : -1;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        Node node = rows.get(index);
        long now = Util.getMillis();
        boolean doubleClick = index == lastClickIndex && now - lastClickTime < DOUBLE_CLICK_MS;
        lastClickTime = now;
        lastClickIndex = index;

        int arrowX = getX() + 1 + PADDING + node.depth * INDENT;
        boolean onArrow = mouseX >= arrowX && mouseX < arrowX + ARROW_WIDTH;

        selectedPath = node.path;
        if (onSelect != null) {
            onSelect.accept(node);
        }
        if (node.isContainer() && (onArrow || doubleClick)) {
            toggle(node);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scrollRows -= (int) Math.signum(delta) * 2;
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
        int visible = visibleRows();
        boolean scrollbar = rows.size() > visible;
        int innerRight = x + w - 1 - (scrollbar ? SCROLLBAR_WIDTH + 1 : 0);

        g.enableScissor(x + 1, y + 1, innerRight, y + h - 1);
        for (int i = 0; i < visible; i++) {
            int index = scrollRows + i;
            if (index >= rows.size()) {
                break;
            }
            Node node = rows.get(index);
            int rowY = y + 1 + i * ROW_HEIGHT;
            boolean selected = node.path.equals(selectedPath);

            if (selected) {
                g.fill(x + 1, rowY, innerRight, rowY + ROW_HEIGHT, EditorTheme.ACCENT_SOFT);
                g.fill(x + 1, rowY, x + 3, rowY + ROW_HEIGHT, EditorTheme.ACCENT);
            } else if (index == hoveredIndex) {
                g.fill(x + 1, rowY, innerRight, rowY + ROW_HEIGHT, EditorTheme.ACCENT_GLOW);
            }

            int textY = rowY + (ROW_HEIGHT - 8) / 2;
            int cursorX = x + 1 + PADDING + node.depth * INDENT;

            // 展开/折叠标记
            if (node.isContainer()) {
                boolean open = expanded.contains(node.path);
                EditorTheme.chamfer(g, cursorX, rowY + 2, 8, ROW_HEIGHT - 4, 1, open ? EditorTheme.ACCENT_MID : EditorTheme.BUTTON_BORDER);
                g.drawString(font, open ? "-" : "+", cursorX + 2, textY, open ? EditorTheme.ACCENT_HI : EditorTheme.TEXT, false);
            }
            cursorX += ARROW_WIDTH + 2;

            // 类型标记
            NbtType type = NbtType.of(node.tag);
            String badge = type == null ? "?" : type.badge();
            g.drawString(font, badge, cursorX, textY, badgeColor(type), false);
            cursorX += BADGE_WIDTH;

            // 名字 + 值
            Component name = node.displayName();
            g.drawString(font, name, cursorX, textY, EditorTheme.TEXT, false);
            cursorX += font.width(name);
            String summary = summary(node.tag);
            g.drawString(font, ": " + summary, cursorX, textY, EditorTheme.TEXT_MUTED, false);
        }
        g.disableScissor();

        if (scrollbar) {
            int trackX = x + w - 1 - SCROLLBAR_WIDTH;
            int trackHeight = h - 2;
            g.fill(trackX, y + 1, trackX + SCROLLBAR_WIDTH, y + 1 + trackHeight, EditorTheme.ACCENT_FAINT);
            int thumbHeight = Math.max(8, trackHeight * visible / rows.size());
            int maxScroll = rows.size() - visible;
            int thumbY = y + 1 + (trackHeight - thumbHeight) * scrollRows / maxScroll;
            g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, EditorTheme.ACCENT);
            g.fill(trackX + 1, thumbY + 1, trackX + 2, thumbY + thumbHeight - 1, EditorTheme.ACCENT_HI);
        }
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

    /**
     * 行尾的值摘要：容器和数组显示个数，字符串截断，数字带原版后缀（5b、1.5f）
     *
     * @param tag 标签
     * @return 文本
     */
    @Nonnull
    public static String summary(@Nonnull Tag tag) {
        if (tag instanceof CompoundTag compound) {
            return "{" + compound.size() + "}";
        }
        if (tag instanceof ListTag list) {
            return "[" + list.size() + "]";
        }
        if (tag instanceof ByteArrayTag array) {
            return "[B; " + array.size() + "]";
        }
        if (tag instanceof IntArrayTag array) {
            return "[I; " + array.size() + "]";
        }
        if (tag instanceof LongArrayTag array) {
            return "[L; " + array.size() + "]";
        }
        if (tag instanceof StringTag) {
            String value = tag.getAsString();
            if (value.length() > MAX_STRING_PREVIEW) {
                value = value.substring(0, MAX_STRING_PREVIEW) + "…";
            }
            return "\"" + value.replace("\n", "\\n") + "\"";
        }
        return tag.toString();
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
