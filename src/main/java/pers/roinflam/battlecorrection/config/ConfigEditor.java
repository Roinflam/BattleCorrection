package pers.roinflam.battlecorrection.config;

import net.minecraftforge.common.config.Config;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * 物品编辑器配置
 * <p>
 * 同步走 {@link ConfigBattle#onConfigChanged}（它按 modid 同步全部配置类）。
 */
@Config(modid = Reference.MOD_ID, category = "ItemEditor")
@Config.LangKey("config." + Reference.MOD_ID + ".itemeditor")
public final class ConfigEditor {

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Enable the in-game item editor (/battlecorrection edit, hotkeys I / O / P)",
            "     Only players with OP level 2 in creative mode can use it",
            "     false = command and hotkeys are rejected, saving is refused on the server",
            "",
            "[中文] 启用游戏内物品编辑器（/battlecorrection edit，快捷键 I / O / P）",
            "     只有 OP 2 级且处于创造模式的玩家能用",
            "     false = 命令和快捷键都不可用，服务端也会拒绝保存",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".itemeditor.itemEditorEnabled")
    public static boolean itemEditorEnabled = true;

    /*
     * 默认 256、上限 2048 的由来（1.12.2 的数字）：
     * - 单件物品的 NBT 读取记账上限和 1.20.1 一样是 2 MB（PacketBuffer#readCompoundTag 用 NBTSizeTracker(2097152)，
     *   记的是字节，不是位）；紧凑的复合标签记账是序列化字节数的 5 倍多，全是空复合标签的列表能到 50 倍；
     * - 但 1.12.2 解压后的单个数据包本身也不能超过 2 MB（NettyCompressionDecoder 的上限，1.20.1 是 8 MB），
     *   而整个背包（SPacketWindowItems，46 格左右）、整个区块的方块实体（SPacketChunkData）都各自塞在一个包里；
     * - 1.20.1 是 8 MB 的包配 1024 KB 的默认，一个包大约能放下 8 件满额物品；这里按同样的余量取 256 KB；
     * - 超过 2048 KB 时一件物品的 SPacketSetSlot 自己就超了包的上限，持有者会断线、而且每次进服都断，所以上限卡在 2048。
     */
    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Max NBT size (KB) the editor may save onto one item, entity or block entity",
            "     The editor sends large NBT in chunks, so its own packets are not the limit. The real limit is",
            "     vanilla sync: a 1.12.2 packet may not exceed 2 MB after decompression, and a whole inventory or",
            "     all block entities of a chunk travel in one packet. Oversized items disconnect their holder",
            "     on every login",
            "     Raise it with care; the hard maximum is 2048",
            "",
            "[中文] 编辑器允许保存到单个物品 / 实体 / 方块实体上的 NBT 大小上限（KB）",
            "     编辑器的大 NBT 是分片发送的，编辑器自己的包不受限。真正的上限在原版同步：",
            "     1.12.2 解压后的单个数据包不能超过 2 MB，而整个背包、一个区块里的全部方块实体都是一个包发的，",
            "     物品太大会让持有者断线，而且每次进服都断",
            "     调大要谨慎，最大只能到 2048",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".itemeditor.itemEditorMaxNbtKb")
    @Config.RangeInt(min = 16, max = 2048)
    public static int itemEditorMaxNbtKb = 256;

    private ConfigEditor() {
    }
}
