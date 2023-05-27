package net.lobby_simulator_companion.loop.util;

import lombok.experimental.UtilityClass;

import java.net.InetAddress;

@UtilityClass
public class NetUtil {

    public boolean isLocalAddress(InetAddress address) {
        byte[] ipAddress = address.getAddress();

        if (ipAddress.length == 4) { // IPv4 address
            if (ipAddress[0] == (byte) 10 ||
                    (ipAddress[0] == (byte) 172 && (ipAddress[1] >= (byte) 16 && ipAddress[1] <= (byte) 31)) ||
                    (ipAddress[0] == (byte) 192 && ipAddress[1] == (byte) 168)) {
                return true; // Private IP address range found
            }
        } else if (ipAddress.length == 16) { // IPv6 address
            if ((ipAddress[0] & 0xFF) == 0xFC) {
                return true; // IPv6 unique local address range found
            }
        }

        return address.isLoopbackAddress();
    }



}
