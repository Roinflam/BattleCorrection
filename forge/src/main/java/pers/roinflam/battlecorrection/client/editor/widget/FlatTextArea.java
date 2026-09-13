package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * 多行文本框（原始 NBT 页用）
 * <p>
 * 文字逻辑（光标、选区、增删、复制粘贴、换行）全部交给原版的 {@link MultilineTextField}，这里只负责画和接鼠标键盘。
 * 不直接用原版的 MultiLineEditBox 是因为它的鼠标处理有问题：选了一大段之后再点一下，选区不消，得按键盘才清；
 * 而它内部那个文本对象是私有的，子类没法修，只能自己包一层。这里点一下 = 光标移过去、选区清掉；按住拖 = 选；Shift+点 = 扩选。
 * <p>
 * 外观走主题的凹槽样式：沟槽 + 内阴影 + 扫描线，焦点时外圈变青。选区用半透明青色盖在文字上。
 */
public class FlatTextArea extends AbstractScrollWidget {

    private final Font font;
    private final MultilineTextField textField;
    private final Component placeholder;

    /**
     * @param font        字体
     * @param x           左
     * @param y           上
     * @param width       宽
     * @param height      高
     * @param placeholder 空时显示的灰字提示
     * @param message     无障碍朗读用的名字
     */
    public FlatTextArea(@Nonnull Font font, int x, int y, int width, int height, @Nonnull Component placeholder,
                        @Nonnull Component message) {
        super(x, y, width, height, message);
        this.font = font;
        this.placeholder = placeholder;
        this.textField = new MultilineTextField(font, width - totalInnerPadding());
        this.textField.setCursorListener(this::scrollToCursor);
    }

    /**
     * 内容变化时的回调
     *
     * @param listener 回调
     */
    public void setValueListener(@Nonnull Consumer<String> listener) {
        textField.setValueListener(listener);
    }

    /**
     * 整体替换内容（会触发回调，光标移到末尾）
     *
     * @param value 新内容
     */
    public void setValue(@Nonnull String value) {
        textField.setValue(value);
    }

    /**
     * 当前内容
     *
     * @return 文本
     */
    @Nonnull
    public String getValue() {
        return textField.value();
    }

    // ═══════════════════════════════════════════════════════════════
    // 输入
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) {
            return false;
        }
        // 先让父类处理滚动条拖拽；它对内容区的点击也可能返回 true，所以不能靠它的返回值决定要不要移光标
        boolean onScrollbar = scrollbarVisible() && mouseX >= getX() + width - 8 && mouseX <= getX() + width
                && mouseY >= getY() && mouseY < getY() + height;
        if (onScrollbar) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && withinContentAreaPoint(mouseX, mouseY)) {
            // Shift+点 = 从原光标扩选到点击处；普通点 = 光标移过去并清掉选区
            textField.setSelecting(Screen.hasShiftDown());
            seekCursorScreen(mouseX, mouseY);
            textField.setSelecting(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (button == 0 && visible && isFocused()) {
            textField.setSelecting(true);
            seekCursorScreen(mouseX, mouseY);
            textField.setSelecting(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !isFocused()) {
            return false;
        }
        return textField.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !isFocused() || !SharedConstants.isAllowedChatCharacter(codePoint)) {
            return false;
        }
        textField.insertText(Character.toString(codePoint));
        return true;
    }

    /**
     * 把屏幕坐标换成文本框内坐标交给文本对象定位光标
     */
    private void seekCursorScreen(double mouseX, double mouseY) {
        double localX = mouseX - getX() - innerPadding();
        double localY = mouseY - getY() - innerPadding() + scrollAmount();
        textField.seekCursorToPoint(localX, localY);
    }

    /**
     * 光标移出可视范围时把滚动位置跟过去
     */
    private void scrollToCursor() {
        int lineHeight = font.lineHeight;
        int cursorLine = textField.getLineAtCursor();
        double top = cursorLine * lineHeight;
        double bottom = top + lineHeight;
        double visibleTop = scrollAmount();
        double visibleBottom = visibleTop + height - totalInnerPadding();
        if (top < visibleTop) {
            setScrollAmount(top);
        } else if (bottom > visibleBottom) {
            setScrollAmount(bottom - (height - totalInnerPadding()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 绘制
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected int getInnerHeight() {
        return font.lineHeight * textField.getLineCount();
    }

    @Override
    protected double scrollRate() {
        return font.lineHeight / 2.0D;
    }

    @Override
    protected void renderBorder(@Nonnull GuiGraphics g, int x, int y, int width, int height) {
        EditorTheme.well(g, x, y, width, height, true);
        if (isFocused()) {
            EditorTheme.wellOutline(g, x, y, width, height, EditorTheme.INPUT_BORDER_FOCUS);
        }
    }

    @Override
    protected void renderContents(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String value = textField.value();
        int left = getX() + innerPadding();
        int lineHeight = font.lineHeight;

        if (value.isEmpty() && !isFocused()) {
            g.drawWordWrap(font, placeholder, left, getY() + innerPadding(), width - totalInnerPadding(), EditorTheme.TEXT_DIM);
            return;
        }

        int cursor = textField.cursor();
        boolean cursorVisible = isFocused() && (Util.getMillis() / 500L) % 2L == 0L;
        boolean cursorInsideText = cursor < value.length();
        int cursorX = left;
        int lastLineY = getY() + innerPadding();
        int lineY = getY() + innerPadding();

        for (MultilineTextField.StringView line : textField.iterateLines()) {
            boolean lineVisible = withinContentAreaTopBottom(lineY, lineY + lineHeight);
            boolean cursorOnLine = cursorVisible && cursorInsideText && cursor >= line.beginIndex() && cursor <= line.endIndex();
            if (lineVisible) {
                if (cursorOnLine) {
                    cursorX = g.drawString(font, value.substring(line.beginIndex(), cursor), left, lineY, EditorTheme.TEXT, false);
                    g.fill(cursorX, lineY - 1, cursorX + 1, lineY + lineHeight, EditorTheme.ACCENT_HI);
                    g.drawString(font, value.substring(cursor, line.endIndex()), cursorX, lineY, EditorTheme.TEXT, false);
                } else {
                    cursorX = g.drawString(font, value.substring(line.beginIndex(), line.endIndex()), left, lineY, EditorTheme.TEXT, false);
                }
            }
            lastLineY = lineY;
            lineY += lineHeight;
        }
        // 光标在文本末尾：画在最后一行末尾
        if (cursorVisible && !cursorInsideText && withinContentAreaTopBottom(lastLineY, lastLineY + lineHeight)) {
            g.fill(cursorX, lastLineY - 1, cursorX + 1, lastLineY + lineHeight, EditorTheme.ACCENT_HI);
        }

        // 选区：半透明青色盖在文字上
        MultilineTextField.StringView selected = textField.getSelected();
        if (selected.beginIndex() != selected.endIndex()) {
            int selectionY = getY() + innerPadding();
            for (MultilineTextField.StringView line : textField.iterateLines()) {
                if (selected.beginIndex() > line.endIndex()) {
                    selectionY += lineHeight;
                    continue;
                }
                if (line.beginIndex() > selected.endIndex()) {
                    break;
                }
                if (withinContentAreaTopBottom(selectionY, selectionY + lineHeight)) {
                    int startX = font.width(value.substring(line.beginIndex(), Math.max(selected.beginIndex(), line.beginIndex())));
                    int endX = selected.endIndex() > line.endIndex()
                            ? width - innerPadding()
                            : font.width(value.substring(line.beginIndex(), selected.endIndex()));
                    g.fill(left + startX, selectionY, left + endX, selectionY + lineHeight, EditorTheme.ACCENT_SOFT);
                }
                selectionY += lineHeight;
            }
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
