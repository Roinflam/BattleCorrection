package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物品模板：把一件物品的 NBT 连名字存在本地文件里，之后套用到别的物品上
 * <p>
 * 文件在客户端游戏目录下 {@code battlecorrection/item_templates.dat}（压缩 NBT），
 * 格式 {@code {Templates:[{Name, Item, Damage, Count, Tag, Created}]}}。纯客户端功能，不经过服务端。
 * 1.12.2 的损害值在物品根上、不在 tag 里，所以单独存一份 Damage（耐久，或者有子类型的物品的子类型），套用时由模板页决定用不用。
 * 同名保存直接覆盖。文件读失败只记日志不崩，当作没有模板。
 */
public final class ItemTemplates {

    /**
     * 一份模板
     */
    public static final class Template {
        private final String name;
        private final ResourceLocation item;
        private final int damage;
        private final int count;
        private final NBTTagCompound tag;
        private final long created;

        /**
         * @param name    模板名
         * @param item    保存时的物品 ID（只做参考，套用到别的物品上也行）
         * @param damage  保存时的损害值
         * @param count   保存时的数量
         * @param tag     物品 tag 的副本（可能为空）
         * @param created 保存时间（毫秒）
         */
        public Template(@Nonnull String name, @Nonnull ResourceLocation item, int damage, int count,
                        @Nonnull NBTTagCompound tag, long created) {
            this.name = name;
            this.item = item;
            this.damage = damage;
            this.count = count;
            this.tag = tag;
            this.created = created;
        }

        @Nonnull
        public String name() {
            return name;
        }

        @Nonnull
        public ResourceLocation item() {
            return item;
        }

        public int damage() {
            return damage;
        }

        public int count() {
            return count;
        }

        @Nonnull
        public NBTTagCompound tag() {
            return tag;
        }

        public long created() {
            return created;
        }
    }

    private static final String KEY_TEMPLATES = "Templates";
    private static final String KEY_NAME = "Name";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_DAMAGE = "Damage";
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
     * @param stack 物品（取它的 tag 副本、损害值和数量）
     * @param item  物品 ID
     * @return 存进去的模板
     */
    @Nonnull
    public static Template save(@Nonnull String name, @Nonnull ItemStack stack, @Nonnull ResourceLocation item) {
        List<Template> list = loaded();
        list.removeIf(template -> template.name().equalsIgnoreCase(name));
        NBTTagCompound tag = stack.getTagCompound();
        Template template = new Template(name, item, stack.getItemDamage(), stack.getCount(),
                tag == null ? new NBTTagCompound() : tag.copy(), System.currentTimeMillis());
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
        return new File(new File(Minecraft.getMinecraft().mcDataDir, DIRECTORY), FILE_NAME);
    }

    @Nonnull
    private static List<Template> read() {
        List<Template> list = new ArrayList<>();
        File file = file();
        if (!file.isFile()) {
            return list;
        }
        try (InputStream in = new FileInputStream(file)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            NBTTagList templates = root.getTagList(KEY_TEMPLATES, 10);
            for (int i = 0; i < templates.tagCount(); i++) {
                NBTTagCompound entry = templates.getCompoundTagAt(i);
                ResourceLocation item = EditorSession.parseItemId(entry.getString(KEY_ITEM));
                list.add(new Template(entry.getString(KEY_NAME),
                        item == null ? new ResourceLocation("minecraft", "air") : item,
                        Math.max(0, entry.getInteger(KEY_DAMAGE)), Math.max(1, entry.getInteger(KEY_COUNT)),
                        entry.getCompoundTag(KEY_TAG).copy(), entry.getLong(KEY_CREATED)));
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
        NBTTagList templates = new NBTTagList();
        for (Template template : loaded()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(KEY_NAME, template.name());
            entry.setString(KEY_ITEM, template.item().toString());
            entry.setInteger(KEY_DAMAGE, template.damage());
            entry.setInteger(KEY_COUNT, template.count());
            entry.setTag(KEY_TAG, template.tag().copy());
            entry.setLong(KEY_CREATED, template.created());
            templates.appendTag(entry);
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setTag(KEY_TEMPLATES, templates);
        try (OutputStream out = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(root, out);
        } catch (IOException e) {
            LogUtil.error("写入物品模板文件失败: " + file, e);
        }
    }
}
