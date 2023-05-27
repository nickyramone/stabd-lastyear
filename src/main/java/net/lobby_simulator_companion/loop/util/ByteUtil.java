package net.lobby_simulator_companion.loop.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ByteUtil {

    public int byteArrayToInt(byte[] byteArray) {
        int result = 0;
        for (int i = 0; i < byteArray.length; i++) {
            result = (result << 8) | (byteArray[i] & 0xFF);
        }

        return result;
    }

}
