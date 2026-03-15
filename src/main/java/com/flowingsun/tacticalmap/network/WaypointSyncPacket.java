package com.flowingsun.tacticalmap.network;

import com.flowingsun.tacticalmap.TacticalMap;
import com.flowingsun.tacticalmap.util.SyncActionGenerator;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.waypoint.Waypoint;
import dev.ftb.mods.ftbchunks.client.map.WaypointImpl;
import dev.ftb.mods.ftbchunks.client.map.WaypointManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/* class update plan
 * 1. Implement version verification
 * 2. Use BitSet instead of long to send data
 */
public class WaypointSyncPacket {
    private static final int ACTION_BITS = SyncActionGenerator.TOTAL_BITS;
    private static final int ACTION_MASK = SyncActionGenerator.TOTAL_MASK;
    private static final int COLOR_MASK = ~ACTION_MASK;

    private static final int LOCAL_PROTOCOL_VERSION = 1; // if you edited this file, you need to bump PROTOCOL_VERSION

    private final String name;
    private final BlockPos pos;
    private final int color;
    private final SyncActionGenerator.SyncAction syncAction;
    private final SyncActionGenerator.SideFlag sideFlag;
    private final int protocolVersion;

    public WaypointSyncPacket(Waypoint wp, byte action) {
        this(wp.getName(), wp.getPos(), ((long) wp.getColor() << ACTION_BITS) | action);
    }

    public WaypointSyncPacket(String name, long posLong, long data) {
        this(name, BlockPos.of(posLong), data);
    }

    public WaypointSyncPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), BlockPos.of(buf.readLong()), buf.readLong(), buf.readInt());
    }

    public WaypointSyncPacket(String name, BlockPos pos, long data) {
        this(name, pos, data, LOCAL_PROTOCOL_VERSION);
    }

    public WaypointSyncPacket(String name, BlockPos pos, long data, int protocolVersion) {
        this.name = name;
        this.pos = pos;
        this.color = (int) (data >> ACTION_BITS);
        byte actionByte = (byte) (data & ACTION_MASK);
        this.syncAction = SyncActionGenerator.getSyncAction(actionByte);
        this.sideFlag = SyncActionGenerator.getSideFlag(actionByte);
        this.protocolVersion = protocolVersion;
    }

    public WaypointSyncPacket(String name, BlockPos pos, byte syncAction, int color) {
        this(name, pos, SyncActionGenerator.getSyncAction(syncAction), SyncActionGenerator.getSideFlag(syncAction), color);
    }

    public WaypointSyncPacket(String name, BlockPos pos, SyncActionGenerator.SyncAction syncAction, SyncActionGenerator.SideFlag sideFlag, int color) {
        this.name = name;
        this.pos = pos;
        this.syncAction = syncAction;
        this.sideFlag = sideFlag;
        this.color = color;
        this.protocolVersion = LOCAL_PROTOCOL_VERSION;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.name);
        buf.writeLong(this.pos.asLong());
        buf.writeLong(this.encodeData());
        buf.writeInt(this.protocolVersion);
    }

    public WaypointSyncPacket transformToS2C() {
        return new WaypointSyncPacket(this.name, this.pos, this.syncAction, SyncActionGenerator.SideFlag.S2C, this.color);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {

            ServerPlayer sender = ctx.get().getSender();
            if (this.sideFlag.equals(SyncActionGenerator.SideFlag.C2S)) {
                if (sender != null) {
                    TacticalMap.broadcastToTeammates(sender, this.transformToS2C());
                }
            } else if (this.sideFlag.equals(SyncActionGenerator.SideFlag.S2C)) {
                FTBChunksAPI.clientApi().getWaypointManager().ifPresent(manager -> {
                    if (!(manager instanceof WaypointManagerImpl impl)) return;

                    TacticalMap.IS_SYNCING.set(true);
                    try {
                        if (this.syncAction == SyncActionGenerator.SyncAction.ADD) {
                            // 【修正逻辑】：
                            // 1. 调用 addWaypointAt 创建并自动添加路径点（它内部会处理所有复杂的构造参数）
                            Waypoint wp = impl.addWaypointAt(this.pos, this.name);

                            // 2. 转换为实现类设置颜色（源码显示 setColor 返回 WaypointImpl，支持链式调用）
                            if (wp instanceof WaypointImpl wpImpl) {
                                wpImpl.setColor(color);
                                // 3. 必须手动刷新图标，否则地图上显示的还是默认颜色
                                wpImpl.refreshIcon();
                            }
                        } else if (this.syncAction == SyncActionGenerator.SyncAction.DELETE) {
                            for (Waypoint waypoint : manager.getAllWaypoints()) {
                                if (waypoint.getPos().atY(0).equals(this.pos.atY(0))) {
                                    manager.removeWaypoint(waypoint);
                                    break;
                                }
                            }
                        }
                    } finally {
                        TacticalMap.IS_SYNCING.set(false);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String getName() {
        return name;
    }

    private long encodeData() {
        return ((long) this.color << ACTION_BITS) | SyncActionGenerator.generate(syncAction, sideFlag);
    }
}