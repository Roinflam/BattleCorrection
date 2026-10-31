package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.snbt.ColorCodes;
import pers.roinflam.battlecorrection.client.editor.snbt.SnbtEditing;
import pers.roinflam.battlecorrection.client.editor.snbt.SnbtFormatter;
import pers.roinflam.battlecorrection.client.editor.snbt.SnbtValidator;
import pers.roinflam.battlecorrection.client.editor.snbt.TextDocument;
import pers.roinflam.battlecorrection.client.editor.snbt.TextSearch;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiEventListener;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;
import pers.roinflam.battlecorrection.client.editor.ui.Ui;
import pers.roinflam.battlecorrection.client.editor.widget.ButtonRows;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatTextArea;
import pers.roinflam.battlecorrection.client.editor.widget.TreeEditBox;
import pers.roinflam.battlecorrection.editor.EditorPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * SNBT 页：整份 NBT 以 SNBT 文本编辑（SNBT 就是原版 /give、/summon、/blockdata 里写 NBT 用的文本格式）
 * <p>
 * 物品显示的是存盘时的完整结构 {@code {id:"minecraft:stick",Count:1b,Damage:0s,tag:{...}}}，实体 / 方块实体是整份 NBT；
 * Forge 附加的 ForgeCaps 是运行时能力数据，编辑器改不了，显示时去掉（见 {@link EditorSession#loadRoot()}）。
 * <p>
 * 文本、光标、选区、滚动位置和文本级撤销历史都放在页面持有的 {@link TextDocument} 里，编辑框只是它的视图：
 * 切页再回来、窗口缩放、开帮助界面再回来时控件会重建，没应用的文本、光标和滚动位置都还在。
 * <ul>
 *   <li>载入：大 NBT 排版成多行要几十上百毫秒，放到后台线程做（主线程只负责拷一份根），
 *   期间编辑框只读并盖一层"载入中…"，结果在 tick 里取回再写进文档；格式化、压成一行也是这样。
 *   取回结果时只写文档，不碰当时可能已经换掉的控件，编辑框永远是当前那一个。</li>
 *   <li>校验：停止输入 300 毫秒后用原版 JsonToNBT 解析（大文本在后台线程），状态栏显示格式正确或"第几行第几列：原因"，
 *   出错位置在文本里标红。解析结果连同文档版本号一起缓存，应用时文本没再改过就直接用，2MB 的文本不用再解析一遍。</li>
 *   <li>应用（按钮或 Ctrl+Enter）：交给 {@link EditorSession#pushRoot}，id 不同就换物品；id 写错或物品不存在时它会抛出
 *   IllegalArgumentException，信息直接显示在状态栏。写回后的实际结果和文本对不上（数量夹到范围里、id 补上命名空间、
 *   多写了物品不认的字段……）就把文本刷新成实际结果，免得界面显示的和物品里的不一样。</li>
 *   <li>切页、保存、换物品、导入之前（{@link #beforeLeave}）自动尝试应用没应用的改动，失败就拦住；
 *   按 Esc 关编辑器之前也先应用（应用后关闭确认框能列出这些改动），应用不了时第一次 Esc 只提示并跳到错误处，
 *   再按一次才交给界面弹出关闭确认框，在那里选放弃修改才会丢。</li>
 *   <li>查找栏（Ctrl+F，F3 / Shift+F3 上下一个）、跳到行（Ctrl+G）、& 与 § 互转。</li>
 * </ul>
 * 别的页改过物品时：回到这一页先比编辑内容的版本号（EditorSession.revision），变了再拿物品现在的根和文本对应的根比一次，确实不同才重新载入。
 * 没应用的文本不会被覆盖——切走之前已经应用或被拦住了。
 * <p>
 * 状态栏按原样画（§ 显示成 §，不当格式码）：错误原因、物品 ID 这些内容来自玩家写的文本，带着 § 时照原版的画法会把后面的字吃掉。
 */
public class RawNbtPage extends EditorPageBase {

    private static final String LANG = "gui.battlecorrection.editor.snbt.";
    private static final String RAW = "gui.battlecorrection.editor.raw.";
    private static final String TAG_ID = "id";
    private static final int TAG_STRING = 8;
    private static final String ELLIPSIS = "…";

    private static final int BOX_HEIGHT = 14;
    private static final int STATUS_HEIGHT = 12;
    private static final int GAP = 4;
    private static final int ICON_BUTTON = 16;
    /**
     * 底部按钮放不下一行时，从第几个开始换到第二行（复制、重新载入）
     */
    private static final int BUTTON_ROW_BREAK = 5;
    /**
     * 底部按钮里哪几个的标签按原样画："&→§"和"§→&"真的要显示 § 号
     */
    private static final boolean[] RAW_LABELS = {false, false, false, true, true, false, false};

    /**
     * 停止输入多久之后校验
     */
    private static final long VALIDATE_DELAY_MS = 300L;

    /**
     * 文本改动后多久重新查找（大文本的查找在后台线程，连续打字时不必每个字都查）
     */
    private static final long SEARCH_DELAY_MS = 150L;

    /**
     * 操作提示（已复制、转换了几处……）在状态栏停留多久
     */
    private static final long FLASH_MS = 2500L;

    /**
     * 文本不超过这么多字符时，校验、查找、格式化直接在主线程做：64K 字符的 SNBT 解析一遍不到一毫秒，
     * 走后台反而要多等一个 tick 才能看到结果
     */
    private static final int SYNC_TEXT_CHARS = 64 * 1024;

    /**
     * NBT 不超过这么多字节时载入直接在主线程格式化（同上，常见的物品都在这个范围里，不会闪一下"载入中"）
     */
    private static final int SYNC_FORMAT_BYTES = 32 * 1024;

    /**
     * 打开查找栏时，选中的文字不超过这么长（而且不跨行）就拿来当查找词
     */
    private static final int FIND_PREFILL_MAX = 128;

    private enum Status {
        LOADING,
        CLEAN,
        DIRTY,
        APPLIED,
        ERROR
    }

    private enum Bar {
        NONE,
        FIND,
        GOTO
    }

    /**
     * 后台任务的种类：前三种是"把某个根格式化成文本"，后两种是"把当前文本变个样"
     */
    private enum JobKind {
        LOAD,
        RELOAD,
        CANONICAL,
        FORMAT,
        COMPACT;

        boolean fromRoot() {
            return this == LOAD || this == RELOAD || this == CANONICAL;
        }
    }

    /**
     * 后台任务的结果
     */
    private static final class JobResult {
        /**
         * 新文本，失败时为 null
         */
        @Nullable
        final String text;
        /**
         * 顺带得到的解析结果（格式化 / 压缩要先解析），没有时为 null
         */
        @Nullable
        final SnbtValidator.Result validation;

        JobResult(@Nullable String text, @Nullable SnbtValidator.Result validation) {
            this.text = text;
            this.validation = validation;
        }
    }

    /**
     * 一个进行中的后台任务
     */
    private static final class Job {
        /**
         * 种类
         */
        @Nonnull
        final JobKind kind;
        /**
         * 开始时的文档版本（文本类任务据此判断结果还能不能用）
         */
        final long docVersion;
        /**
         * 开始时的编辑内容版本号（根类任务据此判断物品在这期间有没有被改过）
         */
        final int revision;
        /**
         * 格式化的那份根（根类任务）
         */
        @Nullable
        final NBTTagCompound root;
        /**
         * 结果
         */
        @Nonnull
        final CompletableFuture<JobResult> future;

        Job(@Nonnull JobKind kind, long docVersion, int revision, @Nullable NBTTagCompound root,
            @Nonnull CompletableFuture<JobResult> future) {
            this.kind = kind;
            this.docVersion = docVersion;
            this.revision = revision;
            this.root = root;
            this.future = future;
        }
    }

    private final TextDocument doc = new TextDocument(Ui::millis);

    // 控件（每次 build 重建）
    @Nullable
    private FlatTextArea box;
    @Nullable
    private TreeEditBox searchBox;
    @Nullable
    private TreeEditBox gotoBox;
    @Nullable
    private FlatButton caseButton;
    @Nullable
    private FlatButton prevButton;
    @Nullable
    private FlatButton nextButton;
    @Nullable
    private FlatButton closeBarButton;
    @Nullable
    private FlatButton jumpButton;
    @Nullable
    private FlatButton[] editButtons;

    // 几何
    private int textTopClosed;
    private int textTopOpen;
    private int textBottom;
    private int statusTop;
    private int statusRight;
    private int countX;
    private int gotoLabelX;
    @Nonnull
    private Component gotoLabel = Component.empty();

    // 文本与物品的对应
    private boolean loaded;
    private boolean reloadRequested;
    /**
     * 和物品一致的那份文本（载入或应用之后的文本）
     */
    @Nonnull
    private String baselineText = "";
    private boolean baselineApplied;
    /**
     * baselineText 对应的根（物品写回之后的实际结果），回到这一页时用来判断物品有没有在别处被改过
     */
    @Nullable
    private NBTTagCompound loadedRoot;
    private int seenRevision = Integer.MIN_VALUE;
    private Status status = Status.LOADING;
    /**
     * 正在修改文档的是页面自己（载入、刷新），不算玩家的改动
     */
    private boolean programmatic;
    private boolean escWarned;

    // 后台任务
    @Nullable
    private Job job;

    // 校验
    @Nullable
    private SnbtValidator.Result validation;
    private long validationVersion = -1;
    private long validateDueAt;
    @Nullable
    private CompletableFuture<SnbtValidator.Result> validateTask;
    private long validateTaskVersion = -1;
    /**
     * 解析出的 id 和现在的物品不同：应用后会换成它（已规范成带命名空间的写法）
     */
    @Nullable
    private String itemChange;
    /**
     * 解析出的 id 不能用：应用一定会失败，原因先显示出来
     */
    @Nullable
    private String itemProblem;

    // 错误
    @Nullable
    private Component errorText;
    private int errorOffset = -1;
    @Nullable
    private Component appliedNote;

    // 查找 / 跳到行
    private Bar bar = Bar.NONE;
    private boolean focusBarOnBuild;
    @Nonnull
    private String query = "";
    private boolean caseSensitive;
    @Nullable
    private TextSearch.Result searchResult;
    @Nullable
    private CompletableFuture<TextSearch.Result> searchTask;
    @Nonnull
    private String searchTaskQuery = "";
    private boolean searchTaskCase;
    private long searchDueAt;
    private boolean searchJump;
    private int searchOrigin;
    private int pendingStep;
    private String countText = "";
    private int countColor = EditorTheme.TEXT_MUTED;
    @Nullable
    private TextSearch.Result countFor;
    private int countSelStart = -1;
    private int countSelEnd = -1;
    private boolean countPending;
    private boolean countQueryEmpty = true;

    // 状态栏
    private final StatusLine statusLine = new StatusLine();
    private final StatusLine flashLine = new StatusLine();
    private boolean statusStale = true;
    private long flashUntil;

    /**
     * @param screen  所属界面
     * @param session 正在编辑的内容
     */
    public RawNbtPage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
        doc.setOwnerListener(this::onDocChanged);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.RAW;
    }

    /**
     * 文本编辑要宽度，预览栏收起来（悬停标题栏图标照样能看完整提示框）
     */
    @Override
    public boolean wantsPreview() {
        return false;
    }

    // 布局

    @Override
    protected void build() {
        // 按钮一行放不下时排成两行，文本框相应变矮，所以先排按钮
        Component[] buttonLabels = buttonLabels();
        ButtonRows buttonRows = layoutButtons(buttonLabels);
        int buttonsTop = y + height - BOX_HEIGHT - (buttonRows.rows() - 1) * (BOX_HEIGHT + ButtonRows.ROW_GAP);
        statusTop = buttonsTop - 2 - STATUS_HEIGHT;
        textBottom = statusTop - 2;
        textTopClosed = y;
        textTopOpen = y + BOX_HEIGHT + GAP;

        // 控件按从上到下的顺序登记，Tab 切焦点时也是这个顺序
        buildBars();
        int textTop = bar == Bar.NONE ? textTopClosed : textTopOpen;
        Component placeholder = Component.translatable(session.isItem() ? RAW + "empty" : LANG + "empty_nbt");
        box = add(new FlatTextArea(font, x, textTop, width, Math.max(FlatTextArea.ROW_HEIGHT + 4, textBottom - textTop),
                doc, placeholder, Component.translatable("gui.battlecorrection.editor.tab.raw")));
        box.setErrorMarker(errorOffset);
        box.setSearchResult(bar == Bar.FIND ? searchResult : null);

        Component jumpLabel = Component.translatable(LANG + "jump_error");
        int jumpWidth = font.width(jumpLabel) + 10;
        jumpButton = add(new FlatButton(x + width - jumpWidth, statusTop, jumpWidth, STATUS_HEIGHT, jumpLabel, this::jumpToError)
                .keepFocus()
                .tooltip(Component.translatable(LANG + "jump_error.tooltip")));
        buildButtonRow(buttonsTop, buttonLabels, buttonRows);
        applyBarVisibility();

        // 第一次进来、或者撤销 / 换物品之后：从物品读；否则看物品有没有在别的页被改过
        if (reloadRequested || (!loaded && job == null)) {
            reloadRequested = false;
            startLoad();
        } else if (job == null && (status == Status.CLEAN || status == Status.APPLIED)
                && session.revision() != seenRevision) {
            NBTTagCompound now = session.loadRoot();
            seenRevision = session.revision();
            if (loadedRoot == null || !now.equals(loadedRoot)) {
                startLoadFrom(now);
            }
        }
        // 页面不在前台时 tick 不会调到这里，期间做完的后台任务现在取回
        pumpJob();
        updateBusy();
        // 直接重算一次：跳到错误按钮的显隐在这里定，不能等到第一帧画完
        rebuildStatus();

        if (focusBarOnBuild && bar == Bar.FIND && searchBox != null) {
            screen.setFocused(searchBox);
        } else if (focusBarOnBuild && bar == Bar.GOTO && gotoBox != null) {
            screen.setFocused(gotoBox);
        } else {
            screen.setFocused(box);
        }
    }

    /**
     * 顶上一行：查找栏和跳到行共用这一行，一次只显示一个；控件都建好，开关时只切可见性和编辑框的位置
     */
    private void buildBars() {
        int searchWidth = Mth.clamp(width / 2, 90, 220);
        searchBox = add(new TreeEditBox(font, x + 1, y, searchWidth - 2, BOX_HEIGHT,
                Component.translatable(LANG + "find.hint"))
                .keepSectionSigns(true)
                .onKey(this::handleFindKey));
        searchBox.setMaxLength(256);
        // 先填值再挂回调，重建页面时不会被当成一次新的输入
        searchBox.setValue(query);
        searchBox.setResponder(this::onQueryChanged);

        int buttonX = x + searchWidth + GAP;
        caseButton = add(new FlatButton(buttonX, y, 22, BOX_HEIGHT, Component.literal("Aa"), this::toggleCase)
                .keepFocus()
                .tooltip(Component.translatable(LANG + "find.case.tooltip")));
        caseButton.style(caseSensitive ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL);
        buttonX += 22 + GAP;
        prevButton = add(new FlatButton(buttonX, y, ICON_BUTTON, BOX_HEIGHT, Component.literal("▲"), () -> findStep(-1))
                .keepFocus()
                .tooltip(Component.translatable(LANG + "find.prev.tooltip")));
        buttonX += ICON_BUTTON + 2;
        nextButton = add(new FlatButton(buttonX, y, ICON_BUTTON, BOX_HEIGHT, Component.literal("▼"), () -> findStep(1))
                .keepFocus()
                .tooltip(Component.translatable(LANG + "find.next.tooltip")));
        countX = buttonX + ICON_BUTTON + GAP;

        gotoLabel = Component.translatable(LANG + "goto.label");
        gotoLabelX = x + 1;
        int gotoX = gotoLabelX + font.width(gotoLabel) + 6;
        gotoBox = add(new TreeEditBox(font, gotoX, y, 90, BOX_HEIGHT, Component.translatable(LANG + "goto.hint", doc.lineCount()))
                .onKey(this::handleGotoKey));
        gotoBox.setMaxLength(16);
        gotoBox.setFilter(RawNbtPage::isLineSpec);

        // 关闭按钮也不抢焦点：closeBar 已经把焦点还给编辑框，界面随后再把焦点设到这个（马上隐藏的）按钮上就打不了字了
        closeBarButton = add(new FlatButton(x + width - ICON_BUTTON, y, ICON_BUTTON, BOX_HEIGHT, Component.literal("×"), this::closeBar)
                .keepFocus()
                .tooltip(Component.translatable(LANG + "find.close.tooltip")));
    }

    /**
     * 底部按钮的标签：应用 格式化 压成一行 &→§ §→& | 复制 重新载入
     * <p>
     * 两个转换按钮的标签就是这几个符号，不随语言变，直接在这里拼（lang 文件里写不了 §：读进来之后照样会被当成格式码）。
     */
    @Nonnull
    private static Component[] buttonLabels() {
        return new Component[]{
                Component.translatable(RAW + "apply"),
                Component.translatable(LANG + "format"),
                Component.translatable(LANG + "compact"),
                Component.raw(ColorCodes.AMPERSAND + "→" + ColorCodes.SECTION),
                Component.raw(ColorCodes.SECTION + "→" + ColorCodes.AMPERSAND),
                Component.translatable(RAW + "copy"),
                Component.translatable(RAW + "reload")
        };
    }

    /**
     * 一行放得下就一行；放不下分两行（复制、重新载入换到第二行），每个按钮都至少能把字完整显示出来
     */
    @Nonnull
    private ButtonRows layoutButtons(@Nonnull Component[] labels) {
        int[] preferred = new int[labels.length];
        int[] minimum = new int[labels.length];
        int[] gaps = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int labelWidth = RAW_LABELS[i] ? FlatButton.rawWidth(font, labels[i]) : font.width(labels[i]);
            preferred[i] = Math.max(28, labelWidth + 12);
            minimum[i] = ButtonRows.minWidthFor(labelWidth);
            gaps[i] = GAP;
        }
        return ButtonRows.layout(width, preferred, minimum, gaps, BUTTON_ROW_BREAK);
    }

    /**
     * 按排好的位置建底部按钮
     *
     * @param top    第一行按钮的上沿
     * @param labels 标签
     * @param layout 排版结果
     */
    private void buildButtonRow(int top, @Nonnull Component[] labels, @Nonnull ButtonRows layout) {
        Component[] tooltips = {
                Component.translatable(session.isItem() ? LANG + "apply.tooltip" : LANG + "apply.tooltip_nbt"),
                Component.translatable(LANG + "format.tooltip"),
                Component.translatable(LANG + "compact.tooltip"),
                Component.translatable(LANG + "to_section.tooltip"),
                Component.translatable(LANG + "to_ampersand.tooltip"),
                Component.translatable(RAW + "copy.tooltip"),
                Component.translatable(LANG + "reload.tooltip")
        };
        Runnable[] actions = {
                this::applyByUser,
                () -> runTextJob(JobKind.FORMAT),
                () -> runTextJob(JobKind.COMPACT),
                () -> convertCodes(true),
                () -> convertCodes(false),
                this::copyAll,
                this::reload
        };
        editButtons = new FlatButton[labels.length];
        for (int i = 0; i < labels.length; i++) {
            // 不抢焦点：点完按钮焦点还在编辑框里，紧接着按 Ctrl+Z 撤销的是文本，不会落到界面的整体撤销上
            FlatButton button = new FlatButton(x + layout.offset(i), top + layout.row(i) * (BOX_HEIGHT + ButtonRows.ROW_GAP),
                    layout.width(i), BOX_HEIGHT, labels[i], actions[i])
                    .keepFocus()
                    .tooltip(tooltips[i]);
            if (i == 0) {
                button.style(FlatButton.Style.PRIMARY);
            }
            if (RAW_LABELS[i]) {
                button.raw();
            }
            editButtons[i] = add(button);
        }
    }

    /**
     * 按 bar 切换顶上一行各控件的可见性，并把编辑框挪到对应位置
     */
    private void applyBarVisibility() {
        boolean find = bar == Bar.FIND;
        boolean go = bar == Bar.GOTO;
        if (searchBox != null) {
            searchBox.setVisible(find);
        }
        setVisible(caseButton, find);
        setVisible(prevButton, find);
        setVisible(nextButton, find);
        if (gotoBox != null) {
            gotoBox.setVisible(go);
        }
        setVisible(closeBarButton, find || go);
        if (box != null) {
            int top = bar == Bar.NONE ? textTopClosed : textTopOpen;
            box.setBounds(top, Math.max(FlatTextArea.ROW_HEIGHT + 4, textBottom - top));
            box.setSearchResult(find ? searchResult : null);
        }
    }

    private static void setVisible(@Nullable FlatButton button, boolean visible) {
        if (button != null) {
            button.visible = visible;
        }
    }

    // 绘制

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int textY = y + (BOX_HEIGHT - 8) / 2;
        if (bar == Bar.FIND) {
            refreshCount();
            if (!countText.isEmpty()) {
                g.drawString(font, countText, countX, textY, countColor, false);
            }
        } else if (bar == Bar.GOTO) {
            label(g, gotoLabel, gotoLabelX, textY);
        }
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (statusStale) {
            rebuildStatus();
        }
        StatusLine line = Ui.millis() < flashUntil ? flashLine : statusLine;
        int maxWidth = statusRight - (x + 1);
        if (maxWidth <= 20) {
            return;
        }
        int textY = statusTop + (STATUS_HEIGHT - 8) / 2;
        g.drawString(font, line.shown(maxWidth), x + 1, textY, line.color, false);
        // 状态只有一行的位置，太长就裁掉；完整内容鼠标悬停时显示
        if (line.truncated && mouseX >= x && mouseX < statusRight && mouseY >= statusTop && mouseY < statusTop + STATUS_HEIGHT) {
            g.renderTooltip(font, line.tooltip(Math.min(300, screen.width - 20)), mouseX, mouseY);
        }
    }

    /**
     * 查找计数（"3 / 12"、"无结果"……）：命中结果或选区变了才重新生成文字，平时每帧只比较几个数
     */
    private void refreshCount() {
        boolean empty = query.isEmpty();
        TextSearch.Result result = searchResult;
        boolean pending = !empty && (result == null || result.version() != doc.version() || searchTask != null
                || searchDueAt != 0);
        int selStart = doc.selectionStart();
        int selEnd = doc.selectionEnd();
        if (empty == countQueryEmpty && pending == countPending && result == countFor
                && selStart == countSelStart && selEnd == countSelEnd) {
            return;
        }
        countQueryEmpty = empty;
        countPending = pending;
        countFor = result;
        countSelStart = selStart;
        countSelEnd = selEnd;
        if (empty) {
            countText = "";
        } else if (pending || result == null) {
            countText = ELLIPSIS;
            countColor = EditorTheme.TEXT_DIM;
        } else if (result.count() == 0) {
            countText = Ui.translate(LANG + "find.none");
            countColor = EditorTheme.DANGER;
        } else {
            String total = result.count() + (result.truncated() ? "+" : "");
            int index = result.indexOf(selStart, selEnd);
            countText = index >= 0 ? Ui.translate(LANG + "find.index", index + 1, total) : Ui.translate(LANG + "find.count", total);
            countColor = EditorTheme.TEXT_MUTED;
        }
    }

    /**
     * 按当前状态重新生成状态栏文字（状态变化时才调用，不在每帧里拼字符串）
     * <p>
     * 来自玩家文本的参数（物品 ID、错误原因）都用原样组件包起来，里面的 § 不会被当成格式码。
     */
    private void rebuildStatus() {
        statusStale = false;
        Component text;
        int color;
        switch (status) {
            case LOADING:
                text = Component.translatable(LANG + "loading");
                color = EditorTheme.TEXT_DIM;
                break;
            case ERROR:
                text = errorText != null ? errorText : Component.translatable(RAW + "status.dirty");
                color = EditorTheme.DANGER;
                break;
            case DIRTY: {
                boolean checked = validation != null && validationVersion == doc.version() && validation.ok();
                if (checked && itemProblem != null) {
                    text = Component.translatable(LANG + "status.item_problem", Component.raw(itemProblem));
                    color = EditorTheme.DANGER;
                } else if (checked && itemChange != null) {
                    text = Component.translatable(LANG + "status.item_change", Component.raw(itemChange));
                    color = EditorTheme.ACCENT;
                } else if (checked) {
                    text = Component.translatable(LANG + "status.valid_dirty");
                    color = EditorTheme.WARNING;
                } else {
                    text = Component.translatable(RAW + "status.dirty");
                    color = EditorTheme.WARNING;
                }
                break;
            }
            case APPLIED:
                text = appliedNote != null ? appliedNote : Component.translatable(RAW + "status.applied");
                color = EditorTheme.SUCCESS;
                break;
            default:
                text = session.isItem() && session.hasForgeCaps()
                        ? Component.translatable(RAW + "status.clean_caps")
                        : Component.translatable(LANG + "status.clean");
                color = EditorTheme.TEXT_DIM;
                break;
        }
        statusLine.set(text, color);
        boolean showJump = status == Status.ERROR && errorOffset >= 0;
        setVisible(jumpButton, showJump);
        statusRight = showJump && jumpButton != null ? jumpButton.getX() - GAP : x + width;
    }

    private void invalidateStatus() {
        statusStale = true;
    }

    /**
     * 状态栏临时显示一句操作结果，几秒后回到正常状态
     */
    private void flash(@Nonnull Component text, int color) {
        flashLine.set(text, color);
        flashUntil = Ui.millis() + FLASH_MS;
    }

    // 生命周期

    @Override
    public void tick() {
        // 页面在前台时，编辑内容的变化只可能来自本页的应用（撤销、换物品、导入都会先调 onExternalChange），
        // 记下版本号，切走再回来时据此判断物品有没有在别的页被改过
        if (job == null) {
            seenRevision = session.revision();
        }
        pumpJob();
        pumpValidation();
        pumpSearch();
        GuiEventListener focused = screen.getFocused();
        focusBarOnBuild = focused != null && (focused == searchBox || focused == gotoBox);
    }

    @Override
    public boolean beforeLeave() {
        // 人都要走了，还没做完的格式化 / 压缩就别再改文本了（回来时突然变样更让人摸不着头脑）
        if (job != null && !job.kind.fromRoot()) {
            cancelJob();
        }
        if (status != Status.DIRTY && status != Status.ERROR) {
            return true;
        }
        if (apply()) {
            return true;
        }
        revealError();
        return false;
    }

    @Override
    public void onExternalChange() {
        // 撤销 / 重做 / 换物品 / 导入之后物品变了：没应用的文本作废，重建时重新读
        cancelJob();
        validateTask = null;
        validateDueAt = 0;
        validation = null;
        reloadRequested = true;
    }

    @Override
    public boolean onEscape() {
        if (bar != Bar.NONE) {
            closeBar();
            return true;
        }
        if (status != Status.DIRTY && status != Status.ERROR) {
            return false;
        }
        // 没应用的文本先落到物品上，关闭确认框才列得出这些改动；落不下去时第一次 Esc 只提示，免得一按就丢
        if (apply()) {
            return false;
        }
        if (escWarned) {
            return false;
        }
        escWarned = true;
        revealError();
        flash(Component.translatable(LANG + "status.esc_warn"), EditorTheme.DANGER);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = Screen.hasControlDown();
        boolean alt = Screen.hasAltDown();
        if (ctrl && !alt && (keyCode == Keys.KEY_ENTER || keyCode == Keys.KEY_KP_ENTER)) {
            applyByUser();
            return true;
        }
        if (ctrl && !alt && keyCode == Keys.KEY_F) {
            openFind();
            return true;
        }
        if (ctrl && !alt && keyCode == Keys.KEY_G) {
            openGoto();
            return true;
        }
        if (keyCode == Keys.KEY_F3) {
            findStep(Screen.hasShiftDown() ? -1 : 1);
            return true;
        }
        return false;
    }

    // 载入

    /**
     * 从物品重新读（丢掉没应用的改动；文档里用一步替换完成，Ctrl+Z 还能找回来）
     */
    private void startLoad() {
        startLoadFrom(session.loadRoot());
    }

    /**
     * @param root 物品现在的根（loadRoot 给的是独立副本，交给后台线程之后这边不再改它）
     */
    private void startLoadFrom(@Nonnull NBTTagCompound root) {
        cancelJob();
        seenRevision = session.revision();
        if (smallEnough(root)) {
            finishLoad(root, formatRoot(root), false);
            // 调用方可能刚把做完的任务摘掉（pumpJob 发现结果作废后改成同步载入），这时 cancelJob 不会再刷新，
            // 不在这里刷新的话编辑框会一直只读、"载入中"的遮罩也不消失
            updateBusy();
            return;
        }
        status = Status.LOADING;
        startRootJob(loaded ? JobKind.RELOAD : JobKind.LOAD, root);
        invalidateStatus();
    }

    private void startRootJob(@Nonnull JobKind kind, @Nonnull NBTTagCompound root) {
        CompletableFuture<JobResult> future = CompletableFuture.supplyAsync(
                () -> new JobResult(formatRoot(root), null), Ui.backgroundExecutor());
        job = new Job(kind, doc.version(), session.revision(), root, future);
        updateBusy();
    }

    /**
     * 格式化一份根；格式化器是递归的，嵌套极深时退回原版的一行输出，连那也溢出就给空文本（不至于让游戏崩掉）
     */
    @Nonnull
    private static String formatRoot(@Nonnull NBTTagCompound root) {
        try {
            return SnbtFormatter.pretty(root);
        } catch (RuntimeException | StackOverflowError e) {
            try {
                return root.toString();
            } catch (RuntimeException | StackOverflowError ignored) {
                return "";
            }
        }
    }

    /**
     * 根小到可以直接在主线程格式化
     * <p>
     * 1.12.2 的标签没有现成的大小统计，这里按存盘格式粗算字节数，超过门槛就停，不把大 NBT 整个走一遍；
     * 嵌套深到递归溢出的当成大的，交给后台线程（那边格式化失败也有兜底）。
     */
    private static boolean smallEnough(@Nonnull NBTTagCompound root) {
        try {
            return new SizeBudget(SYNC_FORMAT_BYTES).take(root);
        } catch (StackOverflowError e) {
            return false;
        }
    }

    /**
     * 载入结果写进文档
     *
     * @param root    文本对应的根
     * @param text    文本
     * @param applied true = 刚应用完、按实际结果刷新的文本（状态显示"已应用"）
     */
    private void finishLoad(@Nonnull NBTTagCompound root, @Nonnull String text, boolean applied) {
        programmatic = true;
        try {
            if (loaded) {
                // 只替换真正不同的那一段：光标在没变的部分里就留在原处，滚动位置也不跳
                doc.replaceAll(text, TextDocument.EditKind.COMMAND);
            } else {
                doc.setText(text);
            }
        } finally {
            programmatic = false;
        }
        doc.sealHistory();
        loaded = true;
        baselineText = text;
        baselineApplied = applied;
        loadedRoot = root;
        status = applied ? Status.APPLIED : Status.CLEAN;
        if (!applied) {
            appliedNote = null;
        }
        validation = null;
        validateDueAt = 0;
        validateTask = null;
        escWarned = false;
        clearError();
        if (bar == Bar.FIND && !query.isEmpty()) {
            scheduleSearch(0, false);
        }
        invalidateStatus();
    }

    /**
     * 后台任务做完了就取回结果（tick 和 build 里调用）
     */
    private void pumpJob() {
        Job current = job;
        if (current == null || !current.future.isDone()) {
            return;
        }
        job = null;
        JobResult result;
        try {
            result = current.future.join();
        } catch (CompletionException | CancellationException e) {
            result = new JobResult(null, null);
        }
        if (current.kind.fromRoot()) {
            NBTTagCompound root = current.root;
            if (root == null) {
                updateBusy();
                return;
            }
            // 这期间物品在别处被改过（离开本页时）：结果作废，按现在的物品重来
            if (session.revision() != current.revision) {
                NBTTagCompound now = session.loadRoot();
                if (!now.equals(root)) {
                    // 同步载入和重新开后台任务两条路都会自己刷新只读状态和遮罩
                    startLoadFrom(now);
                    return;
                }
            }
            // 后台格式化抛了异常才会没有文本（formatRoot 自己兜过底，这里只是保险）
            String text = result.text != null ? result.text : formatRoot(root);
            finishLoad(root, text, current.kind == JobKind.CANONICAL);
        } else {
            finishTextJob(current.kind, current.docVersion, result);
        }
        updateBusy();
    }

    private void cancelJob() {
        if (job != null) {
            job = null;
            updateBusy();
        }
    }

    /**
     * 有后台任务时编辑框只读并盖一层提示，会改文本的按钮也暂时不能点
     */
    private void updateBusy() {
        Job current = job;
        boolean busy = current != null;
        if (box != null) {
            box.setReadOnly(busy);
            box.setOverlay(busy ? Component.translatable(LANG + overlayKey(current.kind)) : null);
        }
        if (editButtons != null) {
            for (int i = 0; i < editButtons.length; i++) {
                // 复制（5）随时可用；重新载入（6）本身就会取消进行中的任务
                if (i != 5 && i != 6) {
                    editButtons[i].active = !busy;
                }
            }
        }
    }

    @Nonnull
    private static String overlayKey(@Nonnull JobKind kind) {
        switch (kind) {
            case FORMAT:
                return "formatting";
            case COMPACT:
                return "compacting";
            default:
                return "loading";
        }
    }

    // 文本改动 / 校验

    /**
     * 文档改了（玩家输入、撤销、格式化、颜色码转换……）：判断状态，安排校验和重新查找
     */
    private void onDocChanged(@Nonnull TextDocument.Change change) {
        if (programmatic) {
            return;
        }
        escWarned = false;
        appliedNote = null;
        if (doc.contentEquals(baselineText)) {
            // 改回了和物品一致的样子（例如撤销到底）
            status = baselineApplied ? Status.APPLIED : Status.CLEAN;
            validation = null;
            validateDueAt = 0;
            clearError();
        } else {
            // 已经在报错时先留着错误提示，等新的校验结果出来再换，打字时状态栏不会红绿闪
            if (status != Status.ERROR) {
                status = Status.DIRTY;
            }
            validateDueAt = Ui.millis() + VALIDATE_DELAY_MS;
        }
        // 查找栏关着时不跟着重查；F3 用到过期结果时会自己先重查
        if (bar == Bar.FIND && !query.isEmpty()) {
            scheduleSearch(SEARCH_DELAY_MS, false);
        }
        invalidateStatus();
    }

    private void pumpValidation() {
        CompletableFuture<SnbtValidator.Result> task = validateTask;
        if (task != null && task.isDone()) {
            validateTask = null;
            try {
                onValidated(validateTaskVersion, task.join());
            } catch (CompletionException | CancellationException e) {
                validateDueAt = Ui.millis();
            }
        }
        if (validateDueAt == 0 || Ui.millis() < validateDueAt || validateTask != null || job != null) {
            return;
        }
        validateDueAt = 0;
        long version = doc.version();
        String text = doc.snapshot();
        if (text.length() <= SYNC_TEXT_CHARS) {
            onValidated(version, SnbtValidator.validate(text));
            return;
        }
        validateTaskVersion = version;
        validateTask = CompletableFuture.supplyAsync(() -> SnbtValidator.validate(text), Ui.backgroundExecutor());
    }

    /**
     * 校验结果回来了；文本在这期间又改过就丢掉（改动时已经安排了新的校验）
     */
    private void onValidated(long version, @Nonnull SnbtValidator.Result result) {
        if (version != doc.version() || (status != Status.DIRTY && status != Status.ERROR)) {
            return;
        }
        rememberValidation(version, result);
        if (result.ok()) {
            status = Status.DIRTY;
            clearError();
        } else {
            showParseError(result);
        }
        invalidateStatus();
    }

    private void rememberValidation(long version, @Nonnull SnbtValidator.Result result) {
        validation = result;
        validationVersion = version;
        itemChange = null;
        itemProblem = null;
        if (result.ok()) {
            inspectItemId(result.tag());
        }
    }

    /**
     * 物品模式下看一眼解析出的 id：和现在的不同就提示应用后会换物品，写法不对或物品不存在就提前说明应用会失败
     * <p>
     * 判断规则和 {@link EditorSession#pushRoot} 一致（都走 {@link EditorSession#parseItemId}）：
     * 没写命名空间的按 minecraft 补全，大写字母转成小写。
     */
    private void inspectItemId(@Nullable NBTTagCompound parsed) {
        if (parsed == null || !session.isItem() || !parsed.hasKey(TAG_ID, TAG_STRING)) {
            return;
        }
        String text = parsed.getString(TAG_ID).trim();
        ResourceLocation id = EditorSession.parseItemId(text);
        if (id == null) {
            itemProblem = Ui.translate("gui.battlecorrection.editor.item.invalid_id", text);
            return;
        }
        if (id.equals(session.itemId())) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        if (item == null || item == Items.AIR) {
            itemProblem = Ui.translate("gui.battlecorrection.editor.item.unknown", id.toString());
            return;
        }
        itemChange = id.toString();
    }

    private void showParseError(@Nullable SnbtValidator.Result result) {
        status = Status.ERROR;
        if (result == null) {
            errorText = Component.translatable(RAW + "status.error", "?");
            setErrorOffset(-1);
            return;
        }
        String reason = result.messageKey() != null
                ? Ui.translate(result.messageKey(), result.messageArgs())
                : result.message();
        errorText = Component.translatable(LANG + "status.parse_error", result.line(), result.column(), Component.raw(reason));
        setErrorOffset(result.offset());
    }

    private void clearError() {
        errorText = null;
        setErrorOffset(-1);
    }

    private void setErrorOffset(int offset) {
        errorOffset = offset;
        if (box != null) {
            box.setErrorMarker(offset);
        }
    }

    // 应用

    private void applyByUser() {
        if (!apply()) {
            revealError();
        }
    }

    /**
     * 解析文本并写回物品副本
     *
     * @return true = 没有要应用的，或者应用成功
     */
    private boolean apply() {
        if (!loaded || (status != Status.DIRTY && status != Status.ERROR)) {
            return true;
        }
        // 格式化 / 压缩还没做完：应用的是现在这份文本，那个任务作废
        if (job != null && !job.kind.fromRoot()) {
            cancelJob();
        }
        long version = doc.version();
        SnbtValidator.Result result = validation != null && validationVersion == version
                ? validation : SnbtValidator.validate(doc.snapshot());
        rememberValidation(version, result);
        validateDueAt = 0;
        if (!result.ok() || result.tag() == null) {
            showParseError(result);
            invalidateStatus();
            return false;
        }
        NBTTagCompound parsed = result.tag();
        ResourceLocation before = session.isItem() ? session.itemId() : null;
        try {
            session.pushRoot(parsed);
        } catch (IllegalArgumentException e) {
            status = Status.ERROR;
            errorText = Component.translatable(LANG + "status.apply_failed", Component.raw(String.valueOf(e.getMessage())));
            setErrorOffset(-1);
            invalidateStatus();
            return false;
        }
        doc.sealHistory();
        clearError();
        escWarned = false;
        status = Status.APPLIED;
        baselineApplied = true;
        ResourceLocation after = session.isItem() ? session.itemId() : null;
        appliedNote = after != null && !after.equals(before)
                ? Component.translatable(LANG + "status.applied_item", Component.raw(after.toString())) : null;

        // 写回的实际结果和文本不一致（数量夹到范围里、id 补了命名空间、Count 写成了 int、物品不认的字段被丢掉……）：
        // 文本刷新成实际结果，免得显示的和物品里的不一样
        NBTTagCompound actual = session.loadRoot();
        loadedRoot = actual;
        baselineText = doc.snapshot();
        if (!actual.equals(parsed)) {
            if (appliedNote == null) {
                appliedNote = Component.translatable(LANG + "status.applied_adjusted");
            }
            if (smallEnough(actual)) {
                finishLoad(actual, formatRoot(actual), true);
            } else {
                startRootJob(JobKind.CANONICAL, actual);
            }
        }
        invalidateStatus();
        return true;
    }

    private void revealError() {
        if (box != null && status == Status.ERROR && errorOffset >= 0) {
            box.revealOffset(Math.min(errorOffset, doc.length()), true);
        }
    }

    private void jumpToError() {
        if (errorOffset < 0 || box == null) {
            return;
        }
        int offset = Math.min(errorOffset, doc.length());
        doc.moveCursor(offset, false);
        box.revealOffset(offset, true);
        screen.setFocused(box);
    }

    // 按钮

    /**
     * 格式化 / 压成一行：先解析（文本没改过就用校验时缓存的结果），解析失败标出错误，成功就整篇换成新文本（一步撤销）
     */
    private void runTextJob(@Nonnull JobKind kind) {
        if (job != null || !loaded) {
            return;
        }
        long version = doc.version();
        SnbtValidator.Result cached = validation != null && validationVersion == version ? validation : null;
        String text = doc.snapshot();
        if (text.length() <= SYNC_TEXT_CHARS) {
            finishTextJob(kind, version, transform(kind, text, cached));
            return;
        }
        CompletableFuture<JobResult> future = CompletableFuture.supplyAsync(() -> transform(kind, text, cached),
                Ui.backgroundExecutor());
        job = new Job(kind, version, session.revision(), null, future);
        updateBusy();
    }

    /**
     * 格式化 / 压缩本身（纯数据处理，可以在后台线程跑）
     */
    @Nonnull
    private static JobResult transform(@Nonnull JobKind kind, @Nonnull String text, @Nullable SnbtValidator.Result cached) {
        SnbtValidator.Result result = cached != null ? cached : SnbtValidator.validate(text);
        NBTTagCompound tag = result.tag();
        if (!result.ok() || tag == null) {
            return new JobResult(null, result);
        }
        try {
            String out = kind == JobKind.FORMAT ? SnbtFormatter.pretty(tag) : SnbtFormatter.compact(text, tag);
            return new JobResult(out, result);
        } catch (RuntimeException | StackOverflowError e) {
            return new JobResult(null, new SnbtValidator.Result(null, 0, 1, 1, "", SnbtValidator.KEY_TOO_DEEP));
        }
    }

    private void finishTextJob(@Nonnull JobKind kind, long version, @Nonnull JobResult result) {
        if (doc.version() != version) {
            return;
        }
        SnbtValidator.Result validated = result.validation;
        if (validated != null && validated.ok()) {
            rememberValidation(version, validated);
        }
        String text = result.text;
        if (text == null) {
            showParseError(validated);
            invalidateStatus();
            revealError();
            return;
        }
        if (!doc.replaceAll(text, TextDocument.EditKind.COMMAND)) {
            flash(Component.translatable(LANG + "flash.unchanged"), EditorTheme.TEXT_MUTED);
        }
    }

    /**
     * & ↔ § 转换：作用于选区，没有选区就是全文；只转合法的颜色码
     */
    private void convertCodes(boolean toSection) {
        if (job != null || !loaded) {
            return;
        }
        int count = SnbtEditing.convertCodes(doc, toSection);
        if (count > 0) {
            flash(Component.translatable(LANG + "flash.converted", count), EditorTheme.SUCCESS);
        } else {
            flash(Component.translatable(LANG + "flash.converted_none"), EditorTheme.TEXT_MUTED);
        }
    }

    private void copyAll() {
        String text = doc.snapshot();
        Ui.setClipboard(text);
        flash(Component.translatable(LANG + "flash.copied", text.length()), EditorTheme.SUCCESS);
    }

    /**
     * 丢掉没应用的改动，按物品现在的样子重新载入（文档里是一步替换，Ctrl+Z 能找回来）
     */
    private void reload() {
        startLoad();
    }

    // 查找

    /**
     * Ctrl+F：打开查找栏；选中的是一小段单行文字就拿来当查找词
     */
    private void openFind() {
        if (searchBox == null) {
            return;
        }
        searchOrigin = doc.selectionStart();
        String prefill = null;
        if (doc.hasSelection() && doc.selectionEnd() - doc.selectionStart() <= FIND_PREFILL_MAX) {
            String selected = doc.selectedText();
            if (selected.indexOf('\n') < 0) {
                prefill = selected;
            }
        }
        bar = Bar.FIND;
        applyBarVisibility();
        if (prefill != null && !prefill.equals(query)) {
            searchBox.setValue(prefill);
        } else if (!query.isEmpty()) {
            scheduleSearch(0, false);
        }
        screen.setFocused(searchBox);
        searchBox.moveCursorToEnd();
        searchBox.setHighlightPos(0);
    }

    /**
     * Ctrl+G：打开跳到行
     */
    private void openGoto() {
        if (gotoBox == null) {
            return;
        }
        bar = Bar.GOTO;
        applyBarVisibility();
        gotoBox.setHint(Component.translatable(LANG + "goto.hint", doc.lineCount()));
        gotoBox.setValue("");
        screen.setFocused(gotoBox);
    }

    private void closeBar() {
        bar = Bar.NONE;
        applyBarVisibility();
        if (box != null) {
            screen.setFocused(box);
        }
    }

    /**
     * 查找框里的按键（输入框自己处理之前）：回车上下跳；上下方向键界面会拿去切焦点，这里截下来当上一个 / 下一个；
     * Ctrl+Z / Y 吃掉——查找框没有自己的撤销，放过去就成了界面的整体撤销，会把没应用的文本一起丢掉
     */
    private boolean handleFindKey(int keyCode, int modifiers) {
        boolean ctrl = Screen.hasControlDown();
        if (isAltGrLetter(keyCode)) {
            return true;
        }
        switch (keyCode) {
            case Keys.KEY_ENTER:
            case Keys.KEY_KP_ENTER:
                if (ctrl) {
                    applyByUser();
                } else {
                    findStep(Screen.hasShiftDown() ? -1 : 1);
                }
                return true;
            case Keys.KEY_UP:
                findStep(-1);
                return true;
            case Keys.KEY_DOWN:
                findStep(1);
                return true;
            case Keys.KEY_Z:
            case Keys.KEY_Y:
                return ctrl;
            default:
                return false;
        }
    }

    private boolean handleGotoKey(int keyCode, int modifiers) {
        boolean ctrl = Screen.hasControlDown();
        if (isAltGrLetter(keyCode)) {
            return true;
        }
        switch (keyCode) {
            case Keys.KEY_ENTER:
            case Keys.KEY_KP_ENTER:
                if (ctrl) {
                    applyByUser();
                } else {
                    goToLine();
                }
                return true;
            case Keys.KEY_UP:
            case Keys.KEY_DOWN:
                return true;
            case Keys.KEY_Z:
            case Keys.KEY_Y:
                return ctrl;
            default:
                return false;
        }
    }

    /**
     * Ctrl+Alt 加字母：多半是 AltGr 在打字（Windows 上 AltGr 会附带按下一个左 Ctrl；波兰语 AltGr+S = ś）。
     * 输入框不认这种组合，万一放过去就成了界面的 Ctrl+S 保存、Ctrl+Z 整体撤销；字符本身随后从 charTyped 进来，按键吃掉即可
     */
    private static boolean isAltGrLetter(int keyCode) {
        return Screen.hasControlDown() && Screen.hasAltDown() && keyCode >= Keys.KEY_A && keyCode <= Keys.KEY_Z;
    }

    /**
     * 跳到行的输入只收数字和一个冒号（行:列）
     */
    private static boolean isLineSpec(@Nonnull String value) {
        int colons = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ':') {
                colons++;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return colons <= 1;
    }

    private void goToLine() {
        if (gotoBox == null || box == null) {
            return;
        }
        String value = gotoBox.getValue().trim();
        if (value.isEmpty()) {
            closeBar();
            return;
        }
        int colon = value.indexOf(':');
        long lineNumber = parseNumber(colon >= 0 ? value.substring(0, colon) : value, 1);
        long columnNumber = colon >= 0 ? parseNumber(value.substring(colon + 1), 1) : 1;
        int line = (int) Mth.clamp(lineNumber - 1, 0L, (long) doc.lineCount() - 1);
        int lineStart = doc.lineStart(line);
        int column = (int) Mth.clamp(columnNumber - 1, 0L, (long) doc.lineEnd(line) - lineStart);
        int offset = lineStart + column;
        doc.moveCursor(offset, false);
        box.revealOffset(offset, true);
        closeBar();
    }

    private static long parseNumber(@Nonnull String text, long fallback) {
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            // 只收数字，超出 long 才会到这里：当成很大的数，夹到最后一行
            return Long.MAX_VALUE;
        }
    }

    private void toggleCase() {
        caseSensitive = !caseSensitive;
        if (caseButton != null) {
            caseButton.style(caseSensitive ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL);
        }
        if (!query.isEmpty()) {
            scheduleSearch(0, false);
        }
    }

    /**
     * 查找词变了：从打开查找栏时的位置往后找第一个命中并选中（边打边找）
     */
    private void onQueryChanged(@Nonnull String value) {
        if (value.equals(query)) {
            return;
        }
        query = value;
        pendingStep = 0;
        if (value.isEmpty()) {
            searchTask = null;
            searchDueAt = 0;
            searchResult = null;
            if (box != null) {
                box.setSearchResult(null);
            }
            return;
        }
        scheduleSearch(doc.length() <= SYNC_TEXT_CHARS ? 0 : SEARCH_DELAY_MS, true);
    }

    /**
     * 安排一次查找
     *
     * @param delayMs 多久之后（0 = 马上）
     * @param jump    结果出来后要不要选中第一个命中
     */
    private void scheduleSearch(long delayMs, boolean jump) {
        if (jump) {
            searchJump = true;
        }
        if (delayMs <= 0) {
            runSearch();
        } else {
            searchDueAt = Ui.millis() + delayMs;
        }
    }

    private void runSearch() {
        searchDueAt = 0;
        if (query.isEmpty()) {
            return;
        }
        long version = doc.version();
        String needle = query;
        boolean matchCase = caseSensitive;
        if (doc.length() <= SYNC_TEXT_CHARS) {
            searchTask = null;
            onSearchResult(TextSearch.findAll(doc.chars(), needle, matchCase, version), needle, matchCase);
            return;
        }
        String text = doc.snapshot();
        searchTaskQuery = needle;
        searchTaskCase = matchCase;
        searchTask = CompletableFuture.supplyAsync(() -> TextSearch.findAll(text, needle, matchCase, version),
                Ui.backgroundExecutor());
    }

    private void pumpSearch() {
        CompletableFuture<TextSearch.Result> task = searchTask;
        if (task != null && task.isDone()) {
            searchTask = null;
            try {
                onSearchResult(task.join(), searchTaskQuery, searchTaskCase);
            } catch (CompletionException | CancellationException e) {
                searchDueAt = Ui.millis();
            }
        }
        if (searchDueAt != 0 && Ui.millis() >= searchDueAt && searchTask == null) {
            runSearch();
        }
    }

    /**
     * 查找结果回来了：查找词、大小写和文本都还是当时的样子才用，否则重查
     */
    private void onSearchResult(@Nonnull TextSearch.Result result, @Nonnull String needle, boolean matchCase) {
        if (!needle.equals(query) || matchCase != caseSensitive || result.version() != doc.version()) {
            if (!query.isEmpty() && searchDueAt == 0) {
                searchDueAt = Ui.millis();
            }
            return;
        }
        searchResult = result;
        if (box != null) {
            box.setSearchResult(bar == Bar.FIND ? result : null);
        }
        if (searchJump) {
            searchJump = false;
            if (result.count() > 0) {
                selectHit(result, result.next(searchOrigin));
            }
        }
        if (pendingStep != 0) {
            int step = pendingStep;
            pendingStep = 0;
            findStep(step);
        }
    }

    /**
     * 上一个 / 下一个命中（首尾相接）；结果过期时先重查，查完再走这一步
     *
     * @param delta 1 = 下一个，-1 = 上一个
     */
    private void findStep(int delta) {
        if (query.isEmpty()) {
            openFind();
            return;
        }
        TextSearch.Result result = searchResult;
        if (result == null || result.version() != doc.version() || searchTask != null || searchDueAt != 0) {
            pendingStep = delta;
            if (searchTask == null) {
                runSearch();
            }
            return;
        }
        if (result.count() == 0) {
            return;
        }
        int index = result.indexOf(doc.selectionStart(), doc.selectionEnd());
        int target;
        if (index >= 0) {
            target = Math.floorMod(index + delta, result.count());
        } else {
            target = delta > 0 ? result.next(doc.cursor()) : result.previous(doc.selectionStart());
        }
        selectHit(result, target);
        searchOrigin = result.hits()[target];
    }

    private void selectHit(@Nonnull TextSearch.Result result, int index) {
        if (index < 0 || index >= result.count()) {
            return;
        }
        int start = result.hits()[index];
        doc.setSelection(start, start + result.length());
        if (box != null) {
            box.revealOffset(start, true);
        }
    }

    // 粗算字节数

    /**
     * 按存盘格式（NBTTagCompound.write）累计字节数，超出预算就立刻停
     * <p>
     * 字符串按修改版 UTF-8 算长度；只用来决定在哪个线程格式化，差几个字节无所谓。
     */
    private static final class SizeBudget {

        private long left;

        SizeBudget(long budget) {
            this.left = budget;
        }

        /**
         * @return true = 还在预算内
         */
        boolean take(@Nonnull NBTBase tag) {
            switch (tag.getId()) {
                case 1:
                    return spend(1);
                case 2:
                    return spend(2);
                case 3:
                case 5:
                    return spend(4);
                case 4:
                case 6:
                    return spend(8);
                case 7:
                    return spend(4L + ((NBTTagByteArray) tag).getByteArray().length);
                case 8:
                    return spendString(((NBTTagString) tag).getString());
                case 9: {
                    NBTTagList list = (NBTTagList) tag;
                    // 元素类型 + 个数
                    if (!spend(5)) {
                        return false;
                    }
                    for (int i = 0; i < list.tagCount(); i++) {
                        if (!take(list.get(i))) {
                            return false;
                        }
                    }
                    return true;
                }
                case 10: {
                    NBTTagCompound compound = (NBTTagCompound) tag;
                    // 结尾的 TAG_End
                    if (!spend(1)) {
                        return false;
                    }
                    for (String key : compound.getKeySet()) {
                        // 每项一个类型字节 + 键名 + 值
                        if (!spend(1) || !spendString(key)) {
                            return false;
                        }
                        NBTBase value = compound.getTag(key);
                        if (value != null && !take(value)) {
                            return false;
                        }
                    }
                    return true;
                }
                case 11:
                    return spend(4L + 4L * ((NBTTagIntArray) tag).getIntArray().length);
                case 12:
                    return spend(4L + 8L * SnbtFormatter.longArrayData((NBTTagLongArray) tag).length);
                default:
                    return spend(1);
            }
        }

        private boolean spend(long bytes) {
            left -= bytes;
            return left >= 0;
        }

        private boolean spendString(@Nonnull String text) {
            // 每个字符至少一个字节，光看长度就超了的不用再细数
            if (!spend(2L + text.length())) {
                return false;
            }
            long extra = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == 0 || c >= 0x80) {
                    extra += c < 0x800 ? 1 : 2;
                }
            }
            return spend(extra);
        }
    }

    // 状态栏的一行（裁剪结果和悬停提示按宽度缓存）

    /**
     * 按原样画：量宽、裁剪、提示框都不解析 §
     */
    private final class StatusLine {

        private Component text = Component.empty();
        private int color = EditorTheme.TEXT_DIM;
        private int width = -1;
        private Component shown = Component.empty();
        private boolean truncated;
        @Nullable
        private List<Component> tooltip;
        private int tooltipWidth = -1;

        void set(@Nonnull Component text, int color) {
            this.text = text;
            this.color = color;
            this.width = -1;
            this.tooltip = null;
        }

        @Nonnull
        Component shown(int maxWidth) {
            if (maxWidth != width) {
                width = maxWidth;
                String plain = text.getString();
                truncated = font.widthRaw(plain) > maxWidth;
                shown = Component.raw(truncated
                        ? font.plainSubstrByWidth(plain, Math.max(0, maxWidth - font.widthRaw(ELLIPSIS))) + ELLIPSIS
                        : plain);
            }
            return shown;
        }

        @Nonnull
        List<Component> tooltip(int wrapWidth) {
            if (tooltip == null || tooltipWidth != wrapWidth) {
                tooltip = font.split(Component.raw(text.getString()), wrapWidth);
                tooltipWidth = wrapWidth;
            }
            return tooltip;
        }
    }
}
