package com.infectedmod.logic;

import com.infectedmod.logic.Game;
import com.infectedmod.logic.GameSession;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
    private static SessionManager instance;
    private final Map<Integer, GameSession> sessions = new HashMap<>();
    private final Map<Integer, Game>        games    = new HashMap<>();
    private MinecraftServer server;

    private SessionManager(MinecraftServer server) {
        this.server = server;
        createSession(1);
    }

    public static SessionManager getInstance() {
        return instance;
    }

    public static void setInstance(SessionManager instance) {
        SessionManager.instance = instance;
    }

    public Map<Integer, GameSession> getSessions() {
        return sessions;
    }

    public Map<Integer, Game> getGames() {
        return games;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public static void init(MinecraftServer server) {
        if (instance == null) instance = new SessionManager(server);
    }
    public static SessionManager get() {
        if (instance == null)
            throw new IllegalStateException("SessionManager.init(server) has not been called!");
        return instance;
    }

    private GameSession createSession(int id) {
        GameSession s = new GameSession(id);
        sessions.put(id, s);
        games.put(id, new Game(id, server));
        return s;
    }

    /** Look up by ID, never by list index. */
    public GameSession getSession(int id) {
        return sessions.get(id);
    }

    /** For a player, find their session’s Game. */
    public Game joinSession(UUID playerId) {
        // 1) already in one?
        for (GameSession s : sessions.values()) {
            if (s.hasPlayer(playerId)) return games.get(s.getSessionId());
        }
        // 2) find a non‑full
        for (GameSession s : sessions.values()) {
            if (!s.isFull()) {
                s.addPlayer(playerId);
                return games.get(s.getSessionId());
            }
        }
        // 3) all full → make a new

        int newId = sessions.size() + 1;
        if(sessions.containsKey(newId)) {
            while(sessions.containsKey(newId)) {
                newId++;
            }
        }
        GameSession s = createSession(newId);
        s.addPlayer(playerId);
        return games.get(newId);
    }

        public Game joinSessionById(UUID playerId, int id) {
        // 1) find existing session
        for (GameSession s : sessions.values()) {
            if (s.hasPlayer(playerId)) return games.get(s.getSessionId());
        }
        if(id > 0 && sessions.get(id) == null) {
            GameSession s = createSession(id);
            s.addPlayer(playerId);
            return games.get(id);
        }
        for (GameSession s : sessions.values()) {
            if (s.getSessionId() == id && !s.isFull()) {
                s.addPlayer(playerId);
                return games.get(s.getSessionId());
            }
        }

        return  null;
    }

    public Game getGame(int id) {
        return games.get(id);
    }

    public Game getGameOfPlayer(UUID playerId) {
        for (GameSession s : sessions.values()) {
            if (s.hasPlayer(playerId)) return games.get(s.getSessionId());
        }
        return null;
    }

    public void leaveSession(UUID playerId) {
        for (GameSession s : sessions.values()) {
            if (s.hasPlayer(playerId)) {
                s.removePlayer(playerId);
            }
        }
    }
}


//package com.infectedmod.logic;
//
//import net.minecraft.server.MinecraftServer;
//
//import java.util.*;
//
//public class SessionManager {
//    private static SessionManager instance;
//    private final List<GameSession> sessions = new ArrayList<>();
//    private final Map<Integer, Game> games = new HashMap<>();
//    private MinecraftServer server;
//
//
//    private SessionManager(MinecraftServer server) {
//        this.server   = server;
//        // now you can safely create games:
//
//    }
//
//    /** Must be called exactly once, after the server is up */
//    public static void init(MinecraftServer server) {
//        if (instance != null) throw new IllegalStateException("Already initialized");
//        instance = new SessionManager(server);
//        instance.createSession(1);
//    }
//
//    public static SessionManager get() {
//        if (instance == null) {
//            throw new IllegalStateException("SessionManager.init(server) has not been called!");
//        }
//        return instance;
//    }
//
//    /** Returns the GameSession for this ID, or null if not found. */
//    public GameSession getSession(int id) {
//        return sessions.get(id);
//    }
//
//
//    public GameSession createSession(int id) {
//        GameSession s = new GameSession(id);
//        sessions.add(s);
//        games.put(id, new Game(id, server));
//        System.out.println("Created new session " + id + "game session" + games.get(id).getSessionId());
//       // games.get(id).startIntermission(server);
//        // In SessionManager, after creating Game:
//// each session gets its own Game
//        return s;
//    }
//
//    /** Joins (or creates) a session for this player and returns its Game. */
//    public Game joinSession(UUID playerId) {
//        // 1) find existing session
//        for (GameSession s : sessions) {
//            if (s.hasPlayer(playerId)) return games.get(s.getSessionId());
//        }
//        // 2) find non-full
//        for (GameSession s : sessions) {
//            if (!s.isFull()) {
//                s.addPlayer(playerId);
//                return games.get(s.getSessionId());
//            }
//        }
//        // 3) all full → new session
//        int newId = sessions.size() + 1;
//        GameSession s = createSession(newId);
//        s.addPlayer(playerId);
//        return games.get(newId);
//    }
//
//
//    public Game joinSessionById(UUID playerId, int id) {
//        // 1) find existing session
//        for (GameSession s : sessions) {
//            if (s.hasPlayer(playerId)) return games.get(s.getSessionId());
//        }
//        if(id > 0 && sessions.get(id) == null) {
//            GameSession s = createSession(id);
//            s.addPlayer(playerId);
//            return games.get(id);
//        }
//        for (GameSession s : sessions) {
//            if (s.getSessionId() == id && !s.isFull()) {
//                s.addPlayer(playerId);
//                return games.get(s.getSessionId());
//            }
//        }
//
//        return  null;
//    }
//
//    public void leaveSession(UUID playerId) {
//        for (GameSession s : sessions) {
//            if (s.hasPlayer(playerId)) {
//                // do not clear infected if they were infected
//                s.removePlayer(playerId);
//                // but keep Game.infected state untouched
//            }
//        }
//    }
//
//    /** Get the game for a known sessionId. */
//    public Game getGame(int sessionId) {
//        return games.get(sessionId);
//    }
//
//
//    /** Get the game for a known sessionId. */
//    public Game getGameOfPlayer(UUID playerId) {
//        for (GameSession s : sessions) {
//            if (s.hasPlayer(playerId)) {
//                return games.get(s.getSessionId());
//            }
//        }
//        return null;
//    }
//
//    /** Returns all active Game instances */
//    public Collection<Game> getAllGames() {
//        return games.values();
//    }
//
//    public List<GameSession> getSessions() { return sessions; }
//}
