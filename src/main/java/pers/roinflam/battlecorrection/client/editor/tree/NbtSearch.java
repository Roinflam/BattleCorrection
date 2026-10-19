package pers.roinflam.battlecorrection.client.editor.tree;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import pers.roinflam.battlecorrection.client.editor.NbtType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 在一份 NBT 里按键名或值搜索（不区分大小写的子串匹配）
 * <p>
 * 设计成在后台线程跑：只读传进来的那份标签（调用方给的是拷贝），不碰界面和编辑中的物品。
 * 遍历顺序和树里的显示顺序一致（复合标签按键名排序、列表按下标），所以"下一个"就是往下走。
 * 路径只在命中时才生成，平时用一个段栈记位置，二十万个节点走一遍也不会生成二十万个路径对象。
 * 数组（B; / I; / L;）只比键名，不比里面的数字。
 */
public final class NbtSearch {

    /**
     * 命中数上限，到了就停
     */
    public static final int MAX_HITS = 2000;

    /**
     * 每走这么多个节点看一次是否被取消
     */
    private static final int CANCEL_CHECK_INTERVAL = 4096;

    /**
     * 搜索结果
     */
    public static final class Result {

        private final String query;
        private final List<NbtPath> hits;
        private final Set<NbtPath> hitSet;
        private final Set<NbtPath> ancestors;
        private final boolean capped;

        /**
         * @param query     搜的是什么
         * @param hits      命中的路径，按树的显示顺序
         * @param hitSet    同上，方便查
         * @param ancestors 所有命中节点的祖先（用于给折叠着的容器加标记）
         * @param capped    是否到了上限
         */
        public Result(@Nonnull String query, @Nonnull List<NbtPath> hits, @Nonnull Set<NbtPath> hitSet,
                      @Nonnull Set<NbtPath> ancestors, boolean capped) {
            this.query = query;
            this.hits = hits;
            this.hitSet = hitSet;
            this.ancestors = ancestors;
            this.capped = capped;
        }

        @Nonnull
        public String query() {
            return query;
        }

        @Nonnull
        public List<NbtPath> hits() {
            return hits;
        }

        @Nonnull
        public Set<NbtPath> hitSet() {
            return hitSet;
        }

        @Nonnull
        public Set<NbtPath> ancestors() {
            return ancestors;
        }

        public boolean capped() {
            return capped;
        }
    }

    private final String needle;
    private final BooleanSupplier cancelled;
    private final List<NbtPath> hits = new ArrayList<>();
    private Object[] stack = new Object[16];
    private int depth;
    private int visited;
    private boolean stop;

    private NbtSearch(@Nonnull String needle, @Nonnull BooleanSupplier cancelled) {
        this.needle = needle;
        this.cancelled = cancelled;
    }

    /**
     * 搜索
     *
     * @param root      根（只读）
     * @param query     关键字，首尾空白忽略；空串返回空结果
     * @param cancelled 返回 true 时尽快放弃（结果作废）
     * @return 结果；被取消返回 null
     */
    @Nullable
    public static Result run(@Nonnull NBTTagCompound root, @Nonnull String query, @Nonnull BooleanSupplier cancelled) {
        String needle = query.trim();
        if (needle.isEmpty()) {
            return new Result(query, Collections.<NbtPath>emptyList(), Collections.<NbtPath>emptySet(),
                    Collections.<NbtPath>emptySet(), false);
        }
        NbtSearch search = new NbtSearch(needle, cancelled);
        search.visitCompound(root);
        if (search.stop && search.hits.size() < MAX_HITS) {
            return null;
        }
        Set<NbtPath> hitSet = new HashSet<>(search.hits);
        Set<NbtPath> ancestors = new HashSet<>();
        for (NbtPath hit : search.hits) {
            NbtPath p = hit.parent();
            // 某个祖先已经在集合里时，它再往上的也都在，可以停
            while (p != null && ancestors.add(p)) {
                p = p.parent();
            }
        }
        return new Result(query, search.hits, hitSet, ancestors, search.hits.size() >= MAX_HITS);
    }

    private void visitCompound(@Nonnull NBTTagCompound compound) {
        String[] keys = compound.getKeySet().toArray(new String[0]);
        Arrays.sort(keys);
        for (String key : keys) {
            if (stop) {
                return;
            }
            NBTBase child = compound.getTag(key);
            if (child != null) {
                push(key);
                visit(child, containsIgnoreCase(key, needle));
                depth--;
            }
        }
    }

    private void visit(@Nonnull NBTBase tag, boolean keyMatched) {
        if (++visited % CANCEL_CHECK_INTERVAL == 0 && cancelled.getAsBoolean()) {
            stop = true;
            return;
        }
        if (keyMatched || valueMatches(tag)) {
            hits.add(NbtPath.of(stack, depth));
            if (hits.size() >= MAX_HITS) {
                stop = true;
                return;
            }
        }
        if (tag instanceof NBTTagCompound) {
            visitCompound((NBTTagCompound) tag);
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            for (int i = 0; i < list.tagCount() && !stop; i++) {
                push(i);
                visit(list.get(i), false);
                depth--;
            }
        }
    }

    private boolean valueMatches(@Nonnull NBTBase tag) {
        if (tag instanceof NBTTagString) {
            return containsIgnoreCase(((NBTTagString) tag).getString(), needle);
        }
        if (tag instanceof NBTPrimitive) {
            return containsIgnoreCase(NbtType.numberText((NBTPrimitive) tag), needle);
        }
        return false;
    }

    private void push(@Nonnull Object segment) {
        if (depth == stack.length) {
            stack = Arrays.copyOf(stack, depth * 2);
        }
        stack[depth++] = segment;
    }

    /**
     * 不区分大小写的子串查找，不生成小写副本
     *
     * @param haystack 在这里找
     * @param needle   找这个（非空）
     * @return true = 包含
     */
    public static boolean containsIgnoreCase(@Nonnull String haystack, @Nonnull String needle) {
        int n = needle.length();
        int max = haystack.length() - n;
        if (n == 0 || max < 0) {
            return n == 0;
        }
        char first = needle.charAt(0);
        char firstLower = Character.toLowerCase(first);
        char firstUpper = Character.toUpperCase(first);
        for (int i = 0; i <= max; i++) {
            char c = haystack.charAt(i);
            if (c != first && c != firstLower && c != firstUpper
                    && Character.toLowerCase(c) != firstLower && Character.toUpperCase(c) != firstUpper) {
                continue;
            }
            if (haystack.regionMatches(true, i, needle, 0, n)) {
                return true;
            }
        }
        return false;
    }
}
