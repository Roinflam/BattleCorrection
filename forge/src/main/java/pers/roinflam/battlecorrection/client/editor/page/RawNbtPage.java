package pers.roinflam.battlecorrection.client.editor.page;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatMultiLineEditBox;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 原始 NBT 页：整件物品的 NBT 以 SNBT 文本形式编辑
 * <p>
 * 显示的是物品存盘时的完整结构 {@code {id:"minecraft:stick", Count:1b, tag:{...}}}，
 * 和 NBTEdit、/data 命令看到的一样；Forge 附加的 ForgeCaps 字段是运行时能力数据，编辑器改不了，显示时去掉。
 * 进入页面时格式化（缩进换行）后填进多行框；点"应用修改"才解析写回，解析失败在下方标红原因，不写回。
 * 应用时：id 必须和当前物品一致（编辑器不允许换物品）；Count 取 1-64；tag 整体替换，没写 tag 就等于清空。
 * 切到别的页或点"保存"时，如果有没应用的改动会自动尝试应用，失败就拦住。
 * <p>
 * 这一页改的是整份 NBT，别的页只改自己那一小块；两边都是改同一个物品副本，所以来回切不会互相冲突：
 * 别的页改完，这一页进入时重新读；这一页应用完，别的页进入时也重新读。
 */
public class RawNbtPage extends EditorPageBase {

    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_WIDTH = 64;
    private static final int GAP = 4;
    private static final String KEY_ID = "id";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_TAG = "tag";
    private static final String KEY_FORGE_CAPS = "ForgeCaps";

    /**
     * 状态
     */
    private enum Status {
        CLEAN,
        DIRTY,
        APPLIED,
        ERROR
    }

    @Nullable
    private FlatMultiLineEditBox box;
    private Status status = Status.CLEAN;
    private String errorText = "";

    /**
     * 上次应用（或载入）成功的文本，用来判断有没有未应用的改动
     */
    private String appliedText = "";

    /**
     * 未应用的编辑内容；窗口大小变化重建控件时靠它恢复
     */
    @Nullable
    private String pendingText;

    private boolean binding;
    private int statusY;
    private int statusX;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public RawNbtPage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.RAW;
    }

    @Override
    protected void build() {
        int boxHeight = height - BUTTON_HEIGHT - GAP - 2;
        box = add(new FlatMultiLineEditBox(font, x, y, width, boxHeight,
                Component.translatable("gui.battlecorrection.editor.raw.empty"),
                Component.translatable("gui.battlecorrection.editor.tab.raw")));
        // 不设字数上限：原版设了上限就会在框底下画一个"当前/上限"的计数器，正好压在状态文字上

        String text;
        if (pendingText != null) {
            text = pendingText;
        } else {
            text = prettyPrint(session.working());
            appliedText = text;
            status = Status.CLEAN;
        }
        binding = true;
        box.setValue(text);
        binding = false;
        box.setValueListener(this::onTextChanged);

        int buttonY = y + height - BUTTON_HEIGHT;
        int buttonX = x;
        add(new FlatButton(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.raw.apply"), this::apply)
                .style(FlatButton.Style.PRIMARY)
                .tooltip(Component.translatable("gui.battlecorrection.editor.raw.apply.tooltip")));
        buttonX += BUTTON_WIDTH + GAP;
        add(new FlatButton(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.raw.reload"), this::reload)
                .tooltip(Component.translatable("gui.battlecorrection.editor.raw.reload.tooltip")));
        buttonX += BUTTON_WIDTH + GAP;
        add(new FlatButton(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.raw.copy"), this::copy)
                .tooltip(Component.translatable("gui.battlecorrection.editor.raw.copy.tooltip")));
        buttonX += BUTTON_WIDTH + GAP;

        statusX = buttonX + 2;
        statusY = buttonY + (BUTTON_HEIGHT - 8) / 2;
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Component text;
        int color;
        switch (status) {
            case DIRTY:
                text = Component.translatable("gui.battlecorrection.editor.raw.status.dirty");
                color = EditorTheme.WARNING;
                break;
            case APPLIED:
                text = Component.translatable("gui.battlecorrection.editor.raw.status.applied");
                color = EditorTheme.SUCCESS;
                break;
            case ERROR:
                text = Component.translatable("gui.battlecorrection.editor.raw.status.error", errorText);
                color = EditorTheme.DANGER;
                break;
            default:
                text = session.hasForgeCaps()
                        ? Component.translatable("gui.battlecorrection.editor.raw.status.clean_caps")
                        : Component.translatable("gui.battlecorrection.editor.raw.status.clean");
                color = EditorTheme.TEXT_DIM;
                break;
        }
        int maxWidth = x + width - statusX;
        if (maxWidth <= 20) {
            return;
        }
        // 状态只有一行的位置，太长就裁掉；完整错误信息鼠标悬停时显示
        String plain = text.getString();
        if (font.width(plain) > maxWidth) {
            plain = font.plainSubstrByWidth(plain, Math.max(0, maxWidth - font.width("…"))) + "…";
            g.drawString(font, plain, statusX, statusY, color, false);
            if (mouseX >= statusX && mouseX <= x + width && mouseY >= statusY - 3 && mouseY <= statusY + 11) {
                g.renderTooltip(font, font.split(text, Math.min(300, screen.width - 20)), mouseX, mouseY);
            }
        } else {
            g.drawString(font, plain, statusX, statusY, color, false);
        }
    }

    @Override
    public boolean beforeLeave() {
        if (status == Status.DIRTY || status == Status.ERROR) {
            return apply();
        }
        return true;
    }

    /**
     * 文本变化
     *
     * @param value 新文本
     */
    private void onTextChanged(@Nonnull String value) {
        if (binding) {
            return;
        }
        pendingText = value;
        if (value.equals(appliedText)) {
            pendingText = null;
            status = Status.CLEAN;
        } else if (status != Status.ERROR) {
            status = Status.DIRTY;
        }
    }

    /**
     * 解析并写回物品副本
     *
     * @return true = 成功
     */
    private boolean apply() {
        if (box == null) {
            return true;
        }
        String text = box.getValue();
        CompoundTag parsed;
        try {
            parsed = TagParser.parseTag(text);
        } catch (CommandSyntaxException e) {
            status = Status.ERROR;
            errorText = e.getMessage() == null ? "?" : e.getMessage();
            return false;
        }

        // id 必须还是这件物品；编辑器不允许在这里换物品
        String id = parsed.getString(KEY_ID);
        if (!session.itemId().toString().equals(id)) {
            status = Status.ERROR;
            errorText = I18n.get("gui.battlecorrection.editor.raw.status.id_changed", session.itemId());
            return false;
        }

        ItemStack working = session.working();
        if (parsed.contains(KEY_COUNT, Tag.TAG_ANY_NUMERIC)) {
            working.setCount(Mth.clamp(parsed.getInt(KEY_COUNT),
                    ApplyItemEditC2SPacket.MIN_COUNT, ApplyItemEditC2SPacket.MAX_COUNT));
        }
        session.setTag(parsed.contains(KEY_TAG, Tag.TAG_COMPOUND) ? parsed.getCompound(KEY_TAG).copy() : null);

        appliedText = text;
        pendingText = null;
        status = Status.APPLIED;
        return true;
    }

    /**
     * 丢掉文本框里的改动，重新从物品副本载入
     */
    private void reload() {
        if (box == null) {
            return;
        }
        String text = prettyPrint(session.working());
        binding = true;
        box.setValue(text);
        binding = false;
        appliedText = text;
        pendingText = null;
        status = Status.CLEAN;
    }

    /**
     * 复制文本框内容到剪贴板
     */
    private void copy() {
        if (box == null) {
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(box.getValue());
    }

    /**
     * 把整件物品的存盘 NBT 格式化成带缩进换行的 SNBT
     *
     * @param stack 物品
     * @return 文本
     */
    @Nonnull
    private static String prettyPrint(@Nonnull ItemStack stack) {
        CompoundTag full = stack.save(new CompoundTag());
        // 运行时能力数据，编辑器改不了，显示出来只会误导
        full.remove(KEY_FORGE_CAPS);
        return new SnbtPrinterTagVisitor().visit(full);
    }
}
