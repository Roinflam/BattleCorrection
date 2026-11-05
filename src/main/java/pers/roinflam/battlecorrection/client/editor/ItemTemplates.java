package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物品模板：把一件物品的 NBT 连名字存在本地文件里，之后套用到别的物品上
 * <p>
 * 文件在客户端游戏目录下 {@code battlecorrection/item_templates.dat}（压缩 NBT），
 * 格式 {@code {Templates:[{Name, Item, Count, Tag, Created}]}}。纯客户端功能，不经过服务端。
 * 同名保存直接覆盖。文件读失败只记日志不崩，当作没有模板。
 */
public final class ItemTemplates {

    /**
     * 一份模板
     *
     * @param name    模板名
     * @param item    保存时的物品 ID（只做参考，套用到别的物品上也行）
     * @param count   保存时的数量
     * @param tag     物品 tag 的副本（可能为空）
     * @param created 保存时间（毫秒）
     */
    public record Template(String name, ResourceLocation item, int count, CompoundTag tag, long created) {
    }

    private static final String KEY_TEMPLATES = "Templates";
    private static final String KEY_NAME = "Name";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_TAG = "Tag";
    private static final String KEY_CREATED = "Created";
    private static final String DIRECTORY = "battlecorrection";
    private static final String FILE_NAME = "item_templates.dat";

    @Nullable
    private static List<Template> cache;

    private ItemTemplates() {
    }

    /**
     * 全部模板（按名字排序的只读副本）
     */
    @Nonnull
    public static List<Template> all() {
        List<Template> list = new ArrayList<>(loaded());
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return Collections.unmodifiableList(list);
    }

    /**
     * 保存一份模板（同名覆盖）
     *
     * @param name  模板名
     * @param stack 物品（取它的 tag 副本）
     * @param item  物品 ID
     * @return 存进去的模板
     */
    @Nonnull
    public static Template save(@Nonnull String name, @Nonnull ItemStack stack, @Nonnull ResourceLocation item) {
        List<Template> list = loaded();
        list.removeIf(template -> template.name().equalsIgnoreCase(name));
        CompoundTag tag = stack.getTag();
        Template template = new Template(name, item, stack.getCount(), tag == null ? new CompoundTag() : tag.copy(),
                System.currentTimeMillis());
        list.add(template);
        write();
        return template;
    }

    /**
     * 删除一份模板
     *
     * @param name 模板名
     */
    public static void delete(@Nonnull String name) {
        if (loaded().removeIf(template -> template.name().equalsIgnoreCase(name))) {
            write();
        }
    }

    @Nonnull
    private static List<Template> loaded() {
        if (cache == null) {
            cache = read();
        }
        return cache;
    }

    @Nonnull
    private static File file() {
        return new File(new File(Minecraft.getInstance().gameDirectory, DIRECTORY), FILE_NAME);
    }

    @Nonnull
    private static List<Template> read() {
        List<Template> list = new ArrayList<>();
        File file = file();
        if (!file.isFile()) {
            return list;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file);
            ListTag templates = root.getList(KEY_TEMPLATES, Tag.TAG_COMPOUND);
            for (int i = 0; i < templates.size(); i++) {
                CompoundTag entry = templates.getCompound(i);
                ResourceLocation item = ResourceLocation.tryParse(entry.getString(KEY_ITEM));
                list.add(new Template(entry.getString(KEY_NAME),
                        item == null ? new ResourceLocation("minecraft", "air") : item,
                        Math.max(1, entry.getInt(KEY_COUNT)), entry.getCompound(KEY_TAG).copy(), entry.getLong(KEY_CREATED)));
            }
        } catch (IOException | RuntimeException e) {
            LogUtil.error("读取物品模板文件失败，当作没有模板: " + file, e);
        }
        return list;
    }

    private static void write() {
        File file = file();
        File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            LogUtil.error("创建物品模板目录失败: " + directory);
            return;
        }
        ListTag templates = new ListTag();
        for (Template template : loaded()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_NAME, template.name());
            entry.putString(KEY_ITEM, template.item().toString());
            entry.putInt(KEY_COUNT, template.count());
            entry.put(KEY_TAG, template.tag().copy());
            entry.putLong(KEY_CREATED, template.created());
            templates.add(entry);
        }
        CompoundTag root = new CompoundTag();
        root.put(KEY_TEMPLATES, templates);
        try {
            NbtIo.writeCompressed(root, file);
        } catch (IOException e) {
            LogUtil.error("写入物品模板文件失败: " + file, e);
        }
    }
}
