package pers.roinflam.battlecorrection.client.editor.tree;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/**
 * NBT 树的数据层：懒建的节点树 + 展开 / 分页 / 选中 / 搜索标记状态 + 拍平后的行
 * <p>
 * 不碰任何界面类，可以脱离游戏单独跑。
 * <ul>
 *   <li>懒构建：只有展开的容器才建子节点；折叠的只记个数。{@link #refresh()} 从根往下走一遍已展开的部分，
 *   代价正比于拍出来的行数，和物品里总共有多少节点无关。</li>
 *   <li>分页：一个容器一次最多建 {@link #PAGE_SIZE} 个子节点，后面跟一行"还有 N 项"，点一下再多建一页。
 *   几万个元素的列表展开也只多出两百行。</li>
 *   <li>增量更新：叶子改值只清那一个节点的文字缓存，行列表不动；增删改名只让那个容器下次重建子节点，
 *   别的已建好的子树原样复用。</li>
 *   <li>状态按 {@link NbtPath} 记，重建后展开的还展开、选中的还选中；列表增删、移动、复合标签改名时
 *   顺手把受影响的路径挪过去，不会出现"删了第 0 个，原来第 1 个的展开状态跑到别人身上"。</li>
 * </ul>
 * 只在主线程用。
 */
public final class NbtTreeModel {

    /**
     * 分页大小
     */
    public static final int PAGE_SIZE = 200;

    /**
     * 全部展开最多展开出多少行；再多就停，免得几十万行一次全建出来
     */
    public static final int EXPAND_ALL_BUDGET = 20_000;

    @Nullable
    private NBTTagCompound root;
    @Nullable
    private TreeNode rootNode;
    private final Set<NbtPath> expanded = new HashSet<>();
    private final Map<NbtPath, Integer> limits = new HashMap<>();
    private final ArrayList<TreeNode> rows = new ArrayList<>();
    @Nullable
    private NbtPath selectedPath;
    @Nullable
    private TreeNode selectedNode;
    @Nullable
    private Set<NbtPath> hits;
    @Nullable
    private Set<NbtPath> hitAncestors;
    @Nonnull
    private String rootLabel = "";
    @Nonnull
    private IntFunction<String> moreLabel = remaining -> "+" + remaining;

    /**
     * 滚动位置（第几行在最上面），放在模型里是为了切页回来还停在原处
     */
    public int scroll;

    public NbtTreeModel() {
        expanded.add(NbtPath.ROOT);
    }

    /**
     * 设置显示文字（根节点的名字、分页行的文字）
     *
     * @param rootLabel 根节点名字
     * @param moreLabel 分页行文字，参数是剩余个数
     */
    public void setLabels(@Nonnull String rootLabel, @Nonnull IntFunction<String> moreLabel) {
        this.rootLabel = rootLabel;
        this.moreLabel = moreLabel;
        if (rootNode != null) {
            rootNode.nameText = rootLabel;
        }
    }

    /**
     * 换一份根（重新读物品、撤销重做之后）；展开、分页、选中状态保留
     *
     * @param root 根
     */
    public void setRoot(@Nonnull NBTTagCompound root) {
        this.root = root;
        TreeNode node = new TreeNode(null, null, -1, root, 0, NbtPath.ROOT, TreeNode.Kind.ENTRY);
        node.nameText = rootLabel;
        this.rootNode = node;
        refresh();
    }

    @Nullable
    public NBTTagCompound root() {
        return root;
    }

    @Nullable
    public TreeNode rootNode() {
        return rootNode;
    }

    /**
     * 拍平后的行。直接给内部列表，省得每帧包一层；调用方只读
     *
     * @return 行
     */
    @Nonnull
    public List<TreeNode> rows() {
        return rows;
    }

    // ═══════════════════════════════════════════════════════════════
    // 拍平与懒建
    // ═══════════════════════════════════════════════════════════════

    /**
     * 按当前展开状态重新拍平
     */
    public void refresh() {
        rows.clear();
        selectedNode = null;
        if (rootNode == null) {
            return;
        }
        flatten(rootNode, hits != null && !hits.isEmpty());
    }

    private void flatten(@Nonnull TreeNode node, boolean searching) {
        rows.add(node);
        boolean container = node.isContainer();
        node.expanded = container && expanded.contains(node.path);
        if (selectedNode == null && selectedPath != null && node.path.equals(selectedPath)) {
            selectedNode = node;
        }
        if (searching) {
            if (hits.contains(node.path)) {
                node.hit = 1;
            } else {
                node.hit = container && !node.expanded && hitAncestors != null && hitAncestors.contains(node.path) ? (byte) 2 : 0;
            }
        } else {
            node.hit = 0;
        }
        if (!node.expanded) {
            return;
        }
        List<TreeNode> children = ensureChildren(node);
        for (TreeNode child : children) {
            flatten(child, searching);
        }
        int remaining = node.childCount() - children.size();
        if (remaining > 0) {
            rows.add(moreRow(node, remaining));
        }
    }

    /**
     * 确保容器的子节点建到了分页上限
     *
     * @param node 容器
     * @return 子节点（已建的部分）
     */
    @Nonnull
    private List<TreeNode> ensureChildren(@Nonnull TreeNode node) {
        int size = node.childCount();
        List<TreeNode> children = node.children;
        if (children == null || node.builtSize != size) {
            // 第一次建，或者大小对不上（数据被改了），从头建
            children = new ArrayList<>(Math.min(size, limitOf(node.path)));
            node.children = children;
            node.builtSize = size;
            node.moreRow = null;
            node.sortedKeys = null;
            if (node.tag instanceof NBTTagCompound) {
                // 原版 NBTTagCompound 底下是 HashMap，没有稳定顺序，按键名排
                String[] keys = ((NBTTagCompound) node.tag).getKeySet().toArray(new String[0]);
                Arrays.sort(keys);
                node.sortedKeys = keys;
            }
        }
        int want = Math.min(size, limitOf(node.path));
        if (children.size() < want) {
            extendChildren(node, want);
        }
        return children;
    }

    /**
     * 往后多建子节点，已建的不动
     */
    private static void extendChildren(@Nonnull TreeNode node, int want) {
        List<TreeNode> children = node.children;
        if (children == null) {
            return;
        }
        int depth = node.depth + 1;
        if (node.tag instanceof NBTTagCompound && node.sortedKeys != null) {
            NBTTagCompound compound = (NBTTagCompound) node.tag;
            String[] keys = node.sortedKeys;
            for (int i = children.size(); i < want && i < keys.length; i++) {
                String key = keys[i];
                NBTBase child = compound.getTag(key);
                if (child == null) {
                    break;
                }
                children.add(new TreeNode(node, key, -1, child, depth, node.path.child(key), TreeNode.Kind.ENTRY));
            }
        } else if (node.tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) node.tag;
            for (int i = children.size(); i < want && i < list.tagCount(); i++) {
                children.add(new TreeNode(node, null, i, list.get(i), depth, node.path.child(i), TreeNode.Kind.ENTRY));
            }
        }
    }

    @Nonnull
    private TreeNode moreRow(@Nonnull TreeNode container, int remaining) {
        TreeNode more = container.moreRow;
        if (more == null || more.remaining != remaining) {
            more = new TreeNode(container, null, -1, container.tag, container.depth + 1, container.path, TreeNode.Kind.MORE);
            more.remaining = remaining;
            more.nameText = moreLabel.apply(remaining);
            container.moreRow = more;
        }
        return more;
    }

    private int limitOf(@Nonnull NbtPath path) {
        Integer limit = limits.get(path);
        return limit == null ? PAGE_SIZE : limit;
    }

    /**
     * 分页行被点了：这个容器再多显示一页
     *
     * @param more 分页行
     */
    public void showMore(@Nonnull TreeNode more) {
        TreeNode container = more.parent;
        if (!more.isMore() || container == null) {
            return;
        }
        int built = container.children == null ? 0 : container.children.size();
        limits.put(container.path, Math.max(limitOf(container.path), built) + PAGE_SIZE);
        refresh();
    }

    // ═══════════════════════════════════════════════════════════════
    // 展开折叠
    // ═══════════════════════════════════════════════════════════════

    public boolean isExpanded(@Nonnull NbtPath path) {
        return expanded.contains(path);
    }

    /**
     * 只改展开状态，不重新拍平（连着改好几处时最后调一次 {@link #refresh()}）
     *
     * @param path     路径
     * @param expand   true = 展开
     */
    public void setExpanded(@Nonnull NbtPath path, boolean expand) {
        if (expand) {
            expanded.add(path);
        } else if (!path.isRoot()) {
            expanded.remove(path);
        }
    }

    /**
     * 展开 / 折叠一个容器并重新拍平
     *
     * @param node 节点
     */
    public void toggle(@Nonnull TreeNode node) {
        if (!node.isContainer()) {
            return;
        }
        setExpanded(node.path, !expanded.contains(node.path));
        refresh();
    }

    /**
     * 全部展开（按层往下，行数到 {@link #EXPAND_ALL_BUDGET} 就停）
     *
     * @return true = 节点太多，没展开完
     */
    public boolean expandAll() {
        if (rootNode == null) {
            return false;
        }
        ArrayDeque<TreeNode> queue = new ArrayDeque<>();
        queue.add(rootNode);
        int count = 1;
        boolean truncated = false;
        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            List<TreeNode> children = ensureChildren(node);
            int added = children.size() + (node.childCount() > children.size() ? 1 : 0);
            if (count + added > EXPAND_ALL_BUDGET && node != rootNode) {
                truncated = true;
                break;
            }
            expanded.add(node.path);
            count += added;
            for (TreeNode child : children) {
                if (child.isContainer()) {
                    queue.add(child);
                }
            }
        }
        refresh();
        return truncated;
    }

    /**
     * 全部折叠（根保持展开，不然什么都看不见）
     */
    public void collapseAll() {
        expanded.clear();
        expanded.add(NbtPath.ROOT);
        scroll = 0;
        refresh();
    }

    // ═══════════════════════════════════════════════════════════════
    // 选中与定位
    // ═══════════════════════════════════════════════════════════════

    /**
     * 按路径选中（不展开、不重新拍平）
     *
     * @param path 路径；null = 清除选中
     */
    public void select(@Nullable NbtPath path) {
        selectedPath = path;
        selectedNode = path == null ? null : resolve(path, true);
    }

    @Nullable
    public NbtPath selectedPath() {
        return selectedPath;
    }

    /**
     * 当前选中的节点（就算它的父节点折叠了也能拿到；数据改过之后按路径重新找）
     *
     * @return 节点；没选中或路径已不存在返回 null
     */
    @Nullable
    public TreeNode selected() {
        if (selectedPath == null) {
            return null;
        }
        selectedNode = resolve(selectedPath, true);
        return selectedNode;
    }

    /**
     * 画的时候判断"是不是选中行"用：上次拍平 / 选中时记下的节点，不再查找
     *
     * @return 节点
     */
    @Nullable
    public TreeNode selectedForRender() {
        return selectedNode;
    }

    /**
     * 按路径找节点，沿路需要的子节点顺手建出来
     *
     * @param path   路径（下标必须已规范化成非负）
     * @param extend 目标落在分页后面时要不要把分页放宽到能看见它
     * @return 节点；不存在返回 null
     */
    @Nullable
    public TreeNode resolve(@Nonnull NbtPath path, boolean extend) {
        TreeNode current = rootNode;
        if (current == null) {
            return null;
        }
        for (int i = 0; i < path.size(); i++) {
            if (!current.isContainer()) {
                return null;
            }
            List<TreeNode> children = ensureChildren(current);
            int position;
            if (path.isIndex(i)) {
                if (!(current.tag instanceof NBTTagList)) {
                    return null;
                }
                position = path.index(i);
            } else {
                String key = path.key(i);
                if (!(current.tag instanceof NBTTagCompound) || current.sortedKeys == null || key == null) {
                    return null;
                }
                position = Arrays.binarySearch(current.sortedKeys, key);
            }
            if (position < 0 || position >= current.childCount()) {
                return null;
            }
            if (position >= children.size()) {
                if (!extend) {
                    return null;
                }
                // 落在分页后面：放宽到包含它的那一页
                int limit = (position / PAGE_SIZE + 1) * PAGE_SIZE;
                limits.put(current.path, Math.max(limit, limitOf(current.path)));
                extendChildren(current, Math.min(current.childCount(), limit));
                if (position >= children.size()) {
                    return null;
                }
            }
            current = children.get(position);
        }
        return current;
    }

    /**
     * 展开到某个路径、选中它并重新拍平
     *
     * @param path 路径（下标已规范化）
     * @return 它在行列表里的位置；不存在返回 -1（选中不变）
     */
    public int reveal(@Nonnull NbtPath path) {
        TreeNode node = resolve(path, true);
        if (node == null) {
            return -1;
        }
        for (NbtPath p = path.parent(); p != null; p = p.parent()) {
            expanded.add(p);
        }
        selectedPath = path;
        refresh();
        return rows.indexOf(node);
    }

    /**
     * 删除前先算好"删掉之后该选谁"：同一容器里显示在它后面的那个，没有就前面那个，再没有就父节点
     *
     * @param node 要删的节点
     * @return 删除之后的路径（列表下标已按删除后算好）
     */
    @Nonnull
    public NbtPath selectionAfterRemoval(@Nonnull TreeNode node) {
        TreeNode parent = node.parent;
        if (parent == null) {
            return NbtPath.ROOT;
        }
        if (node.isListChild()) {
            if (node.index + 1 < parent.childCount()) {
                // 后一个会顶到这个下标上来
                return parent.path.child(node.index);
            }
            return node.index > 0 ? parent.path.child(node.index - 1) : parent.path;
        }
        ensureChildren(parent);
        String[] keys = parent.sortedKeys;
        int position = keys == null || node.key == null ? -1 : Arrays.binarySearch(keys, node.key);
        if (position < 0) {
            return parent.path;
        }
        if (position + 1 < keys.length) {
            return parent.path.child(keys[position + 1]);
        }
        return position > 0 ? parent.path.child(keys[position - 1]) : parent.path;
    }

    /**
     * 节点在行列表里的位置
     *
     * @param node 节点
     * @return 位置；不在（被折叠了）返回 -1
     */
    public int rowOf(@Nullable TreeNode node) {
        return node == null ? -1 : rows.indexOf(node);
    }

    // ═══════════════════════════════════════════════════════════════
    // 数据改动的通知
    // ═══════════════════════════════════════════════════════════════

    /**
     * 叶子换了值（页面已经把新标签放进 node.tag）：只清它的文字缓存，行不用重排
     *
     * @param node 节点
     */
    public void leafChanged(@Nonnull TreeNode node) {
        node.invalidateText();
    }

    /**
     * 容器的子项有增删 / 改名 / 换位：下次拍平时重建它的子节点（之后记得 {@link #refresh()}）
     * <p>
     * 容器本身换成了另一个标签对象（页面把 node.tag 换掉了）也走这里。
     *
     * @param container 容器
     */
    public void childrenChanged(@Nonnull TreeNode container) {
        container.children = null;
        container.sortedKeys = null;
        container.builtSize = -1;
        container.moreRow = null;
        container.invalidateText();
    }

    /**
     * 复合标签里的子节点改了名：父容器重建，展开 / 分页 / 选中状态跟着搬到新名字下
     *
     * @param oldPath 旧路径
     * @param newPath 新路径
     * @param parent  父容器
     */
    public void renamed(@Nonnull NbtPath oldPath, @Nonnull NbtPath newPath, @Nonnull TreeNode parent) {
        childrenChanged(parent);
        movePrefix(oldPath, newPath);
    }

    /**
     * 列表删了第 index 个：它下面的状态丢掉，后面的下标都减一
     *
     * @param list  列表节点
     * @param index 下标
     */
    public void listRemoved(@Nonnull TreeNode list, int index) {
        childrenChanged(list);
        remapIndices(list.path, k -> k == index ? -1 : (k > index ? k - 1 : k));
    }

    /**
     * 列表在 index 处插入了一个：从 index 开始的下标都加一
     *
     * @param list  列表节点
     * @param index 下标
     */
    public void listInserted(@Nonnull TreeNode list, int index) {
        childrenChanged(list);
        remapIndices(list.path, k -> k >= index ? k + 1 : k);
    }

    /**
     * 列表元素从 from 挪到 to
     *
     * @param list 列表节点
     * @param from 原下标
     * @param to   新下标
     */
    public void listMoved(@Nonnull TreeNode list, int from, int to) {
        childrenChanged(list);
        remapIndices(list.path, k -> {
            if (k == from) {
                return to;
            }
            if (from < to && k > from && k <= to) {
                return k - 1;
            }
            if (from > to && k >= to && k < from) {
                return k + 1;
            }
            return k;
        });
    }

    /**
     * 把以 from 开头的状态路径搬到 to 下面
     */
    private void movePrefix(@Nonnull NbtPath from, @Nonnull NbtPath to) {
        List<NbtPath> drop = new ArrayList<>();
        List<NbtPath> add = new ArrayList<>();
        for (NbtPath path : expanded) {
            if (path.startsWith(from)) {
                drop.add(path);
                add.add(path.replacePrefix(from, to));
            }
        }
        expanded.removeAll(drop);
        expanded.addAll(add);

        Map<NbtPath, Integer> moved = new HashMap<>();
        limits.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(from)) {
                moved.put(entry.getKey().replacePrefix(from, to), entry.getValue());
                return true;
            }
            return false;
        });
        limits.putAll(moved);

        if (selectedPath != null && selectedPath.startsWith(from)) {
            selectedPath = selectedPath.replacePrefix(from, to);
        }
    }

    /**
     * 把某个列表下面的状态路径按 map 换下标（map 返回 -1 = 丢掉）
     */
    private void remapIndices(@Nonnull NbtPath listPath, @Nonnull IntUnaryOperator map) {
        int depth = listPath.size();
        List<NbtPath> drop = new ArrayList<>();
        List<NbtPath> add = new ArrayList<>();
        for (NbtPath path : expanded) {
            NbtPath mapped = remap(path, listPath, depth, map);
            if (mapped != path) {
                drop.add(path);
                if (mapped != null) {
                    add.add(mapped);
                }
            }
        }
        expanded.removeAll(drop);
        expanded.addAll(add);

        Map<NbtPath, Integer> moved = new HashMap<>();
        limits.entrySet().removeIf(entry -> {
            NbtPath mapped = remap(entry.getKey(), listPath, depth, map);
            if (mapped == entry.getKey()) {
                return false;
            }
            if (mapped != null) {
                moved.put(mapped, entry.getValue());
            }
            return true;
        });
        limits.putAll(moved);

        if (selectedPath != null) {
            NbtPath mapped = remap(selectedPath, listPath, depth, map);
            if (mapped != selectedPath) {
                selectedPath = mapped;
            }
        }
    }

    /**
     * @return 原对象 = 不受影响；null = 丢掉；否则是换了下标的新路径
     */
    @Nullable
    private static NbtPath remap(@Nonnull NbtPath path, @Nonnull NbtPath listPath, int depth,
                                 @Nonnull IntUnaryOperator map) {
        if (path.size() <= depth || !path.isIndex(depth) || !path.startsWith(listPath)) {
            return path;
        }
        int index = path.index(depth);
        int mapped = map.applyAsInt(index);
        if (mapped == index) {
            return path;
        }
        return mapped < 0 ? null : path.withIndex(depth, mapped);
    }

    // ═══════════════════════════════════════════════════════════════
    // 搜索标记
    // ═══════════════════════════════════════════════════════════════

    /**
     * 设置搜索命中（命中行高亮、折叠着但里面有命中的容器加个标记）并重新拍平
     *
     * @param hits      命中的路径；null = 清除
     * @param ancestors 命中节点的所有祖先
     */
    public void setSearchMarks(@Nullable Set<NbtPath> hits, @Nullable Set<NbtPath> ancestors) {
        this.hits = hits;
        this.hitAncestors = ancestors;
        refresh();
    }
}
