package net.lobby_simulator_companion.loop.service;

// TODO: remove
public interface PingDigest {

    void requestSent(long pingPacketTimestamp);

    int calculatePing();

    void reset();

}
