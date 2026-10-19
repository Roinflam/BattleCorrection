package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 改动摘要：对比打开时和现在的 NBT，列出改了哪些地方
 * <p>
 * 关闭确认框里用，让玩家在丢掉修改之前看一眼"到底改了什么"。逐级比较复合标签，
 * 列表在长度没变时按下标逐项比，其余整个当作一处改动。几个玩家熟悉的位置（名称、Lore、附魔、属性……）
 * 当成一个整体，直接用中文名报告，不再往里一层层展开；其余的报告 NBT 路径。
 * 1.12.2 的耐久是根上的 Damage（不在 tag 里），附魔在 tag.ench。
 * <p>
 * 只在打开确认框时算一次，不在渲染路径上。
 */
public final class ChangeSummary {

    /**
     * 递归的最大深度，再深的整段当一处改动
     */
    private static final int MAX_DEPTH = 5;

    /**
     * 列表逐项比较的元素数上限，更长的整个当一处改动
     */
    private static final int MAX_LIST_COMPARE = 64;

    /**
     * 认得的路径 → 翻译键后缀（gui.battlecorrection.editor.change.xxx）
     */
    private static final Map<String, String> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put("id", "item");
        KNOWN.put("Count", "count");
        KNOWN.put("Damage", "damage");
        KNOWN.put("tag.display.Name", "name");
        KNOWN.put("tag.display.Lore", "lore");
        KNOWN.put("tag.ench", "enchantments");
        KNOWN.put("tag.StoredEnchantments", "stored_enchantments");
        KNOWN.put("tag.AttributeModifiers", "attributes");
        KNOWN.put("tag.Unbreakable", "unbreakable");
        KNOWN.put("tag.HideFlags", "hide_flags");
        KNOWN.put("tag.RepairCost", "repair_cost");
    }

    /**
     * 一条改动：+ 新增 / - 删除 / ~ 修改
     */
    public static final class Entry {
        private final char mark;
        @Nonnull
        private final String label;

        public Entry(char mark, @Nonnull String label) {
            this.mark = mark;
            this.label = label;
        }

        public char mark() {
            return mark;
        }

        @Nonnull
        public String label() {
            return label;
        }
    }

    /**
     * 摘要
     */
    public static final class Result {
        @Nonnull
        private final List<Entry> entries;
        private final int total;

        /**
         * @param entries 显示出来的条目（有数量上限）
         * @param total   实际改动处总数
         */
        public Result(@Nonnull List<Entry> entries, int total) {
            this.entries = entries;
            this.total = total;
        }

        @Nonnull
        public List<Entry> entries() {
            return entries;
        }

        public int total() {
            return total;
        }

        public boolean isEmpty() {
            return total == 0;
        }
    }

    private ChangeSummary() {
    }

    /**
     * 比较两份 NBT
     *
     * @param before 打开时的
     * @param after  现在的
     * @param limit  最多列多少条
     * @return 摘要
     */
    @Nonnull
    public static Result diff(@Nonnull NBTTagCompound before, @Nonnull NBTTagCompound after, int limit) {
        List<Entry> entries = new ArrayList<>();
        int[] total = {0};
        walk("", before, after, 0, limit, entries, total);
        return new Result(Collections.unmodifiableList(entries), total[0]);
    }

    private static void walk(@Nonnull String path, @Nullable NBTBase before, @Nullable NBTBase after, int depth, int limit,
                             @Nonnull List<Entry> entries, @Nonnull int[] total) {
        if (before == null && after == null) {
            return;
        }
        if (before != null && before.equals(after)) {
            return;
        }
        if (before == null || after == null) {
            add(entries, total, limit, before == null ? '+' : '-', path);
            return;
        }
        boolean known = KNOWN.containsKey(path);
        if (!known && depth < MAX_DEPTH && before instanceof NBTTagCompound && after instanceof NBTTagCompound) {
            NBTTagCompound a = (NBTTagCompound) before;
            NBTTagCompound b = (NBTTagCompound) after;
            TreeSet<String> keys = new TreeSet<>(a.getKeySet());
            keys.addAll(b.getKeySet());
            for (String key : keys) {
                walk(path.isEmpty() ? key : path + "." + key, a.getTag(key), b.getTag(key), depth + 1, limit, entries, total);
            }
            return;
        }
        if (!known && depth < MAX_DEPTH && before instanceof NBTTagList && after instanceof NBTTagList) {
            NBTTagList a = (NBTTagList) before;
            NBTTagList b = (NBTTagList) after;
            if (a.tagCount() == b.tagCount() && a.tagCount() <= MAX_LIST_COMPARE) {
                for (int i = 0; i < a.tagCount(); i++) {
                    walk(path + "[" + i + "]", a.get(i), b.get(i), depth + 1, limit, entries, total);
                }
                return;
            }
        }
        add(entries, total, limit, '~', path);
    }

    private static void add(@Nonnull List<Entry> entries, @Nonnull int[] total, int limit, char mark,
                            @Nonnull String path) {
        total[0]++;
        if (entries.size() < limit) {
            entries.add(new Entry(mark, labelOf(path)));
        }
    }

    /**
     * 路径的显示名：认得的用中文名并附上路径，其余直接显示路径
     */
    @Nonnull
    private static String labelOf(@Nonnull String path) {
        String suffix = KNOWN.get(path);
        if (suffix == null) {
            return path.isEmpty() ? "(root)" : path;
        }
        return I18n.format("gui.battlecorrection.editor.change." + suffix) + "  " + path;
    }
}
