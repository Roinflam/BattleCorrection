package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.EditBox;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.widget.ColorToolbar;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.FlatToggle;
import pers.roinflam.battlecorrection.client.editor.widget.LossyMarker;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.LegacyComponents;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * 名字页：改显示名称（display.Name）
 * <p>
 * 输入框里用 & 写颜色码，下面的色块/格式按钮点一下就插一个码，预览行实时显示效果。
 * 留空 = 用原版名称（删掉 display.Name）；读进来就是空白名字、还没动过时除外，见 {@link #writeToItem}。
 * <p>
 * 关于斜体：原版提示框给自定义名字前面加稀有度颜色和 §o，大多数人不想要斜体，所以默认在名字最前面写 §r
 * （1.12.2 没有单独关掉斜体的代码，§r 会连稀有度颜色一起清掉，没写颜色码的字是白色）；勾上"斜体"就不写，恢复原版行为。
 * 读取时每个字都是斜体的名字自动勾上（例如铁砧改的名字），这样一个代码都不用多写。
 * 开关管的是整个名字：切换时连文字里的 {@code &o} 一起改写（{@link #setItalic}）。
 * <p>
 * 原样保留：读进来的名字只要文字和斜体开关都没动，写回的就是原来那串字符串，编辑器放不下的控制字符、多余的代码都不会丢；
 * 动过之后才按编辑文本重新生成，此时如果原来有编辑器表达不了的东西，标题旁亮一个黄色标记提醒。
 * 预览画的就是提示框第一行：稀有度颜色 + §o + 写进物品的名字 + §r（ItemStack#getTooltip、GuiScreen#getItemToolTip 的拼法）。
 */
public class NamePage extends EditorPageBase {

    private static final int BOX_HEIGHT = 16;

    private static final Component NAME_LABEL = Component.translatable("gui.battlecorrection.editor.name.label");
    private static final Component PREVIEW_LABEL = Component.translatable("gui.battlecorrection.editor.preview");
    private static final Component HELP = Component.translatable("gui.battlecorrection.editor.name.help.codes");

    /**
     * 当前编辑的名字（& 码形式）；空 = 原版名称
     */
    private String editable = "";

    /**
     * 是否斜体（= 原版基础样式，名字前面不加 §r）
     */
    private boolean italic;

    /**
     * 是否正在从物品载入（此时输入框的回调不要写回）
     */
    private boolean loading;

    /**
     * 载入时物品上的 display.Name 原文；文字和斜体开关都没动时原样写回
     */
    @Nullable
    private String loadedRaw;
    private String loadedEditable = "";
    private boolean loadedItalic;
    /**
     * 原名字里编辑器表达不了的东西
     */
    private Set<LegacyComponents.Lossy> lossy = Collections.emptySet();

    /**
     * 上次写进物品的 display.Name（null = 删掉了）；页面重建时物品里还是它，说明没被别的页改过，保留输入状态
     */
    @Nullable
    private String writtenRaw;
    private boolean synced;

    /**
     * 稀有度颜色和名字的外层样式（稀有度颜色 + 斜体）
     */
    private TextFormatting rarity = TextFormatting.WHITE;
    private LegacyComponents.Look parent = LegacyComponents.nameParent(TextFormatting.WHITE);

    /**
     * 预览：提示框第一行；null = 没有自定义名字
     */
    @Nullable
    private Component preview;

    /**
     * 没有自定义名字时预览显示的物品本名（灰色）；换了物品会重建页面，build 时按当前物品算一次，不在每帧里拼
     */
    private Component vanillaName = Component.empty();

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
        // 稀有度可能变了（换了物品、加了附魔），每次重建都重新算
        ItemStack working = session.working();
        rarity = working.getItem().getForgeRarity(working).getColor();
        parent = LegacyComponents.nameParent(rarity);
        // 用现在的物品：换过物品后 original() 还是打开编辑器时那件。
        // 在副本上去掉自定义名字再取显示名：药水、头颅这类会按 NBT 变，比直接取物品的本地化名准
        ItemStack plain = working.copy();
        if (plain.hasDisplayName()) {
            plain.clearCustomName();
        }
        vanillaName = Component.literal(plain.getDisplayName());
        loadFromItem();

        int cursorY = y + 11;

        // 输入框（标签在 render 里画在它上面）
        nameBox = add(new FlatEditBox(font, x + 1, cursorY, width - 2, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.name.hint"), true));
        nameBox.ensureCapacity(editable.length());
        loading = true;
        nameBox.setValue(editable);
        loading = false;
        nameBox.setResponder(this::onNameChanged);
        cursorY += BOX_HEIGHT + 8;

        // 斜体开关 + 还原按钮
        italicToggle = add(new FlatToggle(x, cursorY, Component.translatable("gui.battlecorrection.editor.name.italic"),
                italic, this::setItalic)
                .tooltip(Component.translatableLines("gui.battlecorrection.editor.name.italic.tooltip.codes")));
        int resetWidth = Math.min(100, width / 2);
        add(new FlatButton(x + width - resetWidth, cursorY - 1, resetWidth, 14,
                Component.translatable("gui.battlecorrection.editor.name.reset"), this::resetName)
                .tooltip(Component.translatable("gui.battlecorrection.editor.name.reset.tooltip")));
        cursorY += FlatToggle.HEIGHT + 8;

        // 颜色工具条
        cursorY += ColorToolbar.build(this::add, x, cursorY, width, () -> nameBox,
                () -> LegacyComponents.start(italic, parent)) + 8;

        previewY = cursorY;
        helpY = previewY + 14;
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        label(g, NAME_LABEL, x, y);

        // 预览：提示框第一行原样画（§r 回到画字的颜色，提示框里是白色，所以这里也用白色）；太长的部分裁掉
        label(g, PREVIEW_LABEL, x, previewY);
        int previewX = x + font.width(PREVIEW_LABEL) + 4;
        g.enableScissor(previewX, previewY - 1, x + width, previewY + font.lineHeight + 1);
        if (preview != null) {
            g.drawString(font, preview, previewX, previewY, 0xFFFFFFFF, false);
        } else {
            g.drawString(font, vanillaName, previewX, previewY, EditorTheme.TEXT_MUTED, false);
        }
        g.disableScissor();

        paragraph(g, HELP, x, helpY, width, EditorTheme.TEXT_DIM);
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (lossy.isEmpty()) {
            return;
        }
        // 标题行右侧：标记 + 一句话，悬停看详情
        boolean warning = isModified();
        Component badge = Component.translatable(warning ? "gui.battlecorrection.editor.lossy.badge.lost"
                : "gui.battlecorrection.editor.lossy.badge");
        int textWidth = font.width(badge);
        int markerX = x + width - textWidth - LossyMarker.SIZE - 4;
        int markerY = y - 1;
        LossyMarker.draw(g, font, markerX, markerY, warning);
        g.drawString(font, badge, markerX + LossyMarker.SIZE + 3, y, warning ? EditorTheme.WARNING : EditorTheme.TEXT_DIM, false);
        if (mouseX >= markerX && mouseX <= x + width && mouseY >= markerY && mouseY < markerY + LossyMarker.SIZE + 1) {
            LossyMarker.renderTooltip(g, font, LossyMarker.tooltip(lossy, warning), mouseX, mouseY);
        }
    }

    @Override
    public void onExternalChange() {
        // 撤销 / 重做 / 换物品 / 导入：物品已经不是页面上次写的样子，重建时从物品重新读
        synced = false;
    }

    /**
     * 输入框内容变化
     *
     * @param value 新内容
     */
    private void onNameChanged(@Nonnull String value) {
        // 点一下输入框、方向键、Home、全选都会带着原样的内容回调：内容没变就什么都不做，
        // 否则空白名字（"" 或 "§r" 这类）一点就被当成"留空"删掉，物品还被标成改过
        if (loading || value.equals(editable)) {
            return;
        }
        editable = value;
        writeToItem();
    }

    /**
     * 切换"斜体"开关：整个名字一起变
     * <p>
     * 只换基础样式管不到文字里的 {@code &o}，也管不到颜色码后面被清掉斜体的字，所以连文字一起改写
     * （{@link LegacyComponents#withItalic}）：关 = 每个字都不斜体，开 = 每个字都斜体，颜色和其余格式不动。
     *
     * @param value true = 斜体
     */
    private void setItalic(boolean value) {
        if (value == italic) {
            return;
        }
        String next = LegacyComponents.withItalic(editable, italic, value, parent, value);
        italic = value;
        if (!next.equals(editable)) {
            editable = next;
            if (nameBox != null) {
                loading = true;
                nameBox.ensureCapacity(next.length());
                nameBox.setValue(next);
                loading = false;
            }
        }
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
        clearName();
    }

    /**
     * 删掉自定义名字
     * <p>
     * 物品上本来就没有名字时什么都不动：clearCustomName 还会顺手删掉空的 display、把空 tag 置空，物品会被白白标成改过。
     */
    private void clearName() {
        ItemStack working = session.working();
        NBTTagCompound display = working.getSubCompound("display");
        if (display != null && display.hasKey("Name")) {
            working.clearCustomName();
        }
        writtenRaw = null;
        refreshPreview();
    }

    /**
     * 现在写进物品的是不是已经不是载入时的原文了
     */
    private boolean isModified() {
        return loadedRaw != null && !Objects.equals(writtenRaw, loadedRaw);
    }

    /**
     * 从物品副本读当前名字
     * <p>
     * 物品上的名字还是这一页上次写的那个时（只是切了页、窗口缩放），保留输入框里的原样文字，不重新读：
     * 重新读会把文字规范化（例如 {@code &6&6x} 变成 {@code &6x}），光标乱跳，也会丢掉"原文"这个基准。
     */
    private void loadFromItem() {
        NBTTagCompound display = session.working().getSubCompound("display");
        String raw = display != null && display.hasKey("Name", 8) ? display.getString("Name") : null;
        if (synced && Objects.equals(raw, writtenRaw)) {
            refreshPreview();
            return;
        }
        synced = true;
        writtenRaw = raw;
        loadedRaw = raw;
        lossy = Collections.emptySet();
        if (raw == null) {
            editable = "";
            italic = false;
        } else {
            italic = LegacyComponents.preferVanilla(raw, parent);
            LegacyComponents.Decoded decoded = LegacyComponents.decode(raw, italic, parent);
            editable = decoded.editable();
            lossy = decoded.lossy();
        }
        loadedEditable = editable;
        loadedItalic = italic;
        refreshPreview();
    }

    /**
     * 把当前名字写回物品副本
     * <p>
     * 文字和斜体开关都和载入时一样就写回原文；否则按编辑文本生成（{@link LegacyComponents#encode}）。
     * 文字为空时删掉名字，只有一种例外：读进来的就是空白名字（{@code ""}、{@code "§r"} 这类，服务器 GUI 的填充物品常用），
     * 而且还没被改过——这时只是切了斜体开关，一个字都没有，斜体无所谓，名字原样留着。
     */
    private void writeToItem() {
        ItemStack working = session.working();
        if (editable.isEmpty()) {
            boolean untouchedBlank = loadedRaw != null && loadedEditable.isEmpty() && loadedRaw.equals(writtenRaw);
            if (!untouchedBlank) {
                clearName();
            }
            return;
        }
        String raw;
        if (loadedRaw != null && editable.equals(loadedEditable) && italic == loadedItalic) {
            raw = loadedRaw;
        } else {
            // 不斜体时 encode 在最前面补 §r，提示框加的 §o 就不起作用了
            raw = LegacyComponents.encode(editable, italic);
        }
        working.getOrCreateSubCompound("display").setString("Name", raw);
        writtenRaw = raw;
        refreshPreview();
    }

    /**
     * 更新预览：和提示框第一行同样的拼法
     */
    private void refreshPreview() {
        String raw = writtenRaw;
        preview = raw == null ? null
                : Component.literal(rarity.toString() + TextFormatting.ITALIC + raw + TextFormatting.RESET);
    }

    /**
     * 名字输入框（供其他页面聚焦）
     *
     * @return 输入框，未初始化时为 null
     */
    @Nullable
    public EditBox nameBox() {
        return nameBox;
    }
}
