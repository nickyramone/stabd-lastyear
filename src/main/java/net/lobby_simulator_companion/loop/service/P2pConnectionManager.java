package net.lobby_simulator_companion.loop.service;

import lombok.Builder;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.lobby_simulator_companion.loop.domain.Connection;
import net.lobby_simulator_companion.loop.util.ByteUtil;
import net.lobby_simulator_companion.loop.util.NetUtil;
import org.pcap4j.core.*;
import org.pcap4j.packet.*;
import org.pcap4j.packet.factory.PacketFactories;
import org.pcap4j.packet.factory.PacketFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
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
public class P2pConnectionManager implements ConnectionManager {

    /**
     * In order to check that the connection is alive, we need to consider at least 150 bytes for server responses.
     */
    private static final int MAX_CAPTURED_PACKET_SIZE = 150;
    private static final int CLEANER_POLL_MS = 1000;
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    /**
     * Berkley Packet Filter (BPF):
     * http://biot.com/capstats/bpf.html
     * https://www.tcpdump.org/manpages/pcap-filter.7.html
     */
    private static final String PACKET_FILTER__SEARCH_CONNECTION = "udp and len <= 110";
    private static final String PACKET_FILTER__CHECK_CONNECTION_ALIVE = "udp and (src host %s or dst host %s)";

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


    @Builder
    private static final class StunPacket {
        enum MessageType {BINDING_REQUEST, BINDING_RESPONSE}

        final InetAddress srcAddress;
        final InetAddress dstAddress;
        final int srcPort;
        final int dstPort;
        final MessageType messageType;
        final int messageLength;
        final int transactionId;
        long timestamp;

        static StunPacket of(PcapHandle pcapHandle, Packet packet) {
            IpPacket ipPacket = packet.get(IpV4Packet.class);
            if (ipPacket == null) {
                return null;
            }

            UdpPacket udpPacket = ipPacket.get(UdpPacket.class);
            if (udpPacket == null) {
                return null;
            }

            byte[] udpPayload = udpPacket.getPayload().getRawData();

            return StunPacket.builder()
                    .srcAddress(ipPacket.getHeader().getSrcAddr())
                    .dstAddress(ipPacket.getHeader().getDstAddr())
                    .srcPort(udpPacket.getHeader().getSrcPort().valueAsInt())
                    .dstPort(udpPacket.getHeader().getDstPort().valueAsInt())
                    .messageType(determineMessageType(udpPayload))
                    .transactionId(extractTransactionId(udpPayload))
                    .messageLength(extractMessageLength(udpPayload))
                    .timestamp(pcapHandle.getTimestamp().getTime())
                    .build();
        }

//        static StunPacket of(PacketInfo packetInfo) {
//            if (packetInfo == null || packetInfo.payload == null || packetInfo.protocol != PacketInfo.Protocol.UDP) {
//                return null;
//            }
//
//            byte[] udpData = packetInfo.payload.getRawData();
//            MessageType messageType = determineMessageType(udpData);
//            int transactionId = extractTransactionId(udpData);
//            int messageLength = extractMessageLength(udpData);
//
//            return new StunPacket(packetInfo.srcAddress, packetInfo.dstAddress, packetInfo.srcPort, packetInfo.dstPort,
//                    messageType, messageLength, transactionId);
//        }

        private static MessageType determineMessageType(byte[] udpPayload) {
            if (udpPayload[0] == 0x00 && udpPayload[1] == 0x01) {
                return MessageType.BINDING_REQUEST;
            } else if (udpPayload[0] == 0x01 && udpPayload[1] == 0x01) {
                return MessageType.BINDING_RESPONSE;
            }
            return null;
        }

        private static int extractMessageLength(byte[] udpPayload) {
            if (udpPayload.length < 4) {
                return 0;
            }

            byte byte1 = udpPayload[2];
            byte byte2 = udpPayload[3];

            return ((byte1 & 0xFF) << 8) | (byte2 & 0xFF);
        }

        private static int extractTransactionId(byte[] udpPayload) {
            byte[] transactionId = new byte[16];

            if (udpPayload.length < 20) {
                return 0;
            }

            for (int i = 0, j = 4; i < 16; i++, j++) {
                transactionId[i] = udpPayload[j];
            }

            return ByteUtil.byteArrayToInt(transactionId);
        }

    }

    private enum State {IDLE, HANDSHAKE_REQUESTED, CONNECTED_TO_HOST}

    private final InetAddress localAddr;
    private final SnifferListener snifferListener;
    private final Timer connectionCleanerTimer = new Timer();
    private PcapHandle pcapHandle;
    private Connection serverConnection;
    private State state = State.IDLE;
    private Map<Integer, Long> requestTimestampByTransactionId = new HashMap<>();


    public P2pConnectionManager(InetAddress localAddr, SnifferListener snifferListener)
            throws PcapNativeException, InvalidNetworkInterfaceException {

        this.localAddr = localAddr;
        this.snifferListener = snifferListener;
        initNetworkInterface();
        startConnectionCleaner();
    }


    private void initNetworkInterface() throws PcapNativeException, InvalidNetworkInterfaceException {
        PcapNetworkInterface networkInterface = Pcaps.getDevByAddress(localAddr);
        if (networkInterface == null) {
            throw new InvalidNetworkInterfaceException();
        }

        final PcapNetworkInterface.PromiscuousMode mode = PcapNetworkInterface.PromiscuousMode.NONPROMISCUOUS;
        pcapHandle = networkInterface.openLive(MAX_CAPTURED_PACKET_SIZE, mode, 1000);
        startFilteringPacketsForConnectionToHost();
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

        StunPacket stunPacket = StunPacket.of(pcapHandle, packet);

        if (state == State.IDLE && isStunBindingRequest(stunPacket)) {
            initHandshakeWithHost(stunPacket);
        } else if (state == State.HANDSHAKE_REQUESTED && isStunBindingResponse(stunPacket)) {
            connectionEstablishedWithHost();
        } else if (state == State.CONNECTED_TO_HOST) {
            if (isStunBindingRequest(stunPacket) && isRequestToHost(packetInfo)) {
                pingRequestSent(stunPacket);
            } else if (isStunBindingResponse(stunPacket) && isResponseFromHost(packetInfo)) {
                pingResponseReceived(stunPacket);
            } else if (isResponseFromHost(packetInfo)) {
                connectionToHostIsStillAlive(packetInfo);
            }
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


    private void initHandshakeWithHost(StunPacket stunPacket) {
        log.debug("Server connection - STUN binding request");
        state = State.HANDSHAKE_REQUESTED;
        serverConnection = new Connection(localAddr, stunPacket.srcPort, stunPacket.dstAddress, stunPacket.dstPort);
        log.debug("Connection request to game server: {}", serverConnection);
    }

    private void connectionEstablishedWithHost() {
        log.debug("Server connection - STUN binding response");
        state = State.CONNECTED_TO_HOST;
        startFilteringPacketsForDisconnectionFromHost();
        snifferListener.notifyMatchConnect(serverConnection);
    }

    private void connectionToHostIsStillAlive(PacketInfo packetInfo) {
        serverConnection.setLastSeen(packetInfo.timestamp);
    }

    private void pingRequestSent(StunPacket stunPacket) {
        requestTimestampByTransactionId.put(stunPacket.transactionId, stunPacket.timestamp);
    }

    private void pingResponseReceived(StunPacket stunPacket) {
        Long requestTimestamp = requestTimestampByTransactionId.remove(stunPacket.transactionId);

        if (requestTimestamp != null) {
            int ping = (int) (stunPacket.timestamp - requestTimestamp);
            snifferListener.notifyPingUpdate(ping);
        }
    }

    private boolean isStunBindingRequest(StunPacket stunPacket) {
        return stunPacket != null
                && stunPacket.messageType == StunPacket.MessageType.BINDING_REQUEST
                && stunPacket.srcAddress.equals(localAddr)
                && stunPacket.messageLength == 36
                && !NetUtil.isLocalAddress(stunPacket.dstAddress);
    }

    private boolean isStunBindingResponse(StunPacket stunPacket) {
        return stunPacket != null
                && stunPacket.messageType == StunPacket.MessageType.BINDING_RESPONSE
                && stunPacket.dstAddress.equals(localAddr)
                && stunPacket.messageLength == 48
                && !NetUtil.isLocalAddress(stunPacket.srcAddress);
    }

    private boolean isRequestToHost(PacketInfo packet) {
        return serverConnection != null
                && packet.dstAddress.equals(serverConnection.getRemoteAddr())
                && packet.dstPort == serverConnection.getRemotePort();
    }

    private boolean isResponseFromHost(PacketInfo packetInfo) {
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
        if (state == State.CONNECTED_TO_HOST) {
            snifferListener.notifyMatchDisconnect();
        }

        serverConnection = null;
        state = State.IDLE;
        requestTimestampByTransactionId.clear();
        setPacketFilter(PACKET_FILTER__SEARCH_CONNECTION);
    }


    private void startFilteringPacketsForConnectionToHost() {
        setPacketFilter(PACKET_FILTER__SEARCH_CONNECTION);
    }

    private void startFilteringPacketsForDisconnectionFromHost() {
        setPacketFilter(PACKET_FILTER__CHECK_CONNECTION_ALIVE,
                serverConnection.getRemoteAddr().getHostAddress(), serverConnection.getRemoteAddr().getHostAddress());
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
