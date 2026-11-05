package pers.roinflam.battlecorrection.client.editor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;
import pers.roinflam.battlecorrection.network.ModNetwork;
import pers.roinflam.battlecorrection.network.packet.RequestNbtEditC2SPacket;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 物品编辑器的客户端入口：快捷键 + 打开界面
 * <p>
 * 三个快捷键（默认 I / O / P，可在按键设置里改）：编辑器、SNBT 文本、NBT 树。
 * <ul>
 *   <li>没开任何界面时按：主手有物品就编辑它；主手空着就看准星指着什么——实体（玩家除外）或带方块实体的方块
 *   （箱子、熔炉、刷怪笼这类）——向服务端要它的 NBT 来编辑。原版只在没界面时才给 KeyMapping 计数，所以走 ClientTickEvent 里的 consumeClick。</li>
 *   <li>开着背包/箱子界面时按：原版不会触发 KeyMapping，改监听 ScreenEvent.KeyPressed.Post，
 *   自己比对键位，编辑鼠标指着的那一格；没指着东西就编辑主手。正在输入框里打字时不响应（不然在铁砧里打个 i 就弹编辑器）。</li>
 * </ul>
 * 打开前先在客户端本地检查功能开关和 OP + 创造，不满足直接提示。服务端保存时还会再查一遍。
 * <p>
 * 这个类只在客户端加载：两个内部类都标了 value = Dist.CLIENT，Forge 在专用服务端上不会去碰它们。
 * 公共代码只能通过 DistExecutor 间接调 {@link #openFromServer}。
 */
public final class ItemEditorClient {

    /**
     * 按键设置里的分类
     */
    public static final String KEY_CATEGORY = "key.category.battlecorrection";

    /**
     * 打开编辑器（默认 I）
     */
    public static final KeyMapping KEY_OPEN = new KeyMapping("key.battlecorrection.item_editor",
            KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, KEY_CATEGORY);

    /**
     * 直接打开 SNBT 文本页（默认 O）
     * <p>
     * 键名和旧版的"NBT 树 / 原始 NBT"不同是故意的：游戏会把每个按键当前的绑定写进 options.txt，
     * 沿用旧键名的话玩家机器上存着的旧绑定（O = 树）会盖掉这里的新默认值，两个键还会撞在一起。
     */
    public static final KeyMapping KEY_OPEN_SNBT = new KeyMapping("key.battlecorrection.item_editor_snbt",
            KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KEY_CATEGORY);

    /**
     * 打开 NBT 树（默认 P）
     */
    public static final KeyMapping KEY_OPEN_NBT = new KeyMapping("key.battlecorrection.item_editor_nbt",
            KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, KEY_CATEGORY);

    private ItemEditorClient() {
    }

    /**
     * MOD 总线：注册按键
     */
    @EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {

        private ModBusEvents() {
        }

        /**
         * 注册三个快捷键
         *
         * @param evt 事件
         */
        @SubscribeEvent
        public static void onRegisterKeyMappings(@Nonnull RegisterKeyMappingsEvent evt) {
            evt.register(KEY_OPEN);
            evt.register(KEY_OPEN_SNBT);
            evt.register(KEY_OPEN_NBT);
            LogUtil.info("物品编辑器快捷键已注册（默认 I / O / P）");
        }
    }

    /**
     * FORGE 总线：按键检测
     */
    @EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class ForgeBusEvents {

        private ForgeBusEvents() {
        }

        /**
         * 没开界面时的按键：编辑主手
         *
         * @param evt 事件
         */
        @SubscribeEvent
        public static void onClientTick(@Nonnull ClientTickEvent.Post evt) {
            // 1.21.1 的客户端 tick 事件分成 Pre / Post 两个类，订 Post 就是 1.20.1 的 END 阶段
            Minecraft mc = Minecraft.getInstance();
            // 点击计数得清掉，否则开着界面时攒下的点击会在关界面后突然触发
            boolean open = KEY_OPEN.consumeClick();
            boolean snbt = KEY_OPEN_SNBT.consumeClick();
            boolean tree = KEY_OPEN_NBT.consumeClick();
            if (mc.player == null || mc.screen != null) {
                return;
            }
            if (open) {
                openFromKey(mc, EditorPage.NAME);
            } else if (snbt) {
                openFromKey(mc, EditorPage.RAW);
            } else if (tree) {
                openFromKey(mc, EditorPage.TREE);
            }
        }

        /**
         * 开着容器界面时的按键：编辑鼠标指着的格子
         *
         * @param evt 事件（Post = 界面自己没处理这个键）
         */
        @SubscribeEvent
        public static void onScreenKeyPressed(@Nonnull ScreenEvent.KeyPressed.Post evt) {
            Screen screen = evt.getScreen();
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || isTyping(screen)) {
                return;
            }
            EditorPage page = pageForKey(evt.getKeyCode(), evt.getScanCode());
            if (page == null) {
                return;
            }

            ItemEditTarget target = resolveHoveredTarget(player, containerScreen);
            if (target == null) {
                return;
            }
            open(mc, target, page, screen);
            evt.setCanceled(true);
        }
    }

    /**
     * 没开界面时按键：主手有东西编辑主手，没有就编辑准星指着的实体 / 方块实体
     *
     * @param mc   Minecraft
     * @param page 初始页面
     */
    private static void openFromKey(@Nonnull Minecraft mc, @Nonnull EditorPage page) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            open(mc, ItemEditTarget.mainHand(), page, null);
            return;
        }
        if (!ItemEditorAccess.checkAndNotify(player)) {
            return;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof Player) {
                player.displayClientMessage(Component.translatable("message.battlecorrection.nbt.player_not_allowed"), true);
                return;
            }
            ModNetwork.sendToServer(new RequestNbtEditC2SPacket(NbtEditTarget.entity(entity.getId()), page));
            return;
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && mc.level != null) {
            BlockPos pos = blockHit.getBlockPos();
            if (mc.level.getBlockEntity(pos) != null) {
                ModNetwork.sendToServer(new RequestNbtEditC2SPacket(NbtEditTarget.blockEntity(pos), page));
                return;
            }
            player.displayClientMessage(Component.translatable("message.battlecorrection.nbt.no_block_entity_here"), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.battlecorrection.editor.no_target"), true);
    }

    /**
     * 服务端把实体 / 方块实体的 NBT 发来了，开界面
     *
     * @param target 目标
     * @param tag    NBT
     * @param name   标题栏显示的名字
     * @param page   初始页面
     */
    public static void openNbtFromServer(@Nonnull NbtEditTarget target, @Nonnull CompoundTag tag, @Nonnull Component name,
                                         @Nonnull EditorPage page) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        EditorSession session = EditorSession.forNbt(target, tag, name);
        ItemEditorScreen editor = new ItemEditorScreen(session, page, null);
        mc.tell(() -> mc.setScreen(editor));
    }

    /**
     * 服务端命令要求打开
     *
     * @param target 目标
     * @param page   初始页面
     */
    public static void openFromServer(@Nonnull ItemEditTarget target, @Nonnull EditorPage page) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 命令是从聊天栏发的，关掉编辑器后不该再弹回聊天栏；其他界面（例如背包）则回去
        Screen parent = mc.screen;
        if (parent instanceof ItemEditorScreen || parent instanceof ChatScreen) {
            parent = null;
        }
        open(mc, target, page, parent);
    }

    /**
     * 检查条件并打开界面
     *
     * @param mc     Minecraft
     * @param target 目标
     * @param page   初始页面
     * @param parent 关闭后回到的界面
     */
    private static void open(@Nonnull Minecraft mc, @Nonnull ItemEditTarget target, @Nonnull EditorPage page,
                             @Nullable Screen parent) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (!ItemEditorAccess.checkAndNotify(player)) {
            return;
        }
        ItemStack stack = target.get(player);
        if (stack.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.battlecorrection.editor.no_target"), true);
            return;
        }

        EditorSession session = EditorSession.forItem(target, stack);
        ItemEditorScreen editor = new ItemEditorScreen(session, page, parent);
        // 延后一帧再开：按键事件之后紧跟着的字符事件才不会打进新界面的输入框里
        mc.tell(() -> mc.setScreen(editor));
        if (LogUtil.isDetailed()) {
            LogUtil.debug(String.format("打开物品编辑器: %s, 目标 %s, 页面 %s",
                    stack.getHoverName().getString(), target, page.id()));
        }
    }

    /**
     * 按键对应哪个页面
     *
     * @param keyCode  键码
     * @param scanCode 扫描码
     * @return 页面；不是编辑器的键返回 null
     */
    @Nullable
    private static EditorPage pageForKey(int keyCode, int scanCode) {
        if (!KEY_OPEN.isUnbound() && KEY_OPEN.matches(keyCode, scanCode)) {
            return EditorPage.NAME;
        }
        if (!KEY_OPEN_SNBT.isUnbound() && KEY_OPEN_SNBT.matches(keyCode, scanCode)) {
            return EditorPage.RAW;
        }
        if (!KEY_OPEN_NBT.isUnbound() && KEY_OPEN_NBT.matches(keyCode, scanCode)) {
            return EditorPage.TREE;
        }
        return null;
    }

    /**
     * 界面里是否有输入框正在接收输入
     *
     * @param screen 界面
     * @return true = 正在打字
     */
    private static boolean isTyping(@Nonnull Screen screen) {
        GuiEventListener focused = screen.getFocused();
        if (focused instanceof EditBox box) {
            return box.isFocused() && box.visible && box.active;
        }
        return focused instanceof MultiLineEditBox;
    }

    /**
     * 根据鼠标指着的格子算出编辑目标
     *
     * @param player          玩家
     * @param containerScreen 容器界面
     * @return 目标；不能编辑时提示并返回 null
     */
    @Nullable
    private static ItemEditTarget resolveHoveredTarget(@Nonnull LocalPlayer player,
                                                       @Nonnull AbstractContainerScreen<?> containerScreen) {
        Slot slot = containerScreen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) {
            return ItemEditTarget.mainHand();
        }

        Inventory inventory = player.getInventory();
        if (slot.container == inventory) {
            // 玩家自己的背包格：按物品引用找背包下标，创造界面的包装格也能这样对上
            int index = findInventoryIndex(inventory, slot.getItem());
            if (index >= 0) {
                return ItemEditTarget.inventory(index);
            }
        }

        if (containerScreen instanceof CreativeModeInventoryScreen) {
            // 创造物品列表里的不是真物品，服务端那边根本没有
            player.displayClientMessage(Component.translatable("message.battlecorrection.editor.creative_list"), true);
            return null;
        }

        return ItemEditTarget.menuSlot(containerScreen.getMenu().containerId, slot.index);
    }

    /**
     * 按引用在背包里找一件物品的下标
     *
     * @param inventory 背包
     * @param stack     物品（引用）
     * @return 下标；找不到返回 -1
     */
    private static int findInventoryIndex(@Nonnull Inventory inventory, @Nonnull ItemStack stack) {
        int size = inventory.getContainerSize();
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == stack) {
                return i;
            }
        }
        return -1;
    }
}
