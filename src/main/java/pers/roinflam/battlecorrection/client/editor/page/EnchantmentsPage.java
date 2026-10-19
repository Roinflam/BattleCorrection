package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
import pers.roinflam.battlecorrection.client.editor.ui.Ui;
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
 * 下面的等级框可以填任意数（上限 32767）。右边是全部已注册的附魔，带搜索，右侧标来自哪个模组，
 * 点一下就加到左边（已有的话只是选中它）。灰字的是原版认为"这个物品通常不能附"的，强行加上照样生效。
 * <p>
 * 悬停任一行显示说明：名字、ID（含存进 NBT 的数字 ID）、来源模组、原版上限（超过可能不生效或表现异常）、诅咒/不适用提示，
 * 以及附魔描述——描述用的是 {@code <附魔的翻译键>.desc} 这个通用翻译键（1.12.2 的附魔描述类模组都用它，
 * 例如 {@code enchantment.damage.all.desc}），本模组自带原版 30 个的中英文，别的模组有就显示，没有就不显示。
 * <p>
 * 存盘：普通物品写 ench，附魔书写 StoredEnchantments，格式 {id: 数字 ID (short), lvl: short}。
 * 等级上限 32767：1.12.2 按 short 读等级，读的时候不截断，再大就存不下了。
 * 读不出来的条目（所在模组被删了、ID 不是数字）和 0 级的条目（标签页那个"附魔光效"用的占位）原样保留，不显示、不改动。
 */
public class EnchantmentsPage extends EditorPageBase {

    private static final int MAX_LEVEL = Short.MAX_VALUE;
    private static final int COLUMN_GAP = 8;
    private static final int BOX_HEIGHT = 14;
    private static final int TOOLTIP_WIDTH = 220;
    private static final String TAG_ENCHANTMENTS = "ench";
    private static final String TAG_STORED_ENCHANTMENTS = "StoredEnchantments";

    /**
     * 物品上已有的一条附魔
     */
    private static final class Entry implements ScrollList.Row {
        final Enchantment enchantment;
        final ResourceLocation id;
        int level;

        Entry(@Nonnull Enchantment enchantment, @Nonnull ResourceLocation id, int level) {
            this.enchantment = enchantment;
            this.id = id;
            this.level = level;
        }

        boolean overMax() {
            return level > enchantment.getMaxLevel();
        }

        @Nonnull
        @Override
        public Component label() {
            return Component.translatable(enchantment.getName());
        }

        @Nullable
        @Override
        public Component secondary() {
            Component text = Component.translatable("gui.battlecorrection.editor.enchant.level_of", level,
                    enchantment.getMaxLevel());
            return overMax() ? text.withColor(EditorTheme.WARNING & 0xFFFFFF) : text;
        }

        @Override
        public int color() {
            return enchantment.isCurse() ? EditorTheme.DANGER : EditorTheme.TEXT;
        }
    }

    /**
     * 全部附魔列表里的一项
     */
    private static final class Choice implements ScrollList.Row {
        final Enchantment enchantment;
        final ResourceLocation id;
        final Component name;
        final Component modName;
        final boolean applicable;
        final String searchText;

        Choice(@Nonnull Enchantment enchantment, @Nonnull ResourceLocation id, boolean applicable) {
            this.enchantment = enchantment;
            this.id = id;
            this.name = Component.translatable(enchantment.getName());
            this.modName = Component.raw(modDisplayName(id.getResourceDomain()));
            this.applicable = applicable;
            this.searchText = (name.getString() + " " + id + " " + modName.getString() + " #"
                    + Enchantment.getEnchantmentID(enchantment)).toLowerCase(Locale.ROOT);
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
            if (enchantment.isCurse()) {
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
    private final List<NBTTagCompound> keptRaw = new ArrayList<>();
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
        levelBox.setMaxLength(5);
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
                    hovered.enchantment.canApply(session.working()));
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
                                          @Nonnull Enchantment enchantment, @Nonnull ResourceLocation id,
                                          int level, boolean applicable) {
        List<Component> lines = new ArrayList<>();
        Component title = Component.translatable(enchantment.getName())
                .withStyle(enchantment.isCurse() ? TextFormatting.RED : TextFormatting.WHITE);
        if (level > 0) {
            title.append(Component.raw(" " + level).withStyle(TextFormatting.AQUA));
        }
        lines.addAll(font.split(title, TOOLTIP_WIDTH));
        // 1.12.2 的 NBT 里存的是数字 ID，一起列出来，手写 NBT 时用得上
        lines.addAll(font.split(Component.raw(id + "  #" + Enchantment.getEnchantmentID(enchantment))
                .withStyle(TextFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.from",
                modDisplayName(id.getResourceDomain())).withStyle(TextFormatting.GRAY), TOOLTIP_WIDTH));
        lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.max_level",
                enchantment.getMaxLevel()).withStyle(TextFormatting.GRAY), TOOLTIP_WIDTH));
        if (level > enchantment.getMaxLevel()) {
            lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.over_max")
                    .withStyle(TextFormatting.YELLOW), TOOLTIP_WIDTH));
        }
        if (enchantment.isCurse()) {
            lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.curse")
                    .withStyle(TextFormatting.RED), TOOLTIP_WIDTH));
        }
        if (!applicable) {
            lines.addAll(font.split(Component.translatable("gui.battlecorrection.editor.enchant.not_applicable")
                    .withStyle(TextFormatting.YELLOW), TOOLTIP_WIDTH));
        }
        String descriptionKey = enchantment.getName() + ".desc";
        if (Ui.hasTranslation(descriptionKey)) {
            lines.addAll(font.split(Component.translatable(descriptionKey).withStyle(TextFormatting.WHITE), TOOLTIP_WIDTH));
        }
        g.renderTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * 模组显示名（minecraft → Minecraft；找不到就用命名空间本身）
     */
    @Nonnull
    private static String modDisplayName(@Nonnull String namespace) {
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        ModContainer container = Loader.instance().getIndexedModList().get(namespace);
        return container == null ? namespace : container.getName();
    }

    /**
     * 建立全部附魔的列表：能附的在前 → Minecraft 在前、其他模组按模组名 → 按附魔名
     */
    private void buildChoices() {
        allChoices.clear();
        ItemStack working = session.working();
        for (Enchantment enchantment : Enchantment.REGISTRY) {
            ResourceLocation id = Enchantment.REGISTRY.getNameForObject(enchantment);
            if (id == null) {
                continue;
            }
            boolean applicable;
            try {
                applicable = enchantment.canApply(working);
            } catch (RuntimeException e) {
                // 模组的 canApply 只考虑了正常物品，碰上奇怪的 NBT 可能会抛异常，当作不适用
                applicable = false;
            }
            allChoices.add(new Choice(enchantment, id, applicable));
        }
        // 能附的在前；同组内 Minecraft 先、其他模组按模组名；再按附魔名
        allChoices.sort(Comparator.comparing((Choice choice) -> !choice.applicable)
                .thenComparing(choice -> !"minecraft".equals(choice.id.getResourceDomain()))
                .thenComparing(choice -> choice.modName.getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(choice -> choice.name.getString(), String.CASE_INSENSITIVE_ORDER));
    }

    /**
     * 按搜索框内容过滤右侧列表（名字、ID、模组名、数字 ID 都能搜）
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
        int max = entry.enchantment.getMaxLevel();
        List<LevelRow> rows = new ArrayList<>();
        for (int level = 1; level <= max; level++) {
            rows.add(new LevelRow(level, max, level == entry.level));
        }
        if (entry.level > max) {
            rows.add(new LevelRow(entry.level, max, true));
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
            if (entry.enchantment == choice.enchantment) {
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
     * 这个物品用哪个键存附魔
     *
     * @return 键名
     */
    @Nonnull
    private String enchantmentsKey() {
        return session.working().getItem() == Items.ENCHANTED_BOOK ? TAG_STORED_ENCHANTMENTS : TAG_ENCHANTMENTS;
    }

    /**
     * 从物品副本读附魔
     */
    private void loadFromItem() {
        entries.clear();
        keptRaw.clear();
        NBTTagCompound tag = session.tagOrNull();
        String key = enchantmentsKey();
        if (tag == null || !tag.hasKey(key, 9)) {
            return;
        }
        NBTTagList list = tag.getTagList(key, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound raw = list.getCompoundTagAt(i);
            int level = raw.getShort("lvl");
            Enchantment enchantment = raw.hasKey("id", 99) ? Enchantment.getEnchantmentByID(raw.getShort("id")) : null;
            ResourceLocation id = enchantment == null ? null : Enchantment.REGISTRY.getNameForObject(enchantment);
            // 0 级的条目不起作用，是光效占位（空条目 {} 读出来也是 0 级），和认不出来的一起原样留着
            if (enchantment == null || id == null || level == 0) {
                keptRaw.add(raw.copy());
                continue;
            }
            // 同一附魔出现两次时原版只认第一条，这里也只保留第一条
            boolean duplicate = false;
            for (Entry entry : entries) {
                if (entry.enchantment == enchantment) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                entries.add(new Entry(enchantment, id, Mth.clamp(level, 1, MAX_LEVEL)));
            }
        }
    }

    /**
     * 把附魔写回物品副本
     */
    private void writeToItem() {
        String key = enchantmentsKey();
        if (entries.isEmpty() && keptRaw.isEmpty()) {
            NBTTagCompound tag = session.tagOrNull();
            if (tag != null) {
                tag.removeTag(key);
                if (tag.hasNoTags()) {
                    session.setTag(null);
                }
            }
            return;
        }
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setShort("id", (short) Enchantment.getEnchantmentID(entry.enchantment));
            compound.setShort("lvl", (short) entry.level);
            list.appendTag(compound);
        }
        for (NBTTagCompound raw : keptRaw) {
            list.appendTag(raw.copy());
        }
        session.tag().setTag(key, list);
    }
}
