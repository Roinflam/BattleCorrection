package pers.roinflam.battlecorrection.client.editor.tree;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 NBTTagList 的插入、移动和类型规则
 * <p>
 * 1.12.2 的列表只有追加（appendTag）、按下标替换（set）和删除（removeTag），没有"插到第 i 个"；
 * 而且 appendTag / set 遇到和列表类型不同的标签不报错，只打一条日志就把它丢了。还有一个坑：
 * 列表删空之后 {@link NBTTagList#getTagType()} 还是原来的类型（要到写盘时才清零），
 * 这时往里放别的类型照样被丢。所以：
 * <ul>
 *   <li>能不能放按 {@link #accepts} 判断（空列表什么类型都收，和存盘后再读回来的行为一致）；</li>
 *   <li>真要放之前再看 {@link #acceptsInPlace}：空列表还记着别的旧类型时原地放不进去，
 *   调用方换一个新列表（{@link #of}）放回父节点；</li>
 *   <li>插入、移动都是"把后半段摘下来、追加、再接回去"，类型一致时 appendTag 不会丢东西。</li>
 * </ul>
 */
public final class NbtLists {

    private NbtLists() {
    }

    /**
     * 列表现在的元素类型（空列表不算有类型）
     *
     * @param list 列表
     * @return 类型 ID；空列表返回 0
     */
    public static int elementType(@Nonnull NBTTagList list) {
        return list.hasNoTags() ? 0 : list.getTagType();
    }

    /**
     * 按 NBT 的规则这个标签能不能放进列表（非空列表的元素类型必须一致）
     *
     * @param list 列表
     * @param tag  标签
     * @return true = 能
     */
    public static boolean accepts(@Nonnull NBTTagList list, @Nonnull NBTBase tag) {
        int type = elementType(list);
        return tag.getId() != 0 && (type == 0 || type == tag.getId());
    }

    /**
     * 能不能原地放进这个列表对象（空列表还记着别的旧类型时不能）
     *
     * @param list 列表
     * @param tag  标签
     * @return true = 能
     */
    public static boolean acceptsInPlace(@Nonnull NBTTagList list, @Nonnull NBTBase tag) {
        int type = list.getTagType();
        return tag.getId() != 0 && (type == 0 || type == tag.getId());
    }

    /**
     * 插到 index 处（index 夹到 0..size）；调用方先用 {@link #acceptsInPlace} 确认
     *
     * @param list  列表
     * @param index 下标
     * @param tag   标签
     * @return false = 类型不符，没有插入（列表原样不动）
     */
    public static boolean insert(@Nonnull NBTTagList list, int index, @Nonnull NBTBase tag) {
        if (!acceptsInPlace(list, tag)) {
            return false;
        }
        int size = list.tagCount();
        int at = index < 0 ? 0 : Math.min(index, size);
        // 从尾巴往前摘（每次摘最后一个不用挪别的元素），几万个元素的列表插到最前面也不卡
        List<NBTBase> tail = new ArrayList<>(size - at);
        while (list.tagCount() > at) {
            tail.add(list.removeTag(list.tagCount() - 1));
        }
        list.appendTag(tag);
        for (int i = tail.size() - 1; i >= 0; i--) {
            list.appendTag(tail.get(i));
        }
        return true;
    }

    /**
     * 按下标替换
     *
     * @param list  列表
     * @param index 下标（必须在范围内）
     * @param tag   标签
     * @return false = 类型不符或越界，没有替换
     */
    public static boolean set(@Nonnull NBTTagList list, int index, @Nonnull NBTBase tag) {
        if (index < 0 || index >= list.tagCount() || !acceptsInPlace(list, tag)) {
            return false;
        }
        list.set(index, tag);
        return true;
    }

    /**
     * 把第 from 个挪到 to（两个下标都必须在范围内）
     *
     * @param list 列表
     * @param from 原下标
     * @param to   新下标
     */
    public static void move(@Nonnull NBTTagList list, int from, int to) {
        if (from == to) {
            return;
        }
        NBTBase moved = list.removeTag(from);
        // 列表里还有别的元素，类型就是 moved 的类型；只剩它自己时 from == to，走不到这里
        insert(list, to, moved);
    }

    /**
     * 用一组同类型的元素建一个新列表
     *
     * @param elements 元素
     * @return 新列表
     */
    @Nonnull
    public static NBTTagList of(@Nonnull List<NBTBase> elements) {
        NBTTagList list = new NBTTagList();
        for (NBTBase element : elements) {
            list.appendTag(element);
        }
        return list;
    }
}
