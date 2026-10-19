package pers.roinflam.battlecorrection.compat;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import baubles.api.cap.BaublesCapabilities;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import pers.roinflam.battlecorrection.config.ConfigBaubles;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 让标记过的物品变成 Baubles 饰品：给物品挂一份按 NBT 现判的饰品能力
 * <p>
 * Baubles 判断"这件物品能不能进某一格"只看物品有没有饰品能力（IBauble）、它报的饰品类型和 canEquip，
 * NBT 上的标记它根本不看。所以本模组自己给物品挂一份能力，由它来回答。
 * <p>
 * 为什么每个物品都挂、而不是只挂标记过的：能力只在物品创建时挂一次，而 1.12.2 的物品
 * 复制（ItemStack#copy）、网络收包（PacketBuffer#readItemStack）都是先建物品、后写 NBT，
 * 挂的那一刻永远看不到标记——只挂标记物品的话，复制品和客户端上的物品都会丢掉饰品能力。
 * 所以挂一个不存盘的空壳，每次被问的时候再看 NBT。空壳不参与序列化，不影响物品能不能叠在一起。
 * <p>
 * 已经是饰品的物品不挂（不会出现两份饰品能力）：
 * 物品类实现了 IBauble 的由 Baubles 自己挂；别的模组先挂了饰品能力的，这里看到就跳过；
 * 物品自己在 initCapabilities 里给的能力排在所有挂上去的能力前面，轮不到这里回答。
 * <p>
 * 这个类引用了 Baubles 的类，只能在装了 Baubles 时由 {@link BaubleMarkEvents} 调用。
 */
public final class BaubleMarkCapability {

    /**
     * 能力的注册名
     */
    public static final ResourceLocation ID = new ResourceLocation(Reference.MOD_ID, "bauble_mark");

    private BaubleMarkCapability() {
    }

    /**
     * 物品创建时挂上能力
     *
     * @param evt 物品能力事件
     */
    static void attach(@Nonnull AttachCapabilitiesEvent<ItemStack> evt) {
        ItemStack stack = evt.getObject();
        if (stack.isEmpty()) {
            return;
        }
        // 物品类本身就是饰品，Baubles 自己会挂
        if (stack.getItem() instanceof IBauble) {
            return;
        }
        // 别的模组已经挂了饰品能力
        for (ICapabilityProvider provider : evt.getCapabilities().values()) {
            if (provider.hasCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null)) {
                return;
            }
        }
        evt.addCapability(ID, new Provider(stack));
    }

    /**
     * 这件物品现在算不算标记饰品
     * <p>
     * 只看有没有可用的标记，不看总开关：已经戴着的标记物品如果突然"不是饰品"了，
     * Baubles 的格子在玩家想把它拿出来时会直接空指针。总开关只管能不能戴上（{@link MarkedBauble#canEquip}）。
     *
     * @param stack 物品
     * @return true = 是
     */
    private static boolean applies(@Nonnull ItemStack stack) {
        return BaubleMark.baubleTypeName(stack) != null;
    }

    /**
     * 挂在物品上的能力提供者（不存盘）
     */
    private static final class Provider implements ICapabilityProvider {

        private final ItemStack stack;

        private Provider(@Nonnull ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
            // 先比能力（其他能力在这里就返回了），再读 NBT
            return capability == BaublesCapabilities.CAPABILITY_ITEM_BAUBLE && applies(stack);
        }

        @Nullable
        @Override
        public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
            if (capability == BaublesCapabilities.CAPABILITY_ITEM_BAUBLE && applies(stack)) {
                return BaublesCapabilities.CAPABILITY_ITEM_BAUBLE.cast(MarkedBauble.INSTANCE);
            }
            return null;
        }
    }

    /**
     * 标记饰品的行为（没有状态，所有物品共用一个）
     */
    static final class MarkedBauble implements IBauble {

        static final MarkedBauble INSTANCE = new MarkedBauble();

        private MarkedBauble() {
        }

        /**
         * 饰品类型：只标了一种就是那一种，标了 any 或多种就是 TRINKET（见 {@link BaubleMark#baubleTypeName}）
         *
         * @param itemstack 物品
         * @return 饰品类型
         */
        @Override
        public BaubleType getBaubleType(ItemStack itemstack) {
            return BaublesAccess.toBaubleType(BaubleMark.baubleTypeName(itemstack));
        }

        /**
         * 总开关关闭后不再放行，但已经戴着的物品不会被强制摘下
         *
         * @param itemstack 物品
         * @param player    佩戴者
         * @return true = 能戴上
         */
        @Override
        public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
            return ConfigBaubles.baubleMarkEnabled;
        }
    }
}
