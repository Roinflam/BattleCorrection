package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 客户端 → 服务端：保存编辑器里的修改
 * <p>
 * 带上"编辑的是哪一格 + 打开时那一格的物品 ID 和损害值 + 保存后的物品 ID 和损害值 + 新 NBT + 新数量"。服务端拿到后按顺序校验：
 * 功能开关 → OP 且创造 → 那一格还有物品 → 那一格的物品还是打开时的那件 → 新物品 ID 是已注册的物品，
 * 全过了才写回。包走 {@link ChunkedPacket} 分片，体积上限由配置决定（分片层在解码之前就卡掉了）。
 * 任何一步不过都回一条红字，什么都不改。客户端上的检查一概不信。
 * <p>
 * "打开时的物品 ID"只用来确认格子里的东西没被换过；1.12.2 的损害值（Damage）对有子类型的物品（羊毛、染料）也是身份的一部分，
 * 这类物品还要比对损害值，普通物品的损害值是耐久，不参与比对。
 * 保存后的物品 ID 和它不同就是换了物品：按新物品重建，NBT、数量、损害值沿用编辑结果，原物品附带的 Forge 能力数据属于旧物品，不带过去。
 * 数量限制在 1-127：NBT 里 Count 是一个 byte，再大就溢出了；损害值限制在 0-32767（NBT 里是 short）。
 */
public class ApplyItemEditC2SPacket {

    /**
     * 数量下限
     */
    public static final int MIN_COUNT = 1;

    /**
     * 数量上限
     */
    public static final int MAX_COUNT = 127;

    /**
     * 损害值上限（NBT 里 Damage 是 short）
     */
    public static final int MAX_DAMAGE = Short.MAX_VALUE;

    private final ItemEditTarget target;
    private final ResourceLocation itemId;
    private final int damage;
    private final ResourceLocation newItemId;
    private final int newDamage;
    @Nullable
    private final NBTTagCompound tag;
    private final int count;

    /**
     * @param target    编辑目标
     * @param itemId    打开编辑器时那一格的物品 ID（防止格子里的东西换了还照写）
     * @param damage    打开编辑器时那一格的损害值（有子类型的物品用来确认没被换过）
     * @param newItemId 保存后的物品 ID；和 itemId 相同 = 不换物品
     * @param newDamage 保存后的损害值
     * @param tag       新 NBT；null = 清空 NBT
     * @param count     新数量
     */
    public ApplyItemEditC2SPacket(@Nonnull ItemEditTarget target, @Nonnull ResourceLocation itemId, int damage,
                                  @Nonnull ResourceLocation newItemId, int newDamage, @Nullable NBTTagCompound tag, int count) {
        this.target = target;
        this.itemId = itemId;
        this.damage = damage;
        this.newItemId = newItemId;
        this.newDamage = newDamage;
        this.tag = tag;
        this.count = count;
    }

    /**
     * 编码
     *
     * @param buf 缓冲
     */
    public void encode(@Nonnull PacketBuffer buf) {
        target.write(buf);
        buf.writeResourceLocation(itemId);
        buf.writeVarInt(damage);
        buf.writeResourceLocation(newItemId);
        buf.writeVarInt(newDamage);
        ChunkedPacket.writeNbt(buf, tag);
        buf.writeVarInt(count);
    }

    /**
     * 解码（只由分片层调用）
     * <p>
     * 读 NBT 用和原版解物品格子相同的 2 MB 记账（{@link ChunkedPacket#VANILLA_NBT_QUOTA}）：
     * 声明了超大数组长度的 NBT 在分配之前就会被拦下；记账超限的物品原版客户端也解不开，写进存档只会让持有者每次进服都掉线。
     *
     * @param buf 缓冲
     * @return 包
     */
    @Nonnull
    public static ApplyItemEditC2SPacket decode(@Nonnull PacketBuffer buf) {
        ItemEditTarget target = ItemEditTarget.read(buf);
        ResourceLocation itemId = buf.readResourceLocation();
        int damage = buf.readVarInt();
        ResourceLocation newItemId = buf.readResourceLocation();
        int newDamage = buf.readVarInt();
        NBTTagCompound tag = ChunkedPacket.readNbt(buf, ChunkedPacket.VANILLA_NBT_QUOTA);
        int count = buf.readVarInt();
        return new ApplyItemEditC2SPacket(target, itemId, damage, newItemId, newDamage, tag, count);
    }

    /**
     * 处理（服务端主线程）
     *
     * @param ctx 网络上下文
     */
    public void handle(@Nonnull MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        EntityPlayerMP player = ctx.getServerHandler().player;
        if (player == null) {
            return;
        }
        apply(player);
    }

    /**
     * 校验并写回
     *
     * @param player 发包的玩家
     */
    private void apply(@Nonnull EntityPlayerMP player) {
        if (!ItemEditorAccess.isEnabled()) {
            player.sendMessage(ItemEditorAccess.disabledMessage());
            return;
        }
        if (!ItemEditorAccess.canUse(player)) {
            player.sendMessage(ItemEditorAccess.noPermissionMessage());
            LogUtil.warn(String.format("玩家 %s 没有权限却发来了物品编辑包，已忽略", player.getName()));
            return;
        }

        ItemStack current = target.get(player);
        if (current.isEmpty()) {
            player.sendMessage(ChunkedPacket.red("message.battlecorrection.editor.save_failed.missing"));
            return;
        }

        ResourceLocation currentId = current.getItem().getRegistryName();
        if (currentId == null || !currentId.equals(itemId) || (current.getHasSubtypes() && current.getMetadata() != damage)) {
            player.sendMessage(ChunkedPacket.red("message.battlecorrection.editor.save_failed.changed"));
            return;
        }

        boolean changeItem = !newItemId.equals(itemId);
        ItemStack edited;
        if (changeItem) {
            Item item = ForgeRegistries.ITEMS.getValue(newItemId);
            if (item == null || item == Items.AIR || !ForgeRegistries.ITEMS.containsKey(newItemId)) {
                player.sendMessage(ChunkedPacket.red("message.battlecorrection.editor.save_failed.unknown_item", newItemId.toString()));
                return;
            }
            edited = new ItemStack(item);
        } else {
            edited = current.copy();
        }
        edited.setTagCompound(tag == null || tag.hasNoTags() ? null : tag);
        edited.setCount(Math.max(MIN_COUNT, Math.min(MAX_COUNT, count)));
        edited.setItemDamage(Math.max(0, Math.min(MAX_DAMAGE, newDamage)));

        if (!target.set(player, edited)) {
            player.sendMessage(ChunkedPacket.red("message.battlecorrection.editor.save_failed.missing"));
            return;
        }
        // 有的格子会悄悄拒收（Baubles 的饰品格只收这一格类型的饰品，不合适的直接忽略、也不报错），写完回读一次
        if (!ItemStack.areItemStacksEqual(target.get(player), edited)) {
            player.sendMessage(ChunkedPacket.red("message.battlecorrection.editor.save_failed.rejected"));
            return;
        }
        // 背包由 inventoryContainer 负责同步（开着箱子时它不会每 tick 自己同步）；容器格子在 set 里已经同步过了
        player.inventoryContainer.detectAndSendChanges();

        player.sendMessage(ChunkedPacket.green("message.battlecorrection.editor.saved", ItemUtil.chatName(edited)));
        LogUtil.info(changeItem
                ? String.format("%s 用物品编辑器把 %s 换成了 %s（%s），数量 %d",
                player.getName(), itemId, newItemId, edited.getDisplayName(), edited.getCount())
                : String.format("%s 用物品编辑器修改了物品 %s（%s），数量 %d",
                player.getName(), edited.getDisplayName(), itemId, edited.getCount()));
    }
}
