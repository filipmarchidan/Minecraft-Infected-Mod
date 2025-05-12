package com.infectedmod.logic;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Represents one “round” lobby of up to MAX_PLAYERS players. */
public class GameSession {
    public static final int MAX_PLAYERS = 30;

    private final int sessionId;
    private final Set<UUID> players = new LinkedHashSet<>();

    public GameSession(int sessionId) {
        this.sessionId = sessionId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public boolean hasPlayer(UUID id) {
        return players.contains(id);
    }

    public boolean addPlayer(UUID id) {
        if (isFull() || players.contains(id)) return false;
        return players.add(id);
    }

    public boolean removePlayer(UUID id) {
        return players.remove(id);
    }

    public Set<UUID> getPlayers() {
        return players;
    }
}
