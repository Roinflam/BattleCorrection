package pers.roinflam.battlecorrection.client.editor.tree;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagString;
import pers.roinflam.battlecorrection.client.editor.NbtType;
import pers.roinflam.battlecorrection.client.editor.ui.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * NBT 树上的一个节点（或者分页的"显示更多"行）
 * <p>
 * 子节点是懒建的：{@link NbtTreeModel} 只在容器展开、或者要定位到它下面时才建 {@link #children}，
 * 折叠的容器只靠 {@link #childCount()} 显示个数。节点持有的是根标签里的那个标签对象本身，
 * 页面改了叶子值就把新标签塞回 {@link #tag}，不用整树重建。
 * <p>
 * 下面几个 render 开头的字段是树视图的缓存（截断后的文字、宽度），只在主线程读写，
 * 节点文字变了由模型把它们清掉，视图下一帧重算一次。
 */
public final class TreeNode {

    /**
     * 行的种类
     */
    public enum Kind {
        /**
         * 普通节点
         */
        ENTRY,
        /**
         * 分页行："还有 N 项，点击显示更多"
         */
        MORE
    }

    private static final int MAX_STRING_PREVIEW = 64;

    @Nullable
    public final TreeNode parent;
    /**
     * 在父复合标签里的键；根、列表元素、分页行为 null
     */
    @Nullable
    public final String key;
    /**
     * 在父列表里的下标；不是列表元素为 -1
     */
    public final int index;
    public final int depth;
    @Nonnull
    public final NbtPath path;
    @Nonnull
    public final Kind kind;

    /**
     * 节点对应的标签；叶子改值时由页面换成新标签（再调 {@link NbtTreeModel#leafChanged}）
     */
    @Nonnull
    public NBTBase tag;
    @Nullable
    NbtType type;

    // ── 懒建的子节点（模型维护） ──
    @Nullable
    List<TreeNode> children;
    /**
     * 复合标签排好序的键（建子节点时算一次）
     */
    @Nullable
    String[] sortedKeys;
    /**
     * 建子节点时容器的大小；数据被改了却没通知时靠它发现
     */
    int builtSize = -1;
    @Nullable
    TreeNode moreRow;
    /**
     * 分页行：还剩几项没显示
     */
    int remaining;

    // ── 拍平时写的状态 ──
    boolean expanded;
    /**
     * 搜索标记：0 无，1 命中，2 折叠着但里面有命中
     */
    byte hit;

    // ── 视图缓存 ──
    @Nullable
    String nameText;
    @Nullable
    String valueText;
    public int renderNameWidth = -1;
    /**
     * 截断后的名字 / 值（带 § 码的字符串，画的时候按格式解析，和没截断时一样）
     */
    @Nullable
    public String renderName;
    public int renderNameLimit = -1;
    @Nullable
    public String renderValue;
    public int renderValueLimit = -1;
    public boolean renderValueClipped;
    @Nullable
    public List<Component> renderTooltip;

    TreeNode(@Nullable TreeNode parent, @Nullable String key, int index, @Nonnull NBTBase tag, int depth,
             @Nonnull NbtPath path, @Nonnull Kind kind) {
        this.parent = parent;
        this.key = key;
        this.index = index;
        this.tag = tag;
        this.depth = depth;
        this.path = path;
        this.kind = kind;
        this.type = NbtType.of(tag);
    }

    public boolean isRoot() {
        return parent == null;
    }

    public boolean isMore() {
        return kind == Kind.MORE;
    }

    public boolean isContainer() {
        return kind == Kind.ENTRY && (tag instanceof NBTTagCompound || tag instanceof NBTTagList);
    }

    public boolean isListChild() {
        return index >= 0;
    }

    /**
     * 当前是否展开（上次拍平时的状态）
     *
     * @return true = 展开
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * 搜索标记
     *
     * @return 0 无，1 命中，2 折叠着但里面有命中
     */
    public int searchMark() {
        return hit;
    }

    /**
     * 标签类型（缓存，换标签时由模型刷新）
     *
     * @return 类型；未知类型返回 null
     */
    @Nullable
    public NbtType type() {
        return type;
    }

    /**
     * 分页行还剩几项
     *
     * @return 个数
     */
    public int remaining() {
        return remaining;
    }

    /**
     * 容器的子项个数，不用建子节点
     *
     * @return 个数；不是容器返回 0
     */
    public int childCount() {
        if (tag instanceof NBTTagCompound) {
            return ((NBTTagCompound) tag).getSize();
        }
        if (tag instanceof NBTTagList) {
            return ((NBTTagList) tag).tagCount();
        }
        return 0;
    }

    /**
     * 行上显示的名字（缓存）
     *
     * @return 名字
     */
    @Nonnull
    public String nameText() {
        String cached = nameText;
        if (cached == null) {
            if (isListChild()) {
                cached = "[" + index + "]";
            } else {
                cached = key == null ? "" : key;
            }
            nameText = cached;
        }
        return cached;
    }

    /**
     * 行尾的值摘要，带前面的冒号（缓存）
     *
     * @return 文字
     */
    @Nonnull
    public String valueText() {
        String cached = valueText;
        if (cached == null) {
            cached = isMore() ? "" : ": " + summary(tag);
            valueText = cached;
        }
        return cached;
    }

    /**
     * 文字变了（改值、增删子项）：清掉文字缓存，视图下一帧重算
     */
    void invalidateText() {
        valueText = null;
        renderValue = null;
        renderValueLimit = -1;
        renderValueClipped = false;
        renderTooltip = null;
        type = NbtType.of(tag);
    }

    /**
     * 行尾的值摘要：容器和数组显示个数，字符串截断，数字带原版后缀（5b、1.5f）
     *
     * @param tag 标签
     * @return 文本
     */
    @Nonnull
    public static String summary(@Nonnull NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            return "{" + ((NBTTagCompound) tag).getSize() + "}";
        }
        if (tag instanceof NBTTagList) {
            return "[" + ((NBTTagList) tag).tagCount() + "]";
        }
        if (tag instanceof NBTTagByteArray) {
            return "[B; " + NbtType.arraySize(tag) + "]";
        }
        if (tag instanceof NBTTagIntArray) {
            return "[I; " + NbtType.arraySize(tag) + "]";
        }
        if (tag instanceof NBTTagLongArray) {
            return "[L; " + NbtType.arraySize(tag) + "]";
        }
        if (tag instanceof NBTTagString) {
            String value = ((NBTTagString) tag).getString();
            if (value.length() > MAX_STRING_PREVIEW) {
                value = value.substring(0, MAX_STRING_PREVIEW) + "…";
            }
            return "\"" + value.replace("\n", "\\n") + "\"";
        }
        // 数字：1.12.2 的 toString 和新版本一样带后缀（5b、2s、10L、1.5f、1.0d）
        return tag.toString();
    }
}
