package com.eventslogger.event;

import lombok.Data;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Data
@NullMarked
public class TickSnapshot {
    @Nullable
    PlayerSnapshot players;
    @Nullable
    NPCSnapshot npcs;

    @Data
    public static class PlayerSnapshot {
        Map<String, EventData.DetailedPosition> positionDeltas = new HashMap<>();
        Map<String, EventData.DetailedPosition> positionAbsolutes = new HashMap<>();

        public boolean isEmpty() {
            return positionDeltas.isEmpty() && positionAbsolutes.isEmpty();
        }
    }

    @Data
    public static class NPCSnapshot {
        Map<String, EventData.DetailedPosition> positionDeltas = new HashMap<>();
        Map<String, EventData.DetailedPosition> positionAbsolutes = new HashMap<>();

        public boolean isEmpty() {
            return positionDeltas.isEmpty() && positionAbsolutes.isEmpty();
        }
    }

    public boolean isEmpty() {
        return this.players == null && this.npcs == null;
    }
}
