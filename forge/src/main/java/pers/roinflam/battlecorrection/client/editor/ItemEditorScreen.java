package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.page.AttributesPage;
import pers.roinflam.battlecorrection.client.editor.page.CuriosPage;
import pers.roinflam.battlecorrection.client.editor.page.EditorPageBase;
import pers.roinflam.battlecorrection.client.editor.page.EnchantmentsPage;
import pers.roinflam.battlecorrection.client.editor.page.LorePage;
import pers.roinflam.battlecorrection.client.editor.page.NamePage;
import pers.roinflam.battlecorrection.client.editor.page.NbtTreePage;
import pers.roinflam.battlecorrection.client.editor.page.PlayerSlotsPage;
import pers.roinflam.battlecorrection.client.editor.page.RawNbtPage;
import pers.roinflam.battlecorrection.client.editor.page.TagsPage;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.TabButton;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.network.ModNetwork;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 物品编辑器主界面
 * <p>
 * 布局（居中的深色面板）：
 * <pre>
 * ┌──────────────────────────────────────────────┐
 * │ [图标] 物品名          物品编辑器              │  标题栏
 * ├────────┬─────────────────────────┬───────────┤
 * │ 名字   │                         │  预览      │
 * │ Lore   │       当前页面内容        │ （宽度够   │
 * │ 标签   │                         │  才显示）  │
 * │ ...    │                         │           │
 * ├────────┴─────────────────────────┴───────────┤
 * │                              [取消] [保存]    │  底栏
 * └──────────────────────────────────────────────┘
 * </pre>
 * 页面只创建一次，切换标签时把上一页的控件摘掉、下一页重新 init 并挂上控件。
 * 窗口大小变化时原版会重新调 init()，这里重新算布局再把当前页挂一遍。
 * 列表类页面（附魔、属性）会要求收起预览栏（{@link EditorPageBase#wantsPreview()}），切页时重新算布局。
 * <p>
 * 保存：先给当前页一个 beforeLeave 的机会（原始 NBT 页要解析），再清理空标签，把副本的 NBT 和数量发给服务端，关界面。
 * Esc 或"取消"直接关，什么都不发。Ctrl+S = 保存。
 */
public class ItemEditorScreen extends Screen {

    private static final int PANEL_MAX_WIDTH = 520;
    private static final int PANEL_MAX_HEIGHT = 280;
    private static final int MARGIN = 8;
    private static final int TITLE_HEIGHT = 24;
    private static final int SIDEBAR_WIDTH = 86;
    private static final int FOOTER_HEIGHT = 28;
    private static final int PREVIEW_WIDTH = 132;
    private static final int PREVIEW_MIN_PANEL_WIDTH = 470;
    private static final int PAD = 6;
    private static final int FOOTER_BUTTON_WIDTH = 64;
    private static final int FOOTER_BUTTON_HEIGHT = 16;

    private final EditorSession session;
    @Nullable
    private final Screen parent;
    private final List<EditorPageBase> pages = new ArrayList<>();
    private final List<TabButton> tabs = new ArrayList<>();
    private final List<AbstractWidget> pageWidgets = new ArrayList<>();
    private EditorPageBase current;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int previewX;
    private boolean previewVisible;

    /**
     * @param session 编辑会话
     * @param initial 先显示哪一页；未实现的页回落到第一页
     * @param parent  关闭后回到的界面（从背包里打开时是背包界面），null = 回到游戏
     */
    public ItemEditorScreen(@Nonnull EditorSession session, @Nonnull EditorPage initial, @Nullable Screen parent) {
        super(Component.translatable("gui.battlecorrection.editor.title"));
        this.session = session;
        this.parent = parent;

        pages.add(new NamePage(this, session));
        pages.add(new LorePage(this, session));
        pages.add(new TagsPage(this, session));
        pages.add(new EnchantmentsPage(this, session));
        pages.add(new AttributesPage(this, session));
        // 饰品栏相关的两页只在装了 Curios 时才有，没装连标签都不显示
        if (CuriosIntegration.isCuriosLoaded()) {
            pages.add(new CuriosPage(this, session));
        }
        pages.add(new NbtTreePage(this, session));
        pages.add(new RawNbtPage(this, session));
        if (CuriosIntegration.isCuriosLoaded()) {
            pages.add(new PlayerSlotsPage(this, session));
        }

        EditorPageBase start = findPage(initial);
        this.current = start != null ? start : pages.get(0);
    }

    /**
     * 编辑会话
     *
     * @return 会话
     */
    @Nonnull
    public EditorSession session() {
        return session;
    }

    @Override
    protected void init() {
        layout();
        clearWidgets();
        tabs.clear();
        pageWidgets.clear();

        // 左侧标签页：按页面顺序排；这次没创建的页（例如没装 Curios 时的饰品栏页）不显示标签
        int tabY = panelY + TITLE_HEIGHT + 2;
        for (EditorPageBase page : pages) {
            TabButton tab = new TabButton(panelX + 1, tabY, SIDEBAR_WIDTH - 1, page.title(), () -> switchPage(page));
            tabs.add(tab);
            addRenderableWidget(tab);
            tabY += TabButton.HEIGHT;
        }

        // 底栏按钮
        int buttonY = panelY + panelHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - FOOTER_BUTTON_HEIGHT) / 2;
        int saveX = panelX + panelWidth - PAD - FOOTER_BUTTON_WIDTH;
        int cancelX = saveX - 4 - FOOTER_BUTTON_WIDTH;
        addRenderableWidget(new FlatButton(cancelX, buttonY, FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.cancel"), this::onClose));
        addRenderableWidget(new FlatButton(saveX, buttonY, FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.save"), this::save)
                .style(FlatButton.Style.PRIMARY)
                .tooltip(Component.translatable("gui.battlecorrection.editor.save.tooltip")));

        mountPage(current);
    }

    /**
     * 按窗口大小算面板和各区域的位置
     */
    private void layout() {
        panelWidth = Math.min(width - 2 * MARGIN, PANEL_MAX_WIDTH);
        panelHeight = Math.min(height - 2 * MARGIN, PANEL_MAX_HEIGHT);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        previewVisible = panelWidth >= PREVIEW_MIN_PANEL_WIDTH && current.wantsPreview();
        int previewWidth = previewVisible ? PREVIEW_WIDTH : 0;

        contentX = panelX + SIDEBAR_WIDTH + PAD;
        contentY = panelY + TITLE_HEIGHT + PAD;
        contentWidth = panelWidth - SIDEBAR_WIDTH - previewWidth - (previewVisible ? 3 : 2) * PAD;
        contentHeight = panelHeight - TITLE_HEIGHT - FOOTER_HEIGHT - 2 * PAD;
        previewX = panelX + panelWidth - PAD - previewWidth;
    }

    /**
     * 找已实现的页面
     *
     * @param page 页面枚举
     * @return 页面实例，未实现返回 null
     */
    @Nullable
    private EditorPageBase findPage(@Nonnull EditorPage page) {
        for (EditorPageBase candidate : pages) {
            if (candidate.page() == page) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 挂载一个页面：摘掉旧控件，init 新页面，挂上新控件
     *
     * @param page 页面
     */
    private void mountPage(@Nonnull EditorPageBase page) {
        for (AbstractWidget widget : pageWidgets) {
            removeWidget(widget);
        }
        pageWidgets.clear();
        setFocused(null);

        current = page;
        // 内容区宽度取决于这一页要不要预览栏，切页后重算
        layout();
        page.init(contentX, contentY, contentWidth, contentHeight);
        for (AbstractWidget widget : page.widgets()) {
            pageWidgets.add(widget);
            addRenderableWidget(widget);
        }
        for (TabButton tab : tabs) {
            tab.setSelected(false);
        }
        int index = pages.indexOf(page);
        if (index >= 0 && index < tabs.size()) {
            tabs.get(index).setSelected(true);
        }
    }

    /**
     * 切换页面
     *
     * @param page 目标页面
     */
    public void switchPage(@Nonnull EditorPageBase page) {
        if (page == current) {
            return;
        }
        if (!current.beforeLeave()) {
            return;
        }
        mountPage(page);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // 面板：切角 + 斜面 + 四角准线，边框上一颗青光巡游
        EditorTheme.panel(g, panelX, panelY, panelWidth, panelHeight);
        EditorTheme.traceBorder(g, panelX + 2, panelY + 2, panelWidth - 4, panelHeight - 4);

        int bodyTop = panelY + TITLE_HEIGHT;
        int bodyBottom = panelY + panelHeight - FOOTER_HEIGHT;
        // 左侧标签栏压暗一档，和内容区之间一条竖线
        EditorTheme.rect(g, panelX + 3, bodyTop, SIDEBAR_WIDTH - 2, bodyBottom - bodyTop, EditorTheme.SIDEBAR_BG);
        EditorTheme.rect(g, panelX + SIDEBAR_WIDTH, bodyTop, 1, bodyBottom - bodyTop, EditorTheme.DIVIDER);
        // 标题栏下一道从亮到暗的强调线，底栏上一道分割线
        EditorTheme.headingLine(g, panelX + 4, bodyTop, panelWidth - 8);
        EditorTheme.rect(g, panelX + 4, bodyBottom, panelWidth - 8, 1, EditorTheme.DIVIDER);

        // 标题栏：切角小槽里放图标 + 当前名字 + 界面标题
        ItemStack working = session.working();
        int iconX = panelX + PAD + 2;
        int iconY = panelY + (TITLE_HEIGHT - 16) / 2;
        EditorTheme.chamfer(g, iconX - 2, iconY - 2, 20, 20, 2, EditorTheme.ACCENT_MID);
        EditorTheme.chamfer(g, iconX - 1, iconY - 1, 18, 18, 1, EditorTheme.WELL);
        g.renderItem(working, iconX, iconY);
        g.renderItemDecorations(font, working, iconX, iconY);
        int nameX = iconX + 24;
        int nameMaxWidth = panelWidth - (nameX - panelX) - font.width(title) - 3 * PAD;
        g.enableScissor(nameX, panelY, nameX + Math.max(0, nameMaxWidth), panelY + TITLE_HEIGHT);
        g.drawString(font, working.getHoverName(), nameX, panelY + (TITLE_HEIGHT - 8) / 2, EditorTheme.ACCENT_HI, false);
        g.disableScissor();
        // 右侧界面标题前面一颗呼吸的指示点
        int titleX = panelX + panelWidth - PAD - 4 - font.width(title);
        g.drawString(font, title, titleX, panelY + (TITLE_HEIGHT - 8) / 2, EditorTheme.TEXT_MUTED, false);
        int dot = EditorTheme.lerp(EditorTheme.ACCENT_MID, EditorTheme.ACCENT_HI, EditorTheme.pulse());
        EditorTheme.rect(g, titleX - 7, panelY + TITLE_HEIGHT / 2 - 1, 3, 3, dot);

        // 页面 → 控件 → 页面前景
        current.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        current.renderForeground(g, mouseX, mouseY, partialTick);

        renderPreview(g);

        // 悬停标题图标：显示完整提示框（预览栏没显示时靠这个看效果）
        if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
            g.renderTooltip(font, working, mouseX, mouseY);
        }
    }

    /**
     * 右侧预览栏：大图标 + 实时的物品提示框内容
     *
     * @param g 画布
     */
    private void renderPreview(@Nonnull GuiGraphics g) {
        if (!previewVisible) {
            return;
        }
        int top = contentY;
        int bottom = contentY + contentHeight;
        EditorTheme.card(g, previewX, top, PREVIEW_WIDTH, contentHeight);
        Component previewTitle = Component.translatable("gui.battlecorrection.editor.preview_pane");
        g.drawString(font, previewTitle, previewX + 6, top + 5, EditorTheme.TEXT_MUTED, false);
        EditorTheme.headingLine(g, previewX + 6, top + 15, PREVIEW_WIDTH - 12);

        ItemStack working = session.working();
        // 图标放大两倍居中，底下垫一块切角托底
        int iconSize = 32;
        int iconX = previewX + (PREVIEW_WIDTH - iconSize) / 2;
        int iconY = top + 22;
        EditorTheme.chamfer(g, iconX - 4, iconY - 4, iconSize + 8, iconSize + 8, 3, EditorTheme.ACCENT_MID);
        EditorTheme.chamfer(g, iconX - 3, iconY - 3, iconSize + 6, iconSize + 6, 2, EditorTheme.WELL);
        g.pose().pushPose();
        g.pose().translate(iconX, iconY, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.renderItem(working, 0, 0);
        g.renderItemDecorations(font, working, 0, 0);
        g.pose().popPose();

        // 提示框各行，超宽自动换行，超高裁掉
        int textX = previewX + 5;
        int textY = iconY + iconSize + 6;
        int textWidth = PREVIEW_WIDTH - 10;
        g.enableScissor(previewX + 1, textY, previewX + PREVIEW_WIDTH - 1, bottom - 1);
        List<Component> lines = working.getTooltipLines(minecraft == null ? null : minecraft.player, TooltipFlag.Default.NORMAL);
        for (Component line : lines) {
            for (FormattedCharSequence wrapped : font.split(line, textWidth)) {
                if (textY > bottom) {
                    break;
                }
                g.drawString(font, wrapped, textX, textY, EditorTheme.TEXT, false);
                textY += font.lineHeight + 1;
            }
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (current.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (current.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (current.onEscape()) {
                return true;
            }
            onClose();
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        return current.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        // 颜色工具条这类"不抢焦点"的按钮点了之后，焦点要留在输入框里
        if (listener instanceof FlatButton button && button.keepsFocus()) {
            return;
        }
        super.setFocused(listener);
    }

    @Override
    public void tick() {
        current.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /**
     * 保存：交给服务端校验并写回，然后关界面
     */
    private void save() {
        if (!current.beforeLeave()) {
            return;
        }
        session.cleanupEmpty();
        ItemStack working = session.working();
        ModNetwork.sendToServer(new ApplyItemEditC2SPacket(session.target(), session.itemId(),
                working.getTag() == null ? null : working.getTag().copy(), working.getCount()));
        onClose();
    }
}
