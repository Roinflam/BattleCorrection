package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.config.ConfigBattle;
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品编辑器的客户端入口：快捷键 + 打开界面
 * <p>
 * 三个快捷键（默认 I / O / P，可在按键设置里改）：编辑器、SNBT 文本、NBT 树。
 * <ul>
 *   <li>没开任何界面时按：主手有物品就编辑它；主手空着就看准星指着什么——实体（玩家除外）或带方块实体的方块
 *   （箱子、熔炉、刷怪笼这类）——向服务端要它的 NBT 来编辑。原版只在没界面时才给 KeyBinding 计数，所以走 ClientTickEvent 里的 isPressed。</li>
 *   <li>开着背包/箱子界面时按：原版不会触发 KeyBinding，改监听 GuiScreenEvent.KeyboardInputEvent.Post，
 *   自己比对键位，编辑鼠标指着的那一格；没指着东西就编辑主手。正在输入框里打字时不响应（不然在铁砧里打个 i 就弹编辑器）。</li>
 * </ul>
 * 打开前先在客户端本地检查功能开关和 OP + 创造，不满足直接提示。服务端保存时还会再查一遍。
 * <p>
 * 这个类只在客户端加载：事件类标了 value = Side.CLIENT，FML 在专用服务端上不会去碰它；
 * 按键由 ClientProxy 调 {@link #registerKeyBindings()} 注册。公共代码（网络包）只在客户端一侧调 {@link #openFromServer}。
 */
public final class ItemEditorClient {

    /**
     * 按键设置里的分类
     */
    public static final String KEY_CATEGORY = "key.category.battlecorrection";

    /**
     * 打开编辑器（默认 I）
     */
    public static final KeyBinding KEY_OPEN = new KeyBinding("key.battlecorrection.item_editor",
            KeyConflictContext.UNIVERSAL, Keyboard.KEY_I, KEY_CATEGORY);

    /**
     * 直接打开 SNBT 文本页（默认 O）
     */
    public static final KeyBinding KEY_OPEN_SNBT = new KeyBinding("key.battlecorrection.item_editor_snbt",
            KeyConflictContext.UNIVERSAL, Keyboard.KEY_O, KEY_CATEGORY);

    /**
     * 打开 NBT 树（默认 P）
     */
    public static final KeyBinding KEY_OPEN_NBT = new KeyBinding("key.battlecorrection.item_editor_nbt",
            KeyConflictContext.UNIVERSAL, Keyboard.KEY_P, KEY_CATEGORY);

    /**
     * 每种界面类里 GuiTextField 类型的字段（反射查一次缓存下来）
     */
    private static final Map<Class<?>, Field[]> TEXT_FIELDS = new ConcurrentHashMap<>();

    private ItemEditorClient() {
    }

    /**
     * 注册三个快捷键（客户端预初始化时调一次）
     */
    public static void registerKeyBindings() {
        ClientRegistry.registerKeyBinding(KEY_OPEN);
        ClientRegistry.registerKeyBinding(KEY_OPEN_SNBT);
        ClientRegistry.registerKeyBinding(KEY_OPEN_NBT);
        LogUtil.info("物品编辑器快捷键已注册（默认 I / O / P）");
    }

    /**
     * 按键检测
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
    public static final class Events {

        private Events() {
        }

        /**
         * 没开界面时的按键：编辑主手
         *
         * @param evt 事件
         */
        @SubscribeEvent
        public static void onClientTick(@Nonnull TickEvent.ClientTickEvent evt) {
            if (evt.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            // 按键计数得清掉，否则攒下的次数会在之后突然触发
            boolean open = consume(KEY_OPEN);
            boolean snbt = consume(KEY_OPEN_SNBT);
            boolean tree = consume(KEY_OPEN_NBT);
            if (mc.player == null || mc.currentScreen != null) {
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
         * <p>
         * Post 在界面自己处理完这个键之后才发，而且界面还开着才发（按 E 关掉背包的那一下不会到这里）。
         *
         * @param evt 事件
         */
        @SubscribeEvent
        public static void onScreenKeyboardInput(@Nonnull GuiScreenEvent.KeyboardInputEvent.Post evt) {
            GuiScreen screen = evt.getGui();
            if (!(screen instanceof GuiContainer)) {
                return;
            }
            if (!Keyboard.getEventKeyState() || Keyboard.isRepeatEvent()) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc.player;
            if (player == null || isTyping(screen)) {
                return;
            }
            int key = Keyboard.getEventKey();
            // 和原版一样：没有键码的字符事件按"字符 + 256"算
            EditorPage page = pageForKey(key == 0 ? Keyboard.getEventCharacter() + 256 : key);
            if (page == null) {
                return;
            }

            GuiContainer containerScreen = (GuiContainer) screen;
            ItemEditTarget target = resolveHoveredTarget(player, containerScreen);
            if (target == null) {
                return;
            }
            open(mc, target, page, screen);
            evt.setCanceled(true);
        }
    }

    /**
     * 取走一次按键计数，把攒下的全部清掉
     */
    private static boolean consume(@Nonnull KeyBinding key) {
        boolean pressed = false;
        while (key.isPressed()) {
            pressed = true;
        }
        return pressed;
    }

    /**
     * 没开界面时按键：主手有东西编辑主手，没有就编辑准星指着的实体 / 方块实体
     *
     * @param mc   Minecraft
     * @param page 初始页面
     */
    private static void openFromKey(@Nonnull Minecraft mc, @Nonnull EditorPage page) {
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return;
        }
        if (!player.getHeldItemMainhand().isEmpty()) {
            open(mc, ItemEditTarget.mainHand(), page, null);
            return;
        }
        if (!ItemEditorAccess.checkAndNotify(player)) {
            return;
        }
        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY && hit.entityHit != null) {
            Entity entity = hit.entityHit;
            if (entity instanceof EntityPlayer) {
                player.sendStatusMessage(new TextComponentTranslation("message.battlecorrection.nbt.player_not_allowed"), true);
                return;
            }
            ModNetwork.sendToServer(new RequestNbtEditC2SPacket(NbtEditTarget.entity(entity.getEntityId()), page));
            return;
        }
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && mc.world != null) {
            BlockPos pos = hit.getBlockPos();
            if (mc.world.getTileEntity(pos) != null) {
                ModNetwork.sendToServer(new RequestNbtEditC2SPacket(NbtEditTarget.blockEntity(pos), page));
                return;
            }
            player.sendStatusMessage(new TextComponentTranslation("message.battlecorrection.nbt.no_block_entity_here"), true);
            return;
        }
        player.sendStatusMessage(new TextComponentTranslation("message.battlecorrection.editor.no_target"), true);
    }

    /**
     * 服务端把实体 / 方块实体的 NBT 发来了，开界面（已在主线程）
     *
     * @param target 目标
     * @param tag    NBT
     * @param name   标题栏显示的名字
     * @param page   初始页面
     */
    public static void openNbtFromServer(@Nonnull NbtEditTarget target, @Nonnull NBTTagCompound tag, @Nonnull ITextComponent name,
                                         @Nonnull EditorPage page) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        EditorSession session = EditorSession.forNbt(target, tag, Component.literal(name.getFormattedText()));
        mc.displayGuiScreen(new ItemEditorScreen(session, page, null));
    }

    /**
     * 服务端命令要求打开（已在主线程）
     *
     * @param target 目标
     * @param page   初始页面
     */
    public static void openFromServer(@Nonnull ItemEditTarget target, @Nonnull EditorPage page) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        // 命令是从聊天栏发的，关掉编辑器后不该再弹回聊天栏；其他界面（例如背包）则回去
        GuiScreen parent = mc.currentScreen;
        if (parent instanceof ItemEditorScreen || parent instanceof GuiChat) {
            parent = null;
        }
        open(mc, target, page, parent);
    }

    /**
     * 检查条件并打开界面
     * <p>
     * 直接在按键事件里换界面是安全的：displayGuiScreen 会把还没处理的键盘、鼠标事件清空，
     * 这次按键带的字符不会打进新界面的输入框里。
     *
     * @param mc     Minecraft
     * @param target 目标
     * @param page   初始页面
     * @param parent 关闭后回到的界面
     */
    private static void open(@Nonnull Minecraft mc, @Nonnull ItemEditTarget target, @Nonnull EditorPage page,
                             @Nullable GuiScreen parent) {
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return;
        }
        if (!ItemEditorAccess.checkAndNotify(player)) {
            return;
        }
        ItemStack stack = target.get(player);
        if (stack.isEmpty()) {
            player.sendStatusMessage(new TextComponentTranslation("message.battlecorrection.editor.no_target"), true);
            return;
        }

        EditorSession session = EditorSession.forItem(target, stack);
        mc.displayGuiScreen(new ItemEditorScreen(session, page, parent));
        if (ConfigBattle.enableDetailedLogging) {
            LogUtil.debug(String.format("打开物品编辑器: %s, 目标 %s, 页面 %s",
                    stack.getDisplayName(), target, page.id()));
        }
    }

    /**
     * 按键对应哪个页面
     *
     * @param keyCode 键码（LWJGL 2）
     * @return 页面；不是编辑器的键返回 null
     */
    @Nullable
    private static EditorPage pageForKey(int keyCode) {
        if (KEY_OPEN.isActiveAndMatches(keyCode)) {
            return EditorPage.NAME;
        }
        if (KEY_OPEN_SNBT.isActiveAndMatches(keyCode)) {
            return EditorPage.RAW;
        }
        if (KEY_OPEN_NBT.isActiveAndMatches(keyCode)) {
            return EditorPage.TREE;
        }
        return null;
    }

    /**
     * 界面里是否有输入框正在接收输入
     * <p>
     * 1.12.2 的界面没有统一的焦点概念，输入框都是各界面自己的 GuiTextField 字段（铁砧的改名框、创造模式的搜索框……），
     * 这里按类型把它们找出来看有没有拿着焦点。创造模式搜索页还会把界面自己的 focused 标记设上。
     *
     * @param screen 界面
     * @return true = 正在打字
     */
    private static boolean isTyping(@Nonnull GuiScreen screen) {
        if (screen.isFocused()) {
            return true;
        }
        for (Field field : TEXT_FIELDS.computeIfAbsent(screen.getClass(), ItemEditorClient::findTextFields)) {
            try {
                Object value = field.get(screen);
                if (value instanceof GuiTextField) {
                    GuiTextField box = (GuiTextField) value;
                    if (box.isFocused() && box.getVisible()) {
                        return true;
                    }
                }
            } catch (IllegalAccessException ignored) {
                // 拿不到就当没在打字
            }
        }
        return false;
    }

    @Nonnull
    private static Field[] findTextFields(@Nonnull Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != GuiScreen.class && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (GuiTextField.class.isAssignableFrom(field.getType()) && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (RuntimeException ignored) {
                        // 设不了可访问就跳过这个字段
                    }
                }
            }
        }
        return fields.toArray(new Field[0]);
    }

    /**
     * 根据鼠标指着的格子算出编辑目标
     *
     * @param player          玩家
     * @param containerScreen 容器界面
     * @return 目标；不能编辑时提示并返回 null
     */
    @Nullable
    private static ItemEditTarget resolveHoveredTarget(@Nonnull EntityPlayerSP player, @Nonnull GuiContainer containerScreen) {
        Slot slot = containerScreen.getSlotUnderMouse();
        if (slot == null || !slot.getHasStack()) {
            return ItemEditTarget.mainHand();
        }

        InventoryPlayer inventory = player.inventory;
        if (slot.inventory == inventory) {
            // 玩家自己的背包格：按物品引用找背包下标，创造界面的包装格也能这样对上
            int index = findInventoryIndex(inventory, slot.getStack());
            if (index >= 0) {
                return ItemEditTarget.inventory(index);
            }
        }

        if (containerScreen instanceof GuiContainerCreative) {
            // 创造物品列表里的不是真物品，服务端那边根本没有
            player.sendStatusMessage(new TextComponentTranslation("message.battlecorrection.editor.creative_list"), true);
            return null;
        }

        return ItemEditTarget.menuSlot(containerScreen.inventorySlots.windowId, slot.slotNumber);
    }

    /**
     * 按引用在背包里找一件物品的下标
     *
     * @param inventory 背包
     * @param stack     物品（引用）
     * @return 下标；找不到返回 -1
     */
    private static int findInventoryIndex(@Nonnull InventoryPlayer inventory, @Nonnull ItemStack stack) {
        int size = inventory.getSizeInventory();
        for (int i = 0; i < size; i++) {
            if (inventory.getStackInSlot(i) == stack) {
                return i;
            }
        }
        return -1;
    }
}
