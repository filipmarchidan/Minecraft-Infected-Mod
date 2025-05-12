package com.infectedmod.logic;

import net.minecraft.server.MinecraftServer;

import java.util.*;

public class SessionManager {
    private static SessionManager instance;
    private final List<GameSession> sessions = new ArrayList<>();
    private final Map<Integer, Game> games = new HashMap<>();
    private MinecraftServer server;

    private SessionManager() {
        createSession(1);
    }


    private SessionManager(MinecraftServer server) {
        this.server = server;
        createSession(1);
    }

    public static void init(MinecraftServer server) {
        if (instance == null) instance = new SessionManager(server);
    }
    public static SessionManager get() {     if (instance == null) {
        instance = new SessionManager();
    }
        return instance; }

    public GameSession createSession(int id) {
        GameSession s = new GameSession(id);
        sessions.add(s);
        games.put(id, new Game(id, server));
        // In SessionManager, after creating Game:
// each session gets its own Game
        return s;
    }

    /** Joins (or creates) a session for this player and returns its Game. */
    public Game joinSession(UUID playerId) {
        // 1) find existing session
        for (GameSession s : sessions) {
            if (s.hasPlayer(playerId)) return games.get(s.getSessionId());
        }
        // 2) find non-full
        for (GameSession s : sessions) {
            if (!s.isFull()) {
                s.addPlayer(playerId);
                return games.get(s.getSessionId());
            }
        }
        // 3) all full → new session
        int newId = sessions.size() + 1;
        GameSession s = createSession(newId);
        s.addPlayer(playerId);
        return games.get(newId);
    }

    public void leaveSession(UUID playerId) {
        for (GameSession s : sessions) {
            if (s.hasPlayer(playerId)) {
                // do not clear infected if they were infected
                s.removePlayer(playerId);
                // but keep Game.infected state untouched
            }
        }
    }

    /** Get the game for a known sessionId. */
    public Game getGame(int sessionId) {
        return games.get(sessionId);
    }


    /** Get the game for a known sessionId. */
    public Game getGameOfPlayer(UUID playerId) {
        for (GameSession s : sessions) {
            if (s.hasPlayer(playerId)) {
                return games.get(s.getSessionId());
            }
        }
        return null;
    }

    /** Returns all active Game instances */
    public Collection<Game> getAllGames() {
        return games.values();
    }

    public List<GameSession> getSessions() { return sessions; }
}
