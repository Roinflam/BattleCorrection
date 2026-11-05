package pers.roinflam.battlecorrection.client.editor;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * 物品"根"的读写：1.21.1 的物品不再有 tag，整件物品的 NBT 就是 {@code {id, count, components:{...}}}
 * <p>
 * 树页、SNBT 页、模板、导入、/give 命令都围着这个根转，统一放在这里：
 * <ul>
 *   <li>{@link #save} / {@link #parse}：物品 ↔ 根。解析失败给出可读的原因（原版的 {@code ItemStack.parse} 只记日志、返回空）</li>
 *   <li>{@link #upgradeLegacy}：1.20.1 的 {@code {id, Count, tag}} 用原版数据修复器升上来（模板文件、剪贴板里的旧写法）</li>
 *   <li>{@link #giveArgument}：拼 {@code id[组件=值,!组件]} 这种 1.20.5 起的物品参数写法</li>
 *   <li>组件逐项拷贝 / 还原默认值（模板只套一部分时用）</li>
 * </ul>
 */
public final class ItemRoots {

    /**
     * 1.20.1 的数据版本：旧格式按它升级
     */
    public static final int LEGACY_DATA_VERSION = 3465;

    public static final String KEY_ID = "id";
    public static final String KEY_COUNT = "count";
    public static final String KEY_COMPONENTS = "components";

    private ItemRoots() {
    }

    /**
     * 客户端当前的注册表（附魔、旗帜图案这类数据驱动的内容要靠它编解码）
     *
     * @return 注册表；还没进世界时退回只有内置注册表的那份
     */
    @Nonnull
    public static RegistryAccess registries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            return connection.registryAccess();
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /**
     * 物品 → 根
     * <p>
     * 原版的 {@code save} 遇到编不出来的组件直接抛异常；这里尽量给出能编出来的部分，实在不行退回只有 id 和数量的根，
     * 界面上至少还能看到、还能改。空物品没有根，返回只有 id 的占位。
     *
     * @param stack      物品
     * @param registries 注册表
     * @return 根（独立副本，随便改）
     */
    @Nonnull
    public static CompoundTag save(@Nonnull ItemStack stack, @Nonnull HolderLookup.Provider registries) {
        CompoundTag fallback = new CompoundTag();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        fallback.putString(KEY_ID, id.toString());
        if (stack.isEmpty()) {
            return fallback;
        }
        fallback.putInt(KEY_COUNT, stack.getCount());
        try {
            DataResult<Tag> result = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack);
            Tag tag = result.resultOrPartial(error -> LogUtil.warn("物品编辑器：物品有编不出来的组件，" + error)).orElse(null);
            return tag instanceof CompoundTag compound ? compound : fallback;
        } catch (RuntimeException e) {
            LogUtil.warn("物品编辑器：物品存成 NBT 失败，" + e);
            return fallback;
        }
    }

    /**
     * 根 → 物品
     *
     * @param registries 注册表
     * @param root       根
     * @return 物品
     * @throws IllegalArgumentException 解析不了；信息是给玩家看的
     */
    @Nonnull
    public static ItemStack parse(@Nonnull HolderLookup.Provider registries, @Nonnull CompoundTag root) {
        DataResult<ItemStack> result;
        try {
            result = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), root);
        } catch (RuntimeException e) {
            // 组件的解码器碰上畸形数据偶尔直接抛，不是都走 DataResult
            throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.parse_failed", String.valueOf(e.getMessage())), e);
        }
        Optional<DataResult.Error<ItemStack>> error = result.error();
        if (error.isPresent()) {
            throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.parse_failed", error.get().message()));
        }
        ItemStack stack = result.result().orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.parse_failed", "empty"));
        }
        return stack;
    }

    /**
     * 是不是 1.20.1 的写法：有 {@code Count} 或 {@code tag}，没有 {@code count} 和 {@code components}
     *
     * @param root 根
     * @return true = 旧格式
     */
    public static boolean isLegacy(@Nonnull CompoundTag root) {
        return !root.contains(KEY_COUNT) && !root.contains(KEY_COMPONENTS)
                && (root.contains("Count") || root.contains("tag"));
    }

    /**
     * 1.20.1 的 {@code {id, Count, tag}} 升级成现在的根（原版数据修复器，从数据版本 3465 升到当前版本）
     * <p>
     * 1.20.1 的数量能写到 127，现在的根最多 99，升完顺手夹一下，免得升级成功了却解析不了。
     *
     * @param legacy 旧格式的根
     * @return 新格式的根；修复器出错时返回 null
     */
    @Nullable
    public static CompoundTag upgradeLegacy(@Nonnull CompoundTag legacy) {
        try {
            int to = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            Dynamic<Tag> fixed = DataFixers.getDataFixer().update(References.ITEM_STACK,
                    new Dynamic<>(NbtOps.INSTANCE, legacy.copy()), LEGACY_DATA_VERSION, to);
            if (!(fixed.getValue() instanceof CompoundTag root)) {
                return null;
            }
            if (root.contains(KEY_COUNT, Tag.TAG_ANY_NUMERIC)) {
                int count = root.getInt(KEY_COUNT);
                root.putInt(KEY_COUNT, Math.max(ApplyItemEditC2SPacket.MIN_COUNT, Math.min(ApplyItemEditC2SPacket.MAX_COUNT, count)));
            }
            return root;
        } catch (RuntimeException e) {
            LogUtil.warn("物品编辑器：旧格式的物品升级失败，" + e);
            return null;
        }
    }

    /**
     * /give 命令里物品那一段：{@code minecraft:diamond_sword[minecraft:damage=5,!minecraft:lore]}
     * <p>
     * 只写和物品默认值不同的组件（组件补丁）：删掉默认组件的写成 {@code !id}，原版物品参数认这种写法。
     * 不存盘的临时组件（transient）跳过；单个组件编不出来就跳过那一个。
     *
     * @param stack      物品
     * @param registries 注册表
     * @return 物品参数
     */
    @Nonnull
    public static String giveArgument(@Nonnull ItemStack stack, @Nonnull HolderLookup.Provider registries) {
        StringBuilder builder = new StringBuilder(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return builder.toString();
        }
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        StringBuilder components = new StringBuilder();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (key == null || type.isTransient()) {
                continue;
            }
            String part;
            if (entry.getValue().isEmpty()) {
                part = "!" + key;
            } else {
                Tag value = encode(type, entry.getValue().get(), ops);
                if (value == null) {
                    continue;
                }
                part = key + "=" + value;
            }
            if (!components.isEmpty()) {
                components.append(',');
            }
            components.append(part);
        }
        if (!components.isEmpty()) {
            builder.append('[').append(components).append(']');
        }
        return builder.toString();
    }

    /**
     * 编一个组件的值；编不出来返回 null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> Tag encode(@Nonnull DataComponentType<T> type, @Nonnull Object value, @Nonnull RegistryOps<Tag> ops) {
        if (type.codec() == null) {
            return null;
        }
        try {
            return type.codec().encodeStart(ops, (T) value).result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 把某个组件还原成物品的默认值（补丁里去掉这一项）
     * <p>
     * 原版的组件表在"设成和默认值一样"时会把补丁项删掉，默认没有这个组件的物品直接删掉就是默认。
     *
     * @param stack 物品
     * @param type  组件类型
     */
    public static <T> void resetToDefault(@Nonnull ItemStack stack, @Nonnull DataComponentType<T> type) {
        DataComponentMap prototype = stack.getPrototype();
        T value = prototype.get(type);
        if (value != null) {
            stack.set(type, value);
        } else {
            stack.remove(type);
        }
    }

    /**
     * 把补丁里某个组件的状态照搬到物品上：补丁里写了值就设成那个值，写了删除就删，没提到就还原默认
     *
     * @param stack 物品
     * @param patch 来源补丁
     * @param type  组件类型
     */
    @SuppressWarnings("unchecked")
    public static <T> void copyFromPatch(@Nonnull ItemStack stack, @Nonnull DataComponentPatch patch,
                                         @Nonnull DataComponentType<T> type) {
        Optional<? extends T> value = patch.get(type);
        if (value == null) {
            resetToDefault(stack, type);
        } else if (value.isPresent()) {
            stack.set(type, (T) value.get());
        } else {
            stack.remove(type);
        }
    }
}
