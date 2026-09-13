package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 颜色/格式代码工具条：16 个色块 + 粗体/斜体/下划线/删除线/乱码/重置
 * <p>
 * 只列原版的 16 种颜色：有些模组会往 ChatFormatting 枚举里塞自己的颜色（例如 goety_nether），
 * 那些没有对应的 § 码，插进去也没用，所以不从 values() 取。
 * 点一下就往目标输入框的光标处插入对应的 & 码（例如 {@code &a}）。目标由 Supplier 提供，
 * Lore 页有很多行，返回当前有焦点的那一行；没有目标时点击无事发生。
 * 所有按钮都是"不抢焦点"的，插完代码输入框还能接着打字。
 * <p>
 * 宽度不够时色块自动换行，{@link #build} 返回实际占用的高度，页面据此往下排版。
 */
public final class ColorToolbar {

    private static final int SWATCH = 10;
    private static final int SWATCH_GAP = 2;
    private static final int FORMAT_WIDTH = 22;
    private static final int FORMAT_HEIGHT = 12;
    private static final int FORMAT_GAP = 3;
    private static final int ROW_GAP = 4;

    /**
     * 格式按钮：显示文字 + 对应的格式码 + 提示翻译键
     */
    private static final Object[][] FORMATS = {
            {Component.literal("B").withStyle(ChatFormatting.BOLD), ChatFormatting.BOLD, "bold"},
            {Component.literal("I").withStyle(ChatFormatting.ITALIC), ChatFormatting.ITALIC, "italic"},
            {Component.literal("U").withStyle(ChatFormatting.UNDERLINE), ChatFormatting.UNDERLINE, "underline"},
            {Component.literal("S").withStyle(ChatFormatting.STRIKETHROUGH), ChatFormatting.STRIKETHROUGH, "strikethrough"},
            {Component.literal("K"), ChatFormatting.OBFUSCATED, "obfuscated"},
            {Component.literal("R"), ChatFormatting.RESET, "reset"},
    };

    private ColorToolbar() {
    }

    /**
     * 创建工具条的所有按钮
     *
     * @param add      把按钮交给页面登记的回调
     * @param x        左
     * @param y        上
     * @param maxWidth 可用宽度
     * @param target   目标输入框（可返回 null）
     * @return 占用的总高度
     */
    public static int build(@Nonnull Consumer<AbstractWidget> add, int x, int y, int maxWidth,
                            @Nonnull Supplier<EditBox> target) {
        int cursorX = x;
        int cursorY = y;

        for (ChatFormatting formatting : LegacyText.VANILLA_COLORS) {
            Integer rgb = formatting.getColor();
            if (rgb == null) {
                continue;
            }
            if (cursorX + SWATCH > x + maxWidth) {
                cursorX = x;
                cursorY += SWATCH + SWATCH_GAP;
            }
            Component name = Component.translatable("gui.battlecorrection.editor.color." + formatting.getName());
            add.accept(FlatButton.swatch(cursorX, cursorY, SWATCH, 0xFF000000 | rgb, name,
                    () -> insert(target, formatting)).keepFocus());
            cursorX += SWATCH + SWATCH_GAP;
        }

        cursorX = x;
        cursorY += SWATCH + ROW_GAP;
        for (Object[] format : FORMATS) {
            Component label = (Component) format[0];
            ChatFormatting formatting = (ChatFormatting) format[1];
            String key = (String) format[2];
            if (cursorX + FORMAT_WIDTH > x + maxWidth) {
                cursorX = x;
                cursorY += FORMAT_HEIGHT + SWATCH_GAP;
            }
            add.accept(new FlatButton(cursorX, cursorY, FORMAT_WIDTH, FORMAT_HEIGHT, label,
                    () -> insert(target, formatting))
                    .tooltip(Component.translatable("gui.battlecorrection.editor.format." + key))
                    .keepFocus());
            cursorX += FORMAT_WIDTH + FORMAT_GAP;
        }

        return cursorY + FORMAT_HEIGHT - y;
    }

    /**
     * 往目标输入框插入一个 & 码
     *
     * @param target     目标提供者
     * @param formatting 颜色/格式
     */
    private static void insert(@Nonnull Supplier<EditBox> target, @Nonnull ChatFormatting formatting) {
        @Nullable EditBox box = target.get();
        if (box == null || !box.visible || !box.active) {
            return;
        }
        box.insertText(String.valueOf(LegacyText.AMPERSAND) + formatting.getChar());
    }
}
