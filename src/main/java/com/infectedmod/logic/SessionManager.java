package com.infectedmod.logic;

import net.minecraft.server.MinecraftServer;

import java.util.*;

public class SessionManager {
    private static SessionManager instance;
    private final List<GameSession> sessions = new ArrayList<>();
    private final Map<Integer, Game> games = new HashMap<>();
    private MinecraftServer server;


    private SessionManager(MinecraftServer server) {
        this.server   = server;
        // now you can safely create games:

    }

    /** Must be called exactly once, after the server is up */
    public static void init(MinecraftServer server) {
        if (instance != null) throw new IllegalStateException("Already initialized");
        instance = new SessionManager(server);
        instance.createSession(1);
    }

    public static SessionManager get() {
        if (instance == null) {
            throw new IllegalStateException("SessionManager.init(server) has not been called!");
        }
        return instance;
    }

    /** Returns the GameSession for this ID, or null if not found. */
    public GameSession getSession(int id) {
        return sessions.get(id);
    }


    public GameSession createSession(int id) {
        GameSession s = new GameSession(id);
        sessions.add(s);
        games.put(id, new Game(id, server));
        System.out.println("Created new session " + id + "game session" + games.get(id).getSessionId());
       // games.get(id).startIntermission(server);
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


    public Game joinSessionById(UUID playerId, int id) {
        // 1) find existing session
        for (GameSession s : sessions) {
            if (s.hasPlayer(playerId)) return games.get(s.getSessionId());
        }
        if(id > 0 && sessions.get(id) == null) {
            GameSession s = createSession(id);
            s.addPlayer(playerId);
            return games.get(id);
        }
        for (GameSession s : sessions) {
            if (s.getSessionId() == id && !s.isFull()) {
                s.addPlayer(playerId);
                return games.get(s.getSessionId());
            }
        }

        return  null;
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
