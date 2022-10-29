package net.lobby_simulator_companion.loop.service;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.lobby_simulator_companion.loop.config.AppProperties;
import net.lobby_simulator_companion.loop.domain.Killer;
import net.lobby_simulator_companion.loop.domain.Player;
import net.lobby_simulator_companion.loop.domain.RealmMap;
import net.lobby_simulator_companion.loop.domain.stats.Match;
import net.lobby_simulator_companion.loop.repository.SteamProfileDao;
import net.lobby_simulator_companion.loop.service.jna.WindowService;
import net.lobby_simulator_companion.loop.service.log_event_orchestrators.ChaseEventManager;
import net.lobby_simulator_companion.loop.service.log_processing.DbdLogEvent;
import net.lobby_simulator_companion.loop.util.Stopwatch;
import net.lobby_simulator_companion.loop.util.event.EventListener;
import net.lobby_simulator_companion.loop.util.event.EventSupport;
import net.lobby_simulator_companion.loop.util.event.SwingEventSupport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Optional;

import static javax.swing.SwingUtilities.invokeLater;
import static net.lobby_simulator_companion.loop.service.DbdLogMonitor.State;

/**
 * The purpose of this class is to store state about game events (e.g., information about current killer player,
 * current match time or queue time).
 * <p>
 * Measures different times (such as queue or match times) and updates stats based on the game state.
 *
 * @author NickyRamone
 */
@Slf4j
public class GameStateManager implements NativeKeyListener {

    /**
     * Minimum time connected from which we can assume that a match has taken place.
     */
    private static final int DEFAULT_MIN_MATCH_SECONDS = 60;

    private static final int KEYCODE__ENTER = 13;
    private static final int KEYCODE__F4 = 115;
    private static final int KEYCODE__0 = 48;
    private static final int KEYCODE__1 = 49;
    private static final int KEYCODE__2 = 50;
    private static final int KEYCODE__3 = 51;
    private static final int KEYCODE__4 = 52;
    private static final int KEYCODE__D = 68;
    private static final int KEYCODE__E = 69;
    private static final int KEY_MODIFIER__LEFT_CTRL = 2;
    private static final int KEY_MODIFIER__RIGHT_CTRL = 32;


    private final AppProperties appProperties;
    private final DbdLogMonitor dbdLogMonitor;
    private final LoopDataService dataService;
    private final SteamProfileDao steamProfileDao;
    private final ChaseEventManager chaseEventManager;
    private final EventSupport eventSupport = new SwingEventSupport();
    private final Stopwatch queueStopwatch = new Stopwatch();
    private final Stopwatch matchWaitStopwatch = new Stopwatch();
    private final Stopwatch matchStopwatch = new Stopwatch();
    private final String appWindowTitle;
    private final String dbdWindowTitle;

    @Setter
    private int minMatchSeconds;
    @Getter
    private Match currentMatch = new Match();
    private boolean resetMatchWait;
    private boolean timerRunning;


    public GameStateManager(AppProperties appProperties, DbdLogMonitor dbdLogMonitor, LoopDataService dataService,
                            SteamProfileDao steamProfileDao, ChaseEventManager chaseEventManager) {
        this.appProperties = appProperties;
        this.dbdLogMonitor = dbdLogMonitor;
        this.dataService = dataService;
        this.steamProfileDao = steamProfileDao;
        this.chaseEventManager = chaseEventManager;
        this.minMatchSeconds = DEFAULT_MIN_MATCH_SECONDS;
        this.appWindowTitle = appProperties.get("app.name.short");
        this.dbdWindowTitle = appProperties.get("dbd.window.title");

        init();
    }


    private void init() {
        dbdLogMonitor.registerListener(DbdLogEvent.MATCH_WAIT, evt -> handleMatchWaitStart());
        dbdLogMonitor.registerListener(DbdLogEvent.MATCH_WAIT_CANCEL, evt -> handleMatchWaitCancel());
        dbdLogMonitor.registerListener(DbdLogEvent.SERVER_CONNECT, evt -> handleServerConnect((InetSocketAddress) evt.getValue()));
        dbdLogMonitor.registerListener(DbdLogEvent.KILLER_PLAYER, evt -> handleNewKillerPlayer((PlayerDto) evt.getValue()));
        dbdLogMonitor.registerListener(DbdLogEvent.KILLER_CHARACTER, evt -> handleNewKillerCharacter((Killer) evt.getValue()));
        dbdLogMonitor.registerListener(DbdLogEvent.MAP_GENERATE, evt -> handleMapGeneration((RealmMap) evt.getValue()));
        dbdLogMonitor.registerListener(DbdLogEvent.REALM_ENTER, evt -> handleRealmEnter());
        dbdLogMonitor.registerListener(DbdLogEvent.MATCH_START, evt -> handleMatchStart());
        dbdLogMonitor.registerListener(DbdLogEvent.USER_LEFT_REALM, evt -> handleRealmLeave());
        dbdLogMonitor.registerListener(DbdLogEvent.SURVIVED, evt -> handleCurrentPlayerSurvival());
        dbdLogMonitor.registerListener(DbdLogEvent.SERVER_DISCONNECT, evt -> handleServerDisconnect());

        chaseEventManager.registerEventListener(ChaseEventManager.Event.CHASE_START, evt -> fireEvent(GameEvent.CHASE_STARTED, evt.getValue()));
        chaseEventManager.registerEventListener(ChaseEventManager.Event.CHASE_END, evt -> fireEvent(GameEvent.CHASE_ENDED));

        dataService.registerListener(evt -> fireEvent(GameEvent.UPDATED_STATS));

        registerHotkeys();
    }


    private void registerHotkeys() {
        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException e) {
            log.error("Failed to register native key hook.", e);
        }

        GlobalScreen.addNativeKeyListener(this);
    }


    public void forceDisconnect() {
        turnToIdle();
    }


    private void handleMatchWaitStart() {
        log.debug("Game event: searching for lobby");
        queueStopwatch.reset();
        queueStopwatch.start();

        if (resetMatchWait) {
            matchWaitStopwatch.reset();
        }

        matchWaitStopwatch.start();
        fireEvent(GameEvent.START_LOBBY_SEARCH);
    }


    private void handleMatchWaitCancel() {
        log.debug("Game event: lobby search cancelled");
        turnToIdle();
    }


    private void handleServerConnect(InetSocketAddress inetSocketAddress) {
        log.debug("Game event: connected to lobby");
        queueStopwatch.stop();

        currentMatch = new Match();
        currentMatch.incrementLobbiesFound();
        currentMatch.incrementSecondsQueued(getQueueTimeInSeconds());
        fireEvent(GameEvent.CONNECTED_TO_LOBBY, inetSocketAddress.getHostName());
    }


    private void handleMatchStart() {
        log.debug("Game event: match start");
        matchWaitStopwatch.stop();
        resetMatchWait = true;
        matchStopwatch.reset();
        matchStopwatch.start();
        currentMatch.setMatchStartTime(LocalDateTime.now());
        currentMatch.incrementSecondsWaited(getMatchWaitTimeInSeconds());
        fireEvent(GameEvent.MATCH_STARTED);
    }

    private void handleRealmLeave() {
        log.debug("Game event: user left the entity's realm");
        matchStopwatch.stop();
        currentMatch.setSecondsPlayed(getMatchDurationInSeconds());
        currentMatch.setEscaped(Optional.ofNullable(currentMatch.getEscaped()).orElse(false));

        // TODO: can we detect match cancel automatically?
        boolean matchCancelled = getMatchDurationInSeconds() < minMatchSeconds;

        if (!matchCancelled) {
            dataService.addMatch(currentMatch);
        } else {
            currentMatch.setCancelled(true);
        }

        fireEvent(GameEvent.MATCH_ENDED, currentMatch);
        fireEvent(GameEvent.UPDATED_STATS);
        fireEvent(GameEvent.UPDATED_CHASE_SUMMARY, chaseEventManager.getChaseSummary());
    }

    private void handleCurrentPlayerSurvival() {
        currentMatch.setEscaped(true);
    }


    private void handleServerDisconnect() {
        log.debug("Game event: server disconnect");
        turnToIdle();
    }

    private void turnToIdle() {
        queueStopwatch.stop();
        matchWaitStopwatch.stop();
        fireEvent(GameEvent.DISCONNECTED);
    }

    private void handleNewKillerPlayer(PlayerDto playerDto) {
        new Thread(() -> {
            String playerName;
            try {
                playerName = steamProfileDao.getPlayerName(playerDto.getSteamId());
            } catch (IOException e) {
                log.error("Failed to retrieve player's name for steam id#{}.", playerDto.getSteamId());
                playerName = "";
            }
            String steamId = playerDto.getSteamId();
            Optional<Player> storedPlayer = dataService.getPlayerBySteamId(steamId);
            final Player player;

            if (!storedPlayer.isPresent()) {
                log.debug("User #{} not found in the storage. Creating new entry...", steamId);
                player = new Player();
                player.setSteamId64(steamId);
                player.setDbdPlayerId(playerDto.getDbdId());
                player.addName(playerName);
                player.incrementTimesEncountered();
                dataService.addPlayer(player);
            } else {
                log.debug("User '{}' (#{}) found in the storage. Updating entry...", playerName, steamId);
                player = storedPlayer.get();
                player.updateLastSeen();
                player.addName(playerName);
                player.incrementTimesEncountered();
                dataService.notifyChange();
            }

            currentMatch.setKillerPlayerSteamId64(player.getSteamId64());
            currentMatch.setKillerPlayerDbdId(player.getDbdPlayerId());
            invokeLater(() -> fireEvent(GameEvent.NEW_KILLER_PLAYER, player));

        }).start();
    }

    private void handleNewKillerCharacter(Killer killerCharacter) {
        currentMatch.setKiller(killerCharacter);
        fireEvent(GameEvent.NEW_KILLER_CHARACTER, killerCharacter);
    }

    private void handleRealmEnter() {
        fireEvent(GameEvent.ENTERING_REALM);
    }

    private void handleMapGeneration(RealmMap realmMap) {
        currentMatch.setRealmMap(realmMap);
        fireEvent(GameEvent.START_MAP_GENERATION, realmMap);
    }

    @Deprecated
    public void notifySurvivalUserInput(Boolean survived) {
        currentMatch.setEscaped(survived);
        dataService.addMatch(currentMatch);
        fireEvent(GameEvent.UPDATED_STATS);
    }

    public Optional<Player> getKillerPlayer() {
        if (currentMatch == null) {
            return Optional.empty();
        }
        return dataService.getPlayerBySteamId(currentMatch.getKillerPlayerSteamId64());
    }

    public int getMatchDurationInSeconds() {
        return matchStopwatch.getSeconds();
    }

    public int getQueueTimeInSeconds() {
        return queueStopwatch.getSeconds();
    }

    public int getMatchWaitTimeInSeconds() {
        return matchWaitStopwatch.getSeconds();
    }

    public void registerListener(EventListener eventListener) {
        eventSupport.registerListener(eventListener);
    }

    public void registerListener(Object eventType, EventListener eventListener) {
        eventSupport.registerListener(eventType, eventListener);
    }

    public State getState() {
        return dbdLogMonitor.getState();
    }

    public void fireEvent(Object eventType) {
        eventSupport.fireEvent(eventType);
    }

    public void fireEvent(Object eventType, Object eventValue) {
        eventSupport.fireEvent(eventType, eventValue);
    }


    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeEvent) {
        if (!isValidActiveWindow()) {
            return;
        }

        int keyCode = nativeEvent.getRawCode();
        boolean ctrlPressed = nativeEvent.getModifiers() == KEY_MODIFIER__LEFT_CTRL || nativeEvent.getModifiers() == KEY_MODIFIER__RIGHT_CTRL;

        if (keyCode == KEYCODE__ENTER && ctrlPressed) {
            updateAggregateStatsWithMatchResults();
            return;
        }

        Integer killCount = null;
        Boolean escaped = null;

        if (keyCode == KEYCODE__0 && ctrlPressed) {
            killCount = 0;
        } else if (keyCode == KEYCODE__1 && ctrlPressed) {
            killCount = 1;
        } else if (keyCode == KEYCODE__2 && ctrlPressed) {
            killCount = 2;
        } else if (keyCode == KEYCODE__3 && ctrlPressed) {
            killCount = 3;
        } else if (keyCode == KEYCODE__4 && ctrlPressed) {
            killCount = 4;
        } else if (keyCode == KEYCODE__D && ctrlPressed) {
            escaped = false;
        } else if (keyCode == KEYCODE__E && ctrlPressed) {
            escaped = true;
        }

        if (killCount == null && escaped == null) {
            return;
        }

        if (currentMatch.getEscaped() != null && currentMatch.getEscaped().equals(escaped)) {
            escaped = null;
            currentMatch.setEscaped(null);
        }
        if (currentMatch.getKillCount() != null && currentMatch.getKillCount().equals(killCount)) {
            killCount = null;
            currentMatch.setKillCount(null);
        }

        if (escaped != null) {
            currentMatch.setEscaped(escaped);
        }
        if (killCount != null) {
            currentMatch.setKillCount(killCount);
        }

        fireEvent(GameEvent.MANUALLY_INPUT_MATCH_STATS, currentMatch);
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeEvent) {
        if (!isValidActiveWindow()) {
            return;
        }

        int keyCode = nativeEvent.getRawCode();
        int modifiers = nativeEvent.getModifiers();

        if (keyCode == KEYCODE__F4 && modifiers == 0) {
            if (timerRunning) {
                timerRunning = false;
                fireEvent(GameEvent.TIMER_END);
            } else {
                timerRunning = true;
                fireEvent(GameEvent.TIMER_START);
            }
        }
    }

    private boolean isValidActiveWindow() {
        String activeWindowTitle = WindowService.getActiveWindowTitle();

        return appWindowTitle.equals(activeWindowTitle) || dbdWindowTitle.equals(activeWindowTitle);
    }

    private void updateAggregateStatsWithMatchResults() {
        Boolean escaped = currentMatch.getEscaped();
        Integer killCount = currentMatch.getKillCount();

        if (escaped == null && killCount == null) {
            return;
        }

        if (escaped != null && killCount != null && ((escaped && killCount == 4) || (!escaped && killCount == 0))) {
            // invalid input
            return;
        }

        dataService.getStats().addMatchStats(currentMatch);
        dataService.getMatchLog().add(currentMatch);
        dataService.notifyChange();
        currentMatch = new Match();
        fireEvent(GameEvent.UPDATED_STATS);
    }

}
