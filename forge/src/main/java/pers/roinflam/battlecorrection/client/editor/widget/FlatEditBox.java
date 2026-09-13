package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;

/**
 * 单行输入框：原版 EditBox 换个边框颜色，再处理颜色码
 * <p>
 * 原版 EditBox 的文字排版、光标、选区逻辑都是私有的，没法重画；它画的边框是 x-1..x+w+1 那一圈 1px，
 * 这里在它画完之后用主题的沟槽色把那一圈盖一遍，再补上内阴影和焦点亮线，内部黑底保留（凹槽本来就该更深）。
 * <p>
 * legacyCodes 打开时，粘贴进来的 § 会先换成 &（原版 insertText 会把 § 过滤掉，不换就丢了）。
 * 默认最大长度放宽到 4096（原版默认只有 32，Lore 一行就超了）。
 */
public class FlatEditBox extends EditBox {

    /**
     * 默认最大长度
     */
    public static final int DEFAULT_MAX_LENGTH = 4096;

    private final boolean legacyCodes;

    /**
     * @param font        字体
     * @param x           左
     * @param y           上
     * @param width       宽
     * @param height      高
     * @param hint        空时显示的灰字提示
     * @param legacyCodes 是否是颜色码文本（粘贴时 § → &）
     */
    public FlatEditBox(@Nonnull Font font, int x, int y, int width, int height, @Nonnull Component hint,
                       boolean legacyCodes) {
        super(font, x, y, width, height, hint);
        this.legacyCodes = legacyCodes;
        setBordered(true);
        setHint(hint);
        setMaxLength(DEFAULT_MAX_LENGTH);
    }

    @Override
    public void insertText(@Nonnull String text) {
        super.insertText(legacyCodes ? LegacyText.fromSection(text) : text);
    }

    @Override
    public void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        if (!isVisible()) {
            return;
        }
        int x = getX() - 1;
        int y = getY() - 1;
        int w = getWidth() + 2;
        int h = getHeight() + 2;
        boolean focused = isFocused();
        // 盖掉原版的灰边：沟槽色外圈 + 上/左内阴影，焦点时整圈青色并在底边加一道亮线
        EditorTheme.wellOutline(g, x, y, w, h, focused ? EditorTheme.INPUT_BORDER_FOCUS : EditorTheme.INPUT_BORDER);
        EditorTheme.rect(g, x + 1, y + 1, w - 2, 1, EditorTheme.WELL_TOP);
        EditorTheme.rect(g, x + 1, y + 1, 1, h - 2, EditorTheme.WELL_TOP);
        if (focused) {
            EditorTheme.rect(g, x + 2, y + h - 1, Math.max(0, w - 4), 1, EditorTheme.ACCENT_HI);
        } else if (active) {
            EditorTheme.rect(g, x + 1, y + h - 2, w - 2, 1, EditorTheme.WELL_LIGHT);
        }
    }
}
