package pers.roinflam.battlecorrection.client.editor;

import com.mojang.serialization.DataResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
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
import java.util.Optional;

/**
 * 物品模板：把一件物品的数据连名字存在本地文件里，之后套用到别的物品上
 * <p>
 * 文件在客户端游戏目录下 {@code battlecorrection/item_templates.dat}（压缩 NBT），
 * 格式 {@code {Templates:[{Name, Item, Count, Components, Created}]}}。纯客户端功能，不经过服务端。
 * 同名保存直接覆盖。文件读失败只记日志不崩，当作没有模板。
 * <p>
 * 1.21.1：物品没有 tag 了，模板存的是组件补丁（和物品默认值不同的那部分，{@code DataComponentPatch} 的存盘格式）。
 * 1.20.1 存的模板（条目里是 {@code Tag}）读到时用原版数据修复器按"物品 ID + 数量 + tag"升级成组件补丁；
 * 升级失败的条目不显示，但原样留在文件里不删（{@link #skipped()} 给界面提示用）。
 * 组件补丁只在套用时才按当前世界的注册表解析（附魔这类是数据驱动的，不同服务器可能不一样）。
 */
public final class ItemTemplates {

    /**
     * 一份模板
     *
     * @param name       模板名
     * @param item       保存时的物品 ID（只做参考，套用到别的物品上也行）
     * @param count      保存时的数量
     * @param components 组件补丁的存盘格式（可能为空）
     * @param created    保存时间（毫秒）
     */
    public record Template(String name, ResourceLocation item, int count, CompoundTag components, long created) {

        /**
         * 按当前世界的注册表解析组件补丁
         *
         * @param registries 注册表
         * @return 补丁
         * @throws IllegalArgumentException 解析不了（比如附魔在这个服务器上不存在）；信息是解析器给的原因
         */
        @Nonnull
        public DataComponentPatch patch(@Nonnull HolderLookup.Provider registries) {
            if (components.isEmpty()) {
                return DataComponentPatch.EMPTY;
            }
            DataResult<DataComponentPatch> result;
            try {
                result = DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), components);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(String.valueOf(e.getMessage()), e);
            }
            Optional<DataResult.Error<DataComponentPatch>> error = result.error();
            if (error.isPresent()) {
                throw new IllegalArgumentException(error.get().message());
            }
            return result.result().orElse(DataComponentPatch.EMPTY);
        }
    }

    private static final String KEY_TEMPLATES = "Templates";
    private static final String KEY_NAME = "Name";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_COMPONENTS = "Components";
    /**
     * 1.20.1 存的模板：物品 tag
     */
    private static final String KEY_LEGACY_TAG = "Tag";
    private static final String KEY_CREATED = "Created";
    private static final String DIRECTORY = "battlecorrection";
    private static final String FILE_NAME = "item_templates.dat";

    @Nullable
    private static List<Template> cache;

    /**
     * 读不出来（旧格式升级失败）的条目原文：不显示，写文件时原样写回，免得一保存就把它们删了
     */
    private static final List<CompoundTag> UNREADABLE = new ArrayList<>();

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
     * 文件里有几份模板读不出来（旧格式升级失败）
     */
    public static int skipped() {
        loaded();
        return UNREADABLE.size();
    }

    /**
     * 保存一份模板（同名覆盖）
     *
     * @param name       模板名
     * @param stack      物品（取它的组件补丁）
     * @param item       物品 ID
     * @param registries 注册表
     * @return 存进去的模板
     */
    @Nonnull
    public static Template save(@Nonnull String name, @Nonnull ItemStack stack, @Nonnull ResourceLocation item,
                                @Nonnull HolderLookup.Provider registries) {
        List<Template> list = loaded();
        list.removeIf(template -> template.name().equalsIgnoreCase(name));
        UNREADABLE.removeIf(entry -> entry.getString(KEY_NAME).equalsIgnoreCase(name));
        CompoundTag components = new CompoundTag();
        DataResult<Tag> encoded = DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),
                stack.getComponentsPatch());
        Tag tag = encoded.resultOrPartial(error -> LogUtil.warn("物品模板：有组件编不出来，" + error)).orElse(null);
        if (tag instanceof CompoundTag compound) {
            components = compound;
        }
        Template template = new Template(name, item, stack.getCount(), components, System.currentTimeMillis());
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
        UNREADABLE.clear();
        File file = file();
        if (!file.isFile()) {
            return list;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            ListTag templates = root.getList(KEY_TEMPLATES, Tag.TAG_COMPOUND);
            for (int i = 0; i < templates.size(); i++) {
                CompoundTag entry = templates.getCompound(i);
                ResourceLocation item = ResourceLocation.tryParse(entry.getString(KEY_ITEM));
                if (item == null) {
                    item = ResourceLocation.fromNamespaceAndPath("minecraft", "air");
                }
                int count = Math.max(1, entry.getInt(KEY_COUNT));
                CompoundTag components;
                if (entry.contains(KEY_COMPONENTS, Tag.TAG_COMPOUND)) {
                    components = entry.getCompound(KEY_COMPONENTS).copy();
                } else {
                    CompoundTag upgraded = upgradeLegacy(entry, item, count);
                    if (upgraded == null) {
                        LogUtil.warn("物品模板：旧格式的模板「" + entry.getString(KEY_NAME) + "」升级失败，已跳过");
                        UNREADABLE.add(entry.copy());
                        continue;
                    }
                    components = upgraded.getCompound(ItemRoots.KEY_COMPONENTS);
                    count = Math.max(1, upgraded.getInt(ItemRoots.KEY_COUNT));
                }
                list.add(new Template(entry.getString(KEY_NAME), item, count, components, entry.getLong(KEY_CREATED)));
            }
        } catch (IOException | RuntimeException e) {
            LogUtil.error("读取物品模板文件失败，当作没有模板: " + file, e);
        }
        return list;
    }

    /**
     * 1.20.1 的模板条目升级成新格式的根（物品 ID、数量、tag 拼成 1.20.1 的物品 NBT 交给原版数据修复器）
     *
     * @return 根；失败返回 null
     */
    @Nullable
    private static CompoundTag upgradeLegacy(@Nonnull CompoundTag entry, @Nonnull ResourceLocation item, int count) {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("id", item.toString());
        legacy.putByte("Count", (byte) Math.min(127, count));
        CompoundTag tag = entry.getCompound(KEY_LEGACY_TAG);
        if (!tag.isEmpty()) {
            legacy.put("tag", tag.copy());
        }
        return ItemRoots.upgradeLegacy(legacy);
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
            entry.put(KEY_COMPONENTS, template.components().copy());
            entry.putLong(KEY_CREATED, template.created());
            templates.add(entry);
        }
        for (CompoundTag entry : UNREADABLE) {
            templates.add(entry.copy());
        }
        CompoundTag root = new CompoundTag();
        root.put(KEY_TEMPLATES, templates);
        try {
            NbtIo.writeCompressed(root, file.toPath());
        } catch (IOException e) {
            LogUtil.error("写入物品模板文件失败: " + file, e);
        }
    }
}
