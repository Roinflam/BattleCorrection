package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Ui;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.PopupList;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.compat.BaubleMark;
import pers.roinflam.battlecorrection.compat.BaublesIntegration;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.init.ModAttributes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 属性页
 * <p>
 * 左边是物品上已有的属性修饰符（原版 AttributeModifiers 和本模组饰品栏专属的 BattleCorrectionBaubleModifiers 合在一起显示），
 * 点一行选中后在下面改数值、运算方式、生效位置或删除；右边是可选的属性，带搜索，
 * 按"原版 → 本模组 → 其他模组（按模组名）"排序，鼠标悬停显示说明（原版、本模组、Forge 的有写，其他模组的没有）。
 * <p>
 * 1.12.2 没有全局的属性注册表，属性挂在各个实体自己的属性表上，物品 NBT 里按名字存。所以候选列表是
 * 原版 SharedMonsterAttributes + 本模组的属性 + Forge 的两个 + 本机玩家属性表里现有的全部，按名字去重（原版按名字找属性不分大小写）；
 * 列表里没有的（只有某些生物才有的属性之类）在搜索框里打全名，列表末尾会多一行"使用这个属性名"。
 * <p>
 * 生效位置决定写到哪个列表：
 * <ul>
 *   <li>主手/副手/头/胸/腿/脚/全部装备槽：原版 AttributeModifiers，Slot 字段对应装备槽（"全部"不写 Slot）</li>
 *   <li>饰品栏（任意 / 某种饰品栏）：本模组的 BattleCorrectionBaubleModifiers，戴在 Baubles 饰品栏时生效；只在装了 Baubles 时可选</li>
 * </ul>
 * 读不出来的条目（UUID 坏了、运算方式不对、原版条目没有名字）原样保留，显示为红字，只能删。
 * 运算方式和原版一样：加法直接加；乘以基础 = 基础值 ×(1 + 所有此类之和)；乘以总量 = 前面算完的结果 ×(1 + 值)。
 * <p>
 * 数值框里直接输 {@code 20%} 会按百分比理解（0.2）并自动切成"乘以基础"；乘法类的数值在框里也按百分比显示。
 * 运算和位置都是点按钮弹出列表直接选。位置选了饰品栏时，会顺手把物品标记成"可放入该饰品栏"（不标的话戴不上，属性也就没用），
 * 状态栏会提示；不想要标记去"饰品栏"页取消。
 */
public class AttributesPage extends EditorPageBase {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final String MODIFIER_NAME = "battlecorrection.editor";
    private static final String TAG_ATTRIBUTE_MODIFIERS = "AttributeModifiers";
    private static final String DESCRIPTION_KEY_PREFIX = "gui.battlecorrection.editor.attr.desc.";
    private static final String MOD_PREFIX = "battlecorrection.";
    private static final int COLUMN_GAP = 8;
    private static final int BOX_HEIGHT = 14;
    private static final int FORM_HEIGHT = 2 * BOX_HEIGHT + 8;
    private static final int TOOLTIP_WIDTH = 220;

    /**
     * 运算方式 0 / 1 / 2 的翻译键后缀
     */
    private static final String[] OPERATION_KEYS = {"addition", "multiply_base", "multiply_total"};
    private static final int ADDITION = 0;
    private static final int MULTIPLY_BASE = 1;
    private static final int MULTIPLY_TOTAL = 2;

    /**
     * 原版的属性（SharedMonsterAttributes）
     */
    private static final IAttribute[] VANILLA_ATTRIBUTES = {
            SharedMonsterAttributes.MAX_HEALTH,
            SharedMonsterAttributes.FOLLOW_RANGE,
            SharedMonsterAttributes.KNOCKBACK_RESISTANCE,
            SharedMonsterAttributes.MOVEMENT_SPEED,
            SharedMonsterAttributes.FLYING_SPEED,
            SharedMonsterAttributes.ATTACK_DAMAGE,
            SharedMonsterAttributes.ATTACK_SPEED,
            SharedMonsterAttributes.ARMOR,
            SharedMonsterAttributes.ARMOR_TOUGHNESS,
            SharedMonsterAttributes.LUCK,
    };

    /**
     * Forge 加的属性（名字前缀不统一，单独列出来，来源才标得对）
     */
    private static final IAttribute[] FORGE_ATTRIBUTES = {
            EntityPlayer.REACH_DISTANCE,
            EntityLivingBase.SWIM_SPEED,
    };

    /**
     * 生效位置
     */
    private static final class Location {

        enum Kind {
            EQUIPMENT,
            EQUIPMENT_ALL,
            BAUBLE
        }

        static final Location ALL_EQUIPMENT = new Location(Kind.EQUIPMENT_ALL, "");

        final Kind kind;
        /**
         * 装备槽名（mainhand 等）或饰品栏 ID（any 等）
         */
        final String id;

        Location(@Nonnull Kind kind, @Nonnull String id) {
            this.kind = kind;
            this.id = id;
        }

        boolean isBauble() {
            return kind == Kind.BAUBLE;
        }

        @Nonnull
        Component name() {
            switch (kind) {
                case EQUIPMENT:
                    return Component.translatable("gui.battlecorrection.editor.attr.slot." + id);
                case BAUBLE:
                    return Component.translatable("gui.battlecorrection.editor.attr.slot.curio", baubleSlotName(id));
                default:
                    return Component.translatable("gui.battlecorrection.editor.attr.slot.all");
            }
        }
    }

    /**
     * 物品上已有的一条修饰符
     */
    private static final class Entry implements ScrollList.Row {
        /**
         * 候选列表里同名的属性；列表里没有的为 null（照样能编辑，只是没有默认值和范围可看）
         */
        @Nullable
        final IAttribute attribute;
        final String attributeName;
        double amount;
        int operation;
        Location location;
        final UUID uuid;
        final String name;
        /**
         * 读不出来的条目：原样保留的 NBT，非 null 时不可编辑
         */
        @Nullable
        final NBTTagCompound raw;

        Entry(@Nullable IAttribute attribute, @Nonnull String attributeName, double amount, int operation,
              @Nonnull Location location, @Nonnull UUID uuid, @Nonnull String name, @Nullable NBTTagCompound raw) {
            this.attribute = attribute;
            this.attributeName = attributeName;
            this.amount = amount;
            this.operation = operation;
            this.location = location;
            this.uuid = uuid;
            this.name = name;
            this.raw = raw;
        }

        boolean editable() {
            return raw == null;
        }

        @Nonnull
        @Override
        public Component label() {
            if (attributeName.isEmpty()) {
                return Component.translatable("gui.battlecorrection.editor.attr.unknown", "?");
            }
            return attributeLabel(attributeName);
        }

        @Nullable
        @Override
        public Component secondary() {
            if (!editable()) {
                return null;
            }
            return Component.raw(formatAmount(amount, operation) + " ").append(location.name());
        }

        @Override
        public int color() {
            return editable() ? EditorTheme.TEXT : EditorTheme.DANGER;
        }
    }

    /**
     * 候选属性列表里的一项
     */
    private static final class Choice implements ScrollList.Row {
        /**
         * null = 搜索框里打的、列表里没有的名字
         */
        @Nullable
        final IAttribute attribute;
        final String attributeName;
        final Component name;
        final int group;
        final String modName;
        final String searchText;

        Choice(@Nullable IAttribute attribute, @Nonnull String attributeName, int group) {
            this.attribute = attribute;
            this.attributeName = attributeName;
            this.name = attribute == null
                    ? Component.translatable("gui.battlecorrection.editor.attr.custom", Component.raw(attributeName))
                    : attributeLabel(attributeName);
            this.group = group;
            this.modName = sourceName(attributeName, group);
            this.searchText = (name.getString() + " " + attributeName + " " + modName).toLowerCase(Locale.ROOT);
        }

        boolean isCustom() {
            return attribute == null;
        }

        @Nonnull
        @Override
        public Component label() {
            return name;
        }

        @Nullable
        @Override
        public Component secondary() {
            if (isCustom()) {
                return Component.translatable("gui.battlecorrection.editor.attr.custom.secondary");
            }
            switch (group) {
                case 0:
                    return Component.translatable("gui.battlecorrection.editor.attr.group.vanilla");
                case 1:
                    return Component.translatable("gui.battlecorrection.editor.attr.group.mod");
                default:
                    return Component.raw(modName);
            }
        }

        @Override
        public int color() {
            return isCustom() ? EditorTheme.ACCENT_HI : EditorTheme.TEXT;
        }
    }

    /**
     * 弹出列表里的一个位置选项
     */
    private static final class LocationRow implements ScrollList.Row {
        final Location location;

        LocationRow(@Nonnull Location location) {
            this.location = location;
        }

        @Nonnull
        @Override
        public Component label() {
            return location.name();
        }
    }

    /**
     * 弹出列表里的一个运算方式选项
     */
    private static final class OperationRow implements ScrollList.Row {
        final int operation;

        OperationRow(int operation) {
            this.operation = operation;
        }

        @Nonnull
        @Override
        public Component label() {
            return operationName(operation);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Choice> allChoices = new ArrayList<>();
    /**
     * 候选属性按小写名字索引（原版的属性表按名字找属性时不分大小写）
     */
    private final Map<String, Choice> choicesByName = new LinkedHashMap<>();
    private final List<Location> locations = new ArrayList<>();
    private final PopupList<LocationRow> locationPopup = new PopupList<>();
    private final PopupList<OperationRow> operationPopup = new PopupList<>();
    private Component status = Component.empty();
    private int statusColor = EditorTheme.TEXT_DIM;
    private Location lastLocation = new Location(Location.Kind.EQUIPMENT, EntityEquipmentSlot.MAINHAND.getName());
    private boolean binding;

    @Nullable
    private ScrollList<Entry> currentList;
    @Nullable
    private ScrollList<Choice> allList;
    @Nullable
    private FlatEditBox searchBox;
    @Nullable
    private FlatEditBox amountBox;
    @Nullable
    private FlatButton operationButton;
    @Nullable
    private FlatButton locationButton;
    @Nullable
    private FlatButton deleteButton;
    private int rightX;
    private int formTop;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public AttributesPage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.ATTRIBUTES;
    }

    @Override
    public boolean wantsPreview() {
        return false;
    }

    @Override
    protected void build() {
        buildChoices();
        loadFromItem();
        buildLocations();

        int columnWidth = (width - COLUMN_GAP) / 2;
        rightX = x + columnWidth + COLUMN_GAP;
        int listTop = y + 11;
        formTop = y + height - FORM_HEIGHT;
        int listBottom = formTop - 4;

        // 左：已有属性
        currentList = add(new ScrollList<Entry>(x, listTop, columnWidth, listBottom - listTop,
                Component.translatable("gui.battlecorrection.editor.attr.empty"))
                .onSelect(this::onEntrySelected));
        currentList.setItems(entries);

        // 右：搜索 + 候选属性
        searchBox = add(new FlatEditBox(font, rightX + 1, listTop, columnWidth - 2, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.attr.search.hint"), false));
        searchBox.setMaxLength(128);
        searchBox.setResponder(text -> refreshChoices());
        int allTop = listTop + BOX_HEIGHT + 4;
        allList = add(new ScrollList<Choice>(rightX, allTop, columnWidth, listBottom - allTop,
                Component.translatable("gui.battlecorrection.editor.search.no_match"))
                .onSelect(this::onChoiceClicked));
        refreshChoices();

        // 底部表单，两行
        int row1 = formTop + 2;
        int row2 = formTop + BOX_HEIGHT + 6;
        int labelWidth = Math.max(
                Math.max(font.width(Component.translatable("gui.battlecorrection.editor.attr.amount")),
                        font.width(Component.translatable("gui.battlecorrection.editor.attr.operation"))),
                font.width(Component.translatable("gui.battlecorrection.editor.attr.location"))) + 6;

        int cursorX = x + labelWidth;
        amountBox = add(new FlatEditBox(font, cursorX + 1, row1, 56, BOX_HEIGHT, Component.literal("0"), false));
        amountBox.setMaxLength(16);
        amountBox.setFilter(text -> text.isEmpty() || text.matches("-?\\d*(\\.\\d*)?%?"));
        amountBox.setResponder(this::onAmountChanged);
        cursorX += 56 + 10;

        int operationLabelWidth = font.width(Component.translatable("gui.battlecorrection.editor.attr.operation")) + 6;
        operationButton = add(new FlatButton(cursorX + operationLabelWidth, row1, 100, BOX_HEIGHT, Component.empty(),
                this::openOperationPopup)
                .tooltip(Component.translatableLines("gui.battlecorrection.editor.attr.operation.tooltip")));

        cursorX = x + labelWidth;
        locationButton = add(new FlatButton(cursorX, row2, Math.min(160, width - labelWidth - 56), BOX_HEIGHT,
                Component.empty(), this::openLocationPopup)
                .tooltip(Component.translatableLines(BaublesIntegration.isBaublesLoaded()
                        ? "gui.battlecorrection.editor.attr.location.tooltip"
                        : "gui.battlecorrection.editor.attr.location.tooltip.no_baubles")));

        deleteButton = add(new FlatButton(x + width - 48, row2, 48, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.attr.delete"), this::deleteSelected)
                .style(FlatButton.Style.DANGER));

        setFormEnabled(null);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // "只当饰品用"要装了 Baubles、总开关开着才成立，光有标记不算
        String currentKey = BaubleMark.isBaubleOnly(session.working())
                ? "gui.battlecorrection.editor.attr.current_marked"
                : "gui.battlecorrection.editor.attr.current";
        label(g, Component.translatable(currentKey, entries.size()), x, y);
        label(g, Component.translatable("gui.battlecorrection.editor.attr.all"), rightX, y);

        EditorTheme.divider(g, x, formTop - 2, width);
        int row1 = formTop + 2 + (BOX_HEIGHT - 8) / 2;
        int row2 = formTop + BOX_HEIGHT + 6 + (BOX_HEIGHT - 8) / 2;
        label(g, Component.translatable("gui.battlecorrection.editor.attr.amount"), x, row1);
        if (amountBox != null) {
            label(g, Component.translatable("gui.battlecorrection.editor.attr.operation"),
                    amountBox.getX() + amountBox.getWidth() + 9, row1);
        }
        label(g, Component.translatable("gui.battlecorrection.editor.attr.location"), x, row2);

        // 状态放在位置按钮和删除按钮之间，太长就裁
        if (locationButton != null && !status.getString().isEmpty()) {
            int statusX = locationButton.getX() + locationButton.getWidth() + 6;
            int maxWidth = x + width - 48 - 6 - statusX;
            if (maxWidth > 20) {
                String plain = status.getString();
                if (font.widthRaw(plain) > maxWidth) {
                    plain = font.plainSubstrByWidth(plain, Math.max(0, maxWidth - font.widthRaw("…"))) + "…";
                }
                g.drawString(font, Component.raw(plain), statusX, row2, statusColor, false);
            }
        }
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        locationPopup.render(g, mouseX, mouseY, partialTick);
        operationPopup.render(g, mouseX, mouseY, partialTick);
        if (locationPopup.isOpen() || operationPopup.isOpen()) {
            return;
        }
        // 右边列表悬停：属性说明；左边列表悬停：同样的说明，外加这条修饰符现在的值和位置
        if (allList != null && allList.hoveredItem() != null) {
            Choice hovered = allList.hoveredItem();
            renderAttributeTooltip(g, mouseX, mouseY, hovered.attribute, hovered.attributeName, null);
            return;
        }
        if (currentList != null && currentList.hoveredItem() != null) {
            Entry hovered = currentList.hoveredItem();
            if (!hovered.attributeName.isEmpty()) {
                renderAttributeTooltip(g, mouseX, mouseY, hovered.attribute, hovered.attributeName, hovered);
            }
        }
    }

    /**
     * 属性说明提示框：名字、属性名、来源、默认值/范围、说明；带上条目时再加一行当前数值和位置
     *
     * @param attribute     候选列表里的属性；null = 列表里没有这个名字
     * @param attributeName 属性名
     * @param entry         已有条目，null = 只是右边列表里的候选
     */
    private void renderAttributeTooltip(@Nonnull GuiGraphics g, int mouseX, int mouseY, @Nullable IAttribute attribute,
                                        @Nonnull String attributeName, @Nullable Entry entry) {
        List<Component> lines = new ArrayList<>();
        lines.addAll(font.split(attributeLabel(attributeName).withStyle(TextFormatting.WHITE), TOOLTIP_WIDTH));
        lines.addAll(font.split(Component.raw(attributeName).withStyle(TextFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        Choice known = choicesByName.get(attributeName.toLowerCase(Locale.ROOT));
        int group = known == null ? groupOf(attributeName, null) : known.group;
        lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.attr.from", sourceName(attributeName, group))
                .withStyle(TextFormatting.GRAY), TOOLTIP_WIDTH));
        if (entry != null && entry.editable()) {
            lines.addAll(font.split(Component.raw(formatAmount(entry.amount, entry.operation) + "  ")
                    .append(entry.location.name()).withStyle(TextFormatting.AQUA), TOOLTIP_WIDTH));
        }
        if (attribute != null) {
            lines.addAll(font.split(rangeLine(attribute).withStyle(TextFormatting.GRAY), TOOLTIP_WIDTH));
        } else {
            lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.attr.not_listed")
                    .withStyle(TextFormatting.YELLOW), TOOLTIP_WIDTH));
        }
        lines.addAll(font.split(description(attributeName).withStyle(TextFormatting.WHITE), TOOLTIP_WIDTH));
        g.renderTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return locationPopup.mouseClicked(mouseX, mouseY, button)
                || operationPopup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return locationPopup.mouseScrolled(mouseX, mouseY, delta)
                || operationPopup.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean onEscape() {
        if (locationPopup.isOpen() || operationPopup.isOpen()) {
            locationPopup.close();
            operationPopup.close();
            return true;
        }
        return false;
    }

    /**
     * 属性的显示名：有 {@code attribute.name.<属性名>} 的翻译就用（原版、Forge、本模组都有），没有就显示属性名本身
     *
     * @param attributeName 属性名
     * @return 组件
     */
    @Nonnull
    private static Component attributeLabel(@Nonnull String attributeName) {
        String key = "attribute.name." + attributeName;
        return Ui.hasTranslation(key) ? Component.translatable(key) : Component.raw(attributeName);
    }

    /**
     * 饰品栏的显示名（不认识的 ID 原样显示）
     *
     * @param slotId 饰品栏 ID
     * @return 组件
     */
    @Nonnull
    private static Component baubleSlotName(@Nonnull String slotId) {
        String key = BaubleMark.slotNameKey(slotId);
        return key == null ? Component.raw(slotId) : Component.translatable(key);
    }

    /**
     * 排序分组：原版 0 → 本模组 1 → 其他 2（Forge 的也在 2，按来源名排）
     *
     * @param attributeName 属性名
     * @param attribute     属性对象，可为 null
     * @return 分组
     */
    private static int groupOf(@Nonnull String attributeName, @Nullable IAttribute attribute) {
        for (IAttribute vanilla : VANILLA_ATTRIBUTES) {
            if (vanilla == attribute || vanilla.getName().equalsIgnoreCase(attributeName)) {
                return 0;
            }
        }
        return attributeName.startsWith(MOD_PREFIX) ? 1 : 2;
    }

    /**
     * 属性来自哪里：原版、Forge 的两个写死，其余按名字里第一个点前面的部分当模组 ID 去找模组名
     *
     * @param attributeName 属性名
     * @param group         分组
     * @return 显示用的来源名
     */
    @Nonnull
    private static String sourceName(@Nonnull String attributeName, int group) {
        if (group == 0) {
            return "Minecraft";
        }
        for (IAttribute forge : FORGE_ATTRIBUTES) {
            if (forge.getName().equalsIgnoreCase(attributeName)) {
                return "Forge";
            }
        }
        int dot = attributeName.indexOf('.');
        String namespace = dot > 0 ? attributeName.substring(0, dot) : attributeName;
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        ModContainer container = Loader.instance().getIndexedModList().get(namespace);
        return container == null ? namespace : container.getName();
    }

    /**
     * "默认 X，范围 a ~ b"那一行
     * <p>
     * 1.12.2 的 RangedAttribute 不给取上下限的方法，拿正负无穷去夹一下就是上下限。
     *
     * @param attribute 属性
     * @return 组件
     */
    @Nonnull
    private static Component rangeLine(@Nonnull IAttribute attribute) {
        String defaultValue = NUMBER_FORMAT.format(attribute.getDefaultValue());
        if (attribute instanceof RangedAttribute) {
            RangedAttribute ranged = (RangedAttribute) attribute;
            return Component.translatable("gui.battlecorrection.editor.attr.range", defaultValue,
                    formatBound(ranged.clampValue(Double.NEGATIVE_INFINITY)),
                    formatBound(ranged.clampValue(Double.POSITIVE_INFINITY)));
        }
        return Component.translatable("gui.battlecorrection.editor.attr.default", defaultValue);
    }

    /**
     * 范围端点：Float.MAX_VALUE 这种"没有上限"的值显示成"无上限"，不然会打印几十位数字
     *
     * @param value 端点
     * @return 文本
     */
    @Nonnull
    private static String formatBound(double value) {
        if (value >= 1.0E15D) {
            return Ui.translate("gui.battlecorrection.editor.attr.range.unbounded_high");
        }
        if (value <= -1.0E15D) {
            return Ui.translate("gui.battlecorrection.editor.attr.range.unbounded_low");
        }
        return NUMBER_FORMAT.format(value);
    }

    /**
     * 属性说明：有翻译就用，没有就提示"暂无说明"
     *
     * @param attributeName 属性名
     * @return 组件
     */
    @Nonnull
    private static Component description(@Nonnull String attributeName) {
        String key = DESCRIPTION_KEY_PREFIX + attributeName;
        if (Ui.hasTranslation(key)) {
            return Component.translatable(key);
        }
        return Component.translatable("gui.battlecorrection.editor.attr.desc.none");
    }

    /**
     * 数值的显示形式："+5"、"+20% 基"、"-10% 总"
     *
     * @param amount    数值
     * @param operation 运算方式
     * @return 文本
     */
    @Nonnull
    private static String formatAmount(double amount, int operation) {
        String sign = amount >= 0 ? "+" : "";
        switch (operation) {
            case MULTIPLY_BASE:
                return sign + NUMBER_FORMAT.format(amount * 100) + "% " + Ui.translate("gui.battlecorrection.editor.attr.op.short.multiply_base");
            case MULTIPLY_TOTAL:
                return sign + NUMBER_FORMAT.format(amount * 100) + "% " + Ui.translate("gui.battlecorrection.editor.attr.op.short.multiply_total");
            default:
                return sign + NUMBER_FORMAT.format(amount);
        }
    }

    /**
     * 运算方式的显示名
     *
     * @param operation 运算方式（0..2）
     * @return 组件
     */
    @Nonnull
    private static Component operationName(int operation) {
        return Component.translatable("gui.battlecorrection.editor.attr.op." + OPERATION_KEYS[operation]);
    }

    /**
     * 建立候选属性列表：原版 → 本模组 → 其他按来源名，同组内按显示名排序
     */
    private void buildChoices() {
        allChoices.clear();
        choicesByName.clear();
        for (IAttribute attribute : VANILLA_ATTRIBUTES) {
            addChoice(attribute);
        }
        for (IAttribute attribute : ModAttributes.getAllAttributes()) {
            addChoice(attribute);
        }
        for (IAttribute attribute : FORGE_ATTRIBUTES) {
            addChoice(attribute);
        }
        // 本机玩家属性表里现有的全部：其他模组给玩家加的属性从这里来
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            for (IAttributeInstance instance : player.getAttributeMap().getAllAttributes()) {
                addChoice(instance.getAttribute());
            }
        }
        allChoices.sort(Comparator.comparingInt((Choice choice) -> choice.group)
                .thenComparing(choice -> choice.modName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(choice -> choice.name.getString(), String.CASE_INSENSITIVE_ORDER));
    }

    private void addChoice(@Nullable IAttribute attribute) {
        if (attribute == null || attribute.getName() == null || attribute.getName().isEmpty()) {
            return;
        }
        String key = attribute.getName().toLowerCase(Locale.ROOT);
        if (choicesByName.containsKey(key)) {
            return;
        }
        Choice choice = new Choice(attribute, attribute.getName(), groupOf(attribute.getName(), attribute));
        choicesByName.put(key, choice);
        allChoices.add(choice);
    }

    /**
     * 建立生效位置列表：六个装备槽、全部装备槽，装了 Baubles 再加上任意饰品栏和每种饰品栏
     */
    private void buildLocations() {
        locations.clear();
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            locations.add(new Location(Location.Kind.EQUIPMENT, slot.getName()));
        }
        locations.add(Location.ALL_EQUIPMENT);
        if (BaublesIntegration.isBaublesLoaded()) {
            locations.add(new Location(Location.Kind.BAUBLE, BaubleMark.ANY));
            for (String slotId : BaubleMark.SLOT_IDS) {
                locations.add(new Location(Location.Kind.BAUBLE, slotId));
            }
        }
    }

    /**
     * 按搜索框内容过滤右侧列表；打的是列表里没有的属性名时，末尾加一行"使用这个属性名"
     */
    private void refreshChoices() {
        if (allList == null) {
            return;
        }
        String typed = searchBox == null ? "" : searchBox.getValue().trim();
        String query = typed.toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            allList.setItems(allChoices);
            return;
        }
        List<Choice> filtered = new ArrayList<>();
        for (Choice choice : allChoices) {
            if (choice.searchText.contains(query)) {
                filtered.add(choice);
            }
        }
        if (isPlausibleName(typed) && !choicesByName.containsKey(query)) {
            filtered.add(new Choice(null, typed, 2));
        }
        allList.setItems(filtered);
    }

    /**
     * 像不像一个属性名：不带空白，只有字母、数字和 . _ - :
     */
    private static boolean isPlausibleName(@Nonnull String text) {
        return !text.isEmpty() && text.matches("[A-Za-z0-9._:\\-]+");
    }

    /**
     * 把表单绑定到某条修饰符；null = 清空并禁用
     *
     * @param entry 条目
     */
    private void setFormEnabled(@Nullable Entry entry) {
        if (amountBox == null || operationButton == null || locationButton == null || deleteButton == null) {
            return;
        }
        boolean editable = entry != null && entry.editable();
        binding = true;
        // 乘法类的数值按百分比显示（0.2 → 20%），加法按原数
        String amountText = "";
        if (editable) {
            amountText = entry.operation == ADDITION
                    ? NUMBER_FORMAT.format(entry.amount)
                    : NUMBER_FORMAT.format(entry.amount * 100) + "%";
        }
        amountBox.setValue(amountText);
        binding = false;
        amountBox.active = editable;
        operationButton.active = editable;
        operationButton.setMessage(editable ? operationName(entry.operation) : Component.empty());
        locationButton.active = editable;
        locationButton.setMessage(editable ? entry.location.name() : Component.empty());
        deleteButton.active = entry != null;
    }

    /**
     * 左侧选中了一条
     *
     * @param entry 条目
     */
    private void onEntrySelected(@Nonnull Entry entry) {
        setFormEnabled(entry);
    }

    /**
     * 右侧点了一项：新增一条修饰符并选中
     *
     * @param choice 选项
     */
    private void onChoiceClicked(@Nonnull Choice choice) {
        if (currentList == null) {
            return;
        }
        Location location = lastLocation;
        // 上一次选的饰品栏位置在没装 Baubles 时不可选，退回主手
        if (location.isBauble() && !BaublesIntegration.isBaublesLoaded()) {
            location = new Location(Location.Kind.EQUIPMENT, EntityEquipmentSlot.MAINHAND.getName());
        }
        Entry entry = new Entry(choice.attribute, choice.attributeName, 1.0D, ADDITION,
                location, newModifierUuid(), MODIFIER_NAME, null);
        entries.add(entry);
        writeToItem();
        currentList.setItems(entries);
        currentList.select(entry);
        setFormEnabled(entry);
        if (amountBox != null) {
            screen.setFocused(amountBox);
        }
    }

    /**
     * 新修饰符的 UUID：原版只认高低两半都不为 0 的 UUID（ItemStack#getAttributeModifiers），随机到这种就重来
     *
     * @return UUID
     */
    @Nonnull
    private static UUID newModifierUuid() {
        UUID uuid = BaubleMark.newModifierUuid();
        while (uuid.getMostSignificantBits() == 0L || uuid.getLeastSignificantBits() == 0L) {
            uuid = BaubleMark.newModifierUuid();
        }
        return uuid;
    }

    /**
     * 数值输入框变化
     *
     * @param text 新内容
     */
    private void onAmountChanged(@Nonnull String text) {
        if (binding || currentList == null) {
            return;
        }
        Entry entry = currentList.selectedItem();
        if (entry == null || !entry.editable()) {
            return;
        }
        boolean percent = text.endsWith("%");
        double value;
        try {
            value = Double.parseDouble(percent ? text.substring(0, text.length() - 1) : text);
        } catch (NumberFormatException ignored) {
            // "-"、"1."、"%" 这种输了一半的内容先不动
            return;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return;
        }
        if (percent) {
            // 带 % 就按百分比：20% = 0.2；还是加法的话顺手切成"乘以基础"
            value /= 100.0D;
            if (entry.operation == ADDITION) {
                entry.operation = MULTIPLY_BASE;
                if (operationButton != null) {
                    operationButton.setMessage(operationName(entry.operation));
                }
            }
        } else if (entry.operation != ADDITION) {
            // 乘法类的框里显示的是百分比，去掉 % 输的裸数也按百分比理解
            value /= 100.0D;
        }
        entry.amount = value;
        writeToItem();
    }

    /**
     * 弹出运算方式列表
     */
    private void openOperationPopup() {
        if (currentList == null || operationButton == null) {
            return;
        }
        Entry entry = currentList.selectedItem();
        if (entry == null || !entry.editable()) {
            return;
        }
        List<OperationRow> rows = new ArrayList<>();
        for (int operation = ADDITION; operation <= MULTIPLY_TOTAL; operation++) {
            rows.add(new OperationRow(operation));
        }
        locationPopup.close();
        operationPopup.open(operationButton.getX(), operationButton.getY() - rows.size() * ScrollList.ROW_HEIGHT - 4,
                operationButton.getWidth(), operationButton.getY() - 2, rows, row -> setOperation(entry, row.operation));
    }

    /**
     * 弹出生效位置列表
     */
    private void openLocationPopup() {
        if (currentList == null || locationButton == null || locations.isEmpty()) {
            return;
        }
        Entry entry = currentList.selectedItem();
        if (entry == null || !entry.editable()) {
            return;
        }
        List<LocationRow> rows = new ArrayList<>();
        for (Location location : locations) {
            rows.add(new LocationRow(location));
        }
        operationPopup.close();
        int popupHeight = Math.min(rows.size(), 10) * ScrollList.ROW_HEIGHT + 2;
        locationPopup.open(locationButton.getX(), locationButton.getY() - popupHeight - 2,
                Math.max(locationButton.getWidth(), 180), locationButton.getY() - 2, rows,
                row -> setLocation(entry, row.location));
    }

    /**
     * 应用选中的运算方式
     *
     * @param entry     条目
     * @param operation 运算方式
     */
    private void setOperation(@Nonnull Entry entry, int operation) {
        entry.operation = operation;
        if (operationButton != null) {
            operationButton.setMessage(operationName(operation));
        }
        writeToItem();
        // 数值框的显示方式随运算变（加法原数，乘法百分比）
        setFormEnabled(entry);
    }

    /**
     * 应用选中的生效位置；选了饰品栏就顺手打上对应标记
     *
     * @param entry    条目
     * @param location 位置
     */
    private void setLocation(@Nonnull Entry entry, @Nonnull Location location) {
        entry.location = location;
        lastLocation = location;
        if (locationButton != null) {
            locationButton.setMessage(location.name());
        }
        if (location.isBauble() && !BaubleMark.canEquip(session.tagOrNull(), location.id)) {
            BaubleMark.addSlot(session.tag(), location.id);
            status = Component.translatable("gui.battlecorrection.editor.attr.status.marked", baubleSlotName(location.id));
            statusColor = EditorTheme.WARNING;
        }
        writeToItem();
    }

    /**
     * 删除选中的修饰符
     */
    private void deleteSelected() {
        if (currentList == null) {
            return;
        }
        Entry entry = currentList.selectedItem();
        if (entry == null) {
            return;
        }
        entries.remove(entry);
        writeToItem();
        currentList.setItems(entries);
        currentList.select(null);
        setFormEnabled(null);
    }

    /**
     * 从物品副本读两个列表
     */
    private void loadFromItem() {
        entries.clear();
        NBTTagCompound tag = session.tagOrNull();
        if (tag == null) {
            return;
        }
        readList(tag, TAG_ATTRIBUTE_MODIFIERS, false);
        readList(tag, BaubleMark.TAG_MODIFIERS, true);
    }

    /**
     * 读一个修饰符列表
     *
     * @param tag    物品 NBT
     * @param key    列表键
     * @param bauble true = 饰品栏专属列表
     */
    private void readList(@Nonnull NBTTagCompound tag, @Nonnull String key, boolean bauble) {
        if (!tag.hasKey(key, 9)) {
            return;
        }
        NBTTagList list = tag.getTagList(key, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound compound = list.getCompoundTagAt(i);
            String attributeName = compound.getString("AttributeName");
            String slot = compound.getString("Slot");
            Location location;
            if (bauble) {
                // trinket 是 Baubles 的"任意"，读到时当 any
                location = new Location(Location.Kind.BAUBLE,
                        slot.isEmpty() ? BaubleMark.ANY : BaubleMark.isAnyLike(slot) ? BaubleMark.ANY : slot);
            } else {
                location = slot.isEmpty() ? Location.ALL_EQUIPMENT : new Location(Location.Kind.EQUIPMENT, slot);
            }

            AttributeModifier modifier = attributeName.isEmpty() ? null : readModifier(compound, bauble);
            if (modifier == null) {
                entries.add(new Entry(null, attributeName, 0.0D, ADDITION, location,
                        UUID.randomUUID(), "", compound.copy()));
                continue;
            }
            Choice known = choicesByName.get(attributeName.toLowerCase(Locale.ROOT));
            entries.add(new Entry(known == null ? null : known.attribute, attributeName, modifier.getAmount(),
                    modifier.getOperation(), location, modifier.getID(), modifier.getName(), null));
        }
    }

    /**
     * 按游戏实际的读法解析一条修饰符，游戏不认的返回 null（原样保留，不让编辑）
     * <ul>
     *   <li>原版列表：要有名字（SharedMonsterAttributes#readAttributeModifierFromNBT 碰到空名字直接放弃）、
     *   UUID 高低两半都不为 0（ItemStack#getAttributeModifiers 的判断）</li>
     *   <li>饰品栏列表：照 BaubleMark 的读法，UUID 不能全 0，名字可以空</li>
     * </ul>
     * 两边都要求运算方式在 0..2、数值是有限数。
     *
     * @param compound 条目
     * @param bauble   是不是饰品栏列表
     * @return 修饰符
     */
    @Nullable
    private static AttributeModifier readModifier(@Nonnull NBTTagCompound compound, boolean bauble) {
        if (!compound.hasUniqueId("UUID")) {
            return null;
        }
        UUID uuid = compound.getUniqueId("UUID");
        if (uuid == null) {
            return null;
        }
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        if (bauble ? (most == 0L && least == 0L) : (most == 0L || least == 0L)) {
            return null;
        }
        int operation = compound.getInteger("Operation");
        if (operation < ADDITION || operation > MULTIPLY_TOTAL) {
            return null;
        }
        double amount = compound.getDouble("Amount");
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return null;
        }
        String name = compound.getString("Name");
        if (name.isEmpty()) {
            if (!bauble) {
                return null;
            }
            name = BaubleMark.MODIFIER_NAME;
        }
        return new AttributeModifier(uuid, name, amount, operation);
    }

    /**
     * 把两个列表写回物品副本
     */
    private void writeToItem() {
        NBTTagList vanilla = new NBTTagList();
        NBTTagList bauble = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagList target = entry.location.isBauble() ? bauble : vanilla;
            if (entry.raw != null) {
                target.appendTag(entry.raw.copy());
                continue;
            }
            // 格式同原版 AttributeModifiers（饰品栏列表也一样，见 BaubleMark）：UUID 存成 UUIDMost / UUIDLeast
            NBTTagCompound compound = new NBTTagCompound();
            compound.setString("AttributeName", entry.attributeName);
            compound.setString("Name", entry.name.isEmpty() ? MODIFIER_NAME : entry.name);
            compound.setDouble("Amount", entry.amount);
            compound.setInteger("Operation", entry.operation);
            compound.setUniqueId("UUID", entry.uuid);
            if (entry.location.kind != Location.Kind.EQUIPMENT_ALL) {
                compound.setString("Slot", entry.location.id);
            }
            target.appendTag(compound);
        }

        if (vanilla.hasNoTags()) {
            removeTagKey(TAG_ATTRIBUTE_MODIFIERS);
        } else {
            session.tag().setTag(TAG_ATTRIBUTE_MODIFIERS, vanilla);
        }
        if (bauble.hasNoTags()) {
            removeTagKey(BaubleMark.TAG_MODIFIERS);
        } else {
            session.tag().setTag(BaubleMark.TAG_MODIFIERS, bauble);
        }
    }

    /**
     * 删一个键，tag 删空了就整个去掉（1.12.2 里 tag 为 {} 和没有 tag 的物品叠不到一起）
     *
     * @param key 键
     */
    private void removeTagKey(@Nonnull String key) {
        NBTTagCompound tag = session.tagOrNull();
        if (tag == null) {
            return;
        }
        tag.removeTag(key);
        if (tag.hasNoTags()) {
            session.setTag(null);
        }
    }
}
