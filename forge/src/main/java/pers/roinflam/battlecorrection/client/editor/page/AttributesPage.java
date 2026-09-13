package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.battlecorrection.client.editor.CurioSlotsClient;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.PopupList;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.compat.CurioMark;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 属性页
 * <p>
 * 左边是物品上已有的属性修饰符（原版 AttributeModifiers 和本模组饰品栏专属的 BattleCorrectionCurioModifiers 合在一起显示），
 * 点一行选中后在下面改数值、运算方式、生效位置或删除；右边是全部已注册的属性，带搜索，
 * 按"本模组 → 原版 → Forge → 其他模组"排序，鼠标悬停显示说明（原版和本模组的有写，其他模组的没有）。
 * <p>
 * 生效位置决定写到哪个列表：
 * <ul>
 *   <li>主手/副手/头/胸/腿/脚/全部装备槽：原版 AttributeModifiers，Slot 字段对应装备槽（"全部"不写 Slot）</li>
 *   <li>饰品栏（任意 / 某个饰品栏）：本模组的 BattleCorrectionCurioModifiers，戴在饰品栏时由 CurioMarkEvents 加到身上</li>
 * </ul>
 * 读不出来的条目（属性所在模组被删了、UUID 坏了）原样保留，显示为红字，只能删。
 * 运算方式和原版一样：加法直接加；乘以基础 = 基础值 ×(1 + 所有此类之和)；乘以总量 = 前面算完的结果 ×(1 + 值)。
 * <p>
 * 运算和位置都是点按钮弹出列表直接选。位置选了饰品栏时，会顺手把物品标记成"可放入该饰品栏"（不标的话戴不上，属性也就没用），
 * 状态栏会提示；不想要标记去"饰品栏"页取消。
 */
public class AttributesPage extends EditorPageBase {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final String MODIFIER_NAME = "battlecorrection.editor";
    private static final String TAG_ATTRIBUTE_MODIFIERS = "AttributeModifiers";
    private static final String DESCRIPTION_KEY_PREFIX = "gui.battlecorrection.editor.attr.desc.";
    private static final int COLUMN_GAP = 8;
    private static final int BOX_HEIGHT = 14;
    private static final int FORM_HEIGHT = 2 * BOX_HEIGHT + 8;
    private static final int TOOLTIP_WIDTH = 220;

    /**
     * 生效位置
     *
     * @param kind 类型
     * @param id   装备槽名（mainhand 等）或饰品栏 ID（any 等）
     */
    private record Location(Kind kind, String id) {

        enum Kind {
            EQUIPMENT,
            EQUIPMENT_ALL,
            CURIO
        }

        static final Location ALL_EQUIPMENT = new Location(Kind.EQUIPMENT_ALL, "");

        boolean isCurio() {
            return kind == Kind.CURIO;
        }

        @Nonnull
        Component name() {
            switch (kind) {
                case EQUIPMENT:
                    return Component.translatable("gui.battlecorrection.editor.attr.slot." + id);
                case CURIO:
                    return Component.translatable("gui.battlecorrection.editor.attr.slot.curio", CurioMark.slotName(id));
                default:
                    return Component.translatable("gui.battlecorrection.editor.attr.slot.all");
            }
        }
    }

    /**
     * 物品上已有的一条修饰符
     */
    private static final class Entry implements ScrollList.Row {
        @Nullable
        final Attribute attribute;
        final String attributeId;
        double amount;
        AttributeModifier.Operation operation;
        Location location;
        final UUID uuid;
        final String name;
        /**
         * 读不出来的条目：原样保留的 NBT，非 null 时不可编辑
         */
        @Nullable
        final CompoundTag raw;

        Entry(@Nullable Attribute attribute, @Nonnull String attributeId, double amount,
              @Nonnull AttributeModifier.Operation operation, @Nonnull Location location,
              @Nonnull UUID uuid, @Nonnull String name, @Nullable CompoundTag raw) {
            this.attribute = attribute;
            this.attributeId = attributeId;
            this.amount = amount;
            this.operation = operation;
            this.location = location;
            this.uuid = uuid;
            this.name = name;
            this.raw = raw;
        }

        boolean editable() {
            return raw == null && attribute != null;
        }

        @Nonnull
        @Override
        public Component label() {
            if (attribute == null) {
                return Component.translatable("gui.battlecorrection.editor.attr.unknown", attributeId);
            }
            return Component.translatable(attribute.getDescriptionId());
        }

        @Nullable
        @Override
        public Component secondary() {
            if (!editable()) {
                return null;
            }
            return Component.literal(formatAmount(amount, operation) + " ").append(location.name());
        }

        @Override
        public int color() {
            return editable() ? EditorTheme.TEXT : EditorTheme.DANGER;
        }
    }

    /**
     * 全部属性列表里的一项
     */
    private static final class Choice implements ScrollList.Row {
        final Attribute attribute;
        final ResourceLocation id;
        final Component name;
        final int group;
        final String searchText;

        Choice(@Nonnull Attribute attribute, @Nonnull ResourceLocation id) {
            this.attribute = attribute;
            this.id = id;
            this.name = Component.translatable(attribute.getDescriptionId());
            this.group = groupOf(id.getNamespace());
            this.searchText = (name.getString() + " " + id).toLowerCase(Locale.ROOT);
        }

        static int groupOf(@Nonnull String namespace) {
            switch (namespace) {
                case Reference.MOD_ID:
                    return 0;
                case "minecraft":
                    return 1;
                case "forge":
                    return 2;
                default:
                    return 3;
            }
        }

        @Nonnull
        @Override
        public Component label() {
            return name;
        }

        @Nullable
        @Override
        public Component secondary() {
            switch (group) {
                case 0:
                    return Component.translatable("gui.battlecorrection.editor.attr.group.mod");
                case 1:
                    return Component.translatable("gui.battlecorrection.editor.attr.group.vanilla");
                case 2:
                    return Component.translatable("gui.battlecorrection.editor.attr.group.forge");
                default:
                    return Component.literal(id.getNamespace());
            }
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
        final AttributeModifier.Operation operation;

        OperationRow(@Nonnull AttributeModifier.Operation operation) {
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
    private final List<Location> locations = new ArrayList<>();
    private final PopupList<LocationRow> locationPopup = new PopupList<>();
    private final PopupList<OperationRow> operationPopup = new PopupList<>();
    private Component status = Component.empty();
    private int statusColor = EditorTheme.TEXT_DIM;
    private Location lastLocation = new Location(Location.Kind.EQUIPMENT, EquipmentSlot.MAINHAND.getName());
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
        loadFromItem();
        buildChoices();
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

        // 右：搜索 + 全部属性
        searchBox = add(new FlatEditBox(font, rightX + 1, listTop, columnWidth - 2, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.search.hint"), false));
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
        amountBox.setFilter(text -> text.isEmpty() || text.matches("-?\\d*(\\.\\d*)?"));
        amountBox.setResponder(this::onAmountChanged);
        cursorX += 56 + 10;

        int operationLabelWidth = font.width(Component.translatable("gui.battlecorrection.editor.attr.operation")) + 6;
        operationButton = add(new FlatButton(cursorX + operationLabelWidth, row1, 100, BOX_HEIGHT, Component.empty(),
                this::openOperationPopup)
                .tooltip(Component.translatable("gui.battlecorrection.editor.attr.operation.tooltip")));

        cursorX = x + labelWidth;
        locationButton = add(new FlatButton(cursorX, row2, Math.min(160, width - labelWidth - 56), BOX_HEIGHT,
                Component.empty(), this::openLocationPopup)
                .tooltip(Component.translatable("gui.battlecorrection.editor.attr.location.tooltip")));

        deleteButton = add(new FlatButton(x + width - 48, row2, 48, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.attr.delete"), this::deleteSelected)
                .style(FlatButton.Style.DANGER));

        setFormEnabled(null);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String currentKey = CurioMark.isMarked(session.working())
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
                if (font.width(plain) > maxWidth) {
                    plain = font.plainSubstrByWidth(plain, Math.max(0, maxWidth - font.width("…"))) + "…";
                }
                g.drawString(font, plain, statusX, row2, statusColor, false);
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
            renderAttributeTooltip(g, mouseX, mouseY, hovered.attribute, hovered.id, null);
            return;
        }
        if (currentList != null && currentList.hoveredItem() != null) {
            Entry hovered = currentList.hoveredItem();
            ResourceLocation id = ResourceLocation.tryParse(hovered.attributeId);
            if (hovered.attribute != null && id != null) {
                renderAttributeTooltip(g, mouseX, mouseY, hovered.attribute, id, hovered);
            }
        }
    }

    /**
     * 属性说明提示框：名字、ID、默认值/范围、说明；带上条目时再加一行当前数值和位置
     *
     * @param entry 已有条目，null = 只是右边列表里的候选
     */
    private void renderAttributeTooltip(@Nonnull GuiGraphics g, int mouseX, int mouseY, @Nonnull Attribute attribute,
                                        @Nonnull ResourceLocation id, @Nullable Entry entry) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.translatable(attribute.getDescriptionId()).withStyle(ChatFormatting.WHITE), TOOLTIP_WIDTH));
        lines.addAll(font.split(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        if (entry != null && entry.editable()) {
            lines.addAll(font.split(Component.literal(formatAmount(entry.amount, entry.operation) + "  ")
                    .append(entry.location.name()).withStyle(ChatFormatting.AQUA), TOOLTIP_WIDTH));
        }
        lines.addAll(font.split(rangeLine(attribute).withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        lines.addAll(font.split(description(id).withStyle(ChatFormatting.WHITE), TOOLTIP_WIDTH));
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
     * "默认 X，范围 a ~ b"那一行
     *
     * @param attribute 属性
     * @return 组件
     */
    @Nonnull
    private static MutableComponent rangeLine(@Nonnull Attribute attribute) {
        String defaultValue = NUMBER_FORMAT.format(attribute.getDefaultValue());
        if (attribute instanceof RangedAttribute ranged) {
            return Component.translatable("gui.battlecorrection.editor.attr.range", defaultValue,
                    NUMBER_FORMAT.format(ranged.getMinValue()), NUMBER_FORMAT.format(ranged.getMaxValue()));
        }
        return Component.translatable("gui.battlecorrection.editor.attr.default", defaultValue);
    }

    /**
     * 属性说明：有翻译就用，没有就提示"暂无说明"
     *
     * @param id 属性注册名
     * @return 组件
     */
    @Nonnull
    private static MutableComponent description(@Nonnull ResourceLocation id) {
        String key = DESCRIPTION_KEY_PREFIX + id.getNamespace() + "." + id.getPath();
        if (I18n.exists(key)) {
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
    private static String formatAmount(double amount, @Nonnull AttributeModifier.Operation operation) {
        String sign = amount >= 0 ? "+" : "";
        switch (operation) {
            case MULTIPLY_BASE:
                return sign + NUMBER_FORMAT.format(amount * 100) + "% " + I18n.get("gui.battlecorrection.editor.attr.op.short.multiply_base");
            case MULTIPLY_TOTAL:
                return sign + NUMBER_FORMAT.format(amount * 100) + "% " + I18n.get("gui.battlecorrection.editor.attr.op.short.multiply_total");
            default:
                return sign + NUMBER_FORMAT.format(amount);
        }
    }

    /**
     * 运算方式的显示名
     *
     * @param operation 运算方式
     * @return 组件
     */
    @Nonnull
    private static Component operationName(@Nonnull AttributeModifier.Operation operation) {
        return Component.translatable("gui.battlecorrection.editor.attr.op." + operation.name().toLowerCase(Locale.ROOT));
    }

    /**
     * 建立全部属性列表：本模组 → 原版 → Forge → 其他，同组内按显示名排序
     */
    private void buildChoices() {
        allChoices.clear();
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            if (id != null) {
                allChoices.add(new Choice(attribute, id));
            }
        }
        allChoices.sort(Comparator.comparingInt((Choice choice) -> choice.group)
                .thenComparing(choice -> choice.name.getString(), String.CASE_INSENSITIVE_ORDER));
    }

    /**
     * 建立生效位置列表：六个装备槽、全部装备槽，装了 Curios 再加上任意饰品栏和每个饰品栏
     */
    private void buildLocations() {
        locations.clear();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            locations.add(new Location(Location.Kind.EQUIPMENT, slot.getName()));
        }
        locations.add(Location.ALL_EQUIPMENT);
        if (CuriosIntegration.isCuriosLoaded()) {
            locations.add(new Location(Location.Kind.CURIO, CurioMark.ANY));
            for (String slotId : CurioSlotsClient.slotIds()) {
                locations.add(new Location(Location.Kind.CURIO, slotId));
            }
        }
    }

    /**
     * 按搜索框内容过滤右侧列表
     */
    private void refreshChoices() {
        if (allList == null) {
            return;
        }
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
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
        allList.setItems(filtered);
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
        amountBox.setValue(editable ? NUMBER_FORMAT.format(entry.amount) : "");
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
        Entry entry = new Entry(choice.attribute, choice.id.toString(), 1.0D, AttributeModifier.Operation.ADDITION,
                lastLocation, CurioMark.newModifierUuid(), MODIFIER_NAME, null);
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
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            // "-"、"1." 这种输了一半的内容先不动
            return;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return;
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
        for (AttributeModifier.Operation operation : AttributeModifier.Operation.values()) {
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
    private void setOperation(@Nonnull Entry entry, @Nonnull AttributeModifier.Operation operation) {
        entry.operation = operation;
        if (operationButton != null) {
            operationButton.setMessage(operationName(operation));
        }
        writeToItem();
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
        if (location.isCurio() && !CurioMark.canEquip(session.working(), location.id())) {
            CurioMark.addSlot(session.working(), location.id());
            status = Component.translatable("gui.battlecorrection.editor.attr.status.marked", CurioMark.slotName(location.id()));
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
        CompoundTag tag = session.tagOrNull();
        if (tag == null) {
            return;
        }
        readList(tag, TAG_ATTRIBUTE_MODIFIERS, false);
        readList(tag, CurioMark.TAG_MODIFIERS, true);
    }

    /**
     * 读一个修饰符列表
     *
     * @param tag   物品 NBT
     * @param key   列表键
     * @param curio true = 饰品栏专属列表
     */
    private void readList(@Nonnull CompoundTag tag, @Nonnull String key, boolean curio) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag compound = list.getCompound(i);
            String attributeId = compound.getString("AttributeName");
            ResourceLocation id = ResourceLocation.tryParse(attributeId);
            Attribute attribute = id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
            AttributeModifier modifier = AttributeModifier.load(compound);

            String slot = compound.getString("Slot");
            Location location;
            if (curio) {
                location = new Location(Location.Kind.CURIO, slot.isEmpty() ? CurioMark.ANY : slot);
            } else {
                location = slot.isEmpty() ? Location.ALL_EQUIPMENT : new Location(Location.Kind.EQUIPMENT, slot);
            }

            if (attribute == null || modifier == null) {
                entries.add(new Entry(null, attributeId, 0.0D, AttributeModifier.Operation.ADDITION, location,
                        UUID.randomUUID(), "", compound.copy()));
                continue;
            }
            entries.add(new Entry(attribute, attributeId, modifier.getAmount(), modifier.getOperation(), location,
                    modifier.getId(), modifier.getName(), null));
        }
    }

    /**
     * 把两个列表写回物品副本
     */
    private void writeToItem() {
        ListTag vanilla = new ListTag();
        ListTag curio = new ListTag();
        for (Entry entry : entries) {
            ListTag target = entry.location.isCurio() ? curio : vanilla;
            if (entry.raw != null) {
                target.add(entry.raw.copy());
                continue;
            }
            CompoundTag compound = new CompoundTag();
            compound.putString("AttributeName", entry.attributeId);
            compound.putString("Name", entry.name.isEmpty() ? MODIFIER_NAME : entry.name);
            compound.putDouble("Amount", entry.amount);
            compound.putInt("Operation", entry.operation.toValue());
            compound.putUUID("UUID", entry.uuid);
            if (entry.location.kind() != Location.Kind.EQUIPMENT_ALL) {
                compound.putString("Slot", entry.location.id());
            }
            target.add(compound);
        }

        ItemStack working = session.working();
        if (vanilla.isEmpty()) {
            working.removeTagKey(TAG_ATTRIBUTE_MODIFIERS);
        } else {
            session.tag().put(TAG_ATTRIBUTE_MODIFIERS, vanilla);
        }
        if (curio.isEmpty()) {
            working.removeTagKey(CurioMark.TAG_MODIFIERS);
        } else {
            session.tag().put(CurioMark.TAG_MODIFIERS, curio);
        }
    }
}
