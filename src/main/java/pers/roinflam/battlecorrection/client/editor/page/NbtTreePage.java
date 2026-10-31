package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.NbtType;
import pers.roinflam.battlecorrection.client.editor.tree.NbtLists;
import pers.roinflam.battlecorrection.client.editor.tree.NbtPath;
import pers.roinflam.battlecorrection.client.editor.tree.NbtSearch;
import pers.roinflam.battlecorrection.client.editor.tree.NbtTreeModel;
import pers.roinflam.battlecorrection.client.editor.tree.NumberConversion;
import pers.roinflam.battlecorrection.client.editor.tree.TreeNode;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.EditBox;
import pers.roinflam.battlecorrection.client.editor.ui.GuiEventListener;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;
import pers.roinflam.battlecorrection.client.editor.ui.Ui;
import pers.roinflam.battlecorrection.client.editor.widget.ButtonRows;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.NbtTreeView;
import pers.roinflam.battlecorrection.client.editor.widget.PopupList;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.client.editor.widget.TreeEditBox;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.LegacyText;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NBT 树页：整件物品的 NBT 按树状结构增删改
 * <p>
 * 物品模式下树的根是存盘时的完整结构 {@code {id, Count, Damage, tag}}；实体 / 方块实体模式下根就是整份 NBT，
 * 下面的 id / Count / Damage 规则不适用。物品模式规则：
 * <ul>
 *   <li>id 可以改成别的物品（必须是已注册的物品 ID，没写命名空间按 minecraft: 补），由 session 换成新物品
 *   （旧物品有子类型时损害值归零，否则当耐久带过去）；Count 取 1 到数量上限；Damage 取 0 到 32767，
 *   有子类型的物品（羊毛、染料）改它就等于换成另一种；这三个都不能删，根下面的四个键都不能改名</li>
 *   <li>根下面只能有这四个键，想加东西选中 tag 再加；tag 删掉 = 清空物品 NBT</li>
 *   <li>选中一行后，下面的名称/值框里改，点"应用"或按回车才写入；容器（复合/列表）没有值可改；
 *   不在输入框里时 Delete 删除、Ctrl+C / Ctrl+V 复制粘贴、Ctrl+D 重复一份、Ctrl+R 改名、Alt+↑↓ 移动列表元素；点列表空白处取消选中。
 *   名称框没动过时应用只改值，键名原样保留（前后空格、超长、空键名都不受影响）</li>
 *   <li>添加：选中容器后选类型点"添加"；列表的元素类型必须一致，非空列表会自动锁定类型</li>
 *   <li>复制/粘贴：复制的节点记在编辑器内部（连名字一起），同时以 SNBT 写进系统剪贴板；
 *   粘贴优先用内部记的，没有就把系统剪贴板当 SNBT 解析</li>
 *   <li>选中数字时"类型"按钮变成类型转换：byte/short/int/long/float/double 互转，值不变，超范围或丢精度拒绝</li>
 * </ul>
 * 顶上一行是搜索（Ctrl+F，回车 / Shift+回车在命中之间跳），树下面是路径栏：显示选中节点的路径（新版本 /data 的写法），
 * 也可以粘贴一个路径回车直接跳过去。右键节点有菜单。
 * <p>
 * 树的展开、分页、选中、滚动状态放在本页的 {@link NbtTreeModel} 里，切到别的页再回来、撤销重做之后都还在。
 * 每次改动立刻同步到物品副本（Count → 数量，Damage → 损害值，tag → NBT），预览和其他页看到的都是最新的。
 * 改叶子值只刷新那一行，增删才重建所在容器；搜索在后台线程上对一份拷贝做，数据没变就复用同一份拷贝。
 * <p>
 * 1.12.2 的列表放进类型不同的元素不报错、直接丢掉，删空了还记着旧类型，所以列表元素的增删改一律走 {@link NbtLists}。
 */
public class NbtTreePage extends EditorPageBase {

    private static final String KEY_ID = "id";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_DAMAGE = "Damage";
    private static final String KEY_TAG = "tag";
    private static final String LANG = "gui.battlecorrection.editor.tree.";
    private static final int BOX_HEIGHT = 14;
    private static final int GAP = 4;
    /**
     * 顶上搜索行占的高度（输入框 + 间隙）
     */
    private static final int SEARCH_ROW = 18;
    /**
     * 树下面路径栏占的高度
     */
    private static final int PATH_ROW = 18;
    /**
     * 底部表单：名称/值一行 + 提示行 + 按钮一行（按钮排成两行时再加 {@link #BUTTON_ROW_STEP}）
     */
    private static final int FORM_HEIGHT = 44;
    /**
     * 按钮每多一行，表单加高多少
     */
    private static final int BUTTON_ROW_STEP = BOX_HEIGHT + ButtonRows.ROW_GAP;
    /**
     * 按钮放不下一行时，从第几个开始换到第二行（上移 下移 全部展开 全部折叠 在第二行）
     */
    private static final int BUTTON_ROW_BREAK = 6;
    private static final int VALUE_MAX_LENGTH = FlatEditBox.DEFAULT_MAX_LENGTH * 4;
    /**
     * 名称框平时的长度上限；选中的键更长时临时放宽到键的长度，免得填进去就被截断
     */
    private static final int NAME_MAX_LENGTH = 256;
    /**
     * 输入框最窄多宽（窗口再窄也不让宽度变成负数、和旁边的控件叠在一起）
     */
    private static final int MIN_BOX_WIDTH = 24;
    /**
     * 名称框、值框各自至少要多宽；两个加起来放不下时先把左边的"名称""值"两个字去掉，腾地方给输入框
     */
    private static final int MIN_NAME_WIDTH = 40;
    private static final int MIN_VALUE_WIDTH = 60;
    /**
     * 停止打字多少 tick 后开始搜
     */
    private static final int SEARCH_DEBOUNCE_TICKS = 4;
    private static final int TOOLTIP_WIDTH = 240;
    private static final int TOOLTIP_MAX_CHARS = 600;
    private static final int COUNT_LABEL_WIDTH = 48;
    private static final String ELLIPSIS = "…";
    private static final int STATUS_NEUTRAL = EditorTheme.ACCENT_HOVER;
    /**
     * 剪贴板里不是复合标签时，套一层这个交给 JsonToNBT 解析（见 {@link #parseSnbtValue}）
     */
    private static final String WRAP_KEY = "v";
    private static final String WRAP_HEAD = "{" + WRAP_KEY + ":[";
    private static final String WRAP_TAIL = "]}";

    /**
     * 编辑器内部的剪贴板：带名字的一份标签副本。静态，切页、重开编辑器都还在
     */
    @Nullable
    private static ClipboardEntry clipboard;

    private static final class ClipboardEntry {

        private final String name;
        private final NBTBase tag;

        ClipboardEntry(@Nonnull String name, @Nonnull NBTBase tag) {
            this.name = name;
            this.tag = tag;
        }

        @Nonnull
        String name() {
            return name;
        }

        @Nonnull
        NBTBase tag() {
            return tag;
        }
    }

    /**
     * 类型弹出列表里的一项
     */
    private static final class TypeRow implements ScrollList.Row {

        private final NbtType type;

        TypeRow(@Nonnull NbtType type) {
            this.type = type;
        }

        @Nonnull
        NbtType type() {
            return type;
        }

        @Nonnull
        @Override
        public Component label() {
            return type.displayName();
        }

        @Nullable
        @Override
        public Component secondary() {
            return Component.literal(type.badge());
        }
    }

    /**
     * 右键菜单里的一项
     */
    private static final class MenuItem implements ScrollList.Row {

        private final Component label;
        private final int color;
        private final Runnable action;

        MenuItem(@Nonnull Component label, int color, @Nonnull Runnable action) {
            this.label = label;
            this.color = color;
            this.action = action;
        }

        @Nonnull
        @Override
        public Component label() {
            return label;
        }

        @Override
        public int color() {
            return color;
        }

        @Nonnull
        Runnable action() {
            return action;
        }
    }

    /**
     * 按钮行里一个按钮的描述（先量好宽度再统一排）
     */
    private static final class ButtonSpec {

        private final Component label;
        private final Runnable action;
        private final int minWidth;
        private final int textWidth;
        private final int gapAfter;
        private final FlatButton.Style style;
        @Nullable
        private final Component tooltip;

        /**
         * @param label     标签
         * @param action    点击动作
         * @param minWidth  宽裕时至少给多宽
         * @param textWidth 按多宽的文字留位置（类型按钮会换成别的类型名，按最宽的那个算）
         * @param gapAfter  后面的间距
         * @param style     样式
         * @param tooltip   悬停提示
         */
        ButtonSpec(@Nonnull Component label, @Nonnull Runnable action, int minWidth, int textWidth,
                   int gapAfter, @Nonnull FlatButton.Style style, @Nullable Component tooltip) {
            this.label = label;
            this.action = action;
            this.minWidth = minWidth;
            this.textWidth = textWidth;
            this.gapAfter = gapAfter;
            this.style = style;
            this.tooltip = tooltip;
        }

        @Nonnull
        Component label() {
            return label;
        }

        @Nonnull
        Runnable action() {
            return action;
        }

        int minWidth() {
            return minWidth;
        }

        int textWidth() {
            return textWidth;
        }

        int gapAfter() {
            return gapAfter;
        }

        @Nonnull
        FlatButton.Style style() {
            return style;
        }

        @Nullable
        Component tooltip() {
            return tooltip;
        }
    }

    private final PopupList<TypeRow> typePopup = new PopupList<>();
    private final PopupList<MenuItem> menuPopup = new PopupList<>();
    private final NbtTreeModel model = new NbtTreeModel();
    private boolean firstBuild = true;

    private NBTTagCompound root = new NBTTagCompound();
    /**
     * 每改一次数据加一；搜索快照按它判断要不要重新拷
     */
    private int dataVersion;
    private NbtType addType = NbtType.STRING;
    /**
     * 类型按钮现在是"转换数字类型"而不是"选要添加的类型"
     */
    private boolean typeButtonConverts;
    /**
     * 表单当前对应的节点
     */
    @Nullable
    private TreeNode formNode;

    // 状态栏
    private Component status = Component.empty();
    private int statusColor = EditorTheme.TEXT_DIM;
    @Nullable
    private String statusShown;
    private int statusShownLimit = -1;
    private int statusShownWidth;
    private boolean statusClipped;
    @Nullable
    private List<Component> statusTooltip;

    // 提示行（字符串值的彩色预览 / 输入提示）
    @Nullable
    private Component hintLine;
    private int hintColor = EditorTheme.TEXT_DIM;

    // 搜索
    private String searchQuery = "";
    private int searchDebounce;
    private boolean searchJump;
    @Nullable
    private CompletableFuture<NbtSearch.Result> searchTask;
    @Nullable
    private AtomicBoolean searchCancel;
    @Nullable
    private NbtSearch.Result searchResult;
    private int searchCursor = -1;
    @Nullable
    private NBTTagCompound searchSnapshot;
    private int snapshotVersion = -1;
    private String searchCountText = "";
    private int searchCountColor = EditorTheme.TEXT_MUTED;

    // 控件
    @Nullable
    private NbtTreeView tree;
    @Nullable
    private TreeEditBox searchBox;
    @Nullable
    private TreeEditBox pathBox;
    @Nullable
    private FlatButton copyPathButton;
    @Nullable
    private TreeEditBox nameBox;
    /**
     * 选中节点时填进名称框的文字：框里还是这段文字就说明没改名，应用时直接沿用原键名，
     * 不 trim、不受框的长度上限影响（键名前后带空格、超长、为空都是合法的 NBT）
     */
    private String nameShown = "";
    @Nullable
    private TreeEditBox valueBox;
    @Nullable
    private FlatButton ampButton;
    @Nullable
    private FlatButton applyButton;
    @Nullable
    private FlatButton addButton;
    @Nullable
    private FlatButton typeButton;
    @Nullable
    private FlatButton deleteButton;
    @Nullable
    private FlatButton copyButton;
    @Nullable
    private FlatButton pasteButton;
    @Nullable
    private FlatButton duplicateButton;
    @Nullable
    private FlatButton upButton;
    @Nullable
    private FlatButton downButton;

    // 布局
    private int formTop;
    private int pathTop;
    private int countX;
    private int hintY;
    /**
     * 名称 / 值两个标签字画不画（窗口太窄时去掉，输入框里的灰字提示照样说明是什么）
     */
    private boolean editLabelsShown = true;
    private final Component nameLabel = Component.translatable(LANG + "name");
    private final Component valueLabel = Component.translatable(LANG + "value");
    private final Component capsNote = Component.translatable(LANG + "caps_note");
    private int capsNoteWidth;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public NbtTreePage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.TREE;
    }

    @Override
    public boolean wantsPreview() {
        return false;
    }

    @Override
    protected void build() {
        typePopup.close();
        menuPopup.close();
        model.setLabels(Component.translatable(session.isItem() ? LANG + "root" : LANG + "root.nbt").getString(),
                remaining -> Component.translatable(LANG + "more", remaining).getString());
        if (firstBuild) {
            firstBuild = false;
            if (session.isItem()) {
                model.setExpanded(NbtPath.ROOT.child(KEY_TAG), true);
            }
        }
        // 每次挂载都从 session 重读：别的页、撤销重做都可能改过物品
        reloadFromSession();

        capsNoteWidth = font.width(capsNote);
        statusShown = null;
        // 按钮一行放不下要排成两行，表单跟着加高，所以先排按钮再定表单和树的位置
        List<ButtonSpec> buttonSpecs = buttonSpecs();
        ButtonRows buttonRows = layoutButtons(buttonSpecs);
        formTop = y + height - FORM_HEIGHT - (buttonRows.rows() - 1) * BUTTON_ROW_STEP;
        pathTop = formTop - PATH_ROW;
        int treeTop = y + SEARCH_ROW;
        // 控件按从上到下的顺序登记，Tab 切焦点的顺序就是 搜索 → 树 → 路径栏 → 表单
        buildSearchRow();
        tree = add(new NbtTreeView(x, treeTop, width, Math.max(1, pathTop - 3 - treeTop), model)
                .onSelect(this::onNodeSelected)
                .onContextMenu(this::openContextMenu)
                .onKey(this::handleTreeKey));
        buildPathRow();
        buildEditRow();
        hintY = formTop + 2 + BOX_HEIGHT + 3;
        buildButtonRow(formTop + FORM_HEIGHT - BOX_HEIGHT, buttonSpecs, buttonRows);

        onNodeSelected(model.selected());
        tree.scrollToSelection();
        updateSearchCount();
    }

    private void buildSearchRow() {
        int searchWidth = Mth.clamp(width * 2 / 5, 90, 180);
        searchBox = add(new TreeEditBox(font, x + 1, y, searchWidth - 2, BOX_HEIGHT,
                Component.translatable(LANG + "search.hint")).onKey(this::handleSearchKey));
        searchBox.setMaxLength(256);
        // 先填值再挂回调，重建页面时不会被当成一次新的输入
        searchBox.setValue(searchQuery);
        searchBox.setResponder(this::onSearchChanged);
        int buttonX = x + searchWidth + GAP;
        add(new FlatButton(buttonX, y, 16, BOX_HEIGHT, Component.literal("▲"), this::previousHit)
                .tooltip(Component.translatable(LANG + "search.prev.tooltip")));
        add(new FlatButton(buttonX + 18, y, 16, BOX_HEIGHT, Component.literal("▼"), this::nextHit)
                .tooltip(Component.translatable(LANG + "search.next.tooltip")));
        countX = buttonX + 18 + 16 + GAP;
    }

    private void buildPathRow() {
        Component copyLabel = Component.translatable(LANG + "path.copy");
        // 复制按钮让着路径栏：窗口再窄，路径栏也留出最小宽度
        int copyWidth = Math.max(0, Math.min(font.width(copyLabel) + 12, width - GAP - 2 - MIN_BOX_WIDTH));
        pathBox = add(new TreeEditBox(font, x + 1, pathTop, width - copyWidth - GAP - 2, BOX_HEIGHT,
                Component.translatable(LANG + "path.hint")).onKey(this::handlePathKey));
        pathBox.setMaxLength(2048);
        copyPathButton = add(new FlatButton(x + width - copyWidth, pathTop, copyWidth, BOX_HEIGHT, copyLabel, this::copyPath)
                .tooltip(Component.translatable(LANG + "path.copy.tooltip")));
    }

    private void buildEditRow() {
        int row1 = formTop + 2;
        Component applyLabel = Component.translatable(LANG + "apply");
        // 按钮上要真的显示 § 号：语言文件里写不了 §（会被当成格式码），作为参数代进去，按钮按原样画
        Component ampLabel = Component.translatable(LANG + "amp", String.valueOf(LegacyText.SECTION));
        int applyWidth = Math.max(40, ButtonRows.minWidthFor(font.width(applyLabel)));
        int ampWidth = Math.max(28, ButtonRows.minWidthFor(FlatButton.rawWidth(font, ampLabel)));
        int nameLabelWidth = font.width(nameLabel) + 6;
        int valueLabelWidth = font.width(valueLabel) + 6;
        // 两个输入框能分到的宽度：总宽减去 名称框后的间距、值框后的间距、两个按钮及其间距、左右各 1 像素
        int boxes = width - GAP - (GAP + ampWidth + GAP + applyWidth) - 2;
        editLabelsShown = boxes - nameLabelWidth - valueLabelWidth >= MIN_NAME_WIDTH + MIN_VALUE_WIDTH;
        if (editLabelsShown) {
            boxes -= nameLabelWidth + valueLabelWidth;
        } else {
            nameLabelWidth = 0;
            valueLabelWidth = 0;
        }
        int nameWidth = Math.min(Mth.clamp(width / 4, 50, 110), Math.max(MIN_NAME_WIDTH, boxes - MIN_VALUE_WIDTH));
        nameWidth = Math.max(MIN_BOX_WIDTH, nameWidth);
        int valueWidth = Math.max(MIN_BOX_WIDTH, boxes - nameWidth);
        nameBox = add(new TreeEditBox(font, x + nameLabelWidth + 1, row1, nameWidth, BOX_HEIGHT,
                Component.translatable(LANG + "name.hint")));
        nameBox.setMaxLength(NAME_MAX_LENGTH);
        int valueX = x + nameLabelWidth + 1 + nameWidth + GAP + valueLabelWidth;
        valueBox = add(new TreeEditBox(font, valueX, row1, valueWidth, BOX_HEIGHT,
                Component.translatable(LANG + "value.hint"))
                .keepSectionSigns(true)
                .onKey(this::handleValueKey));
        valueBox.setMaxLength(VALUE_MAX_LENGTH);
        valueBox.setResponder(value -> updateHint());
        int ampX = valueX + valueWidth + 1 + GAP;
        ampButton = add(new FlatButton(ampX, row1, ampWidth, BOX_HEIGHT, ampLabel, this::convertAmpersands)
                .raw()
                .keepFocus()
                .tooltip(Component.translatable(LANG + "amp.tooltip")));
        applyButton = add(new FlatButton(ampX + ampWidth + GAP, row1, applyWidth, BOX_HEIGHT, applyLabel, this::applyEdit)
                .style(FlatButton.Style.PRIMARY)
                .tooltip(Component.translatable(LANG + "apply.tooltip")));
    }

    /**
     * 底部按钮：添加 类型 | 删除 复制 粘贴 重复 | ↑ ↓ | 全部展开 全部折叠
     */
    @Nonnull
    private List<ButtonSpec> buttonSpecs() {
        // 类型按钮会换成任意一种类型名（选要添加的类型、数字类型转换），按最宽的那个留位置
        int typeTextWidth = 0;
        for (NbtType type : NbtType.values()) {
            typeTextWidth = Math.max(typeTextWidth, font.width(type.displayName()));
        }
        List<ButtonSpec> specs = new ArrayList<>();
        specs.add(spec(Component.translatable(LANG + "add"), this::addChild, 32, GAP,
                FlatButton.Style.NORMAL, Component.translatable(LANG + "add.tooltip")));
        specs.add(new ButtonSpec(addType.displayName(), this::openTypePopup, 72, typeTextWidth, GAP * 2,
                FlatButton.Style.NORMAL, Component.translatable(LANG + "type.tooltip")));
        specs.add(spec(Component.translatable(LANG + "delete"), this::deleteSelected, 28, GAP,
                FlatButton.Style.DANGER, Component.translatable(LANG + "delete.tooltip")));
        specs.add(spec(Component.translatable(LANG + "copy"), this::copySelected, 28, GAP,
                FlatButton.Style.NORMAL, Component.translatable(LANG + "copy.tooltip")));
        specs.add(spec(Component.translatable(LANG + "paste"), this::pasteInto, 28, GAP,
                FlatButton.Style.NORMAL, Component.translatable(LANG + "paste.tooltip")));
        specs.add(spec(Component.translatable(LANG + "duplicate"), this::duplicateSelected, 28, GAP * 2,
                FlatButton.Style.NORMAL, Component.translatable(LANG + "duplicate.tooltip")));
        specs.add(spec(Component.translatable(LANG + "move_up"), () -> moveSelected(-1), 16, 2,
                FlatButton.Style.NORMAL, Component.translatable(LANG + "move_up.tooltip")));
        specs.add(spec(Component.translatable(LANG + "move_down"), () -> moveSelected(1), 16, GAP * 2,
                FlatButton.Style.NORMAL, Component.translatable(LANG + "move_down.tooltip")));
        specs.add(spec(Component.translatable(LANG + "expand_all"), this::expandAll, 36, GAP,
                FlatButton.Style.NORMAL, null));
        specs.add(spec(Component.translatable(LANG + "collapse_all"), this::collapseAll, 36, 0,
                FlatButton.Style.NORMAL, null));
        return specs;
    }

    @Nonnull
    private ButtonSpec spec(@Nonnull Component label, @Nonnull Runnable action, int minWidth, int gapAfter,
                            @Nonnull FlatButton.Style style, @Nullable Component tooltip) {
        return new ButtonSpec(label, action, minWidth, font.width(label), gapAfter, style, tooltip);
    }

    /**
     * 一行放得下就一行；放不下分两行（第二行从上移开始），每个按钮都至少能把字完整显示出来
     */
    @Nonnull
    private ButtonRows layoutButtons(@Nonnull List<ButtonSpec> specs) {
        int count = specs.size();
        int[] preferred = new int[count];
        int[] minimum = new int[count];
        int[] gaps = new int[count];
        for (int i = 0; i < count; i++) {
            ButtonSpec spec = specs.get(i);
            minimum[i] = ButtonRows.minWidthFor(spec.textWidth());
            preferred[i] = Math.max(spec.minWidth(), spec.textWidth() + 10);
            gaps[i] = spec.gapAfter();
        }
        return ButtonRows.layout(width, preferred, minimum, gaps, BUTTON_ROW_BREAK);
    }

    /**
     * 按排好的位置建按钮
     *
     * @param top    第一行按钮的上沿
     * @param specs  按钮描述
     * @param layout 排版结果
     */
    private void buildButtonRow(int top, @Nonnull List<ButtonSpec> specs, @Nonnull ButtonRows layout) {
        List<FlatButton> buttons = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            ButtonSpec spec = specs.get(i);
            FlatButton button = new FlatButton(x + layout.offset(i), top + layout.row(i) * BUTTON_ROW_STEP,
                    layout.width(i), BOX_HEIGHT, spec.label(), spec.action())
                    .style(spec.style());
            if (spec.tooltip() != null) {
                button.tooltip(spec.tooltip());
            }
            buttons.add(add(button));
        }
        addButton = buttons.get(0);
        typeButton = buttons.get(1);
        deleteButton = buttons.get(2);
        copyButton = buttons.get(3);
        pasteButton = buttons.get(4);
        duplicateButton = buttons.get(5);
        upButton = buttons.get(6);
        downButton = buttons.get(7);
    }

    // 绘制

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int textY = y + (BOX_HEIGHT - 8) / 2;
        if (!searchCountText.isEmpty()) {
            g.drawString(font, searchCountText, countX, textY, searchCountColor, false);
        }
        // 搜索行右侧：状态；没有状态时提示一下能力数据的事
        int statusLeft = countX + COUNT_LABEL_WIDTH;
        int statusLimit = x + width - statusLeft;
        if (statusLimit > 20) {
            String shown = statusText(statusLimit);
            if (shown != null) {
                g.drawString(font, shown, x + width - statusShownWidth, textY, statusColor, false);
            } else if (session.isItem() && session.hasForgeCaps() && capsNoteWidth <= statusLimit) {
                g.drawString(font, capsNote, x + width - capsNoteWidth, textY, EditorTheme.TEXT_DIM, false);
            }
        }

        EditorTheme.divider(g, x, formTop - 2, width);
        int row1 = formTop + 2 + (BOX_HEIGHT - 8) / 2;
        if (editLabelsShown) {
            label(g, nameLabel, x, row1);
            if (nameBox != null) {
                label(g, valueLabel, nameBox.getX() + nameBox.getWidth() + GAP, row1);
            }
        }
        if (hintLine != null) {
            g.enableScissor(x, hintY - 1, x + width, hintY + 9);
            g.drawString(font, hintLine, x + 1, hintY, hintColor, false);
            g.disableScissor();
        }
    }

    /**
     * 状态文字按可用宽度截断（结果缓存，状态或宽度变了才重算）
     * <p>
     * 状态里可能带着用户输入的文字，其中的 § 码画的时候按格式解析，截断也按解析后的宽度算。
     *
     * @return 要画的文字；没有状态返回 null
     */
    @Nullable
    private String statusText(int limit) {
        if (statusShown == null || statusShownLimit != limit) {
            String plain = status.getString();
            if (plain.isEmpty()) {
                statusShown = "";
            } else if (font.width(plain) > limit) {
                int room = Math.max(0, limit - font.width(ELLIPSIS));
                statusShown = font.substrByWidth(Component.literal(plain), room).toLegacy() + ELLIPSIS;
                statusClipped = true;
            } else {
                statusShown = plain;
                statusClipped = false;
            }
            statusShownLimit = limit;
            statusShownWidth = font.width(statusShown);
        }
        return statusShown.isEmpty() ? null : statusShown;
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        typePopup.render(g, mouseX, mouseY, partialTick);
        menuPopup.render(g, mouseX, mouseY, partialTick);
        if (typePopup.isOpen() || menuPopup.isOpen()) {
            return;
        }
        // 值被截断的行：悬停显示完整内容（提示框的行在节点上缓存，只算一次）
        if (tree != null && tree.isMouseOver(mouseX, mouseY)) {
            TreeNode hovered = tree.hoveredNode();
            if (hovered != null && hovered.renderValueClipped) {
                if (hovered.renderTooltip == null) {
                    hovered.renderTooltip = tooltipLines(hovered.tag);
                }
                g.renderTooltip(font, hovered.renderTooltip, mouseX, mouseY);
            }
            return;
        }
        // 状态被截断：悬停看全文
        if (statusClipped && mouseY >= y && mouseY < y + BOX_HEIGHT && mouseX >= countX + COUNT_LABEL_WIDTH && mouseX < x + width) {
            if (statusTooltip == null) {
                statusTooltip = font.split(status, TOOLTIP_WIDTH);
            }
            g.renderTooltip(font, statusTooltip, mouseX, mouseY);
        }
    }

    @Nonnull
    private List<Component> tooltipLines(@Nonnull NBTBase tag) {
        String text = NbtType.toEditable(tag);
        if (text == null) {
            text = TreeNode.summary(tag);
        }
        if (text.length() > TOOLTIP_MAX_CHARS) {
            text = text.substring(0, TOOLTIP_MAX_CHARS) + ELLIPSIS;
        }
        return font.split(Component.literal(text), TOOLTIP_WIDTH);
    }

    // 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menuPopup.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return typePopup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return menuPopup.mouseScrolled(mouseX, mouseY, delta) || typePopup.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean onEscape() {
        if (menuPopup.isOpen() || typePopup.isOpen()) {
            menuPopup.close();
            typePopup.close();
            return true;
        }
        GuiEventListener focused = screen.getFocused();
        // 搜索框里按 Esc：清空搜索，界面不关
        if (focused == searchBox && searchBox != null && !searchBox.getValue().isEmpty()) {
            searchBox.setValue("");
            screen.setFocused(tree);
            return true;
        }
        // 路径栏里按 Esc：放弃输入，恢复成选中节点的路径
        if (focused == pathBox && pathBox != null) {
            pathBox.setValue(formNode == null ? "" : formNode.path.toString());
            screen.setFocused(tree);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == Keys.KEY_F) {
            focusSearch();
            return true;
        }
        GuiEventListener focused = screen.getFocused();
        if (keyCode == Keys.KEY_ENTER || keyCode == Keys.KEY_KP_ENTER) {
            if (focused == nameBox || focused == valueBox) {
                applyEdit();
                return true;
            }
        }
        if (focused instanceof EditBox) {
            return false;
        }
        return handleTreeKey(keyCode, modifiers);
    }

    /**
     * 不在输入框里时的快捷键（树有焦点时由树先转过来，焦点在按钮上时由界面转过来）
     *
     * @return true = 已处理
     */
    private boolean handleTreeKey(int keyCode, int modifiers) {
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        boolean alt = Screen.hasAltDown();
        if (keyCode == Keys.KEY_DELETE && !ctrl && !alt) {
            if (model.selected() == null) {
                return false;
            }
            deleteSelected();
            return true;
        }
        if (ctrl && !alt) {
            switch (keyCode) {
                case Keys.KEY_C:
                    if (shift) {
                        copyPath();
                    } else {
                        copySelected();
                    }
                    return true;
                case Keys.KEY_V:
                    pasteInto();
                    return true;
                case Keys.KEY_D:
                    duplicateSelected();
                    return true;
                case Keys.KEY_R:
                    // 改名：跳到名称框并全选。不用 F2——它是原版的截图键，界面里按下去照样会截图
                    if (shift || nameBox == null || !nameBox.active) {
                        return false;
                    }
                    screen.setFocused(nameBox);
                    nameBox.moveCursorToEnd();
                    nameBox.setHighlightPos(0);
                    return true;
                default:
                    return false;
            }
        }
        if (alt && !ctrl && (keyCode == Keys.KEY_UP || keyCode == Keys.KEY_DOWN)) {
            moveSelected(keyCode == Keys.KEY_UP ? -1 : 1);
            return true;
        }
        // 回车选中叶子 = 开始改值；容器的回车留给树去展开折叠
        if ((keyCode == Keys.KEY_ENTER || keyCode == Keys.KEY_KP_ENTER) && !ctrl && !alt) {
            TreeNode node = model.selectedForRender();
            if (node != null && !node.isContainer() && valueBox != null && valueBox.active) {
                screen.setFocused(valueBox);
                valueBox.moveCursorToEnd();
                valueBox.setHighlightPos(0);
                return true;
            }
        }
        return false;
    }

    /**
     * 搜索框：回车 / ↓ 下一个，Shift+回车 / ↑ 上一个
     */
    private boolean handleSearchKey(int keyCode, int modifiers) {
        switch (keyCode) {
            case Keys.KEY_ENTER:
            case Keys.KEY_KP_ENTER:
                if (Screen.hasShiftDown()) {
                    previousHit();
                } else {
                    nextHit();
                }
                return true;
            case Keys.KEY_DOWN:
                nextHit();
                return true;
            case Keys.KEY_UP:
                previousHit();
                return true;
            default:
                return false;
        }
    }

    /**
     * 路径栏：回车跳转
     */
    private boolean handlePathKey(int keyCode, int modifiers) {
        if (keyCode == Keys.KEY_ENTER || keyCode == Keys.KEY_KP_ENTER) {
            if (pathBox != null) {
                jumpToPath(pathBox.getValue());
            }
            return true;
        }
        return false;
    }

    /**
     * 值输入框：选中的是数字时 ↑↓ 加减 1，按住 Shift 加减 10（只改框里的文字，回车才写入）
     */
    private boolean handleValueKey(int keyCode, int modifiers) {
        if (keyCode != Keys.KEY_UP && keyCode != Keys.KEY_DOWN) {
            return false;
        }
        TreeNode node = formNode;
        if (node == null || valueBox == null || node.type() == null || !node.type().isNumber()) {
            return false;
        }
        int step = (keyCode == Keys.KEY_UP ? 1 : -1) * (Screen.hasShiftDown() ? 10 : 1);
        String text = valueBox.getValue().trim();
        try {
            NbtType type = node.type();
            if (type == NbtType.FLOAT || type == NbtType.DOUBLE) {
                double value = Double.parseDouble(text) + step;
                valueBox.setValue(type == NbtType.FLOAT ? Float.toString((float) value) : Double.toString(value));
            } else {
                long parsed = Long.parseLong(text);
                // 加到 long 的头就停在头上，不绕回去
                long value = step > 0 ? (parsed > Long.MAX_VALUE - step ? Long.MAX_VALUE : parsed + step)
                        : (parsed < Long.MIN_VALUE - step ? Long.MIN_VALUE : parsed + step);
                long min;
                long max;
                if (isCountNode(node)) {
                    min = ApplyItemEditC2SPacket.MIN_COUNT;
                    max = ApplyItemEditC2SPacket.MAX_COUNT;
                } else if (isDamageNode(node)) {
                    min = 0;
                    max = ApplyItemEditC2SPacket.MAX_DAMAGE;
                } else {
                    min = type == NbtType.BYTE ? Byte.MIN_VALUE : type == NbtType.SHORT ? Short.MIN_VALUE
                            : type == NbtType.INT ? Integer.MIN_VALUE : Long.MIN_VALUE;
                    max = type == NbtType.BYTE ? Byte.MAX_VALUE : type == NbtType.SHORT ? Short.MAX_VALUE
                            : type == NbtType.INT ? Integer.MAX_VALUE : Long.MAX_VALUE;
                }
                valueBox.setValue(String.valueOf(Mth.clamp(value, min, max)));
            }
        } catch (NumberFormatException ignored) {
            // 框里现在不是数字，不动它
        }
        return true;
    }

    private void focusSearch() {
        if (searchBox == null) {
            return;
        }
        screen.setFocused(searchBox);
        searchBox.moveCursorToEnd();
        searchBox.setHighlightPos(0);
    }

    @Override
    public void tick() {
        if (searchDebounce > 0 && --searchDebounce == 0) {
            startSearch();
        }
        pollSearch();
    }

    @Override
    public void onExternalChange() {
        // 撤销重做之后页面会重建、重读数据；搜索结果里的路径可能已经不对了，作废等重搜
        cancelSearch();
        searchResult = null;
        searchCursor = -1;
        model.setSearchMarks(null, null);
    }

    // 选中与表单

    /**
     * 选中变化：把节点的名字和值填进表单，按规则启用/禁用按钮
     *
     * @param node 节点，null = 没选中
     */
    private void onNodeSelected(@Nullable TreeNode node) {
        if (nameBox == null || valueBox == null || applyButton == null || addButton == null || typeButton == null
                || deleteButton == null || copyButton == null || pasteButton == null || duplicateButton == null
                || upButton == null || downButton == null || ampButton == null || pathBox == null || copyPathButton == null) {
            return;
        }
        formNode = node;
        // 路径栏一直跟着选中走；跳转之后这里也会换成规范写法（负下标换成正的）
        pathBox.setValue(node == null ? "" : node.path.toString());
        pathBox.moveCursorToStart();
        if (node == null) {
            nameBox.setMaxLength(NAME_MAX_LENGTH);
            nameBox.setValue("");
            nameShown = "";
            valueBox.setValue("");
            setBoxEnabled(nameBox, false);
            setBoxEnabled(valueBox, false);
            ampButton.active = false;
            applyButton.active = false;
            addButton.active = false;
            typeButton.active = false;
            deleteButton.active = false;
            copyButton.active = false;
            pasteButton.active = false;
            duplicateButton.active = false;
            upButton.active = false;
            downButton.active = false;
            copyPathButton.active = false;
            updateHint();
            return;
        }

        boolean rootChild = isItemRootChild(node);
        boolean container = node.isContainer();

        String name = node.isRoot() ? "" : node.isListChild() ? "[" + node.index + "]" : node.key == null ? "" : node.key;
        // 超长的键也要完整放进框里：setValue 会按上限截断，截完再应用就成了改名
        nameBox.setMaxLength(Math.max(NAME_MAX_LENGTH, name.length()));
        nameBox.setValue(name);
        nameShown = nameBox.getValue();
        setBoxEnabled(nameBox, !node.isRoot() && !node.isListChild() && !rootChild);

        String editable = NbtType.toEditable(node.tag);
        if (editable == null) {
            valueBox.setMaxLength(VALUE_MAX_LENGTH);
            valueBox.setValue(TreeNode.summary(node.tag));
            setBoxEnabled(valueBox, false);
        } else {
            // 超长字符串也要完整放进框里，不然一应用就被截断了
            valueBox.setMaxLength(Math.max(VALUE_MAX_LENGTH, editable.length()));
            valueBox.setValue(editable);
            setBoxEnabled(valueBox, true);
            valueBox.moveCursorToStart();
        }
        ampButton.active = node.tag instanceof NBTTagString && !isIdNode(node);
        applyButton.active = nameBox.active || valueBox.active;

        // 类型按钮：容器 = 选要添加的类型（非空列表锁定元素类型）；数字 = 类型转换
        NbtType lockedType = lockedListType(node);
        if (lockedType != null) {
            addType = lockedType;
        }
        NbtType type = node.type();
        typeButtonConverts = !container && type != null && type.isNumber() && !rootChild;
        if (typeButtonConverts) {
            typeButton.setMessage(type.displayName());
            typeButton.tooltip(Component.translatable(LANG + "type.convert.tooltip"));
            typeButton.active = true;
        } else {
            typeButton.setMessage(addType.displayName());
            typeButton.tooltip(Component.translatable(LANG + "type.tooltip"));
            typeButton.active = container && lockedType == null;
        }
        addButton.active = container;

        deleteButton.active = !node.isRoot() && !isRequiredRootChild(node);
        copyButton.active = !node.isRoot();
        pasteButton.active = container || (node.parent != null && node.parent.isContainer());
        duplicateButton.active = !node.isRoot() && !rootChild;
        int siblings = node.parent == null ? 0 : node.parent.childCount();
        upButton.active = node.isListChild() && node.index > 0;
        downButton.active = node.isListChild() && node.index < siblings - 1;
        copyPathButton.active = !node.isRoot();
        updateHint();
    }

    /**
     * 输入框的可用状态：不可用时同时设成不可编辑，免得焦点还留在里面时继续打字
     */
    private static void setBoxEnabled(@Nonnull EditBox box, boolean enabled) {
        box.active = enabled;
        box.setEditable(enabled);
    }

    /**
     * 物品模式下根节点的直接子节点（id / Count / Damage / tag）
     */
    private boolean isItemRootChild(@Nonnull TreeNode node) {
        return session.isItem() && node.parent != null && node.parent.isRoot();
    }

    /**
     * 物品必须有的 id、Count 和 Damage：可以改值，不能删
     * <p>
     * Damage 删了也没用：写回时缺了它就保持原来的损害值，树上却显示成没了，所以干脆不让删。
     */
    private boolean isRequiredRootChild(@Nonnull TreeNode node) {
        return isItemRootChild(node) && (KEY_ID.equals(node.key) || KEY_COUNT.equals(node.key) || KEY_DAMAGE.equals(node.key));
    }

    private boolean isIdNode(@Nonnull TreeNode node) {
        return isItemRootChild(node) && KEY_ID.equals(node.key);
    }

    private boolean isCountNode(@Nonnull TreeNode node) {
        return isItemRootChild(node) && KEY_COUNT.equals(node.key);
    }

    private boolean isDamageNode(@Nonnull TreeNode node) {
        return isItemRootChild(node) && KEY_DAMAGE.equals(node.key);
    }

    /**
     * 非空列表的元素类型；不是列表或列表为空返回 null
     */
    @Nullable
    private static NbtType lockedListType(@Nonnull TreeNode node) {
        if (node.tag instanceof NBTTagList && !((NBTTagList) node.tag).hasNoTags()) {
            return NbtType.byId(NbtLists.elementType((NBTTagList) node.tag));
        }
        return null;
    }

    /**
     * 刷新值输入框下面那一行：字符串显示带颜色的预览，id 显示会换成什么物品，有子类型的物品的 Damage 显示是哪一种，
     * 其余显示输入提示（只在选中变化、值框文字变化时调，结果缓存到下次变化）
     */
    private void updateHint() {
        TreeNode node = formNode;
        Component hint;
        int color = EditorTheme.TEXT_DIM;
        if (node == null) {
            hint = Component.translatable(LANG + "hint.idle");
        } else if (isIdNode(node)) {
            Item item = valueBox == null ? null : lookupItem(valueBox.getValue());
            if (item == null || item == Items.AIR) {
                hint = Component.translatable(LANG + "hint.id_unknown");
                color = EditorTheme.DANGER;
            } else {
                // 换过去之后的损害值照 session 的规矩：旧物品有子类型时归零，否则当耐久带过去
                ItemStack working = session.working();
                int damage = working.getHasSubtypes() ? 0 : working.getItemDamage();
                hint = Component.translatable(LANG + "hint.id_ok", itemName(item, damage));
                color = EditorTheme.SUCCESS;
            }
        } else if (isCountNode(node)) {
            hint = Component.translatable(LANG + "hint.count", ApplyItemEditC2SPacket.MIN_COUNT, ApplyItemEditC2SPacket.MAX_COUNT);
        } else if (isDamageNode(node)) {
            hint = damageHint();
        } else if (node.tag instanceof NBTTagString) {
            String value = valueBox == null ? ((NBTTagString) node.tag).getString() : valueBox.getValue();
            if (value.indexOf(LegacyText.SECTION) >= 0) {
                // 带颜色码：直接把原文当字面量画出来，§ 码在渲染时生效
                hint = Component.literal(value);
                color = EditorTheme.TEXT;
            } else {
                hint = Component.translatable(LANG + "hint.string");
            }
        } else if (node.type() != null && node.type().isNumber()) {
            NbtType type = node.type();
            String range = type.integerRange();
            hint = range != null
                    ? Component.translatable(LANG + "hint.integer", type.displayName(), range)
                    : Component.translatable(LANG + "hint.decimal", type.displayName());
        } else if (node.isContainer()) {
            hint = Component.translatable(LANG + "hint.container", node.childCount());
        } else if (NbtType.arraySize(node.tag) >= 0) {
            // 走到这里的只剩三种数组（列表在上一个分支）
            hint = Component.translatable(LANG + "hint.array", NbtType.arraySize(node.tag));
        } else {
            hint = Component.empty();
        }
        hintLine = hint;
        hintColor = color;
    }

    /**
     * Damage 的提示：有子类型的物品显示框里这个值对应哪一种，其余显示范围
     */
    @Nonnull
    private Component damageHint() {
        ItemStack working = session.working();
        if (working.getHasSubtypes() && valueBox != null) {
            try {
                long typed = Long.parseLong(valueBox.getValue().trim());
                if (typed >= 0 && typed <= ApplyItemEditC2SPacket.MAX_DAMAGE) {
                    return Component.translatable(LANG + "hint.damage_subtype", itemName(working.getItem(), (int) typed));
                }
            } catch (NumberFormatException ignored) {
                // 框里不是数字，退回显示范围
            }
        }
        return Component.translatable(LANG + "hint.damage", 0, ApplyItemEditC2SPacket.MAX_DAMAGE);
    }

    /**
     * 物品在某个损害值下的名字（不带自定义名字）
     * <p>
     * 有的模组物品按损害值查数组取名字，值不在它认的范围里会直接抛异常，这里退回注册名。
     */
    @Nonnull
    private static Component itemName(@Nonnull Item item, int damage) {
        try {
            return Component.literal(item.getItemStackDisplayName(new ItemStack(item, 1, damage)));
        } catch (RuntimeException e) {
            ResourceLocation id = item.getRegistryName();
            return Component.literal(id == null ? "?" : id.toString());
        }
    }

    /**
     * 按输入框里的文字找物品（没写命名空间按 minecraft: 补，大小写不敏感）
     *
     * @return 物品；格式不对或没注册返回 null
     */
    @Nullable
    private static Item lookupItem(@Nonnull String text) {
        ResourceLocation id = EditorSession.parseItemId(text);
        return id == null || !ForgeRegistries.ITEMS.containsKey(id) ? null : ForgeRegistries.ITEMS.getValue(id);
    }

    private void setStatus(@Nonnull String key, int color, @Nonnull Object... args) {
        setStatus(Component.translatable(LANG + "status." + key, args), color);
    }

    private void setStatus(@Nonnull Component text, int color) {
        status = text;
        statusColor = color;
        statusShown = null;
        statusTooltip = null;
        statusClipped = false;
    }

    private void clearStatus() {
        setStatus(Component.empty(), EditorTheme.TEXT_DIM);
    }

    // 编辑

    /**
     * 把表单里的名称/值写进选中节点
     * <p>
     * 只改值：把新标签放回原位、刷新那一行；改了名：父容器重建，展开状态跟着搬到新名字下。
     */
    private void applyEdit() {
        if (tree == null || nameBox == null || valueBox == null) {
            return;
        }
        TreeNode node = model.selected();
        if (node == null || node.isRoot() || node.isMore() || node.parent == null) {
            return;
        }
        if (isIdNode(node)) {
            applyItemId();
            return;
        }

        NBTBase newTag = node.tag;
        if (valueBox.active) {
            if (isCountNode(node) || isDamageNode(node)) {
                long value;
                try {
                    value = Long.parseLong(valueBox.getValue().trim());
                } catch (NumberFormatException e) {
                    setStatus("bad_value", EditorTheme.DANGER, valueBox.getValue().trim());
                    return;
                }
                if (isCountNode(node)) {
                    if (value < ApplyItemEditC2SPacket.MIN_COUNT || value > ApplyItemEditC2SPacket.MAX_COUNT) {
                        setStatus("count_range", EditorTheme.DANGER, ApplyItemEditC2SPacket.MIN_COUNT, ApplyItemEditC2SPacket.MAX_COUNT);
                        return;
                    }
                    // NBT 里 Count 是 byte
                    newTag = new NBTTagByte((byte) value);
                } else {
                    if (value < 0 || value > ApplyItemEditC2SPacket.MAX_DAMAGE) {
                        setStatus("damage_range", EditorTheme.DANGER, 0, ApplyItemEditC2SPacket.MAX_DAMAGE);
                        return;
                    }
                    // NBT 里 Damage 是 short
                    newTag = new NBTTagShort((short) value);
                }
            } else {
                NbtType type = node.type();
                if (type == null) {
                    return;
                }
                try {
                    newTag = type.parse(valueBox.getValue());
                } catch (IllegalArgumentException e) {
                    setStatus("bad_value", EditorTheme.DANGER, String.valueOf(e.getMessage()));
                    return;
                }
            }
        }

        TreeNode parent = node.parent;
        boolean renamed = false;
        if (node.isListChild()) {
            NBTTagList list = (NBTTagList) parent.tag;
            if (newTag != node.tag) {
                if (!NbtLists.set(list, node.index, newTag)) {
                    NbtType elementType = NbtType.byId(NbtLists.elementType(list));
                    setStatus("list_type", EditorTheme.DANGER, elementType == null ? "?" : elementType.displayName().getString());
                    return;
                }
                node.tag = newTag;
                model.leafChanged(node);
            }
        } else {
            NBTTagCompound compound = (NBTTagCompound) parent.tag;
            String oldName = node.key == null ? "" : node.key;
            // 名称框没动过就是只改值：沿用原键名，前后空格、超长、空键名都原样保留；真改了名才 trim 和校验
            String typedName = nameBox.getValue();
            boolean nameEdited = nameBox.active && !typedName.equals(nameShown);
            String newName = nameEdited ? typedName.trim() : oldName;
            if (nameEdited && newName.isEmpty()) {
                setStatus("name_empty", EditorTheme.DANGER);
                return;
            }
            if (!newName.equals(oldName)) {
                if (compound.hasKey(newName)) {
                    setStatus("name_exists", EditorTheme.DANGER);
                    return;
                }
                compound.removeTag(oldName);
                compound.setTag(newName, newTag);
                model.renamed(node.path, parent.path.child(newName), parent);
                model.refresh();
                renamed = true;
            } else if (newTag != node.tag) {
                compound.setTag(oldName, newTag);
                node.tag = newTag;
                model.leafChanged(node);
            }
        }

        if (!pushToSession()) {
            return;
        }
        onNodeSelected(model.selected());
        if (renamed) {
            tree.scrollToSelection();
        }
        setStatus("applied", EditorTheme.SUCCESS);
    }

    /**
     * 改 id：校验是已注册的物品，交给 session 换成新物品，然后整份重读（新物品的数据可能不一样）
     * <p>
     * 不走 pushRoot：那样会把树上现在的 Damage 原样带给新物品，而有子类型的物品（羊毛的颜色之类）
     * 换成别的物品时损害值得归零，这个规矩在 {@link EditorSession#changeItem(ResourceLocation)} 里。
     * 树上别的改动每次都已经写回过 session，这里只差 id。
     */
    private void applyItemId() {
        if (valueBox == null) {
            return;
        }
        String typed = valueBox.getValue().trim();
        ResourceLocation id = EditorSession.parseItemId(typed);
        if (id == null) {
            setStatus("id_invalid", EditorTheme.DANGER, typed);
            return;
        }
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            setStatus("id_unknown", EditorTheme.DANGER, id.toString());
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            setStatus("id_air", EditorTheme.DANGER);
            return;
        }
        String oldId = root.getString(KEY_ID);
        if (id.toString().equals(oldId)) {
            valueBox.setValue(oldId);
            setStatus("applied", EditorTheme.SUCCESS);
            return;
        }
        try {
            session.changeItem(id);
        } catch (IllegalArgumentException e) {
            setStatus(Component.literal(String.valueOf(e.getMessage())), EditorTheme.DANGER);
            return;
        }
        dataChanged();
        reloadFromSession();
        model.select(NbtPath.ROOT.child(KEY_ID));
        model.refresh();
        onNodeSelected(model.selected());
        if (id.toString().equals(root.getString(KEY_ID))) {
            setStatus("id_changed", STATUS_NEUTRAL, itemName(item, session.damage()));
        } else {
            setStatus("id_not_applied", EditorTheme.WARNING);
        }
    }

    /**
     * 把值框里的 &x 颜色码转成 §x
     */
    private void convertAmpersands() {
        if (valueBox == null || !valueBox.active) {
            return;
        }
        String converted = ampersandToSection(valueBox.getValue());
        if (!converted.equals(valueBox.getValue())) {
            valueBox.setValue(converted);
        }
        screen.setFocused(valueBox);
    }

    /**
     * 后面紧跟 0-9、a-f、k-o、r（大小写都认）的 & 换成 §，别的 & 原样留着
     *
     * @param text 文本
     * @return 转换后的文本；没有可转的返回原对象
     */
    @Nonnull
    private static String ampersandToSection(@Nonnull String text) {
        StringBuilder out = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == LegacyText.AMPERSAND && i + 1 < text.length() && isFormattingCode(text.charAt(i + 1))) {
                if (out == null) {
                    out = new StringBuilder(text.length());
                    out.append(text, 0, i);
                }
                out.append(LegacyText.SECTION);
            } else if (out != null) {
                out.append(c);
            }
        }
        return out == null ? text : out.toString();
    }

    private static boolean isFormattingCode(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f') || (lower >= 'k' && lower <= 'o') || lower == 'r';
    }

    /**
     * 类型按钮：容器 → 选要添加的类型；数字 → 选要转换成的类型
     */
    private void openTypePopup() {
        if (typeButton == null) {
            return;
        }
        menuPopup.close();
        if (typeButtonConverts) {
            int popupHeight = 5 * ScrollList.ROW_HEIGHT + 2;
            openConvertPopup(typeButton.getX(), typeButton.getY() - popupHeight - 2, typeButton.getY() - 2);
            return;
        }
        List<TypeRow> rows = new ArrayList<>();
        for (NbtType type : NbtType.values()) {
            rows.add(new TypeRow(type));
        }
        int popupHeight = Math.min(rows.size(), 10) * ScrollList.ROW_HEIGHT + 2;
        typePopup.open(typeButton.getX(), typeButton.getY() - popupHeight - 2, Math.max(typeButton.getWidth(), 120),
                typeButton.getY() - 2, rows, row -> {
            addType = row.type();
            typeButton.setMessage(addType.displayName());
        });
    }

    /**
     * 弹出数字类型列表（不含当前类型，正好 5 项），选了就转换
     *
     * @param left      左
     * @param top       上
     * @param maxBottom 下沿不超过这里（超了列表自动往上收）
     */
    private void openConvertPopup(int left, int top, int maxBottom) {
        TreeNode node = model.selected();
        if (node == null || node.type() == null || !node.type().isNumber()) {
            return;
        }
        List<TypeRow> rows = new ArrayList<>();
        for (NbtType type : NbtType.values()) {
            if (type.isNumber() && type != node.type()) {
                rows.add(new TypeRow(type));
            }
        }
        int popupWidth = 120;
        typePopup.open(Math.min(left, x + width - popupWidth), top, popupWidth, maxBottom, rows,
                row -> convertSelected(row.type()));
    }

    /**
     * 往选中的容器里加一个默认值的子节点
     */
    private void addChild() {
        TreeNode node = model.selected();
        if (node == null || !node.isContainer()) {
            setStatus("select_container", EditorTheme.WARNING);
            return;
        }
        NbtPath newPath = insertChild(node, addType.createDefault(), null, -1);
        if (newPath == null) {
            return;
        }
        model.setExpanded(node.path, true);
        if (!pushToSession()) {
            return;
        }
        revealAndSelect(newPath);
        setStatus("added", EditorTheme.SUCCESS);
        // 新节点马上起名 / 填值
        if (nameBox != null && nameBox.active) {
            screen.setFocused(nameBox);
            nameBox.moveCursorToEnd();
            nameBox.setHighlightPos(0);
        } else if (valueBox != null && valueBox.active) {
            screen.setFocused(valueBox);
        }
    }

    /**
     * 往容器里插一个子节点
     *
     * @param container     容器节点
     * @param tag           要插的标签
     * @param preferredName 复合标签里想用的键名；null = 自动起名
     * @param listIndex     列表里插在哪；-1 = 末尾
     * @return 新节点的路径；失败返回 null（已设置状态）
     */
    @Nullable
    private NbtPath insertChild(@Nonnull TreeNode container, @Nonnull NBTBase tag, @Nullable String preferredName, int listIndex) {
        if (container.tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) container.tag;
            int at = listIndex < 0 || listIndex > list.tagCount() ? list.tagCount() : listIndex;
            if (!NbtLists.accepts(list, tag)) {
                NbtType elementType = NbtType.byId(NbtLists.elementType(list));
                setStatus("list_type", EditorTheme.DANGER, elementType == null ? "?" : elementType.displayName().getString());
                return null;
            }
            if (NbtLists.acceptsInPlace(list, tag)) {
                NbtLists.insert(list, at, tag);
                model.listInserted(container, at);
                return container.path.child(at);
            }
            // 空列表还记着删空之前的元素类型，原地放不进去：换一个只装着它的新列表
            if (!replaceTag(container, NbtLists.of(Collections.singletonList(tag)))) {
                setStatus("list_type", EditorTheme.DANGER, "?");
                return null;
            }
            return container.path.child(0);
        }

        NBTTagCompound compound = (NBTTagCompound) container.tag;
        if (container.isRoot() && session.isItem()) {
            // 物品的根下面只允许 tag；id、Count、Damage 一直都在
            if (compound.hasKey(KEY_TAG) || !(tag instanceof NBTTagCompound)) {
                setStatus("root_add", EditorTheme.WARNING);
                return null;
            }
            compound.setTag(KEY_TAG, tag);
            model.childrenChanged(container);
            return NbtPath.ROOT.child(KEY_TAG);
        }
        String base = preferredName == null || preferredName.isEmpty() ? "new" : preferredName;
        String name = base;
        int suffix = 2;
        while (compound.hasKey(name)) {
            name = base + "_" + suffix++;
        }
        compound.setTag(name, tag);
        model.childrenChanged(container);
        return container.path.child(name);
    }

    /**
     * 把一个节点在父容器里换成另一个标签对象（列表要换元素类型时用：1.12.2 的列表改不了已记下的类型，只能整个换掉）
     * <p>
     * 路径不变，展开 / 选中状态照旧；节点的子节点下次拍平时按新标签重建。
     *
     * @param node        节点（不能是根，根永远是复合标签）
     * @param replacement 新标签
     * @return false = 没换成
     */
    private boolean replaceTag(@Nonnull TreeNode node, @Nonnull NBTBase replacement) {
        TreeNode parent = node.parent;
        if (parent == null) {
            return false;
        }
        if (node.isListChild()) {
            if (!NbtLists.set((NBTTagList) parent.tag, node.index, replacement)) {
                return false;
            }
        } else {
            ((NBTTagCompound) parent.tag).setTag(node.key == null ? "" : node.key, replacement);
        }
        node.tag = replacement;
        model.childrenChanged(node);
        return true;
    }

    /**
     * 删除选中节点，然后选中同一容器里紧挨着的那个（连按 Delete 可以一路删下去）
     */
    private void deleteSelected() {
        TreeNode node = model.selected();
        if (node == null || node.isMore() || node.parent == null) {
            return;
        }
        if (isRequiredRootChild(node)) {
            setStatus("required", EditorTheme.WARNING);
            return;
        }
        TreeNode parent = node.parent;
        NbtPath next = model.selectionAfterRemoval(node);
        if (node.isListChild()) {
            ((NBTTagList) parent.tag).removeTag(node.index);
            model.listRemoved(parent, node.index);
        } else {
            ((NBTTagCompound) parent.tag).removeTag(node.key == null ? "" : node.key);
            model.childrenChanged(parent);
        }
        if (!pushToSession()) {
            return;
        }
        model.select(next);
        model.refresh();
        onNodeSelected(model.selected());
        if (tree != null) {
            tree.scrollToSelection();
        }
        setStatus("deleted", EditorTheme.SUCCESS);
    }

    /**
     * 在同一容器里复制一份选中节点：列表插在它后面，复合标签起名 原名_copy（冲突再加序号）
     */
    private void duplicateSelected() {
        TreeNode node = model.selected();
        if (node == null || node.isRoot() || node.isMore() || node.parent == null) {
            return;
        }
        if (isItemRootChild(node)) {
            setStatus("root_fixed", EditorTheme.WARNING);
            return;
        }
        TreeNode parent = node.parent;
        NBTBase copy = node.tag.copy();
        NbtPath newPath;
        if (node.isListChild()) {
            // 副本和原元素同类型，列表又非空，原地插入一定成功
            NbtLists.insert((NBTTagList) parent.tag, node.index + 1, copy);
            model.listInserted(parent, node.index + 1);
            newPath = parent.path.child(node.index + 1);
        } else {
            NBTTagCompound compound = (NBTTagCompound) parent.tag;
            String base = (node.key == null ? "" : node.key) + "_copy";
            String name = base;
            int suffix = 2;
            while (compound.hasKey(name)) {
                name = base + "_" + suffix++;
            }
            compound.setTag(name, copy);
            model.childrenChanged(parent);
            newPath = parent.path.child(name);
        }
        if (!pushToSession()) {
            return;
        }
        revealAndSelect(newPath);
        setStatus("duplicated", EditorTheme.SUCCESS);
    }

    /**
     * 列表元素上移 / 下移一格
     *
     * @param delta -1 上移，1 下移
     */
    private void moveSelected(int delta) {
        TreeNode node = model.selected();
        if (node == null || node.isMore() || node.parent == null) {
            return;
        }
        if (!node.isListChild()) {
            setStatus("move_list_only", EditorTheme.WARNING);
            return;
        }
        TreeNode parent = node.parent;
        NBTTagList list = (NBTTagList) parent.tag;
        int from = node.index;
        int to = from + delta;
        if (to < 0 || to >= list.tagCount()) {
            setStatus("move_edge", EditorTheme.WARNING);
            return;
        }
        NbtLists.move(list, from, to);
        model.listMoved(parent, from, to);
        if (!pushToSession()) {
            return;
        }
        revealAndSelect(parent.path.child(to));
        setStatus("moved", EditorTheme.SUCCESS);
    }

    /**
     * 数字类型转换。列表元素的类型必须一致，所以选中的是列表元素时整个列表一起转（全部能转才转）
     *
     * @param target 目标类型
     */
    private void convertSelected(@Nonnull NbtType target) {
        TreeNode node = model.selected();
        if (node == null || node.parent == null || !(node.tag instanceof NBTPrimitive) || isItemRootChild(node)) {
            return;
        }
        TreeNode parent = node.parent;
        if (node.isListChild()) {
            NBTTagList list = (NBTTagList) parent.tag;
            List<NBTBase> converted = new ArrayList<>(list.tagCount());
            for (NBTBase element : list) {
                if (!(element instanceof NBTPrimitive)) {
                    return;
                }
                NumberConversion.Result result = NumberConversion.convert((NBTPrimitive) element, target);
                if (result.tag() == null) {
                    reportConversionProblem(element, target, result.problem());
                    return;
                }
                converted.add(result.tag());
            }
            // 不能原地清空再追加：1.12.2 的列表清空了还记着旧类型，新类型的元素会被全部丢掉。换一个新列表放回去
            if (!replaceTag(parent, NbtLists.of(converted))) {
                return;
            }
            model.refresh();
            if (!pushToSession()) {
                return;
            }
            onNodeSelected(model.selected());
            setStatus("converted_list", EditorTheme.SUCCESS, converted.size(), target.displayName());
            return;
        }
        NumberConversion.Result result = NumberConversion.convert((NBTPrimitive) node.tag, target);
        if (result.tag() == null) {
            reportConversionProblem(node.tag, target, result.problem());
            return;
        }
        ((NBTTagCompound) parent.tag).setTag(node.key == null ? "" : node.key, result.tag());
        node.tag = result.tag();
        model.leafChanged(node);
        if (!pushToSession()) {
            return;
        }
        onNodeSelected(model.selected());
        setStatus("converted", EditorTheme.SUCCESS, target.displayName());
    }

    private void reportConversionProblem(@Nonnull NBTBase value, @Nonnull NbtType target, @Nullable NumberConversion.Problem problem) {
        String text = String.valueOf(NbtType.toEditable(value));
        String key = problem == NumberConversion.Problem.NOT_INTEGER ? "convert_not_integer"
                : problem == NumberConversion.Problem.PRECISION ? "convert_precision" : "convert_range";
        setStatus(key, EditorTheme.DANGER, text, target.displayName());
    }

    // 复制粘贴、路径

    /**
     * 复制选中节点：内部记一份，系统剪贴板放 SNBT
     */
    private void copySelected() {
        TreeNode node = model.selected();
        if (node == null || node.isRoot() || node.isMore()) {
            return;
        }
        clipboard = new ClipboardEntry(node.isListChild() || node.key == null ? "" : node.key, node.tag.copy());
        Ui.setClipboard(node.tag.toString());
        setStatus("copied", EditorTheme.SUCCESS);
    }

    /**
     * 只把 SNBT 放进系统剪贴板（右键菜单用，不动内部剪贴板）
     */
    private void copySnbt() {
        TreeNode node = model.selected();
        if (node == null || node.isMore()) {
            return;
        }
        String snbt = node.tag.toString();
        Ui.setClipboard(snbt);
        setStatus("snbt_copied", EditorTheme.SUCCESS, snbt.length());
    }

    /**
     * 复制选中节点的路径
     */
    private void copyPath() {
        TreeNode node = model.selected();
        if (node == null || node.isRoot() || node.isMore()) {
            return;
        }
        String path = node.path.toString();
        Ui.setClipboard(path);
        setStatus("path_copied", EditorTheme.SUCCESS, path);
    }

    /**
     * 粘贴到选中的容器（选中的不是容器就贴到它父节点里；父节点是列表时贴在选中元素后面）
     */
    private void pasteInto() {
        TreeNode node = model.selected();
        if (node == null || node.isMore()) {
            return;
        }
        TreeNode container = node.isContainer() ? node : node.parent;
        if (container == null || !container.isContainer()) {
            setStatus("select_container", EditorTheme.WARNING);
            return;
        }
        int listIndex = !node.isContainer() && node.isListChild() ? node.index + 1 : -1;

        ClipboardEntry source = clipboard;
        if (source == null) {
            String text = Ui.getClipboard();
            if (text.trim().isEmpty()) {
                setStatus("clipboard_empty", EditorTheme.WARNING);
                return;
            }
            try {
                source = new ClipboardEntry("", parseSnbtValue(text.trim()));
            } catch (IllegalArgumentException e) {
                setStatus("clipboard_invalid", EditorTheme.DANGER, e.getMessage() == null ? "?" : e.getMessage());
                return;
            }
        }

        NbtPath newPath = insertChild(container, source.tag().copy(), source.name(), listIndex);
        if (newPath == null) {
            return;
        }
        model.setExpanded(container.path, true);
        if (!pushToSession()) {
            return;
        }
        revealAndSelect(newPath);
        setStatus("pasted", EditorTheme.SUCCESS);
    }

    /**
     * 把一段 SNBT 解析成任意类型的标签
     * <p>
     * 1.12.2 的 JsonToNBT 对外只有 getTagFromJson，只认复合标签；读单个值的方法不公开。
     * 复合标签直接交给它，报错信息原样；别的值套成 {@code {v:[值]}} 再解析，取出列表里唯一的那个元素。
     * 套成列表而不是 {@code {v:值}}，是为了让 {@code 1,b:2} 这种多出来的东西变成第二个元素被发现，而不是被悄悄吞掉。
     * 报错信息里的位置提示会带上套的那层开头，去掉它，免得用户看到自己没写过的字。
     *
     * @param text SNBT（已去掉首尾空白）
     * @return 标签
     * @throws IllegalArgumentException 格式不对，消息可直接显示给用户
     */
    @Nonnull
    static NBTBase parseSnbtValue(@Nonnull String text) {
        try {
            if (text.startsWith("{")) {
                return JsonToNBT.getTagFromJson(text);
            }
            NBTTagCompound wrapper = JsonToNBT.getTagFromJson(WRAP_HEAD + text + WRAP_TAIL);
            NBTBase value = wrapper.getTag(WRAP_KEY);
            if (wrapper.getSize() != 1 || !(value instanceof NBTTagList) || ((NBTTagList) value).tagCount() != 1) {
                throw new IllegalArgumentException("Expected a single value");
            }
            return ((NBTTagList) value).get(0);
        } catch (NBTException e) {
            String message = String.valueOf(e.getMessage());
            throw new IllegalArgumentException(message.replace("at: " + WRAP_HEAD, "at: "), e);
        }
    }

    /**
     * 跳到路径栏里写的路径：展开祖先、选中、滚动到可见
     *
     * @param text 路径文本
     */
    private void jumpToPath(@Nonnull String text) {
        NbtPath path;
        try {
            path = NbtPath.parse(text);
        } catch (NbtPath.SyntaxException e) {
            setStatus("path_bad", EditorTheme.DANGER, e.position() + 1,
                    Component.translatable(LANG + "path.reason." + e.reason().key()));
            return;
        }
        NbtPath normalized = path.normalize(root);
        if (normalized == null || !revealAndSelect(normalized)) {
            setStatus("path_not_found", EditorTheme.DANGER, text.trim());
            return;
        }
        clearStatus();
        screen.setFocused(tree);
    }

    /**
     * 展开到某个节点、选中、滚到可见，并刷新表单
     *
     * @param path 路径（下标已规范化）
     * @return false = 路径不存在
     */
    private boolean revealAndSelect(@Nonnull NbtPath path) {
        int row = model.reveal(path);
        if (row < 0) {
            return false;
        }
        if (tree != null) {
            tree.scrollToRow(row, true);
        }
        onNodeSelected(model.selected());
        return true;
    }

    private void expandAll() {
        if (model.expandAll()) {
            setStatus("expand_truncated", EditorTheme.WARNING, NbtTreeModel.EXPAND_ALL_BUDGET);
        }
        if (tree != null) {
            tree.scrollToSelection();
        }
    }

    private void collapseAll() {
        model.collapseAll();
    }

    // 右键菜单

    private void openContextMenu(@Nonnull TreeNode node, int mouseX, int mouseY) {
        typePopup.close();
        List<MenuItem> items = new ArrayList<>();
        boolean rootChild = isItemRootChild(node);
        if (!node.isRoot()) {
            items.add(menuItem("copy_path", EditorTheme.TEXT, this::copyPath));
        }
        items.add(menuItem("copy_snbt", EditorTheme.TEXT, this::copySnbt));
        if (node.isContainer() || (node.parent != null && node.parent.isContainer())) {
            items.add(menuItem("paste", EditorTheme.TEXT, this::pasteInto));
        }
        if (!node.isRoot() && !rootChild) {
            items.add(menuItem("duplicate", EditorTheme.TEXT, this::duplicateSelected));
        }
        if (node.isListChild() && node.index > 0) {
            items.add(menuItem("move_up", EditorTheme.TEXT, () -> moveSelected(-1)));
        }
        if (node.isListChild() && node.parent != null && node.index < node.parent.childCount() - 1) {
            items.add(menuItem("move_down", EditorTheme.TEXT, () -> moveSelected(1)));
        }
        if (node.type() != null && node.type().isNumber() && !rootChild) {
            items.add(menuItem("convert", EditorTheme.TEXT, () -> openConvertPopup(mouseX + 2, mouseY + 2, y + height)));
        }
        if (!node.isRoot() && !isRequiredRootChild(node)) {
            items.add(menuItem("delete", EditorTheme.DANGER, this::deleteSelected));
        }
        int menuWidth = 110;
        int left = Math.min(mouseX + 2, x + width - menuWidth);
        menuPopup.open(left, mouseY + 2, menuWidth, y + height, items, item -> item.action().run());
    }

    @Nonnull
    private static MenuItem menuItem(@Nonnull String key, int color, @Nonnull Runnable action) {
        return new MenuItem(Component.translatable(LANG + "menu." + key), color, action);
    }

    // 搜索

    private void onSearchChanged(@Nonnull String text) {
        if (text.equals(searchQuery)) {
            return;
        }
        searchQuery = text;
        searchJump = true;
        if (isBlank(text)) {
            clearSearch();
            return;
        }
        searchDebounce = SEARCH_DEBOUNCE_TICKS;
        updateSearchCount();
    }

    private void clearSearch() {
        cancelSearch();
        searchDebounce = 0;
        searchResult = null;
        searchCursor = -1;
        // 不搜了就放掉快照，大物品的拷贝不白占内存
        searchSnapshot = null;
        snapshotVersion = -1;
        model.setSearchMarks(null, null);
        updateSearchCount();
    }

    /**
     * 在后台线程开始搜：数据没变就复用上次的拷贝，变了才在这里重新拷一份（后台线程只读这份拷贝）
     */
    private void startSearch() {
        cancelSearch();
        String query = searchQuery;
        if (isBlank(query)) {
            return;
        }
        if (searchSnapshot == null || snapshotVersion != dataVersion) {
            searchSnapshot = root.copy();
            snapshotVersion = dataVersion;
        }
        NBTTagCompound snapshot = searchSnapshot;
        AtomicBoolean cancel = new AtomicBoolean();
        searchCancel = cancel;
        searchTask = CompletableFuture.supplyAsync(() -> NbtSearch.run(snapshot, query, cancel::get), Ui.backgroundExecutor());
        updateSearchCount();
    }

    private void cancelSearch() {
        if (searchCancel != null) {
            searchCancel.set(true);
        }
        searchCancel = null;
        searchTask = null;
    }

    /**
     * 每 tick 看一眼后台搜完没有，搜完了在主线程上应用
     */
    private void pollSearch() {
        CompletableFuture<NbtSearch.Result> task = searchTask;
        if (task == null || !task.isDone()) {
            return;
        }
        searchTask = null;
        searchCancel = null;
        NbtSearch.Result result;
        try {
            result = task.getNow(null);
        } catch (RuntimeException e) {
            result = null;
        }
        if (result == null || !result.query().equals(searchQuery)) {
            updateSearchCount();
            return;
        }
        applySearch(result);
    }

    private void applySearch(@Nonnull NbtSearch.Result result) {
        searchResult = result;
        model.setSearchMarks(result.hitSet(), result.ancestors());
        if (result.hits().isEmpty()) {
            searchCursor = -1;
        } else if (searchJump) {
            // 新的关键字：跳到第一个命中
            searchCursor = 0;
            revealAndSelect(result.hits().get(0));
        } else {
            // 改了数据后的重搜：不乱跳，选中的正好是命中就接着从它数
            NbtPath selected = model.selectedPath();
            searchCursor = selected == null ? -1 : result.hits().indexOf(selected);
        }
        searchJump = false;
        if (result.capped()) {
            setStatus("search_capped", EditorTheme.WARNING, NbtSearch.MAX_HITS);
        }
        updateSearchCount();
    }

    private void nextHit() {
        stepHit(1);
    }

    private void previousHit() {
        stepHit(-1);
    }

    private void stepHit(int delta) {
        if (searchDebounce > 0) {
            // 还没开始搜：马上搜，结果回来会自动跳到第一个
            searchDebounce = 0;
            searchJump = true;
            startSearch();
            return;
        }
        NbtSearch.Result result = searchResult;
        if (result == null || result.hits().isEmpty() || !result.query().equals(searchQuery)) {
            return;
        }
        int size = result.hits().size();
        searchCursor = searchCursor < 0 ? (delta > 0 ? 0 : size - 1) : Math.floorMod(searchCursor + delta, size);
        if (!revealAndSelect(result.hits().get(searchCursor))) {
            // 数据刚改过，这条命中已经不在了：等重搜
            searchDebounce = 1;
        }
        updateSearchCount();
    }

    private void updateSearchCount() {
        if (isBlank(searchQuery)) {
            searchCountText = "";
            return;
        }
        NbtSearch.Result result = searchResult;
        if (searchDebounce > 0 || searchTask != null || result == null || !result.query().equals(searchQuery)) {
            searchCountText = ELLIPSIS;
            searchCountColor = EditorTheme.TEXT_DIM;
            return;
        }
        if (result.hits().isEmpty()) {
            searchCountText = Component.translatable(LANG + "search.none").getString();
            searchCountColor = EditorTheme.DANGER;
            return;
        }
        searchCountText = (searchCursor < 0 ? "-" : String.valueOf(searchCursor + 1)) + "/" + result.hits().size()
                + (result.capped() ? "+" : "");
        searchCountColor = result.capped() ? EditorTheme.WARNING : EditorTheme.TEXT_MUTED;
    }

    /**
     * 空串或只有空白（全角空格也算空白）
     */
    private static boolean isBlank(@Nonnull String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // 与物品副本同步

    /**
     * 从 session 重读根（物品是 {id, Count, Damage, tag}，实体/方块实体是整份 NBT），展开 / 选中状态保留
     */
    private void reloadFromSession() {
        root = session.loadRoot();
        model.setRoot(root);
        dataChanged();
    }

    /**
     * 把树写回 session
     *
     * @return false = session 拒收（已在状态栏提示，树已恢复成物品副本里的样子）
     */
    private boolean pushToSession() {
        try {
            session.pushRoot(root);
        } catch (IllegalArgumentException e) {
            // 树上已经改了但 session 没收：整份重读，免得树上显示的和物品实际的对不上
            reloadFromSession();
            onNodeSelected(model.selected());
            setStatus(Component.literal(String.valueOf(e.getMessage())), EditorTheme.DANGER);
            return false;
        }
        dataChanged();
        syncRootNumbersFromSession();
        return true;
    }

    /**
     * 数据变了：搜索快照作废，有关键字的话稍后重搜（不跳选中）
     */
    private void dataChanged() {
        dataVersion++;
        if (!isBlank(searchQuery)) {
            searchDebounce = SEARCH_DEBOUNCE_TICKS;
            updateSearchCount();
        }
    }

    /**
     * session 写回数量、损害值时会夹到合法范围；树里的 Count / Damage 跟着对齐，免得显示的和物品实际的不一样
     */
    private void syncRootNumbersFromSession() {
        if (!session.isItem()) {
            return;
        }
        ItemStack working = session.working();
        syncRootNumber(KEY_COUNT, working.getCount(), new NBTTagByte((byte) working.getCount()));
        syncRootNumber(KEY_DAMAGE, working.getItemDamage(), new NBTTagShort((short) working.getItemDamage()));
    }

    private void syncRootNumber(@Nonnull String key, int actual, @Nonnull NBTBase expected) {
        NBTBase current = root.getTag(key);
        if (current != null && current.getId() == expected.getId() && ((NBTPrimitive) current).getInt() == actual) {
            return;
        }
        root.setTag(key, expected);
        TreeNode node = model.resolve(NbtPath.ROOT.child(key), false);
        if (node != null) {
            node.tag = expected;
            model.leafChanged(node);
        }
    }
}
