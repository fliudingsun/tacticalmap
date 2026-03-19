package com.flowingsun.tacticalmap.data;

import net.minecraft.core.BlockPos;

public class WaypointData {
    public final String name;
    public final BlockPos pos;
    public final Integer color;

    public WaypointData(String name, BlockPos pos, Integer color) {
        this.name = name;
        this.pos = pos;
        this.color = color;
    }
}
