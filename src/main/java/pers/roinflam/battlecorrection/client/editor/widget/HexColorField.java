package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.util.text.TextFormatting;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.editor.ColorMath;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 颜色输入框：一位颜色代码（{@code 0-9}、{@code a-f}，就是 {@code &6} 里的 6），左边一个色块实时显示颜色
 * <p>
 * 1.12.2 的文字只有原版 16 色，所以这里只收一位代码。打一个代码字符就换成它（不用先删旧的）；
 * 粘贴的内容里认得出一个颜色时也换成它（见 {@link #colorIn}）：{@code &6} / 分节号写法、颜色名（{@code gold}）、
 * 一位代码，以及 {@code #FFAA00} 这类十六进制颜色——后者换成最接近的原版颜色（按 CIELAB 色差，{@link ColorMath#nearestLegacy}），
 * 写进文字的永远是原版颜色码。
 * 空着时色块边框是红的，空着点"插入"或"渐变"时输入框边框也变红。在框里按回车 = 点旁边的"插入"。
 */
public class HexColorField extends FlatEditBox {

    /**
     * 左侧色块边长
     */
    public static final int CHIP = 10;

    /**
     * 色块和输入框之间的空隙
     */
    private static final int CHIP_GAP = 3;

    @Nullable
    private Runnable onSubmit;

    /**
     * @param font     字体
     * @param x        左（色块的左边）
     * @param y        上
     * @param width    总宽（含色块）
     * @param height   高
     * @param initial  初始颜色（不是颜色就留空）
     * @param onChange 内容变化（参数是当前颜色；空着时为 null）
     */
    public HexColorField(@Nonnull Font font, int x, int y, int width, int height, @Nullable TextFormatting initial,
                         @Nonnull Consumer<TextFormatting> onChange) {
        // 输入框自己的边框画在 x-1..x+w+1，色块从 x 开始，整个控件正好占 [x, x + width)。
        // 框里只放得下一个字，不写灰字提示（会画到框外面去），说明在悬停提示里
        super(font, x + CHIP + CHIP_GAP + 1, y, width - CHIP - CHIP_GAP - 2, height, Component.empty(), false);
        setMaxLength(1);
        setColor(initial);
        setResponder(value -> {
            // 有了颜色就清掉"插入时是空的"留下的红框
            if (!value.isEmpty()) {
                setError(false);
            }
            onChange.accept(color());
        });
    }

    /**
     * 回车时的动作（链式）
     *
     * @param action 动作
     * @return this
     */
    @Nonnull
    public HexColorField onSubmit(@Nonnull Runnable action) {
        this.onSubmit = action;
        return this;
    }

    /**
     * 挪到新位置
     *
     * @param left 控件左边（色块的左边）
     * @param top  上
     */
    public void moveTo(int left, int top) {
        setX(left + CHIP + CHIP_GAP + 1);
        setY(top);
    }

    /**
     * 当前颜色
     *
     * @return 原版颜色；空着时为 null
     */
    @Nullable
    public TextFormatting color() {
        String value = getValue();
        if (value.length() != 1) {
            return null;
        }
        TextFormatting formatting = LegacyText.codeOf(value.charAt(0));
        return formatting != null && formatting.isColor() ? formatting : null;
    }

    /**
     * 当前颜色的 RGB
     *
     * @return RGB；空着时返回 -1
     */
    public int rgb() {
        TextFormatting color = color();
        return color == null ? -1 : ColorMath.legacyRgb(color);
    }

    /**
     * 设成某个颜色
     *
     * @param color 原版颜色（null 或不是颜色 = 清空）
     */
    public void setColor(@Nullable TextFormatting color) {
        setValue(color == null || !color.isColor() ? "" : String.valueOf(LegacyText.codeChar(color)));
    }

    /**
     * 设成离这个颜色最近的原版颜色
     *
     * @param rgb RGB
     */
    public void setRgb(int rgb) {
        setColor(ColorMath.nearestLegacy(rgb));
    }

    @Override
    public void insertText(@Nonnull String text) {
        // 只有一位：认出颜色就整个换掉，认不出就不动（不会因为长度上限打不进去）
        TextFormatting color = colorIn(text);
        if (color != null) {
            setColor(color);
        }
    }

    /**
     * 从键入或粘贴的文本里认出一个颜色
     * <p>
     * 依次试：一位代码字符；{@code &6} / 分节号写法的颜色码（取第一个）；颜色名（{@code gold}、{@code dark_blue}，不分大小写）；
     * 一段连续 6 位以上的十六进制数字，取最后 6 位（{@code #FFAA00}、{@code 0xFFAA00}、带透明度的 {@code FFFFAA00} 都对），
     * 换成最接近的原版颜色。
     *
     * @param text 文本
     * @return 原版颜色；认不出时返回 null
     */
    @Nullable
    static TextFormatting colorIn(@Nonnull String text) {
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() == 1) {
            TextFormatting formatting = LegacyText.codeOf(value.charAt(0));
            return formatting != null && formatting.isColor() ? formatting : null;
        }
        LegacyText.Scanner scanner = LegacyText.scanEditable(value);
        while (scanner.next()) {
            if (scanner.type() == LegacyText.TokenType.COLOR) {
                return scanner.format();
            }
        }
        TextFormatting named = TextFormatting.getValueByName(value.toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (named != null && named.isColor()) {
            return named;
        }
        int run = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i < value.length() && LegacyText.isHexDigit(value.charAt(i))) {
                run++;
                continue;
            }
            if (run >= 6) {
                return ColorMath.nearestLegacy(Integer.parseInt(value.substring(i - 6, i), 16));
            }
            run = 0;
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (onSubmit != null && canConsumeInput()
                && (keyCode == Keys.KEY_ENTER || keyCode == Keys.KEY_KP_ENTER)) {
            onSubmit.run();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        if (!isVisible()) {
            return;
        }
        int chipX = getX() - 1 - CHIP_GAP - CHIP;
        int chipY = getY() + (getHeight() - CHIP) / 2;
        int rgb = rgb();
        EditorTheme.chamfer(g, chipX, chipY, CHIP, CHIP, 2, rgb < 0 ? EditorTheme.DANGER : EditorTheme.BUTTON_BORDER);
        EditorTheme.chamfer(g, chipX + 1, chipY + 1, CHIP - 2, CHIP - 2, 1, rgb < 0 ? EditorTheme.WELL : 0xFF000000 | rgb);
    }
}
