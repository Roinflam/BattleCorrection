package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * "有未保存的修改，确定关闭？"确认框
 * <p>
 * 单独一个小界面：编辑器实例还在，点"返回编辑"就把它设回去（页面会按会话里的物品副本重建，改动一样不少）；
 * 点"放弃修改"让编辑器直接关掉回到父界面。Esc 等于返回编辑。
 */
public class ConfirmCloseScreen extends Screen {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 84;
    private static final int BUTTON_WIDTH = 92;
    private static final int BUTTON_HEIGHT = 16;

    private final ItemEditorScreen editor;
    private int boxX;
    private int boxY;

    /**
     * @param editor 要关的编辑器
     */
    public ConfirmCloseScreen(@Nonnull ItemEditorScreen editor) {
        super(Component.translatable("gui.battlecorrection.editor.unsaved.title"));
        this.editor = editor;
    }

    @Override
    protected void init() {
        boxX = (width - WIDTH) / 2;
        boxY = (height - HEIGHT) / 2;
        int buttonY = boxY + HEIGHT - BUTTON_HEIGHT - 10;
        addRenderableWidget(new FlatButton(boxX + WIDTH / 2 - BUTTON_WIDTH - 4, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.unsaved.discard"), editor::closeDiscard)
                .style(FlatButton.Style.DANGER));
        addRenderableWidget(new FlatButton(boxX + WIDTH / 2 + 4, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.unsaved.back"), this::backToEditor)
                .style(FlatButton.Style.PRIMARY));
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        EditorTheme.panel(g, boxX, boxY, WIDTH, HEIGHT);
        g.drawString(font, title, boxX + (WIDTH - font.width(title)) / 2, boxY + 12, EditorTheme.ACCENT_HI, false);
        EditorTheme.headingLine(g, boxX + 20, boxY + 23, WIDTH - 40);
        List<FormattedCharSequence> lines = font.split(Component.translatable("gui.battlecorrection.editor.unsaved.body"), WIDTH - 40);
        int lineY = boxY + 30;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, boxX + 20, lineY, EditorTheme.TEXT_MUTED, false);
            lineY += font.lineHeight + 1;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            backToEditor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void backToEditor() {
        if (minecraft != null) {
            minecraft.setScreen(editor);
        }
    }
}
