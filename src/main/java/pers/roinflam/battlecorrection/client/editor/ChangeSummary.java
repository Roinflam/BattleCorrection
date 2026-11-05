package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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
        KNOWN.put("tag.display.Name", "name");
        KNOWN.put("tag.display.Lore", "lore");
        KNOWN.put("tag.Enchantments", "enchantments");
        KNOWN.put("tag.StoredEnchantments", "stored_enchantments");
        KNOWN.put("tag.AttributeModifiers", "attributes");
        KNOWN.put("tag.Unbreakable", "unbreakable");
        KNOWN.put("tag.HideFlags", "hide_flags");
        KNOWN.put("tag.Damage", "damage");
        KNOWN.put("tag.RepairCost", "repair_cost");
        KNOWN.put("tag.CustomModelData", "model");
    }

    /**
     * 一条改动：+ 新增 / - 删除 / ~ 修改
     */
    public record Entry(char mark, String label) {
    }

    /**
     * 摘要
     *
     * @param entries 显示出来的条目（有数量上限）
     * @param total   实际改动处总数
     */
    public record Result(List<Entry> entries, int total) {

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
    public static Result diff(@Nonnull CompoundTag before, @Nonnull CompoundTag after, int limit) {
        List<Entry> entries = new ArrayList<>();
        int[] total = {0};
        walk("", before, after, 0, limit, entries, total);
        return new Result(Collections.unmodifiableList(entries), total[0]);
    }

    private static void walk(@Nonnull String path, @Nullable Tag before, @Nullable Tag after, int depth, int limit,
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
        if (!known && depth < MAX_DEPTH && before instanceof CompoundTag a && after instanceof CompoundTag b) {
            TreeSet<String> keys = new TreeSet<>(a.getAllKeys());
            keys.addAll(b.getAllKeys());
            for (String key : keys) {
                walk(path.isEmpty() ? key : path + "." + key, a.get(key), b.get(key), depth + 1, limit, entries, total);
            }
            return;
        }
        if (!known && depth < MAX_DEPTH && before instanceof ListTag a && after instanceof ListTag b
                && a.size() == b.size() && a.size() <= MAX_LIST_COMPARE) {
            for (int i = 0; i < a.size(); i++) {
                walk(path + "[" + i + "]", a.get(i), b.get(i), depth + 1, limit, entries, total);
            }
            return;
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
        return I18n.get("gui.battlecorrection.editor.change." + suffix) + "  " + path;
    }
}
