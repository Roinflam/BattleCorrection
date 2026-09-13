package pers.roinflam.battlecorrection.client.editor.page;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.NbtType;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.NbtTreeView;
import pers.roinflam.battlecorrection.client.editor.widget.PopupList;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * NBT 树页：整件物品的 NBT 按树状结构增删改
 * <p>
 * 树的根是物品存盘时的完整结构 {@code {id, Count, tag}}。规则：
 * <ul>
 *   <li>id 只读（编辑器不允许换物品），id 和 Count 不能删、不能改名；Count 取 1-64</li>
 *   <li>根下面只能有这三个键，想加东西选中 tag 再加；tag 删掉 = 清空物品 NBT</li>
 *   <li>选中一行后，下面的名称/值框里改，点"应用"或按回车才写入；容器（复合/列表）没有值可改</li>
 *   <li>添加：选中容器后选类型点"添加"；列表的元素类型必须一致，非空列表会自动锁定类型</li>
 *   <li>复制/粘贴：复制的节点记在编辑器内部（连名字一起），同时以 SNBT 写进系统剪贴板；
 *   粘贴优先用内部记的，没有就把系统剪贴板当 SNBT 解析</li>
 * </ul>
 * 每次改动立刻同步到物品副本（Count → 数量，tag → NBT），预览和其他页看到的都是最新的。
 */
public class NbtTreePage extends EditorPageBase {

    private static final String KEY_ID = "id";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_TAG = "tag";
    private static final String KEY_FORGE_CAPS = "ForgeCaps";
    private static final int BOX_HEIGHT = 14;
    private static final int FORM_HEIGHT = 2 * BOX_HEIGHT + 10;
    private static final int GAP = 4;

    /**
     * 编辑器内部的剪贴板：带名字的一份标签副本。静态，切页、重开编辑器都还在
     */
    @Nullable
    private static ClipboardEntry clipboard;

    private record ClipboardEntry(String name, Tag tag) {
    }

    /**
     * 类型弹出列表里的一项
     */
    private static final class TypeRow implements ScrollList.Row {
        final NbtType type;

        TypeRow(@Nonnull NbtType type) {
            this.type = type;
        }

        @Nonnull
        @Override
        public Component label() {
            return type.displayName();
        }

        @Nullable
        @Override
        public Component secondary() {
            return Component.literal(type.badge());
        }
    }

    private final PopupList<TypeRow> typePopup = new PopupList<>();

    private CompoundTag root = new CompoundTag();
    private NbtType addType = NbtType.STRING;
    private Component status = Component.empty();
    private int statusColor = EditorTheme.TEXT_DIM;

    @Nullable
    private NbtTreeView tree;
    @Nullable
    private FlatEditBox nameBox;
    @Nullable
    private FlatEditBox valueBox;
    @Nullable
    private FlatButton applyButton;
    @Nullable
    private FlatButton addButton;
    @Nullable
    private FlatButton typeButton;
    @Nullable
    private FlatButton deleteButton;
    @Nullable
    private FlatButton copyButton;
    @Nullable
    private FlatButton pasteButton;
    private int formTop;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public NbtTreePage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.TREE;
    }

    @Override
    public boolean wantsPreview() {
        return false;
    }

    @Override
    protected void build() {
        loadFromItem();
        status = Component.empty();

        formTop = y + height - FORM_HEIGHT;
        int treeTop = y + 11;
        tree = add(new NbtTreeView(x, treeTop, width, formTop - 4 - treeTop).onSelect(this::onNodeSelected));
        tree.setRoot(root);
        tree.expand("/" + KEY_TAG);
        tree.rebuild();

        // 第一行：名称 / 值 / 应用
        int row1 = formTop + 2;
        int nameLabelWidth = font.width(Component.translatable("gui.battlecorrection.editor.tree.name")) + 6;
        int valueLabelWidth = font.width(Component.translatable("gui.battlecorrection.editor.tree.value")) + 6;
        int applyWidth = 44;
        int nameWidth = Math.min(110, width / 3);
        nameBox = add(new FlatEditBox(font, x + nameLabelWidth + 1, row1, nameWidth, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.name.hint"), false));
        nameBox.setMaxLength(256);
        int valueX = x + nameLabelWidth + nameWidth + GAP + valueLabelWidth + 1;
        int valueWidth = x + width - applyWidth - GAP - valueX - 1;
        valueBox = add(new FlatEditBox(font, valueX, row1, Math.max(40, valueWidth), BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.value.hint"), false));
        valueBox.setMaxLength(FlatEditBox.DEFAULT_MAX_LENGTH * 4);
        applyButton = add(new FlatButton(x + width - applyWidth, row1, applyWidth, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.apply"), this::applyEdit)
                .style(FlatButton.Style.PRIMARY)
                .tooltip(Component.translatable("gui.battlecorrection.editor.tree.apply.tooltip")));

        // 第二行：添加 类型 | 删除 复制 粘贴 | 全部展开 全部折叠
        int row2 = formTop + BOX_HEIGHT + 6;
        int cursorX = x;
        addButton = add(new FlatButton(cursorX, row2, 40, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.add"), this::addChild)
                .tooltip(Component.translatable("gui.battlecorrection.editor.tree.add.tooltip")));
        cursorX += 40 + GAP;
        typeButton = add(new FlatButton(cursorX, row2, 78, BOX_HEIGHT, addType.displayName(), this::openTypePopup)
                .tooltip(Component.translatable("gui.battlecorrection.editor.tree.type.tooltip")));
        cursorX += 78 + GAP * 2;
        deleteButton = add(new FlatButton(cursorX, row2, 36, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.delete"), this::deleteSelected)
                .style(FlatButton.Style.DANGER));
        cursorX += 36 + GAP;
        copyButton = add(new FlatButton(cursorX, row2, 36, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.copy"), this::copySelected)
                .tooltip(Component.translatable("gui.battlecorrection.editor.tree.copy.tooltip")));
        cursorX += 36 + GAP;
        pasteButton = add(new FlatButton(cursorX, row2, 36, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.paste"), this::pasteInto)
                .tooltip(Component.translatable("gui.battlecorrection.editor.tree.paste.tooltip")));
        cursorX += 36 + GAP * 2;
        int expandWidth = Math.max(40, (x + width - cursorX - GAP) / 2);
        add(new FlatButton(cursorX, row2, expandWidth, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.expand_all"), () -> {
            if (tree != null) {
                tree.expandAll();
            }
        }));
        cursorX += expandWidth + GAP;
        add(new FlatButton(cursorX, row2, expandWidth, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tree.collapse_all"), () -> {
            if (tree != null) {
                tree.collapseAll();
            }
        }));

        onNodeSelected(tree.selected());
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        label(g, Component.translatable("gui.battlecorrection.editor.tree.header"), x, y);
        // 状态放在标题行右侧；没有状态时提示一下能力数据的事
        if (status.getString().isEmpty() && session.hasForgeCaps()) {
            Component note = Component.translatable("gui.battlecorrection.editor.tree.caps_note");
            g.drawString(font, note, x + width - font.width(note), y, EditorTheme.TEXT_DIM, false);
        }
        int statusWidth = font.width(status);
        if (statusWidth > 0) {
            int maxWidth = width / 2;
            String plain = status.getString();
            if (statusWidth > maxWidth) {
                plain = font.plainSubstrByWidth(plain, Math.max(0, maxWidth - font.width("…"))) + "…";
                statusWidth = font.width(plain);
            }
            g.drawString(font, plain, x + width - statusWidth, y, statusColor, false);
        }

        EditorTheme.divider(g, x, formTop - 2, width);
        int row1 = formTop + 2 + (BOX_HEIGHT - 8) / 2;
        label(g, Component.translatable("gui.battlecorrection.editor.tree.name"), x, row1);
        if (nameBox != null) {
            label(g, Component.translatable("gui.battlecorrection.editor.tree.value"),
                    nameBox.getX() + nameBox.getWidth() + GAP, row1);
        }
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        typePopup.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return typePopup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return typePopup.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean onEscape() {
        if (typePopup.isOpen()) {
            typePopup.close();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            GuiEventListener focused = screen.getFocused();
            if (focused == nameBox || focused == valueBox) {
                applyEdit();
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // 选中与表单
    // ═══════════════════════════════════════════════════════════════

    /**
     * 选中变化：把节点的名字和值填进表单，按规则启用/禁用按钮
     *
     * @param node 节点，null = 没选中
     */
    private void onNodeSelected(@Nullable NbtTreeView.Node node) {
        if (nameBox == null || valueBox == null || applyButton == null || addButton == null || typeButton == null
                || deleteButton == null || copyButton == null || pasteButton == null) {
            return;
        }
        if (node == null) {
            nameBox.setValue("");
            valueBox.setValue("");
            nameBox.active = false;
            valueBox.active = false;
            applyButton.active = false;
            addButton.active = false;
            typeButton.active = false;
            deleteButton.active = false;
            copyButton.active = false;
            pasteButton.active = false;
            return;
        }

        boolean protectedNode = isProtected(node);
        boolean container = node.isContainer();

        nameBox.setValue(node.isRoot() ? "" : (node.isListChild() ? "[" + node.index + "]" : node.key));
        nameBox.active = !node.isRoot() && !node.isListChild() && !protectedNode;

        String editable = NbtType.toEditable(node.tag);
        if (editable == null) {
            valueBox.setValue(NbtTreeView.summary(node.tag));
            valueBox.active = false;
        } else {
            valueBox.setValue(editable);
            valueBox.active = !isIdNode(node);
        }
        applyButton.active = nameBox.active || valueBox.active;

        // 添加：容器才能加子节点；列表非空时锁定元素类型
        NbtType lockedType = lockedListType(node);
        if (lockedType != null) {
            addType = lockedType;
        }
        typeButton.setMessage(addType.displayName());
        addButton.active = container;
        typeButton.active = container && lockedType == null;

        deleteButton.active = !node.isRoot() && !protectedNode;
        copyButton.active = !node.isRoot();
        pasteButton.active = container || (node.parent != null && node.parent.isContainer());
    }

    /**
     * 根、以及根下面的 id 和 Count：不能删不能改名
     */
    private static boolean isProtected(@Nonnull NbtTreeView.Node node) {
        if (node.isRoot()) {
            return true;
        }
        return node.parent != null && node.parent.isRoot() && (KEY_ID.equals(node.key) || KEY_COUNT.equals(node.key));
    }

    private static boolean isIdNode(@Nonnull NbtTreeView.Node node) {
        return node.parent != null && node.parent.isRoot() && KEY_ID.equals(node.key);
    }

    private static boolean isCountNode(@Nonnull NbtTreeView.Node node) {
        return node.parent != null && node.parent.isRoot() && KEY_COUNT.equals(node.key);
    }

    /**
     * 非空列表的元素类型；不是列表或列表为空返回 null
     */
    @Nullable
    private static NbtType lockedListType(@Nonnull NbtTreeView.Node node) {
        if (node.tag instanceof ListTag list && !list.isEmpty()) {
            return NbtType.byId(list.getElementType());
        }
        return null;
    }

    private void setStatus(@Nonnull String key, int color, @Nonnull Object... args) {
        status = Component.translatable(key, args);
        statusColor = color;
    }

    // ═══════════════════════════════════════════════════════════════
    // 操作
    // ═══════════════════════════════════════════════════════════════

    /**
     * 把表单里的名称/值写进选中节点
     */
    private void applyEdit() {
        if (tree == null || nameBox == null || valueBox == null) {
            return;
        }
        NbtTreeView.Node node = tree.selected();
        if (node == null || node.isRoot() || node.parent == null) {
            return;
        }

        Tag newTag = node.tag;
        if (valueBox.active) {
            NbtType type = NbtType.of(node.tag);
            if (type == null) {
                return;
            }
            try {
                newTag = type.parse(valueBox.getValue());
            } catch (IllegalArgumentException e) {
                setStatus("gui.battlecorrection.editor.tree.status.bad_value", EditorTheme.DANGER, e.getMessage());
                return;
            }
            if (isCountNode(node) && newTag instanceof NumericTag numeric) {
                newTag = NbtType.BYTE.parse(String.valueOf(
                        Mth.clamp(numeric.getAsInt(), ApplyItemEditC2SPacket.MIN_COUNT, ApplyItemEditC2SPacket.MAX_COUNT)));
            }
        }

        String newPath;
        if (node.isListChild()) {
            ListTag list = (ListTag) node.parent.tag;
            if (!list.setTag(node.index, newTag)) {
                NbtType elementType = NbtType.byId(list.getElementType());
                setStatus("gui.battlecorrection.editor.tree.status.list_type", EditorTheme.DANGER,
                        elementType == null ? "?" : elementType.displayName().getString());
                return;
            }
            newPath = node.path;
        } else {
            CompoundTag compound = (CompoundTag) node.parent.tag;
            String newName = nameBox.active ? nameBox.getValue().trim() : node.key;
            if (newName.isEmpty()) {
                setStatus("gui.battlecorrection.editor.tree.status.name_empty", EditorTheme.DANGER);
                return;
            }
            if (!newName.equals(node.key) && compound.contains(newName)) {
                setStatus("gui.battlecorrection.editor.tree.status.name_exists", EditorTheme.DANGER);
                return;
            }
            compound.remove(node.key);
            compound.put(newName, newTag);
            newPath = node.parent.path + "/" + newName;
        }

        pushToSession();
        tree.select(newPath);
        onNodeSelected(tree.selected());
        setStatus("gui.battlecorrection.editor.tree.status.applied", EditorTheme.SUCCESS);
    }

    /**
     * 弹出类型列表选要添加的类型
     */
    private void openTypePopup() {
        if (typeButton == null) {
            return;
        }
        List<TypeRow> rows = new ArrayList<>();
        for (NbtType type : NbtType.values()) {
            rows.add(new TypeRow(type));
        }
        int popupHeight = Math.min(rows.size(), 10) * ScrollList.ROW_HEIGHT + 2;
        typePopup.open(typeButton.getX(), typeButton.getY() - popupHeight - 2, Math.max(typeButton.getWidth(), 120),
                typeButton.getY() - 2, rows, row -> {
            addType = row.type;
            typeButton.setMessage(addType.displayName());
        });
    }

    /**
     * 往选中的容器里加一个默认值的子节点
     */
    private void addChild() {
        if (tree == null) {
            return;
        }
        NbtTreeView.Node node = tree.selected();
        if (node == null || !node.isContainer()) {
            setStatus("gui.battlecorrection.editor.tree.status.select_container", EditorTheme.WARNING);
            return;
        }
        String newPath = insertChild(node, addType.createDefault(), null);
        if (newPath == null) {
            return;
        }
        tree.expand(node.path);
        pushToSession();
        tree.select(newPath);
        onNodeSelected(tree.selected());
        setStatus("gui.battlecorrection.editor.tree.status.added", EditorTheme.SUCCESS);
        if (nameBox != null && nameBox.active) {
            screen.setFocused(nameBox);
        }
    }

    /**
     * 往容器里插一个子节点
     *
     * @param container     容器节点
     * @param tag           要插的标签
     * @param preferredName 复合标签里想用的键名；null = 自动起名
     * @return 新节点的路径；失败返回 null（已设置状态）
     */
    @Nullable
    private String insertChild(@Nonnull NbtTreeView.Node container, @Nonnull Tag tag, @Nullable String preferredName) {
        if (container.tag instanceof ListTag list) {
            if (!list.addTag(list.size(), tag)) {
                NbtType elementType = NbtType.byId(list.getElementType());
                setStatus("gui.battlecorrection.editor.tree.status.list_type", EditorTheme.DANGER,
                        elementType == null ? "?" : elementType.displayName().getString());
                return null;
            }
            return container.path + "/" + (list.size() - 1);
        }

        CompoundTag compound = (CompoundTag) container.tag;
        if (container.isRoot()) {
            // 根下面只允许 tag；id 和 Count 一直都在
            if (compound.contains(KEY_TAG) || !(tag instanceof CompoundTag)) {
                setStatus("gui.battlecorrection.editor.tree.status.root_add", EditorTheme.WARNING);
                return null;
            }
            compound.put(KEY_TAG, tag);
            return "/" + KEY_TAG;
        }
        String base = preferredName == null || preferredName.isEmpty() ? "new" : preferredName;
        String name = base;
        int suffix = 2;
        while (compound.contains(name)) {
            name = base + "_" + suffix++;
        }
        compound.put(name, tag);
        return container.path + "/" + name;
    }

    /**
     * 删除选中节点
     */
    private void deleteSelected() {
        if (tree == null) {
            return;
        }
        NbtTreeView.Node node = tree.selected();
        if (node == null || node.parent == null) {
            return;
        }
        if (isProtected(node)) {
            setStatus("gui.battlecorrection.editor.tree.status.protected", EditorTheme.WARNING);
            return;
        }
        if (node.isListChild()) {
            ((ListTag) node.parent.tag).remove(node.index);
        } else {
            ((CompoundTag) node.parent.tag).remove(node.key);
        }
        pushToSession();
        tree.select(node.parent.path);
        onNodeSelected(tree.selected());
        setStatus("gui.battlecorrection.editor.tree.status.deleted", EditorTheme.SUCCESS);
    }

    /**
     * 复制选中节点：内部记一份，系统剪贴板放 SNBT
     */
    private void copySelected() {
        if (tree == null) {
            return;
        }
        NbtTreeView.Node node = tree.selected();
        if (node == null || node.isRoot()) {
            return;
        }
        clipboard = new ClipboardEntry(node.isListChild() ? "" : node.key, node.tag.copy());
        Minecraft.getInstance().keyboardHandler.setClipboard(node.tag.toString());
        setStatus("gui.battlecorrection.editor.tree.status.copied", EditorTheme.SUCCESS);
    }

    /**
     * 粘贴到选中的容器（选中的不是容器就贴到它父节点里）
     */
    private void pasteInto() {
        if (tree == null) {
            return;
        }
        NbtTreeView.Node node = tree.selected();
        if (node == null) {
            return;
        }
        NbtTreeView.Node container = node.isContainer() ? node : node.parent;
        if (container == null || !container.isContainer()) {
            setStatus("gui.battlecorrection.editor.tree.status.select_container", EditorTheme.WARNING);
            return;
        }

        ClipboardEntry source = clipboard;
        if (source == null) {
            String text = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (text == null || text.trim().isEmpty()) {
                setStatus("gui.battlecorrection.editor.tree.status.clipboard_empty", EditorTheme.WARNING);
                return;
            }
            try {
                source = new ClipboardEntry("", new TagParser(new StringReader(text.trim())).readValue());
            } catch (CommandSyntaxException e) {
                setStatus("gui.battlecorrection.editor.tree.status.clipboard_invalid", EditorTheme.DANGER,
                        e.getMessage() == null ? "?" : e.getMessage());
                return;
            }
        }

        String newPath = insertChild(container, source.tag().copy(), source.name());
        if (newPath == null) {
            return;
        }
        tree.expand(container.path);
        pushToSession();
        tree.select(newPath);
        onNodeSelected(tree.selected());
        setStatus("gui.battlecorrection.editor.tree.status.pasted", EditorTheme.SUCCESS);
    }

    // ═══════════════════════════════════════════════════════════════
    // 与物品副本同步
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从物品副本读完整 NBT
     */
    private void loadFromItem() {
        root = session.working().save(new CompoundTag());
        root.remove(KEY_FORGE_CAPS);
    }

    /**
     * 把树的 Count 和 tag 写回物品副本，并重建树
     */
    private void pushToSession() {
        ItemStack working = session.working();
        if (root.contains(KEY_COUNT, Tag.TAG_ANY_NUMERIC)) {
            working.setCount(Mth.clamp(root.getInt(KEY_COUNT),
                    ApplyItemEditC2SPacket.MIN_COUNT, ApplyItemEditC2SPacket.MAX_COUNT));
        }
        session.setTag(root.contains(KEY_TAG, Tag.TAG_COMPOUND) ? root.getCompound(KEY_TAG).copy() : null);
        if (tree != null) {
            tree.rebuild();
        }
    }
}
