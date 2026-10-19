package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import pers.roinflam.battlecorrection.client.editor.page.AttributesPage;
import pers.roinflam.battlecorrection.client.editor.page.CuriosPage;
import pers.roinflam.battlecorrection.client.editor.page.EditorPageBase;
import pers.roinflam.battlecorrection.client.editor.page.EnchantmentsPage;
import pers.roinflam.battlecorrection.client.editor.page.LorePage;
import pers.roinflam.battlecorrection.client.editor.page.NamePage;
import pers.roinflam.battlecorrection.client.editor.page.NbtTreePage;
import pers.roinflam.battlecorrection.client.editor.page.RawNbtPage;
import pers.roinflam.battlecorrection.client.editor.page.TagsPage;
import pers.roinflam.battlecorrection.client.editor.page.TemplatesPage;
import pers.roinflam.battlecorrection.client.editor.ui.AbstractWidget;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiEventListener;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;
import pers.roinflam.battlecorrection.client.editor.ui.Ui;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.TabButton;
import pers.roinflam.battlecorrection.compat.BaublesIntegration;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;
import pers.roinflam.battlecorrection.network.packet.ApplyNbtEditC2SPacket;
import pers.roinflam.battlecorrection.network.packet.ChunkedPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
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
 * 窗口大小变化时原版会重新调 initGui，这里重新算布局再把当前页挂一遍。
 * 列表类页面（附魔、属性）会要求收起预览栏（{@link EditorPageBase#wantsPreview()}），切页时重新算布局。
 * <p>
 * 保存：先给当前页一个 beforeLeave 的机会（SNBT 页要解析），再清理空标签，把副本的 NBT、数量、损害值和物品 ID 发给服务端，关界面。
 * Esc 或"取消"直接关，什么都不发。Ctrl+S = 保存。
 * <p>
 * 其它顺手的功能：
 * <ul>
 *   <li>点标题栏的物品图标（或 F4）换成别的物品，见 {@link ItemPickerScreen}。
 *   不用 F2：它是原版的截图键，界面里按也会截图</li>
 *   <li>底栏"导入"：把剪贴板里的 /give 命令或物品 SNBT 整件导入，见 {@link ItemImport}</li>
 *   <li>Ctrl+Tab / Ctrl+Shift+Tab 切页，Ctrl+1..9 直达第 N 页，F1 快捷键速查</li>
 *   <li>标题带 * = 有未保存的改动；底栏显示这份 NBT 序列化后的大小，快到原版客户端的承受上限时变色</li>
 * </ul>
 * 性能上：撤销用的"内容有没有变"检测只在有键鼠输入的前后几个 tick 里做（闲着每秒才兜底查一次），
 * 右侧预览的提示框行按内容版本号缓存，没改动就不重新生成。
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
    /**
     * 面板四角的准线角饰占的宽度，标题栏和底栏的内容从它里面让开
     */
    private static final int CORNER_CLEARANCE = 22;
    /**
     * 键鼠输入之后继续检测内容变化的 tick 数（页面可能晚一两个 tick 才把输入落到物品副本上）
     */
    private static final int INPUT_BOOST_TICKS = 4;
    /**
     * 没有输入时兜底检测的间隔（tick）
     */
    private static final int IDLE_POLL_TICKS = 20;
    /**
     * 提示条显示多久（tick）
     */
    private static final int FLASH_TICKS = 80;
    /**
     * 聊天栏一条消息的长度上限，超过的 /give 命令只能用命令方块
     */
    private static final int CHAT_LIMIT = 256;

    private final EditorSession session;
    private long tickCounter;
    /**
     * 已确认丢弃修改，关闭时不再弹确认
     */
    private boolean discardConfirmed;
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
    private int titleIconX;
    private int titleIconY;
    private int statusX;
    private int statusMaxX;
    private int statusY;

    /**
     * 还要继续检测内容变化的 tick 数，有键鼠输入时重置
     */
    private int inputBoost;
    private int modifiedRevision = -1;
    private boolean modifiedCache;

    /**
     * 预览提示框行的缓存：按（内容版本，文字宽度，Shift 是否按着）失效
     */
    private int previewRevision = -1;
    private int previewWrapWidth = -1;
    private boolean previewShift;
    @Nullable
    private List<Component> previewComponents;
    @Nullable
    private List<Component> previewWrapped;

    @Nullable
    private Component flashText;
    private int flashColor;
    private long flashUntil;

    /**
     * @param session 编辑会话
     * @param initial 先显示哪一页；未实现的页回落到第一页
     * @param parent  关闭后回到的界面（从背包里打开时是背包界面），null = 回到游戏
     */
    public ItemEditorScreen(@Nonnull EditorSession session, @Nonnull EditorPage initial, @Nullable GuiScreen parent) {
        super(Component.translatable("gui.battlecorrection.editor.title"), parent);
        this.session = session;

        if (session.isItem()) {
            pages.add(new NamePage(this, session));
            pages.add(new LorePage(this, session));
            pages.add(new TagsPage(this, session));
            pages.add(new EnchantmentsPage(this, session));
            pages.add(new AttributesPage(this, session));
            // 饰品栏页只在装了 Baubles 时才有，没装连标签都不显示。
            // Baubles 的饰品栏是固定的 7 格，没有"玩家饰品栏数量"可调，所以不建那一页（枚举 PLAYER_SLOTS 留着，序号是网络协议）
            if (BaublesIntegration.isBaublesLoaded()) {
                pages.add(new CuriosPage(this, session));
            }
            pages.add(new NbtTreePage(this, session));
            pages.add(new RawNbtPage(this, session));
            pages.add(new TemplatesPage(this, session));
        } else {
            // 实体 / 方块实体只有 NBT 可编辑
            pages.add(new NbtTreePage(this, session));
            pages.add(new RawNbtPage(this, session));
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

        // 左侧标签页：按页面顺序排；这次没创建的页（例如没装 Baubles 时的饰品栏页）不显示标签
        int tabY = panelY + TITLE_HEIGHT + 2;
        // 装了 Baubles 有 9 个标签，窗口矮的时候（GUI 高 240）放不下 18 像素一个，按剩余高度压缩，免得盖住底栏
        int bodyBottom = panelY + panelHeight - FOOTER_HEIGHT;
        int tabHeight = Math.max(12, Math.min(TabButton.HEIGHT, (bodyBottom - tabY - 1) / Math.max(1, pages.size())));
        for (EditorPageBase page : pages) {
            TabButton tab = new TabButton(panelX + 1, tabY, SIDEBAR_WIDTH - 1, tabHeight, page.title(), () -> switchPage(page));
            tabs.add(tab);
            addRenderableWidget(tab);
            tabY += tabHeight;
        }

        // 底栏按钮
        int buttonY = panelY + panelHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - FOOTER_BUTTON_HEIGHT) / 2;
        int saveX = panelX + panelWidth - CORNER_CLEARANCE - FOOTER_BUTTON_WIDTH;
        int cancelX = saveX - 4 - FOOTER_BUTTON_WIDTH;
        addRenderableWidget(new FlatButton(cancelX, buttonY, FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.cancel"), this::onClose));
        statusX = panelX + CORNER_CLEARANCE;
        if (session.isItem()) {
            int copyWidth = Math.min(90, cancelX - 8 - (panelX + CORNER_CLEARANCE));
            addRenderableWidget(new FlatButton(panelX + CORNER_CLEARANCE, buttonY, copyWidth, FOOTER_BUTTON_HEIGHT,
                    Component.translatable("gui.battlecorrection.editor.copy_command"), this::copyGiveCommand)
                    .tooltip(Component.translatable("gui.battlecorrection.editor.copy_command.tooltip")));
            int importX = panelX + CORNER_CLEARANCE + copyWidth + 4;
            int importWidth = Math.min(54, cancelX - 8 - importX);
            if (importWidth >= 30) {
                addRenderableWidget(new FlatButton(importX, buttonY, importWidth, FOOTER_BUTTON_HEIGHT,
                        Component.translatable("gui.battlecorrection.editor.import"), this::importFromClipboard)
                        .tooltip(Component.translatable("gui.battlecorrection.editor.import.tooltip")));
                statusX = importX + importWidth + 8;
            } else {
                statusX = panelX + CORNER_CLEARANCE + copyWidth + 8;
            }
        }
        statusMaxX = cancelX - 8;
        statusY = buttonY + (FOOTER_BUTTON_HEIGHT - 8) / 2;
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

        previewVisible = session.isItem() && panelWidth >= PREVIEW_MIN_PANEL_WIDTH && current.wantsPreview();
        int previewWidth = previewVisible ? PREVIEW_WIDTH : 0;

        contentX = panelX + SIDEBAR_WIDTH + PAD;
        contentY = panelY + TITLE_HEIGHT + PAD;
        contentWidth = panelWidth - SIDEBAR_WIDTH - previewWidth - (previewVisible ? 3 : 2) * PAD;
        contentHeight = panelHeight - TITLE_HEIGHT - FOOTER_HEIGHT - 2 * PAD;
        previewX = panelX + panelWidth - PAD - previewWidth;
        titleIconX = panelX + CORNER_CLEARANCE + 2;
        titleIconY = panelY + (TITLE_HEIGHT - 16) / 2;
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

        // 标题栏：切角小槽里放图标 + 当前名字 + 界面标题（左右各让出角饰的位置）
        int iconX = titleIconX;
        int iconY = titleIconY;
        boolean iconHover = session.isItem() && isOverTitleIcon(mouseX, mouseY);
        EditorTheme.chamfer(g, iconX - 2, iconY - 2, 20, 20, 2, iconHover ? EditorTheme.ACCENT_HI : EditorTheme.ACCENT_MID);
        EditorTheme.chamfer(g, iconX - 1, iconY - 1, 18, 18, 1, EditorTheme.WELL);
        if (session.isItem()) {
            ItemStack working = session.working();
            g.renderItem(working, iconX, iconY);
            g.renderItemDecorations(font, working, iconX, iconY);
        } else {
            // 实体 / 方块实体没有图标，槽里画一个字母
            String glyph = session.mode() == EditorSession.Mode.ENTITY ? "E" : "B";
            g.drawString(font, glyph, iconX + 5, iconY + 4, EditorTheme.ACCENT_HI, false);
        }
        Component subjectTitle = isModifiedCached()
                ? Component.empty().append(session.subjectName()).append(Component.literal(" *").withStyle(TextFormatting.GOLD))
                : session.subjectName();
        Component modeTitle = session.isItem() ? title
                : Component.translatable(session.mode() == EditorSession.Mode.ENTITY
                ? "gui.battlecorrection.editor.title.entity" : "gui.battlecorrection.editor.title.block_entity");
        int nameX = iconX + 24;
        int nameMaxWidth = panelWidth - (nameX - panelX) - font.width(modeTitle) - CORNER_CLEARANCE - 3 * PAD;
        g.enableScissor(nameX, panelY, nameX + Math.max(0, nameMaxWidth), panelY + TITLE_HEIGHT);
        g.drawString(font, subjectTitle, nameX, panelY + (TITLE_HEIGHT - 8) / 2, EditorTheme.ACCENT_HI, false);
        g.disableScissor();
        // 右侧界面标题前面一颗呼吸的指示点
        int titleX = panelX + panelWidth - CORNER_CLEARANCE - font.width(modeTitle);
        g.drawString(font, modeTitle, titleX, panelY + (TITLE_HEIGHT - 8) / 2, EditorTheme.TEXT_MUTED, false);
        int dot = EditorTheme.lerp(EditorTheme.ACCENT_MID, EditorTheme.ACCENT_HI, EditorTheme.pulse());
        EditorTheme.rect(g, titleX - 7, panelY + TITLE_HEIGHT / 2 - 1, 3, 3, dot);

        // 页面 → 控件 → 页面前景
        current.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        current.renderForeground(g, mouseX, mouseY, partialTick);

        renderPreview(g);
        renderStatus(g);

        // 悬停标题图标：显示完整提示框（预览栏没显示时靠这个看效果）+ 一行换物品的提示
        if (iconHover) {
            List<Component> lines = new ArrayList<>(previewComponents());
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.battlecorrection.editor.item.change_hint").withStyle(TextFormatting.AQUA));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private boolean isOverTitleIcon(int mouseX, int mouseY) {
        return mouseX >= titleIconX - 2 && mouseX < titleIconX + 18 && mouseY >= titleIconY - 2 && mouseY < titleIconY + 18;
    }

    /**
     * 有没有未保存的改动：按内容版本号缓存，没变化就不重新比较整份 NBT
     */
    private boolean isModifiedCached() {
        int revision = session.revision();
        if (revision != modifiedRevision) {
            modifiedRevision = revision;
            modifiedCache = session.isModified();
        }
        return modifiedCache;
    }

    /**
     * 底栏中间：提示条（几秒后消失）或 NBT 大小
     */
    private void renderStatus(@Nonnull GuiGraphics g) {
        int maxWidth = statusMaxX - statusX;
        if (maxWidth < 40) {
            return;
        }
        Component text;
        int color;
        if (flashText != null && tickCounter < flashUntil) {
            text = flashText;
            color = flashColor;
        } else {
            int bytes = session.measuredBytes();
            text = Component.translatable("gui.battlecorrection.editor.size", formatBytes(bytes));
            // 红 = 超过本机配置的上限或原版客户端的记账上限（保存会被拒）；黄 = 过了上限的一半
            color = isOverLimit() ? EditorTheme.DANGER
                    : bytes >= ItemEditorAccess.maxNbtBytes() / 2 ? EditorTheme.WARNING : EditorTheme.TEXT_DIM;
        }
        String plain = text.getString();
        if (font.widthRaw(plain) > maxWidth) {
            plain = font.plainSubstrByWidth(plain, maxWidth - font.widthRaw("…")) + "…";
        }
        g.drawString(font, Component.raw(plain), statusX, statusY, color, false);
    }

    /**
     * 这份 NBT 现在超没超上限：本机配置的大小上限，或原版客户端的 2 MB 记账上限
     * <p>
     * 后台还没测完当前内容时按"没超"算（服务端收到时还会再卡一遍），不会误拦。
     */
    private boolean isOverLimit() {
        return session.measuredIsCurrent()
                && (session.measuredOverQuota() || session.measuredBytes() > ItemEditorAccess.maxNbtBytes());
    }

    /**
     * 字节数的可读写法
     */
    @Nonnull
    private static String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.2f MB", bytes / 1048576.0);
    }

    /**
     * 提示条
     */
    private void flash(@Nonnull Component text, int color) {
        flashText = text;
        flashColor = color;
        flashUntil = tickCounter + FLASH_TICKS;
    }

    /**
     * 物品完整提示框的各行（缓存）
     */
    @Nonnull
    private List<Component> previewComponents() {
        refreshPreviewCache(previewWrapWidth < 0 ? PREVIEW_WIDTH - 10 : previewWrapWidth);
        return previewComponents == null ? Collections.<Component>emptyList() : previewComponents;
    }

    /**
     * 按需重建预览缓存：内容版本、文字宽度、Shift 状态任何一个变了才重新生成提示框行并折行
     */
    private void refreshPreviewCache(int wrapWidth) {
        int revision = session.revision();
        boolean shift = hasShiftDown();
        if (previewComponents != null && revision == previewRevision && wrapWidth == previewWrapWidth && shift == previewShift) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        for (String line : tooltipLines(session.working())) {
            lines.add(Component.literal(line));
        }
        List<Component> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, wrapWidth));
        }
        previewComponents = lines;
        previewWrapped = wrapped;
        previewRevision = revision;
        previewWrapWidth = wrapWidth;
        previewShift = shift;
    }

    /**
     * 物品提示框的文字：第一行按稀有度上色、其余灰色（同原版 GuiScreen#getItemToolTip）
     * <p>
     * 在副本上取，模组的 addInformation 改了物品也不会动到正在编辑的那一份。
     *
     * @param stack 物品
     * @return 每行一个带分节号格式码的字符串
     */
    @Nonnull
    private List<String> tooltipLines(@Nonnull ItemStack stack) {
        ItemStack copy = stack.copy();
        List<String> lines = copy.getTooltip(minecraft == null ? null : minecraft.player, ITooltipFlag.TooltipFlags.NORMAL);
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                lines.set(i, copy.getItem().getForgeRarity(copy).getColor() + lines.get(i));
            } else {
                lines.set(i, TextFormatting.GRAY + lines.get(i));
            }
        }
        return lines;
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
        refreshPreviewCache(textWidth);
        if (previewWrapped != null) {
            for (Component wrapped : previewWrapped) {
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
        inputBoost = INPUT_BOOST_TICKS;
        if (current.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        inputBoost = INPUT_BOOST_TICKS;
        if (button == 0 && session.isItem() && isOverTitleIcon((int) mouseX, (int) mouseY)) {
            openItemPicker();
            return true;
        }
        if (current.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        inputBoost = INPUT_BOOST_TICKS;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        inputBoost = INPUT_BOOST_TICKS;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        inputBoost = INPUT_BOOST_TICKS;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        inputBoost = INPUT_BOOST_TICKS;
        if (keyCode == Keys.KEY_ESCAPE) {
            if (current.onEscape()) {
                return true;
            }
            onClose();
            return true;
        }
        // 这几个全局快捷键要抢在输入框前面处理，输入框不该吃掉它们
        if (keyCode == Keys.KEY_F1) {
            openHelp();
            return true;
        }
        if (keyCode == Keys.KEY_F4 && session.isItem()) {
            openItemPicker();
            return true;
        }
        if (hasControlDown() && keyCode == Keys.KEY_TAB) {
            cyclePage(hasShiftDown() ? -1 : 1);
            return true;
        }
        if (hasControlDown() && keyCode >= Keys.KEY_1 && keyCode <= Keys.KEY_9) {
            int index = keyCode - Keys.KEY_1;
            if (index < pages.size()) {
                switchPage(pages.get(index));
            }
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (hasControlDown() && keyCode == Keys.KEY_S) {
            save();
            return true;
        }
        if (hasControlDown() && keyCode == Keys.KEY_Z && !hasShiftDown()) {
            if (commitPendingEdits()) {
                applyHistory(session.undo());
            }
            return true;
        }
        if (hasControlDown() && (keyCode == Keys.KEY_Y || (keyCode == Keys.KEY_Z && hasShiftDown()))) {
            if (commitPendingEdits()) {
                applyHistory(session.redo());
            }
            return true;
        }
        return current.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        // 颜色工具条这类"不抢焦点"的按钮点了之后，焦点要留在输入框里
        if (listener instanceof FlatButton && ((FlatButton) listener).keepsFocus()) {
            return;
        }
        super.setFocused(listener);
    }

    @Override
    public void tick() {
        tickCounter++;
        // 比较整份 NBT 有成本：有输入的前后几个 tick 里每 tick 查，闲着时每秒兜底查一次
        if (inputBoost > 0 || tickCounter % IDLE_POLL_TICKS == 0) {
            if (inputBoost > 0) {
                inputBoost--;
            }
            session.trackChanges(tickCounter);
        }
        current.tick();
    }

    /**
     * 按顺序切到下一页 / 上一页（首尾相接）
     *
     * @param delta 1 = 下一页，-1 = 上一页
     */
    private void cyclePage(int delta) {
        int size = pages.size();
        if (size < 2) {
            return;
        }
        int index = pages.indexOf(current);
        switchPage(pages.get(((index + delta) % size + size) % size));
    }

    private void openHelp() {
        if (minecraft != null) {
            minecraft.displayGuiScreen(new EditorHelpScreen(this));
        }
    }

    /**
     * 打开换物品的选择器；当前页有没应用的改动先让它落到副本里
     */
    private void openItemPicker() {
        if (!session.isItem() || minecraft == null) {
            return;
        }
        if (!current.beforeLeave()) {
            return;
        }
        minecraft.displayGuiScreen(new ItemPickerScreen(this, session.working().copy(), this::onItemPicked));
    }

    /**
     * 选择器选定了新物品：换过去，NBT 和数量不变，当前页按新物品重建
     * <p>
     * 有子类型的物品（羊毛、染料这类）连损害值一起换成选中的那一种；其余的只换物品，耐久照旧。
     */
    private void onItemPicked(@Nonnull ItemStack picked) {
        ResourceLocation id = picked.isEmpty() ? null : picked.getItem().getRegistryName();
        if (id == null) {
            return;
        }
        try {
            if (picked.getHasSubtypes()) {
                session.changeItem(id, picked.getMetadata());
            } else {
                session.changeItem(id);
            }
        } catch (IllegalArgumentException e) {
            flash(Component.literal(String.valueOf(e.getMessage())), EditorTheme.DANGER);
            return;
        }
        current.onExternalChange();
        mountPage(current);
        // 提示条只有底栏中间那一小段宽，写注册名会被截断，用本地化的物品名（不带自定义名字）
        ItemStack working = session.working();
        flash(Component.translatable("gui.battlecorrection.editor.item.changed",
                Component.literal(working.getItem().getItemStackDisplayName(working))), EditorTheme.SUCCESS);
    }

    /**
     * 把剪贴板里的物品整件导入（/give 命令、物品 ID + NBT、物品 SNBT 都行）；Ctrl+Z 可以撤销
     */
    private void importFromClipboard() {
        if (!session.isItem() || minecraft == null) {
            return;
        }
        if (!current.beforeLeave()) {
            return;
        }
        ItemImport.Result result = ItemImport.parse(Ui.getClipboard(),
                session.itemId().toString(), session.working().getCount(), session.damage());
        if (result.root() == null) {
            flash(Component.translatable("gui.battlecorrection.editor.import.failed", String.valueOf(result.error())), EditorTheme.DANGER);
            return;
        }
        try {
            session.pushRoot(result.root());
        } catch (IllegalArgumentException e) {
            flash(Component.literal(String.valueOf(e.getMessage())), EditorTheme.DANGER);
            return;
        }
        current.onExternalChange();
        mountPage(current);
        flash(Component.translatable("gui.battlecorrection.editor.import.done"), EditorTheme.SUCCESS);
    }

    /**
     * 让当前页把还没写进物品副本的内容（SNBT 页里没点应用的文本）先落下来，并记进撤销栈
     * <p>
     * 整体撤销 / 重做会重建当前页，没落下来的文本会跟着丢。先落下来，撤销就是把这次修改一起撤掉，文本不会凭空消失；
     * 落不下来（文本不合法）就什么都不做，文本留在原地。
     *
     * @return true = 可以继续撤销 / 重做
     */
    private boolean commitPendingEdits() {
        if (!current.beforeLeave()) {
            return false;
        }
        session.trackChanges(tickCounter);
        return true;
    }

    /**
     * 撤销/重做之后：通知当前页丢掉自己的临时状态并重建
     *
     * @param changed 撤销/重做有没有真的改动
     */
    private void applyHistory(boolean changed) {
        if (!changed) {
            return;
        }
        current.onExternalChange();
        mountPage(current);
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
        if (minecraft == null) {
            return;
        }
        // 当前页没写进副本的内容（SNBT 页没点应用的文本）先落一次：落得下来就算进"有改动"，落不下来也要走确认框，
        // 不然点"取消"会悄悄丢掉刚写的东西
        boolean pendingOk = discardConfirmed || current.beforeLeave();
        if (!discardConfirmed && (!pendingOk || session.isModified())) {
            minecraft.displayGuiScreen(new ConfirmCloseScreen(this));
            return;
        }
        minecraft.displayGuiScreen(parent);
    }

    /**
     * 不管有没有改动直接关（保存成功后、或确认丢弃后用）
     */
    public void closeDiscard() {
        discardConfirmed = true;
        if (minecraft != null) {
            minecraft.displayGuiScreen(parent);
        }
    }

    /**
     * 把当前物品导出成 /give 命令放进剪贴板
     * <p>
     * 1.12.2 的格式是 {@code /give <玩家> <物品> [数量] [损害值] [NBT]}，后面的参数是按位置认的，
     * 有 NBT 时损害值必须写上（哪怕是 0）。
     */
    private void copyGiveCommand() {
        if (!session.isItem() || minecraft == null || minecraft.player == null) {
            return;
        }
        session.cleanupEmpty();
        ItemStack working = session.working();
        NBTTagCompound tag = working.getTagCompound();
        boolean hasTag = tag != null && !tag.hasNoTags();
        int damage = session.damage();
        StringBuilder command = new StringBuilder("/give @p ").append(session.itemId()).append(' ').append(working.getCount());
        if (hasTag || damage != 0) {
            command.append(' ').append(damage);
        }
        if (hasTag) {
            command.append(' ').append(tag);
        }
        String text = command.toString();
        Ui.setClipboard(text);
        String key = text.length() > CHAT_LIMIT
                ? "message.battlecorrection.editor.command_copied_long"
                : "message.battlecorrection.editor.command_copied";
        TextComponentTranslation message = new TextComponentTranslation(key, text.length());
        message.getStyle().setColor(TextFormatting.GREEN);
        minecraft.player.sendStatusMessage(message, false);
    }

    /**
     * 保存：交给服务端校验并写回，然后关界面
     */
    private void save() {
        if (!current.beforeLeave()) {
            return;
        }
        // 明摆着会被拒的先在本地拦住：关了界面再被服务端拒收，这次的修改就全丢了
        session.trackChanges(tickCounter);
        if (isOverLimit()) {
            flash(Component.translatable("gui.battlecorrection.editor.save.too_big"), EditorTheme.DANGER);
            return;
        }
        // 都走分片传输，NBT 多大都能发
        if (session.isItem()) {
            session.cleanupEmpty();
            ItemStack working = session.working();
            NBTTagCompound tag = working.getTagCompound();
            ApplyItemEditC2SPacket packet = new ApplyItemEditC2SPacket(session.target(),
                    session.originalItemId(), session.originalDamage(),
                    session.itemId(), session.damage(),
                    tag == null ? null : tag.copy(), working.getCount());
            ChunkedPacket.sendToServer(ChunkedPacket.Kind.APPLY_ITEM, packet::encode);
        } else {
            ApplyNbtEditC2SPacket packet = new ApplyNbtEditC2SPacket(session.nbtTarget(), session.nbtWorking().copy());
            ChunkedPacket.sendToServer(ChunkedPacket.Kind.APPLY_NBT, packet::encode);
        }
        closeDiscard();
    }
}
