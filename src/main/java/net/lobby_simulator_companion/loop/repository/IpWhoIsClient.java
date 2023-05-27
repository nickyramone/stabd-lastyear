package net.lobby_simulator_companion.loop.repository;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Key;
import lombok.Data;
import net.lobby_simulator_companion.loop.domain.Server;

import java.io.IOException;

/**
 * Client for ipwhois.io
 *
 * @author NickyRamone
 */
public class IpWhoIsClient implements ServerDao {

    private static final JsonObjectParser jsonObjParser = new JsonObjectParser(new JacksonFactory());
    private static final HttpRequestFactory httpReqFactory = new NetHttpTransport().createRequestFactory(
            request -> request.setParser(jsonObjParser));

    private final String serviceUrlPrefix;

    @Data
    public static final class WhoisDto {

        @Data
        public static final class Connection {
            @Key
            String isp;
        }

        @Key
        String country;

        @Key("country_code")
        String countryCode;

        @Key
        String region;

        @Key
        String city;

        @Key
        Double latitude;

        @Key
        Double longitude;

        @Key
        Connection connection;

    }


    public IpWhoIsClient(String serviceUrlPrefix) {
        this.serviceUrlPrefix = serviceUrlPrefix;
    }


    public Server getByIpAddress(String ipAddress) throws IOException {
        WhoisDto dto = getIpAddressInfo(ipAddress);

        return convertDtoToDomain(ipAddress, dto);
    }

    private WhoisDto getIpAddressInfo(String ipAddress) throws IOException {
        String url = serviceUrlPrefix + ipAddress;
        HttpRequest req = httpReqFactory.buildGetRequest(new GenericUrl(url));

        return req.execute().parseAs(WhoisDto.class);
    }


    private Server convertDtoToDomain(String ipAddress, WhoisDto dto) {
        return Server.builder()
                .address(ipAddress)
                .city(dto.getCity())
                .country(dto.getCountry())
                .countryCode(dto.getCountryCode())
                .region(dto.getRegion())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .isp(dto.getConnection() != null? dto.getConnection().getIsp(): null)
                .build();
    }

}
