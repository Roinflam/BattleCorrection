package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;

/**
 * 多行输入框：原版 MultiLineEditBox 换个边框颜色
 * <p>
 * 原版的多行框自带滚动、换行、选区、复制粘贴，这些都不用自己写。
 * 它的边框由 AbstractScrollWidget#renderBorder 画（先整块填边框色，再往里缩 1px 填黑），
 * 这里重写这一个方法换成主题色。
 */
public class FlatMultiLineEditBox extends MultiLineEditBox {

    /**
     * @param font        字体
     * @param x           左
     * @param y           上
     * @param width       宽
     * @param height      高
     * @param placeholder 空时显示的灰字提示
     * @param message     无障碍朗读用的名字
     */
    public FlatMultiLineEditBox(@Nonnull Font font, int x, int y, int width, int height,
                                @Nonnull Component placeholder, @Nonnull Component message) {
        super(font, x, y, width, height, placeholder, message);
    }

    @Override
    protected void renderBorder(@Nonnull GuiGraphics g, int x, int y, int width, int height) {
        EditorTheme.well(g, x, y, width, height, true);
        if (isFocused()) {
            EditorTheme.wellOutline(g, x, y, width, height, EditorTheme.INPUT_BORDER_FOCUS);
        }
    }
}
