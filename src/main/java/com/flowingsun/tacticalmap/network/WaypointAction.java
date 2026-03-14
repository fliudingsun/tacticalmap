package com.flowingsun.tacticalmap.network;

public enum WaypointAction {
    ADD(0b00000000),
    DELETE(0b00000001);

    private final int code;

    WaypointAction(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static WaypointAction fromCode(int code) {
        return code == DELETE.code ? DELETE : ADD;
    }
}
