package pers.roinflam.battlecorrection.client.editor.page;

import com.google.gson.JsonParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.widget.ColorToolbar;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.FlatToggle;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lore 页：一行一个输入框，加/删/上移/下移，颜色工具条作用于当前有焦点的那一行
 * <p>
 * 数据模型是 {@link #lines}（& 码形式的字符串列表），每次改动立刻写回物品副本的 display.Lore。
 * 界面上只创建"看得见的那几行"的控件（行数 = 列表区高度 / 行高），滚动时把控件重新绑定到不同的行，
 * 而不是给每一行都建控件——Lore 几十行时控件太多会卡。
 * <p>
 * 存盘格式：默认每行写成 {@code {"text":"...","color":"white","italic":false}}，
 * 这样没写颜色码的文字显示白色、不斜体，颜色码照常生效。勾"原版样式"则只写 {@code {"text":"..."}}，
 * 由原版渲染成紫色斜体。读取时如果第一行的样式是空的，就当成原版样式。
 * <p>
 * 输入框里按回车 = 在这一行下面插一行。
 */
public class LorePage extends EditorPageBase {

    private static final int ROW_HEIGHT = 20;
    private static final int BOX_HEIGHT = 16;
    private static final int BUTTON_WIDTH = 14;
    private static final int GAP = 3;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int MAX_LINE_LENGTH = 1024;

    /**
     * 一行控件
     */
    private static final class Row {
        FlatEditBox box;
        FlatButton up;
        FlatButton down;
        FlatButton delete;
        /**
         * 当前绑定的行下标，-1 = 未绑定
         */
        int index = -1;

        void setVisible(boolean visible) {
            box.visible = visible;
            box.active = visible;
            up.visible = visible;
            down.visible = visible;
            delete.visible = visible;
            delete.active = visible;
            if (!visible) {
                up.active = false;
                down.active = false;
                box.setFocused(false);
            }
        }
    }

    private final List<String> lines = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private boolean vanillaStyle;
    private int scroll;
    private boolean binding;

    private int listY;
    private int listHeight;
    private int visibleRows;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public LorePage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.LORE;
    }

    @Override
    protected void build() {
        loadFromItem();
        rows.clear();

        int cursorY = y;

        // 颜色工具条
        cursorY += ColorToolbar.build(this::add, x, cursorY, width, this::focusedBox) + 6;

        // 添加按钮 + 原版样式开关
        int addWidth = Math.min(72, width / 3);
        add(new FlatButton(x, cursorY, addWidth, 14, Component.translatable("gui.battlecorrection.editor.lore.add"),
                () -> insertLine(lines.size())).style(FlatButton.Style.PRIMARY)
                .tooltip(Component.translatable("gui.battlecorrection.editor.lore.add.tooltip")));
        add(new FlatToggle(x + addWidth + 8, cursorY + 1,
                Component.translatable("gui.battlecorrection.editor.lore.vanilla_style"), vanillaStyle, value -> {
            vanillaStyle = value;
            writeToItem();
        }).tooltip(Component.translatable("gui.battlecorrection.editor.lore.vanilla_style.tooltip")));
        cursorY += 14 + 6;

        // 行列表
        listY = cursorY;
        listHeight = y + height - listY;
        visibleRows = Math.max(1, listHeight / ROW_HEIGHT);

        int boxWidth = width - SCROLLBAR_WIDTH - 4 - 3 * (BUTTON_WIDTH + GAP) - 2;
        for (int i = 0; i < visibleRows; i++) {
            final int rowIndex = i;
            int rowY = listY + i * ROW_HEIGHT;
            Row row = new Row();
            row.box = add(new FlatEditBox(font, x + 1, rowY + 1, boxWidth, BOX_HEIGHT,
                    Component.translatable("gui.battlecorrection.editor.lore.hint"), true));
            row.box.setMaxLength(MAX_LINE_LENGTH);
            row.box.setResponder(value -> onLineChanged(rowIndex, value));

            int buttonX = x + 1 + boxWidth + 4;
            row.up = add(new FlatButton(buttonX, rowY + 1, BUTTON_WIDTH, BOX_HEIGHT, Component.literal("↑"),
                    () -> moveLine(rowIndex, -1))
                    .tooltip(Component.translatable("gui.battlecorrection.editor.lore.up")));
            buttonX += BUTTON_WIDTH + GAP;
            row.down = add(new FlatButton(buttonX, rowY + 1, BUTTON_WIDTH, BOX_HEIGHT, Component.literal("↓"),
                    () -> moveLine(rowIndex, 1))
                    .tooltip(Component.translatable("gui.battlecorrection.editor.lore.down")));
            buttonX += BUTTON_WIDTH + GAP;
            row.delete = add(new FlatButton(buttonX, rowY + 1, BUTTON_WIDTH, BOX_HEIGHT, Component.literal("×"),
                    () -> deleteLine(rowIndex)).style(FlatButton.Style.DANGER)
                    .tooltip(Component.translatable("gui.battlecorrection.editor.lore.delete")));
            rows.add(row);
        }

        clampScroll();
        rebind();
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (lines.isEmpty()) {
            paragraph(g, Component.translatable("gui.battlecorrection.editor.lore.empty"), x, listY + 4, width,
                    EditorTheme.TEXT_DIM);
            return;
        }
        // 滚动条
        if (lines.size() > visibleRows) {
            int trackX = x + width - SCROLLBAR_WIDTH;
            int trackHeight = visibleRows * ROW_HEIGHT;
            g.fill(trackX, listY, trackX + SCROLLBAR_WIDTH, listY + trackHeight, EditorTheme.CARD_BORDER);
            int thumbHeight = Math.max(8, trackHeight * visibleRows / lines.size());
            int maxScroll = lines.size() - visibleRows;
            int thumbY = listY + (trackHeight - thumbHeight) * scroll / maxScroll;
            g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, EditorTheme.ACCENT);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX > x + width || mouseY < listY || mouseY > listY + listHeight) {
            return false;
        }
        if (lines.size() <= visibleRows) {
            return true;
        }
        scroll -= (int) Math.signum(delta);
        clampScroll();
        rebind();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            for (Row row : rows) {
                if (row.box.visible && row.box.isFocused() && row.index >= 0) {
                    insertLine(row.index + 1);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 当前有焦点的行的输入框（颜色工具条的目标）
     *
     * @return 输入框，没有则 null
     */
    @Nullable
    private EditBox focusedBox() {
        for (Row row : rows) {
            if (row.box.visible && row.box.isFocused()) {
                return row.box;
            }
        }
        return null;
    }

    /**
     * 把可见的控件绑定到当前滚动位置对应的行
     */
    private void rebind() {
        binding = true;
        try {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int index = scroll + i;
                if (index < lines.size()) {
                    row.index = index;
                    row.setVisible(true);
                    row.box.setValue(lines.get(index));
                    row.up.active = index > 0;
                    row.down.active = index < lines.size() - 1;
                } else {
                    row.index = -1;
                    row.setVisible(false);
                }
            }
        } finally {
            binding = false;
        }
    }

    /**
     * 把 scroll 限制在合法范围
     */
    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - visibleRows));
    }

    /**
     * 滚动到让某一行可见
     *
     * @param index 行下标
     */
    private void scrollTo(int index) {
        if (index < scroll) {
            scroll = index;
        } else if (index >= scroll + visibleRows) {
            scroll = index - visibleRows + 1;
        }
        clampScroll();
    }

    /**
     * 让某一行的输入框获得焦点
     *
     * @param index 行下标
     */
    private void focusLine(int index) {
        for (Row row : rows) {
            if (row.index == index && row.box.visible) {
                screen.setFocused(row.box);
                return;
            }
        }
    }

    /**
     * 某一行输入框内容变化
     *
     * @param rowIndex 控件行序号
     * @param value    新内容
     */
    private void onLineChanged(int rowIndex, @Nonnull String value) {
        if (binding || rowIndex >= rows.size()) {
            return;
        }
        int index = rows.get(rowIndex).index;
        if (index < 0 || index >= lines.size()) {
            return;
        }
        lines.set(index, value);
        writeToItem();
    }

    /**
     * 在指定位置插入一个空行并聚焦
     *
     * @param index 插入位置
     */
    private void insertLine(int index) {
        index = Mth.clamp(index, 0, lines.size());
        lines.add(index, "");
        writeToItem();
        scrollTo(index);
        rebind();
        focusLine(index);
    }

    /**
     * 上移/下移
     *
     * @param rowIndex  控件行序号
     * @param direction -1 上移，1 下移
     */
    private void moveLine(int rowIndex, int direction) {
        if (rowIndex >= rows.size()) {
            return;
        }
        int index = rows.get(rowIndex).index;
        int target = index + direction;
        if (index < 0 || target < 0 || target >= lines.size()) {
            return;
        }
        Collections.swap(lines, index, target);
        writeToItem();
        scrollTo(target);
        rebind();
        focusLine(target);
    }

    /**
     * 删除一行
     *
     * @param rowIndex 控件行序号
     */
    private void deleteLine(int rowIndex) {
        if (rowIndex >= rows.size()) {
            return;
        }
        int index = rows.get(rowIndex).index;
        if (index < 0 || index >= lines.size()) {
            return;
        }
        lines.remove(index);
        writeToItem();
        clampScroll();
        rebind();
    }

    /**
     * 从物品副本读 Lore
     */
    private void loadFromItem() {
        lines.clear();
        vanillaStyle = false;
        CompoundTag tag = session.tagOrNull();
        if (tag == null || !tag.contains("display", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag display = tag.getCompound("display");
        if (!display.contains("Lore", Tag.TAG_LIST)) {
            return;
        }
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        for (int i = 0; i < lore.size(); i++) {
            String json = lore.getString(i);
            MutableComponent component = null;
            try {
                component = Component.Serializer.fromJson(json);
            } catch (JsonParseException ignored) {
                // 不是合法 JSON：当纯文本处理，保存时会重新包装
            }
            if (component == null) {
                lines.add(LegacyText.fromSection(json));
            } else {
                lines.add(LegacyText.toEditable(component));
                if (i == 0) {
                    vanillaStyle = component.getStyle().isEmpty();
                }
            }
        }
    }

    /**
     * 把 Lore 写回物品副本
     */
    private void writeToItem() {
        if (lines.isEmpty()) {
            CompoundTag tag = session.tagOrNull();
            if (tag != null && tag.contains("display", Tag.TAG_COMPOUND)) {
                CompoundTag display = tag.getCompound("display");
                display.remove("Lore");
                if (display.isEmpty()) {
                    tag.remove("display");
                }
            }
            return;
        }

        Style base = vanillaStyle
                ? Style.EMPTY
                : Style.EMPTY.withItalic(false).withColor(ChatFormatting.WHITE);
        ListTag lore = new ListTag();
        for (String line : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(LegacyText.toComponent(line, base))));
        }

        CompoundTag tag = session.tag();
        CompoundTag display = tag.getCompound("display");
        display.put("Lore", lore);
        tag.put("display", display);
    }
}
