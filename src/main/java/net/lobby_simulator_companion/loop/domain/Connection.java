package net.lobby_simulator_companion.loop.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.net.InetAddress;

/**
 * @author NickyRamone
 */
@Data
@AllArgsConstructor
public class Connection {

    private final InetAddress localAddr;
    private final int localPort;
    private final InetAddress remoteAddr;
    private final int remotePort;
    private final long created;
    private long lastSeen;
    private Integer latency;

    public Connection(InetAddress localAddr, int localPort, InetAddress remoteAddr, int remotePort) {
        this(localAddr, localPort, remoteAddr, remotePort, System.currentTimeMillis());
    }

    private Connection(InetAddress localAddr, int localPort, InetAddress remoteAddr, int remotePort, long createdOn) {
        this(localAddr, localPort, remoteAddr, remotePort, createdOn, createdOn, null);
    }

}
