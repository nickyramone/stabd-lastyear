package net.lobby_simulator_companion.loop.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.net.InetAddress;

/**
 * @author NickyRamone
 */
@RequiredArgsConstructor
@Data
public class PlayerDto {

    private final String steamId; // Steam id64
    private final String dbdId;
    private final InetAddress inetAddress;

}
