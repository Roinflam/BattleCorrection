package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 十六进制颜色输入框：6 位 RRGGBB，左边一个色块实时显示颜色
 * <p>
 * 只收十六进制数字，其他字符直接滤掉；粘贴的内容里认得出一个完整颜色（{@code #FFAA00}、{@code &#ffaa00}、
 * {@code §x§f§f§a§a§0§0}、{@code 0xFFAA00} 等，见 {@link #colorIn}）时整个替换（不然会被长度上限截断，拼成半截颜色）。
 * 不足 6 位时边框变红。
 * 在框里按回车 = 点旁边的"插入"。
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
     * @param initial  初始值（6 位十六进制，不合法就留空）
     * @param onChange 内容变化（参数是当前文本，可能不足 6 位）
     */
    public HexColorField(@Nonnull Font font, int x, int y, int width, int height, @Nonnull String initial,
                         @Nonnull Consumer<String> onChange) {
        // 输入框自己的边框画在 x-1..x+w+1，色块从 x 开始，整个控件正好占 [x, x + width)
        super(font, x + CHIP + CHIP_GAP + 1, y, width - CHIP - CHIP_GAP - 2, height, Component.literal("RRGGBB"), false);
        setMaxLength(6);
        setValue(digitsOf(initial, 6));
        setResponder(value -> {
            setError(!value.isEmpty() && value.length() != 6);
            onChange.accept(value);
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
     * @return RGB；不足 6 位时返回 -1
     */
    public int rgb() {
        String value = getValue();
        return value.length() == 6 ? Integer.parseInt(value, 16) : -1;
    }

    /**
     * 设成某个颜色
     *
     * @param rgb RGB
     */
    public void setRgb(int rgb) {
        setValue(LegacyText.hex(rgb));
    }

    @Override
    public void insertText(@Nonnull String text) {
        int rgb = colorIn(text);
        if (rgb >= 0) {
            setValue(LegacyText.hex(rgb));
            return;
        }
        super.insertText(digitsOf(text, Integer.MAX_VALUE));
    }

    /**
     * 从粘贴的文本里认出一个完整的颜色
     * <p>
     * 先按颜色码认（{@code &#RRGGBB}、{@code §x§R§R§G§G§B§B}、{@code &x&R&R...}），再找第一段连续 6 位以上的十六进制数字、
     * 取它的最后 6 位（{@code #FFAA00}、{@code 0xFFAA00}、带透明度的 {@code FFFFAA00} 都对）。
     * 不把所有数字拼起来取：{@code rgb #FFAA00} 里 rgb 的 b 也是十六进制数字，拼起来就错位了。
     *
     * @param text 粘贴的文本
     * @return RGB；认不出时返回 -1（按普通输入处理，只留数字）
     */
    static int colorIn(@Nonnull String text) {
        if (text.length() < 6) {
            return -1;
        }
        LegacyText.Scanner scanner = LegacyText.scanEditable(text);
        while (scanner.next()) {
            if (scanner.type() == LegacyText.TokenType.HEX) {
                return scanner.rgb();
            }
        }
        int run = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && LegacyText.isHexDigit(text.charAt(i))) {
                run++;
                continue;
            }
            if (run >= 6) {
                return Integer.parseInt(text.substring(i - 6, i), 16);
            }
            run = 0;
        }
        return -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (onSubmit != null && canConsumeInput()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
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
        if (rgb >= 0) {
            EditorTheme.chamfer(g, chipX + 1, chipY + 1, CHIP - 2, CHIP - 2, 1, 0xFF000000 | rgb);
        } else {
            EditorTheme.chamfer(g, chipX + 1, chipY + 1, CHIP - 2, CHIP - 2, 1, EditorTheme.WELL);
        }
    }

    /**
     * 只留十六进制数字（转成大写），最多 limit 个
     * <p>
     * {@code §x§f§f...} 这种写法里的 x 不是十六进制数字，正好被滤掉；{@code &#...} 的 & 和 # 同理。
     */
    @Nonnull
    private static String digitsOf(@Nonnull String text, int limit) {
        StringBuilder digits = new StringBuilder(Math.min(text.length(), 16));
        for (int i = 0; i < text.length() && digits.length() < limit; i++) {
            char c = text.charAt(i);
            if (LegacyText.isHexDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString().toUpperCase(Locale.ROOT);
    }
}
