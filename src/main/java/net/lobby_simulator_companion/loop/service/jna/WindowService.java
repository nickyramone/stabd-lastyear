package net.lobby_simulator_companion.loop.service.jna;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import lombok.experimental.UtilityClass;

/**
 * @author NickyRamone
 */
@UtilityClass
public class WindowService {

    private static final int MAX_TITLE_LENGTH = 1024;


    public String getActiveWindowTitle() {
        char[] buffer = new char[MAX_TITLE_LENGTH * 2];
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        User32.INSTANCE.GetWindowText(hwnd, buffer, MAX_TITLE_LENGTH);

        return Native.toString(buffer).trim();
    }
}
