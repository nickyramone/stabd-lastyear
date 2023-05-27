package net.lobby_simulator_companion.loop.domain.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.lobby_simulator_companion.loop.domain.Killer;
import net.lobby_simulator_companion.loop.domain.RealmMap;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author NickyRamone
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Match {

    private transient Integer lobbiesFound;
    private transient Integer secondsQueued;
    private transient Integer secondsWaited;
    private transient Integer secondsPlayed;
    private transient LocalDateTime matchStartTime;
    private transient boolean cancelled;
    private Boolean escaped;
    private Integer killCount;
    private transient Killer killer;
    private transient RealmMap realmMap;
    private transient String killerPlayerSteamId64;
    private transient String killerPlayerDbdId;
//    private transient InetAddress killerPlayerInetAddress;
    private transient String killerPlayerIpHash;

    public boolean escaped() {
        return Optional.ofNullable(escaped).orElse(false);
    }

    public boolean died() {
        return Optional.ofNullable(escaped).map(e -> !e).orElse(false);
    }

    public void incrementLobbiesFound() {
        lobbiesFound = Optional.ofNullable(lobbiesFound).orElse(0) + 1;
    }

    public void incrementSecondsQueued(int seconds) {
        secondsQueued = Optional.ofNullable(secondsQueued).orElse(0) + seconds;
    }

    public void incrementSecondsWaited(int seconds) {
        secondsWaited = Optional.ofNullable(secondsWaited).orElse(0) + seconds;
    }

}
