package com.moonjs.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Objects;

public final class MoonStateSavedData extends SavedData {
    public static final String DATA_NAME = "moonjs";
    private MoonState state = MoonState.empty();
    private int explicitVanillaPhase = -1;
    private int eventBaseVanillaPhase = -1;

    public static MoonStateSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MoonStateSavedData::load, MoonStateSavedData::create, DATA_NAME);
    }

    public static MoonStateSavedData create() {
        return new MoonStateSavedData();
    }

    public static MoonStateSavedData load(CompoundTag tag) {
        MoonStateSavedData data = create();
        data.state = MoonState.load(tag);
        data.explicitVanillaPhase = readPhase(tag, "explicitVanillaPhase");
        data.eventBaseVanillaPhase = readPhase(tag, "eventBaseVanillaPhase");
        return data;
    }

    private static int readPhase(CompoundTag tag, String key) {
        int phase = tag.contains(key, 3) ? tag.getInt(key) : -1;
        if (phase != -1) MoonPhaseRegistry.validateVanillaPhase(phase);
        return phase;
    }

    public MoonState state() { return state; }
    public int explicitVanillaPhase() { return explicitVanillaPhase; }
    public int eventBaseVanillaPhase() { return eventBaseVanillaPhase; }

    public void setExplicitVanillaPhase(int phase) {
        if (phase != -1) MoonPhaseRegistry.validateVanillaPhase(phase);
        if (explicitVanillaPhase != phase) {
            explicitVanillaPhase = phase;
            setDirty();
        }
    }

    public void setEventBaseVanillaPhase(int phase) {
        if (phase != -1) MoonPhaseRegistry.validateVanillaPhase(phase);
        if (eventBaseVanillaPhase != phase) {
            eventBaseVanillaPhase = phase;
            setDirty();
        }
    }

    public void setState(MoonState state) {
        Objects.requireNonNull(state, "state");
        if (!this.state.equals(state)) {
            this.state = state;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        state.save(tag);
        tag.putInt("explicitVanillaPhase", explicitVanillaPhase);
        tag.putInt("eventBaseVanillaPhase", eventBaseVanillaPhase);
        return tag;
    }
}
