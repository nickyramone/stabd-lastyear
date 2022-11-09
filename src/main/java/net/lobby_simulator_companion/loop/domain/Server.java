package net.lobby_simulator_companion.loop.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

/**
 * @author NickyRamone
 */
@Builder
@Data
public class Server {

    private final String address;
    private String hostName;
    private String country;
    private String countryCode;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private String isp;
    private Integer latency;

}
