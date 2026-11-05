package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.fml.ModList;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.PopupList;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.editor.EditorPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 附魔页
 * <p>
 * 左边是物品上已有的附魔：左键点一行弹出等级列表直接选（1 到原版上限，当前等级高亮），右键直接删；
 * 下面的等级框可以填任意数（上限 255）。右边是全部已注册的附魔，带搜索，右侧标来自哪个模组，
 * 点一下就加到左边（已有的话只是选中它）。灰字的是原版认为"这个物品通常不能附"的，强行加上照样生效。
 * <p>
 * 悬停任一行显示说明：名字、ID、来源模组、原版上限（超过可能不生效或表现异常）、诅咒/不适用提示，
 * 以及附魔描述——描述用的是 {@code enchantment.<模组>.<id>.desc} 这个通用翻译键（附魔描述类模组都用它），
 * 本模组自带原版 39 个的中英文，别的模组有就显示，没有就不显示。
 * <p>
 * 存盘：普通物品写 {@code minecraft:enchantments}，附魔书写 {@code minecraft:stored_enchantments}
 * （1.20.1 是 Enchantments / StoredEnchantments 列表）。组件里"在提示框里显示"的开关（标签页的隐藏信息）原样保留。
 * 等级上限 255：1.21.1 的附魔组件编解码器只认 1 ~ 255，再大整件物品存不了盘。
 * <p>
 * 1.21.1 附魔是数据驱动的，列表从当前世界的附魔注册表取（服务器数据包加的附魔也在里面）。
 * 1.20.1 里"读不出来的条目原样保留"在 1.21.1 不需要了：附魔不存在的物品在读存档时就整件读不出来，到不了编辑器；
 * 标签页的"附魔光效"改用独立的 {@code minecraft:enchantment_glint_override} 组件，也不再借用附魔列表。
 * 组件里的附魔没有先后顺序（原版提示框按 {@code #minecraft:tooltip_order} 排），左边列表的顺序只在这一页没被别的页改过时保持。
 */
public class EnchantmentsPage extends EditorPageBase {

    private static final int MAX_LEVEL = 255;
    private static final int COLUMN_GAP = 8;
    private static final int BOX_HEIGHT = 14;
    private static final int TOOLTIP_WIDTH = 220;
    private static final String TAG_ENCHANTMENTS = "Enchantments";
    private static final String TAG_STORED_ENCHANTMENTS = "StoredEnchantments";

    /**
     * 物品上已有的一条附魔
     */
    private static final class Entry implements ScrollList.Row {
        final Holder<Enchantment> enchantment;
        final ResourceLocation id;
        int level;

        Entry(@Nonnull Holder<Enchantment> enchantment, @Nonnull ResourceLocation id, int level) {
            this.enchantment = enchantment;
            this.id = id;
            this.level = level;
        }

        boolean overMax() {
            return level > enchantment.value().getMaxLevel();
        }

        @Nonnull
        @Override
        public Component label() {
            return nameOf(enchantment);
        }

        @Nullable
        @Override
        public Component secondary() {
            MutableComponent text = Component.translatable("gui.battlecorrection.editor.enchant.level_of", level,
                    enchantment.value().getMaxLevel());
            return overMax() ? text.withStyle(style -> style.withColor(EditorTheme.WARNING & 0xFFFFFF)) : text;
        }

        @Override
        public int color() {
            return isCurse(enchantment) ? EditorTheme.DANGER : EditorTheme.TEXT;
        }
    }

    /**
     * 全部附魔列表里的一项
     */
    private static final class Choice implements ScrollList.Row {
        final Holder<Enchantment> enchantment;
        final ResourceLocation id;
        final Component name;
        final Component modName;
        final boolean applicable;
        final String searchText;

        Choice(@Nonnull Holder<Enchantment> enchantment, @Nonnull ResourceLocation id, boolean applicable) {
            this.enchantment = enchantment;
            this.id = id;
            this.name = nameOf(enchantment);
            this.modName = Component.literal(modDisplayName(id.getNamespace()));
            this.applicable = applicable;
            this.searchText = (name.getString() + " " + id + " " + modName.getString()).toLowerCase(Locale.ROOT);
        }

        @Nonnull
        @Override
        public Component label() {
            return name;
        }

        @Nullable
        @Override
        public Component secondary() {
            return modName;
        }

        @Override
        public int color() {
            if (isCurse(enchantment)) {
                return EditorTheme.DANGER;
            }
            return applicable ? EditorTheme.TEXT : EditorTheme.TEXT_DIM;
        }
    }

    /**
     * 等级弹出列表里的一项
     */
    private static final class LevelRow implements ScrollList.Row {
        final int level;
        final int max;
        final boolean current;

        LevelRow(int level, int max, boolean current) {
            this.level = level;
            this.max = max;
            this.current = current;
        }

        @Nonnull
        @Override
        public Component label() {
            return Component.translatable("gui.battlecorrection.editor.enchant.level_row", level);
        }

        @Nullable
        @Override
        public Component secondary() {
            if (current) {
                return Component.translatable("gui.battlecorrection.editor.enchant.level_current");
            }
            return level > max ? Component.translatable("gui.battlecorrection.editor.enchant.level_over") : null;
        }

        @Override
        public int color() {
            return level > max ? EditorTheme.WARNING : (current ? EditorTheme.ACCENT_HI : EditorTheme.TEXT);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    /**
     * 上次写进物品（或载入时读到）的附魔组件：页面重建时物品上还是它，就保留左边列表的顺序
     */
    @Nullable
    private ItemEnchantments written;
    private final List<Choice> allChoices = new ArrayList<>();
    private final PopupList<LevelRow> levelPopup = new PopupList<>();
    private boolean binding;

    @Nullable
    private ScrollList<Entry> currentList;
    @Nullable
    private ScrollList<Choice> allList;
    @Nullable
    private FlatEditBox searchBox;
    @Nullable
    private FlatEditBox levelBox;
    @Nullable
    private FlatButton deleteButton;
    private int rightX;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public EnchantmentsPage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.ENCHANTMENTS;
    }

    @Override
    public boolean wantsPreview() {
        return false;
    }

    @Override
    protected void build() {
        loadFromItem();
        buildChoices();

        int columnWidth = (width - COLUMN_GAP) / 2;
        rightX = x + columnWidth + COLUMN_GAP;
        int listTop = y + 11;
        int formHeight = BOX_HEIGHT + 6;

        // 左：已有附魔 + 等级/删除
        currentList = add(new ScrollList<Entry>(x, listTop, columnWidth, height - 11 - formHeight,
                Component.translatable("gui.battlecorrection.editor.enchant.empty"))
                .onSelect(this::onEntryClicked)
                .onRightClick(this::deleteEntry));
        currentList.setItems(entries);

        int formY = y + height - BOX_HEIGHT;
        int levelLabelWidth = font.width(Component.translatable("gui.battlecorrection.editor.enchant.level")) + 4;
        levelBox = add(new FlatEditBox(font, x + levelLabelWidth + 1, formY, 44, BOX_HEIGHT,
                Component.literal("1"), false));
        levelBox.setMaxLength(3);
        levelBox.setFilter(text -> text.isEmpty() || text.matches("\\d+"));
        levelBox.setResponder(this::onLevelChanged);
        levelBox.active = false;

        deleteButton = add(new FlatButton(x + columnWidth - 48, formY, 48, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.enchant.delete"), this::deleteSelected)
                .style(FlatButton.Style.DANGER)
                .tooltip(Component.translatable("gui.battlecorrection.editor.enchant.delete.tooltip")));
        deleteButton.active = false;

        // 右：搜索 + 全部附魔
        searchBox = add(new FlatEditBox(font, rightX + 1, listTop, columnWidth - 2, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.search.hint"), false));
        searchBox.setResponder(text -> refreshChoices());
        int allTop = listTop + BOX_HEIGHT + 4;
        allList = add(new ScrollList<Choice>(rightX, allTop, columnWidth, y + height - allTop,
                Component.translatable("gui.battlecorrection.editor.search.no_match"))
                .onSelect(this::onChoiceClicked));
        refreshChoices();
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        label(g, Component.translatable("gui.battlecorrection.editor.enchant.current", entries.size()), x, y);
        label(g, Component.translatable("gui.battlecorrection.editor.enchant.all"), rightX, y);
        label(g, Component.translatable("gui.battlecorrection.editor.enchant.level"), x, y + height - BOX_HEIGHT + 3);
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        levelPopup.render(g, mouseX, mouseY, partialTick);
        if (levelPopup.isOpen()) {
            return;
        }
        if (allList != null && allList.hoveredItem() != null) {
            Choice hovered = allList.hoveredItem();
            renderEnchantmentTooltip(g, mouseX, mouseY, hovered.enchantment, hovered.id, -1, hovered.applicable);
            return;
        }
        if (currentList != null && currentList.hoveredItem() != null) {
            Entry hovered = currentList.hoveredItem();
            renderEnchantmentTooltip(g, mouseX, mouseY, hovered.enchantment, hovered.id, hovered.level,
                    session.working().supportsEnchantment(hovered.enchantment));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return levelPopup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return levelPopup.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean onEscape() {
        if (levelPopup.isOpen()) {
            levelPopup.close();
            return true;
        }
        return false;
    }

    /**
     * 附魔的说明提示框：名字、ID、来源、上限、诅咒/不适用、描述
     *
     * @param level      当前等级；-1 = 还没加到物品上
     * @param applicable 原版认为能不能附在这件物品上
     */
    private void renderEnchantmentTooltip(@Nonnull GuiGraphics g, int mouseX, int mouseY,
                                          @Nonnull Holder<Enchantment> enchantment, @Nonnull ResourceLocation id,
                                          int level, boolean applicable) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        MutableComponent title = nameOf(enchantment).copy()
                .withStyle(isCurse(enchantment) ? ChatFormatting.RED : ChatFormatting.WHITE);
        if (level > 0) {
            title.append(Component.literal(" " + level).withStyle(ChatFormatting.AQUA));
        }
        lines.addAll(font.split(title, TOOLTIP_WIDTH));
        lines.addAll(font.split(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.from",
                modDisplayName(id.getNamespace())).withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.max_level",
                enchantment.value().getMaxLevel()).withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        if (level > enchantment.value().getMaxLevel()) {
            lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.over_max")
                    .withStyle(ChatFormatting.YELLOW), TOOLTIP_WIDTH));
        }
        if (isCurse(enchantment)) {
            lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.curse")
                    .withStyle(ChatFormatting.RED), TOOLTIP_WIDTH));
        }
        if (!applicable) {
            lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.not_applicable")
                    .withStyle(ChatFormatting.YELLOW), TOOLTIP_WIDTH));
        }
        String descriptionKey = "enchantment." + id.getNamespace() + "." + id.getPath() + ".desc";
        if (I18n.exists(descriptionKey)) {
            lines.addAll(font.split(Component.translatable(descriptionKey).withStyle(ChatFormatting.WHITE), TOOLTIP_WIDTH));
        }
        g.renderTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * 附魔名：1.21.1 附魔的名字是附魔定义里的 description 组件（原版就是一个翻译组件）
     */
    @Nonnull
    private static Component nameOf(@Nonnull Holder<Enchantment> enchantment) {
        return enchantment.value().description();
    }

    /**
     * 是不是诅咒：1.21.1 改成看附魔标签 {@code #minecraft:curse}
     */
    private static boolean isCurse(@Nonnull Holder<Enchantment> enchantment) {
        return enchantment.is(EnchantmentTags.CURSE);
    }

    /**
     * 模组显示名（minecraft → Minecraft；找不到就用命名空间本身）
     */
    @Nonnull
    private static String modDisplayName(@Nonnull String namespace) {
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    /**
     * 建立全部附魔的列表：能附的在前 → Minecraft 在前、其他模组按模组名 → 按附魔名
     */
    private void buildChoices() {
        allChoices.clear();
        ItemStack working = session.working();
        session.registries().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(enchantment ->
                allChoices.add(new Choice(enchantment, enchantment.key().location(), working.supportsEnchantment(enchantment))));
        // 能附的在前；同组内 Minecraft 先、其他模组按模组名；再按附魔名
        allChoices.sort(Comparator.comparing((Choice choice) -> !choice.applicable)
                .thenComparing(choice -> !"minecraft".equals(choice.id.getNamespace()))
                .thenComparing(choice -> choice.modName.getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(choice -> choice.name.getString(), String.CASE_INSENSITIVE_ORDER));
    }

    /**
     * 按搜索框内容过滤右侧列表（名字、ID、模组名都能搜）
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
     * 左键点了已有的一条：填等级框，并弹出等级列表
     *
     * @param entry 条目
     */
    private void onEntryClicked(@Nonnull Entry entry) {
        bindForm(entry);
        openLevelPopup(entry);
    }

    /**
     * 把表单绑定到某条附魔
     *
     * @param entry 条目；null = 清空禁用
     */
    private void bindForm(@Nullable Entry entry) {
        if (levelBox == null || deleteButton == null) {
            return;
        }
        binding = true;
        levelBox.setValue(entry == null ? "" : String.valueOf(entry.level));
        binding = false;
        levelBox.active = entry != null;
        deleteButton.active = entry != null;
    }

    /**
     * 在选中行旁边弹出 1 ~ 原版上限 的等级列表；当前等级超过上限时也列进去
     *
     * @param entry 条目
     */
    private void openLevelPopup(@Nonnull Entry entry) {
        if (currentList == null) {
            return;
        }
        int max = entry.enchantment.value().getMaxLevel();
        List<LevelRow> rows = new ArrayList<>();
        for (int level = 1; level <= Math.max(max, entry.level); level++) {
            if (level <= max || level == entry.level) {
                rows.add(new LevelRow(level, max, level == entry.level));
            }
        }
        int rowTop = currentList.rowTop(entry);
        int popupY = rowTop < 0 ? currentList.getY() : rowTop + ScrollList.ROW_HEIGHT;
        int popupWidth = Math.min(currentList.getWidth() - 12, 120);
        levelPopup.open(currentList.getX() + 12, popupY, popupWidth, y + height, rows, row -> setLevel(entry, row.level));
    }

    /**
     * 右侧点了一项：已有就选中，没有就加上（默认等级 1）
     *
     * @param choice 选项
     */
    private void onChoiceClicked(@Nonnull Choice choice) {
        if (currentList == null) {
            return;
        }
        for (Entry entry : entries) {
            if (entry.enchantment.equals(choice.enchantment)) {
                currentList.select(entry);
                bindForm(entry);
                openLevelPopup(entry);
                return;
            }
        }
        Entry entry = new Entry(choice.enchantment, choice.id, 1);
        entries.add(entry);
        writeToItem();
        currentList.setItems(entries);
        currentList.select(entry);
        bindForm(entry);
        openLevelPopup(entry);
    }

    /**
     * 应用一个等级
     *
     * @param entry 条目
     * @param level 等级
     */
    private void setLevel(@Nonnull Entry entry, int level) {
        entry.level = Mth.clamp(level, 1, MAX_LEVEL);
        writeToItem();
        if (currentList != null && currentList.selectedItem() == entry) {
            bindForm(entry);
        }
    }

    /**
     * 等级输入框变化（自定义等级）
     *
     * @param text 新内容
     */
    private void onLevelChanged(@Nonnull String text) {
        if (binding || currentList == null) {
            return;
        }
        Entry entry = currentList.selectedItem();
        if (entry == null || text.isEmpty()) {
            return;
        }
        try {
            entry.level = Mth.clamp(Integer.parseInt(text), 1, MAX_LEVEL);
        } catch (NumberFormatException ignored) {
            return;
        }
        writeToItem();
    }

    /**
     * 删除选中的附魔（按钮）
     */
    private void deleteSelected() {
        if (currentList == null) {
            return;
        }
        Entry entry = currentList.selectedItem();
        if (entry != null) {
            deleteEntry(entry);
        }
    }

    /**
     * 删除一条附魔（右键或按钮）
     *
     * @param entry 条目
     */
    private void deleteEntry(@Nonnull Entry entry) {
        if (currentList == null) {
            return;
        }
        levelPopup.close();
        boolean wasSelected = currentList.selectedItem() == entry;
        entries.remove(entry);
        writeToItem();
        currentList.setItems(entries);
        if (wasSelected) {
            currentList.select(null);
            bindForm(null);
        }
    }

    /**
     * 物品上现在的附魔组件（附魔书是 stored_enchantments）
     */
    @Nonnull
    private ItemEnchantments currentEnchantments() {
        ItemStack working = session.working();
        return working.getOrDefault(EnchantmentHelper.getComponentType(working), ItemEnchantments.EMPTY);
    }

    /**
     * 从物品副本读附魔
     * <p>
     * 物品上的附魔还是这一页上次写的那份时不重新读，保留左边列表的顺序（组件本身不记顺序）。
     */
    private void loadFromItem() {
        ItemEnchantments current = currentEnchantments();
        if (written != null && written.equals(current)) {
            written = current;
            return;
        }
        entries.clear();
        for (var entry : current.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            ResourceLocation id = enchantment.unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) {
                continue;
            }
            entries.add(new Entry(enchantment, id, Mth.clamp(entry.getIntValue(), 1, MAX_LEVEL)));
        }
        written = current;
    }

    /**
     * 把附魔写回物品副本
     * <p>
     * 在原组件的基础上改（保留"在提示框里显示"的开关），一条都不剩时写空组件：和物品默认值一样时补丁项自动去掉，
     * 相当于 1.20.1 删掉附魔列表。
     */
    private void writeToItem() {
        ItemStack working = session.working();
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(currentEnchantments());
        mutable.removeIf(enchantment -> true);
        for (Entry entry : entries) {
            mutable.set(entry.enchantment, Mth.clamp(entry.level, 1, MAX_LEVEL));
        }
        ItemEnchantments result = mutable.toImmutable();
        working.set(EnchantmentHelper.getComponentType(working), result);
        written = result;
    }
}
