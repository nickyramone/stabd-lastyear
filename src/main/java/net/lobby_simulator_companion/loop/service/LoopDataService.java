package net.lobby_simulator_companion.loop.service;

import lombok.extern.slf4j.Slf4j;
import net.lobby_simulator_companion.loop.domain.LoopData;
import net.lobby_simulator_companion.loop.domain.MatchLog;
import net.lobby_simulator_companion.loop.domain.Player;
import net.lobby_simulator_companion.loop.domain.stats.Match;
import net.lobby_simulator_companion.loop.domain.stats.Stats;
import net.lobby_simulator_companion.loop.domain.stats.periodic.PeriodStats;
import net.lobby_simulator_companion.loop.repository.LoopRepository;
import net.lobby_simulator_companion.loop.util.event.EventListener;
import net.lobby_simulator_companion.loop.util.event.EventSupport;
import org.apache.commons.lang3.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toConcurrentMap;

/**
 * Service for managing data related to players and servers.
 *
 * @author NickyRamone
 */
@Slf4j
public class LoopDataService {

    public enum DataServiceEvent {
        STATS_RESET
    }

    private static final long SAVE_PERIOD_MS = 5000;

    private final LoopRepository repository;
    private Map<String, Player> players = new HashMap<>();
    private LoopData loopData = new LoopData();
    private boolean dirty;
    private EventSupport eventSupport = new EventSupport();


    public LoopDataService(LoopRepository loopRepository) {
        repository = loopRepository;
    }


    public void start() throws IOException {
        loopData = loadData();
        players = loopData.getPlayers().stream()
                .collect(toConcurrentMap(Player::getIpHash, identity()));

        // schedule thread for saving dirty data
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                save();
            }
        }, SAVE_PERIOD_MS, SAVE_PERIOD_MS);

        initStatResetTimers();
    }


    private LoopData loadData() throws IOException {
        LoopData data;

        try {
            data = repository.load();
        } catch (FileNotFoundException e) {
            data = new LoopData();
            repository.save(data);
        }

        return data;
    }

    private void initStatResetTimers() {
        getStats().asStream().forEach(this::initStatResetTimer);
    }

    private void initStatResetTimer(PeriodStats periodStats) {
        if (periodStats.getPeriodEnd() == null) {
            return;
        }
        Date statsResetDate = Date.from(periodStats.getPeriodEnd().atZone(ZoneId.systemDefault()).toInstant()
                .plusSeconds(5L));

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                log.debug("Resetting stats timer for {}", periodStats.getClass());
                periodStats.reset();
                notifyChange();
                timer.cancel();
                initStatResetTimer(periodStats);
                eventSupport.fireEvent(DataServiceEvent.STATS_RESET);
            }
        }, statsResetDate);
    }


    public Stats getStats() {
        return loopData.getStats();
    }

    public MatchLog getMatchLog() {
        return loopData.getMatchLog();
    }

    public void addMatch(Match match) {
        Player player = players.get(match.getKillerPlayerSteamId64());

        if (player != null) {
            player.incrementMatchesPlayed();
            player.incrementSecondsPlayed(match.getSecondsPlayed());

            if (match.escaped()) {
                player.incrementEscapes();
            } else if (match.died()) {
                player.incrementDeaths();
            }
        }

        loopData.getStats().addMatchStats(match);
        loopData.getMatchLog().add(match);
        dirty = true;
    }


    public Optional<Player> getPlayerBySteamId(String steamId) {
        return Optional.ofNullable(steamId).filter(StringUtils::isNotBlank).map(players::get);
    }

    public Optional<Player> getPlayerByInetAddress(InetAddress inetAddress) {
        return getPlayerByIpHash(hashInetAddress(inetAddress));
    }

    public Optional<Player> getPlayerByIpHash(String ipHash) {
        return Optional.ofNullable(players.get(ipHash));
    }


    public void addPlayer(Player player) {
//        players.put(player.getSteamId64(), player);
        players.put(player.getIpHash(), player);
        dirty = true;
    }

    private String hashInetAddress(InetAddress address) {
        return hexToString(hashWithMd5(address.getAddress()));
    }

    private byte[] hashWithMd5(byte[] bytes) {

        MessageDigest md5Digest;
        try {
            md5Digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        md5Digest.update(bytes);

        return md5Digest.digest();
    }

    private String hexToString(byte[] bytes) {

        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }


    public void notifyChange() {
        dirty = true;
    }

    public synchronized void save() {
        if (!dirty) {
            return;
        }

        loopData.getPlayers().clear();
        loopData.addPlayers(new ArrayList<>(players.values()));
        try {
            repository.save(loopData);
            dirty = false;
        } catch (IOException e) {
            log.error("Failed to save data.", e);
        }
    }


    public void registerListener(EventListener eventListener) {
        eventSupport.registerListener(eventListener);
    }
}
