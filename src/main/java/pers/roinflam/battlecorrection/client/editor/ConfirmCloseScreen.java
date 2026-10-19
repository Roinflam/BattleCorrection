package pers.roinflam.battlecorrection.client.editor;

import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * "有未保存的修改，确定关闭？"确认框
 * <p>
 * 单独一个小界面：编辑器实例还在，点"返回编辑"就把它设回去（页面会按会话里的物品副本重建，改动一样不少）；
 * 点"放弃修改"让编辑器直接关掉回到父界面。Esc 等于返回编辑。
 * <p>
 * 框里还列出这次到底改了哪些地方（最多 {@link #MAX_LINES} 条，其余折成"还有 N 处"），
 * 玩家不用凭记忆判断丢掉的值不值。摘要在界面打开时算一次。
 */
public class ConfirmCloseScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int BASE_HEIGHT = 84;
    private static final int BUTTON_WIDTH = 92;
    private static final int BUTTON_HEIGHT = 16;
    private static final int MAX_LINES = 8;
    private static final int LINE_STEP = 10;

    private final ItemEditorScreen editor;
    private final ChangeSummary.Result summary;
    private int boxX;
    private int boxY;
    private int boxHeight;

    /**
     * @param editor 要关的编辑器
     */
    public ConfirmCloseScreen(@Nonnull ItemEditorScreen editor) {
        super(Component.translatable("gui.battlecorrection.editor.unsaved.title"));
        this.editor = editor;
        this.summary = editor.session().changeSummary(MAX_LINES);
    }

    @Override
    protected void init() {
        int listHeight = summary.isEmpty() ? 0 : 14 + (summary.entries().size() + (summary.total() > summary.entries().size() ? 1 : 0)) * LINE_STEP;
        boxHeight = BASE_HEIGHT + listHeight;
        boxX = (width - WIDTH) / 2;
        boxY = (height - boxHeight) / 2;
        int buttonY = boxY + boxHeight - BUTTON_HEIGHT - 10;
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
        EditorTheme.panel(g, boxX, boxY, WIDTH, boxHeight);
        g.drawString(font, title, boxX + (WIDTH - font.width(title)) / 2, boxY + 12, EditorTheme.ACCENT_HI, false);
        EditorTheme.headingLine(g, boxX + 20, boxY + 23, WIDTH - 40);
        List<Component> lines = font.split(Component.translatable("gui.battlecorrection.editor.unsaved.body"), WIDTH - 40);
        int lineY = boxY + 30;
        for (Component line : lines) {
            g.drawString(font, line, boxX + 20, lineY, EditorTheme.TEXT_MUTED, false);
            lineY += font.lineHeight + 1;
        }

        if (!summary.isEmpty()) {
            lineY += 4;
            g.drawString(font, Component.translatable("gui.battlecorrection.editor.unsaved.changes", summary.total()),
                    boxX + 20, lineY, EditorTheme.TEXT_DIM, false);
            lineY += LINE_STEP + 4;
            for (ChangeSummary.Entry entry : summary.entries()) {
                int color;
                switch (entry.mark()) {
                    case '+':
                        color = EditorTheme.SUCCESS;
                        break;
                    case '-':
                        color = EditorTheme.DANGER;
                        break;
                    default:
                        color = EditorTheme.WARNING;
                        break;
                }
                g.drawString(font, String.valueOf(entry.mark()), boxX + 22, lineY, color, false);
                g.drawString(font, font.plainSubstrByWidth(entry.label(), WIDTH - 66), boxX + 34, lineY, EditorTheme.TEXT, false);
                lineY += LINE_STEP;
            }
            int more = summary.total() - summary.entries().size();
            if (more > 0) {
                g.drawString(font, Component.translatable("gui.battlecorrection.editor.unsaved.more", more),
                        boxX + 34, lineY, EditorTheme.TEXT_DIM, false);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.KEY_ESCAPE) {
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
            minecraft.displayGuiScreen(editor);
        }
    }
}
