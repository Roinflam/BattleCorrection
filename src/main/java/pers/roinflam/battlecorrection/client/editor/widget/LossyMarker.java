package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;
import pers.roinflam.battlecorrection.editor.LegacyComponents;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "这里有编辑器表达不了的东西"的小标记：名字页标题旁、Lore 每行行尾
 * <p>
 * 1.12.2 的名字和 Lore 是 § 字符串，编辑器表达不了的只有输入框里放不下的控制字符（换行、制表符这类）。两种状态：
 * <ul>
 *   <li>没改动（原样保留）：暗色小方块，悬停说明里面有什么</li>
 *   <li>改动过（保存会丢掉）：亮黄色，一闪一闪，悬停说明会丢什么、怎么挽回</li>
 * </ul>
 * 只是画和拼提示文字，状态由页面判断。
 */
public final class LossyMarker {

    /**
     * 标记的边长
     */
    public static final int SIZE = 9;

    private LossyMarker() {
    }

    /**
     * 画标记
     *
     * @param g       画布
     * @param font    字体
     * @param x       左
     * @param y       上
     * @param warning true = 改动过、保存会丢（醒目）；false = 原样保留（低调）
     */
    public static void draw(@Nonnull GuiGraphics g, @Nonnull Font font, int x, int y, boolean warning) {
        int edge = warning ? EditorTheme.lerp(EditorTheme.WARNING, 0xFFFFFFFF, EditorTheme.pulse() * 0.5F)
                : EditorTheme.BUTTON_BORDER;
        int fill = warning ? 0xFF3A2A08 : EditorTheme.WELL;
        EditorTheme.chamfer(g, x, y, SIZE, SIZE, 2, edge);
        EditorTheme.chamfer(g, x + 1, y + 1, SIZE - 2, SIZE - 2, 1, fill);
        g.drawString(font, "!", x + (SIZE - font.width("!")) / 2 + 1, y + 1,
                warning ? EditorTheme.WARNING : EditorTheme.TEXT_DIM, false);
    }

    /**
     * 鼠标在不在标记上
     */
    public static boolean hovered(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    /**
     * 画悬停说明（按宽度折行；只在鼠标停在标记上时调用）
     * <p>
     * 交给界面在这一帧最后画（Screen#setTooltipForNextRenderPass），不然会被之后才画的预览栏之类盖住；没有界面时直接画。
     *
     * @param g      画布
     * @param font   字体
     * @param lines  {@link #tooltip} 的结果
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    public static void renderTooltip(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull List<Component> lines,
                                     int mouseX, int mouseY) {
        List<Component> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, 240));
        }
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof Screen) {
            ((Screen) screen).setTooltipForNextRenderPass(wrapped);
        } else {
            g.renderTooltip(font, wrapped, mouseX, mouseY);
        }
    }

    /**
     * 悬停说明
     *
     * @param lossy    表达不了的东西
     * @param modified 改动过没有
     * @return 各行
     */
    @Nonnull
    public static List<Component> tooltip(@Nonnull Set<LegacyComponents.Lossy> lossy, boolean modified) {
        List<Component> lines = new ArrayList<>();
        if (!lossy.isEmpty()) {
            lines.add(Component.translatable("gui.battlecorrection.editor.lossy.header")
                    .withStyle(modified ? TextFormatting.YELLOW : TextFormatting.GRAY));
            for (LegacyComponents.Lossy item : lossy) {
                lines.add(Component.literal(" · ").append(Component.translatable(item.translationKey()))
                        .withStyle(TextFormatting.WHITE));
            }
            lines.add(Component.translatable(modified ? "gui.battlecorrection.editor.lossy.lost"
                    : "gui.battlecorrection.editor.lossy.kept").withStyle(modified ? TextFormatting.GOLD : TextFormatting.GRAY));
        }
        return lines;
    }
}
