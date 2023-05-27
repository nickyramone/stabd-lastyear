package net.lobby_simulator_companion.loop.domain.stats;

import lombok.*;
import net.lobby_simulator_companion.loop.domain.Killer;
import net.lobby_simulator_companion.loop.domain.RealmMap;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static java.lang.Math.max;

/**
 * @author NickyRamone
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class AggregateStats {

    private transient int lobbiesFound;
    private transient int secondsQueued;
    private transient int secondsWaited;
    private transient int secondsPlayed;
    private int matchesPlayed;
    private int escapes;
    private int escapesInARow;
    private int maxEscapesInARow;
    private int deaths;
    private int deathsInARow;
    private int maxDeathsInARow;
    private int kill0s;
    private int kill1s;
    private int kill2s;
    private int kill3s;
    private int kill4s;
    private int kill5s;
    private final Map<Killer, KillerStats> killersStats = new TreeMap<>();
    private final Map<RealmMap, MapStats> mapStats = new TreeMap<>();


    public void addMatchStats(Match matchStats) {
        lobbiesFound += Optional.ofNullable(matchStats.getLobbiesFound()).orElse(0);
        secondsQueued += Optional.ofNullable(matchStats.getSecondsQueued()).orElse(0);
        secondsWaited += Optional.ofNullable(matchStats.getSecondsWaited()).orElse(0);
        secondsPlayed += Optional.ofNullable(matchStats.getSecondsPlayed()).orElse(0);
        matchesPlayed++;

        if (matchStats.escaped()) {
            escapes++;
            deathsInARow = 0;
            escapesInARow++;
            maxEscapesInARow = max(escapesInARow, maxEscapesInARow);
        } else if (matchStats.died()) {
            deaths++;
            escapesInARow = 0;
            deathsInARow++;
            maxDeathsInARow = max(deathsInARow, maxDeathsInARow);
        }

        if (matchStats.getKillCount() != null) {
            updateKillCount(matchStats.getKillCount());
        }

        aggregateKillerStats(matchStats);
        aggregateMapStats(matchStats);
    }

    private void updateKillCount(int killCount) {
        if (killCount == 0) {
            kill0s++;
        }
        else if (killCount == 1) {
            kill1s++;
        }
        else if (killCount == 2) {
            kill2s++;
        }
        else if (killCount == 3) {
            kill3s++;
        }
        else if (killCount == 4) {
            kill4s++;
        }
        else if (killCount == 5) {
            kill5s++;
        }
    }



    private void aggregateKillerStats(Match matchStats) {
        Killer killer = Optional.ofNullable(matchStats.getKiller()).orElse(Killer.UNIDENTIFIED);

        KillerStats killerStats = getKillerStats(killer);
        killerStats.incrementMatches();
        killerStats.incrementMatchTime(Optional.ofNullable(matchStats.getSecondsPlayed()).orElse(0));

        if (matchStats.escaped()) {
            killerStats.incrementEscapes();
        } else if (matchStats.died()) {
            killerStats.incrementDeaths();
        }
    }

    private void aggregateMapStats(Match matchStats) {
        RealmMap realmMap = Optional.ofNullable(matchStats.getRealmMap()).orElse(RealmMap.UNIDENTIFIED);
        MapStats mapStats = getMapStats(realmMap);
        mapStats.incrementMatches();
        mapStats.incrementMatchTime(Optional.ofNullable(matchStats.getSecondsPlayed()).orElse(0));

        if (matchStats.escaped()) {
            mapStats.incrementEscapes();
        } else if (matchStats.died()) {
            mapStats.incrementDeaths();
        }
    }

    public int getAverageSecondsInQueue() {
        return lobbiesFound == 0 ? 0 : secondsQueued / lobbiesFound;
    }

    public int getAverageSecondsWaitedPerMatch() {
        return matchesPlayed == 0 ? secondsWaited : secondsWaited / matchesPlayed;
    }

    public int getAverageSecondsPerMatch() {
        return matchesPlayed == 0 ? 0 : (secondsPlayed / matchesPlayed);
    }

    public int getMatchesSubmitted() {
        return escapes + deaths;
    }

    public float getSurvivalProbability() {
        int matches = getMatchesSubmitted();

        if (matches == 0) {
            return 0;
        }

        return (float) escapes / matches * 100;
    }

    public float getKillRate() {
        int matches = kill0s + kill1s + kill2s + kill3s + kill4s + kill5s;
        int survivors = 5 * matches;
        int kills = kill1s + 2 * kill2s + 3 * kill3s + 4 * kill4s + 5 * kill5s;

        if (matches == 0) {
            return 0;
        }

        return (float) kills / survivors * 100;
    }

    private KillerStats getKillerStats(Killer killer) {
        return killersStats.computeIfAbsent(killer, k -> new KillerStats());
    }

    private MapStats getMapStats(RealmMap realmMap) {
        return mapStats.computeIfAbsent(realmMap, m -> new MapStats());
    }


    public void reset() {
        lobbiesFound = 0;
        secondsQueued = 0;
        secondsWaited = 0;
        secondsPlayed = 0;
        matchesPlayed = 0;
        escapes = 0;
        escapesInARow = 0;
        maxEscapesInARow = 0;
        deaths = 0;
        deathsInARow = 0;
        maxDeathsInARow = 0;
        kill0s = 0;
        kill1s = 0;
        kill2s = 0;
        kill3s = 0;
        kill4s = 0;
        kill5s = 0;
        killersStats.clear();
        mapStats.clear();
    }

}
