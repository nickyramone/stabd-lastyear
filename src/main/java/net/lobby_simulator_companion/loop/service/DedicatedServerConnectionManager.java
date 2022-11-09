package net.lobby_simulator_companion.loop.service;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.lobby_simulator_companion.loop.domain.Connection;
import org.pcap4j.core.*;
import org.pcap4j.packet.*;

import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The initial handshake with the dedicated server hosting the match (including lobby) is through WireGuard protocol:
 * https://www.wireguard.com/protocol/
 * <p>
 * Update 2022-11-06: Looking at the traffic, it doesn't really seem WireGuard. Maybe I was wrong before; I don't think
 * it changed.
 *
 * @author NickyRamone
 */
@Slf4j
public class DedicatedServerConnectionManager implements ConnectionManager {

    /**
     * We need at least 71 bytes for the handshake requests and responses, but in order to check that the connection
     * is alive, we need to consider at least 150 bytes for server responses.
     */
    private static final int MAX_CAPTURED_PACKET_SIZE = 150;
    private static final int CLEANER_POLL_MS = 1000;
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    /**
     * Berkley Packet Filter (BPF):
     * http://biot.com/capstats/bpf.html
     * https://www.tcpdump.org/manpages/pcap-filter.7.html
     */
    private static final String PACKET_FILTER__SEARCH_CONNECTION = "udp and len <= 100";
    private static final String PACKET_FILTER__CHECK_CONNECTION_ALIVE = "udp and src host %s and dst host %s and len <= 150";


    @ToString(exclude = "payload")
    private static final class PacketInfo {
        enum Protocol {TCP, UDP}

        Protocol protocol;
        int packetLen;
        InetAddress srcAddress;
        InetAddress dstAddress;
        int srcPort;
        int dstPort;
        int payloadLen;
        Packet payload;
        long timestamp;
    }

    private enum State {IDLE, HANDSHAKE1_REQUESTED, HANDSHAKE1_RESPONDED, HANDSHAKE2_REQUESTED, HANDSHAKE_COMPLETE}

    private final InetAddress localAddr;
    private final SnifferListener snifferListener;
    private final Timer connectionCleanerTimer = new Timer();
    private PcapHandle pcapHandle;
    private Connection serverConnection;
    private State state = State.IDLE;
    private PacketInfo lastRequest;


    public DedicatedServerConnectionManager(InetAddress localAddr, SnifferListener snifferListener)
            throws PcapNativeException, NotOpenException, InvalidNetworkInterfaceException {

        this.localAddr = localAddr;
        this.snifferListener = snifferListener;
        initNetworkInterface();
        startConnectionCleaner();
    }


    private void initNetworkInterface() throws PcapNativeException, InvalidNetworkInterfaceException, NotOpenException {
        PcapNetworkInterface networkInterface = Pcaps.getDevByAddress(localAddr);
        if (networkInterface == null) {
            throw new InvalidNetworkInterfaceException();
        }

        final PcapNetworkInterface.PromiscuousMode mode = PcapNetworkInterface.PromiscuousMode.NONPROMISCUOUS;
        pcapHandle = networkInterface.openLive(MAX_CAPTURED_PACKET_SIZE, mode, 1000);
        pcapHandle.setFilter(PACKET_FILTER__SEARCH_CONNECTION, BpfProgram.BpfCompileMode.OPTIMIZE);
    }


    @Override
    public void start() {
        try {
            sniffPackets();
        } catch (Exception e) {
            snifferListener.handleException(e);
        }
    }

    private void sniffPackets() throws PcapNativeException, NotOpenException {
        log.info("Started sniffing packets.");

        try {
            pcapHandle.loop(-1, this::handlePacket);
        } catch (InterruptedException e) {
            // can be interrupted on purpose
        }
    }

    private void handlePacket(Packet packet) {

        PacketInfo packetInfo = adaptPacket(packet);
        if (packetInfo == null) {
            return;
        }

        boolean isServerRequest = isServerRequest(packetInfo);
        boolean isServerResponse = isServerResponse(packetInfo);

        if (state != State.HANDSHAKE_COMPLETE && isPossibleHandshake(packetInfo)) {
            if (state == State.IDLE && isHandshakeInitRequest(packetInfo)) {
                log.debug("Server connection - handhake 1 requested");
                state = State.HANDSHAKE1_REQUESTED;
                lastRequest = packetInfo;
                serverConnection = new Connection(localAddr, packetInfo.srcPort, packetInfo.dstAddress, packetInfo.dstPort);
                log.debug("Connection request to game server: {}", serverConnection);

            } else if (state == State.HANDSHAKE1_REQUESTED && isServerResponse) {
                log.debug("Server connection - handhake 1 responded");
                state = State.HANDSHAKE1_RESPONDED;
                serverConnection.setLatency((int) (packetInfo.timestamp - lastRequest.timestamp));

            } else if (state == State.HANDSHAKE1_RESPONDED && isServerRequest) {
                log.debug("Server connection - handhake 2 requested");
                state = State.HANDSHAKE2_REQUESTED;
                lastRequest = packetInfo;

            } else if (state == State.HANDSHAKE2_REQUESTED && isServerResponse) {
                log.debug("Server connection - handhake 2 responded");
                state = State.HANDSHAKE_COMPLETE;
                int latency = (int) (packetInfo.timestamp - lastRequest.timestamp);
                serverConnection.setLatency((serverConnection.getLatency() + latency) / 2);
                setPacketFilter(PACKET_FILTER__CHECK_CONNECTION_ALIVE,
                        serverConnection.getRemoteAddr().getHostAddress(), serverConnection.getLocalAddr().getHostAddress());
                snifferListener.notifyMatchConnect(serverConnection);
            }
        } else if (state == State.HANDSHAKE_COMPLETE && isServerResponse) {
            serverConnection.setLastSeen(packetInfo.timestamp);
        }
    }

    private PacketInfo adaptPacket(Packet packet) {
        IpPacket ipPacket = packet.get(IpV4Packet.class);

        if (ipPacket == null) {
            return null;
        }

        PacketInfo info = new PacketInfo();
        info.timestamp = pcapHandle.getTimestamp().getTime();
        info.packetLen = packet.length();
        TcpPacket tcpPacket = ipPacket.get(TcpPacket.class);
        UdpPacket udpPacket = ipPacket.get(UdpPacket.class);
        info.srcAddress = ipPacket.getHeader().getSrcAddr();
        info.dstAddress = ipPacket.getHeader().getDstAddr();

        if (tcpPacket != null) {
            info.protocol = PacketInfo.Protocol.TCP;
            info.srcPort = tcpPacket.getHeader().getSrcPort().valueAsInt();
            info.dstPort = tcpPacket.getHeader().getDstPort().valueAsInt();
            info.payloadLen = tcpPacket.getPayload() != null ? tcpPacket.getPayload().length() : 0;
            info.payload = tcpPacket.getPayload();
        } else if (udpPacket != null) {
            info.protocol = PacketInfo.Protocol.UDP;
            info.srcPort = udpPacket.getHeader().getSrcPort().valueAsInt();
            info.dstPort = udpPacket.getHeader().getDstPort().valueAsInt();
            info.payloadLen = udpPacket.getPayload() != null ? udpPacket.getPayload().length() : 0;
            info.payload = udpPacket.getPayload();
        }

        return info;
    }

    private boolean isPossibleHandshake(PacketInfo packetInfo) {
        return packetInfo != null
                && packetInfo.payload != null
                && packetInfo.payload.length() == 29;
    }

    private boolean isHandshakeInitRequest(PacketInfo packetInfo) {
        byte[] udpData = packetInfo.payload.getRawData();

        return packetInfo.srcAddress.equals(localAddr)
                && udpData[0] == 0x01
                && udpData[2] == 0x00
                && udpData[3] == 0x00
                && udpData[4] == 0x00;
    }

    private boolean isServerRequest(PacketInfo packetInfo) {
        return serverConnection != null
                && packetInfo.dstAddress.equals(serverConnection.getRemoteAddr()) && packetInfo.dstPort == serverConnection.getRemotePort();
    }

    private boolean isServerResponse(PacketInfo packetInfo) {
        return serverConnection != null
                && packetInfo.srcAddress.equals(serverConnection.getRemoteAddr()) && packetInfo.srcPort == serverConnection.getRemotePort();
    }

    @Override
    public void stop() {
        if (pcapHandle != null) {
            log.info("Cleaning up sniffer...");
            try {
                pcapHandle.breakLoop();
            } catch (NotOpenException e) {
                log.error("Failed when attempting to stop sniffer.", e);
            }
        }
    }

    public void close() {
        connectionCleanerTimer.cancel();
        stop();
        pcapHandle.close();
        log.info("Freed network interface handle.");
    }


    private void startConnectionCleaner() {
        connectionCleanerTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                if (serverConnection != null && currentTime > serverConnection.getLastSeen() + CONNECTION_TIMEOUT_MS) {
                    log.debug("Detected match disconnection.");
                    clearConnection();
                }
            }
        }, 0, CLEANER_POLL_MS);
    }

    private void clearConnection() {
        if (state == State.HANDSHAKE_COMPLETE) {
            snifferListener.notifyMatchDisconnect();
        }
        serverConnection = null;
        state = State.IDLE;
        setPacketFilter(PACKET_FILTER__SEARCH_CONNECTION);
    }

    private void setPacketFilter(String filterExpr, Object... args) {
        try {
            String filter = String.format(filterExpr, args);
            pcapHandle.setFilter(filter, BpfProgram.BpfCompileMode.OPTIMIZE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
