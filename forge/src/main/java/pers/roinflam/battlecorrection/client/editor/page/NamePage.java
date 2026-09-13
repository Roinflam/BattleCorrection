package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
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

/**
 * 名字页：改显示名称
 * <p>
 * 输入框里用 & 写颜色码，下面的色块/格式按钮点一下就插一个码，预览行实时显示效果。
 * 留空 = 用原版名称（删掉 display.Name）。
 * <p>
 * 关于斜体：原版给自定义名字默认加斜体，大多数人不想要，所以默认写 italic=false；勾上"斜体"就不写，恢复原版行为。
 */
public class NamePage extends EditorPageBase {

    private static final int BOX_HEIGHT = 16;
    private static final int MAX_LENGTH = 512;

    /**
     * 当前编辑的名字（& 码形式）；空 = 原版名称
     */
    private String editable = "";

    /**
     * 是否允许斜体
     */
    private boolean italic;

    /**
     * 是否正在从物品载入（此时输入框的回调不要写回）
     */
    private boolean loading;

    @Nullable
    private FlatEditBox nameBox;
    @Nullable
    private FlatToggle italicToggle;
    private int previewY;
    private int helpY;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public NamePage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.NAME;
    }

    @Override
    protected void build() {
        loadFromItem();

        int cursorY = y + 11;

        // 输入框（标签在 render 里画在它上面）
        nameBox = add(new FlatEditBox(font, x + 1, cursorY, width - 2, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.name.hint"), true));
        nameBox.setMaxLength(MAX_LENGTH);
        loading = true;
        nameBox.setValue(editable);
        loading = false;
        nameBox.setResponder(this::onNameChanged);
        cursorY += BOX_HEIGHT + 8;

        // 斜体开关 + 还原按钮
        italicToggle = add(new FlatToggle(x, cursorY, Component.translatable("gui.battlecorrection.editor.name.italic"),
                italic, value -> {
            italic = value;
            writeToItem();
        }).tooltip(Component.translatable("gui.battlecorrection.editor.name.italic.tooltip")));
        int resetWidth = Math.min(100, width / 2);
        add(new FlatButton(x + width - resetWidth, cursorY - 1, resetWidth, 14,
                Component.translatable("gui.battlecorrection.editor.name.reset"), this::resetName)
                .tooltip(Component.translatable("gui.battlecorrection.editor.name.reset.tooltip")));
        cursorY += FlatToggle.HEIGHT + 8;

        // 颜色工具条
        cursorY += ColorToolbar.build(this::add, x, cursorY, width, () -> nameBox) + 8;

        previewY = cursorY;
        helpY = previewY + 14;
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        label(g, Component.translatable("gui.battlecorrection.editor.name.label"), x, y);

        // 预览：直接渲染成组件，颜色码会被正常解析；太长的部分裁掉
        Component previewLabel = Component.translatable("gui.battlecorrection.editor.preview");
        label(g, previewLabel, x, previewY);
        int previewX = x + font.width(previewLabel) + 4;
        Component preview = editable.isEmpty()
                ? session.original().getItem().getDescription().copy()
                .withStyle(style -> style.withColor(EditorTheme.TEXT_MUTED & 0xFFFFFF))
                : LegacyText.preview(editable).withStyle(Style.EMPTY.withItalic(italic));
        g.enableScissor(previewX, previewY - 1, x + width, previewY + font.lineHeight + 1);
        g.drawString(font, preview, previewX, previewY, EditorTheme.TEXT, false);
        g.disableScissor();

        paragraph(g, Component.translatable("gui.battlecorrection.editor.name.help"),
                x, helpY, width, EditorTheme.TEXT_DIM);
    }

    /**
     * 输入框内容变化
     *
     * @param value 新内容
     */
    private void onNameChanged(@Nonnull String value) {
        if (loading) {
            return;
        }
        editable = value;
        writeToItem();
    }

    /**
     * 还原原版名称：清空输入框并删掉 display.Name
     */
    private void resetName() {
        editable = "";
        italic = false;
        if (nameBox != null) {
            loading = true;
            nameBox.setValue("");
            loading = false;
        }
        if (italicToggle != null) {
            italicToggle.setSelected(false);
        }
        writeToItem();
    }

    /**
     * 从物品副本读当前名字
     */
    private void loadFromItem() {
        ItemStack working = session.working();
        if (working.hasCustomHoverName()) {
            Component name = working.getHoverName();
            editable = LegacyText.toEditable(name);
            italic = name.getStyle().isItalic();
        } else {
            editable = "";
            italic = false;
        }
    }

    /**
     * 把当前名字写回物品副本
     */
    private void writeToItem() {
        ItemStack working = session.working();
        if (editable.isEmpty()) {
            working.resetHoverName();
            return;
        }
        // italic=false 要显式写进 JSON，原版才不会给自定义名字加斜体
        Style base = Style.EMPTY.withItalic(italic);
        working.setHoverName(LegacyText.toComponent(editable, base));
    }

    /**
     * 名字输入框（供测试或其他页面聚焦）
     *
     * @return 输入框，未初始化时为 null
     */
    @Nullable
    public EditBox nameBox() {
        return nameBox;
    }
}
