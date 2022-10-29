package net.lobby_simulator_companion.loop.ui;

import lombok.extern.slf4j.Slf4j;
import net.lobby_simulator_companion.loop.config.Settings;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;

@Slf4j
public class NetworkInterfaceFrame extends JFrame {

    private static final String SETTING__NIF_ENABLED = "network.interface.enabled";
    private static final String SETTING__NIF_ADDRESS = "network.interface.address";
    private static final String SETTING__NIF_SELECTED_IDX = "network.interface.selected";
    private static final String SETTING__NIF_AUTOLOAD = "network.interface.autoload";
    private static final String EVENT__DISPOSE = "event.dispose";

    private InetAddress localAddr;


    public NetworkInterfaceFrame(Settings settings) throws Exception {
        final JFrame frame = new JFrame("Network Device");
        frame.setFocusableWindowState(true);

        int selectedIdx = settings.getInt(SETTING__NIF_SELECTED_IDX, 0);

        final JLabel ipLab = new JLabel("Select LAN IP obtained from Network Settings:", JLabel.LEFT);
        final JComboBox<String> lanIP = new JComboBox<>();
        final JLabel lanLabel = new JLabel("If your device IP isn't in the dropdown, provide it below.");
        final JTextField lanText = new JTextField(settings.get(SETTING__NIF_ADDRESS, ""));
        final JCheckBox rememberCheckbox = new JCheckBox("Do not prompt me next time and use current selections.", false);

        ArrayList<InetAddress> inets = new ArrayList<InetAddress>();

        for (PcapNetworkInterface i : Pcaps.findAllDevs()) {
            for (PcapAddress x : i.getAddresses()) {
                InetAddress xAddr = x.getAddress();
                if (xAddr != null && x.getNetmask() != null && !xAddr.toString().equals("/0.0.0.0")) {
                    NetworkInterface inf = NetworkInterface.getByInetAddress(x.getAddress());
                    if (inf != null && inf.isUp() && !inf.isVirtual()) {
                        inets.add(xAddr);
                        lanIP.addItem((lanIP.getItemCount() + 1) + " - " + inf.getDisplayName() + " ::: " + xAddr.getHostAddress());
                        log.debug("Found: {} - {} ::: {}", lanIP.getItemCount(), inf.getDisplayName(), xAddr.getHostAddress());
                    }
                }
            }
        }
        lanIP.addItem("Do not use any network interface. Disable lobby region detection.");
        lanIP.setSelectedIndex(selectedIdx);

        if (lanIP.getItemCount() == 0) {
            JOptionPane.showMessageDialog(null, "Unable to locate devices.\nPlease try running the program in Admin Mode.\nIf this does not work, you may need to reboot your computer.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        lanIP.setFocusable(false);
        final JButton start = new JButton("Start");
        start.addActionListener(e -> {
            try {
                if (lanIP.getSelectedIndex() == lanIP.getItemCount() - 1) {
                    // user selected to not use packet sniffing
                    localAddr = null;
                    settings.set(SETTING__NIF_ENABLED, false);
                }
                else if (lanText.getText().length() >= 7 && !lanText.getText().equals("0.0.0.0")) { // 7 is because the minimum field is 0.0.0.0
                    log.debug("Using IP from textfield: {}", lanText.getText());
                    settings.set(SETTING__NIF_ENABLED, true);
                    localAddr = InetAddress.getByName(lanText.getText());
                } else {
                    log.debug("Using device from dropdown: {}", lanIP.getSelectedItem());
                    settings.set(SETTING__NIF_ENABLED, true);
                    localAddr = inets.get(lanIP.getSelectedIndex());
                }
                settings.set(SETTING__NIF_ADDRESS, localAddr != null? localAddr.getHostAddress().replaceAll("/", ""): "");
                settings.set(SETTING__NIF_SELECTED_IDX, lanIP.getSelectedIndex());
                settings.set(SETTING__NIF_AUTOLOAD, rememberCheckbox.isSelected());
                frame.setVisible(false);
                frame.dispose();
                firePropertyChange(EVENT__DISPOSE, null, localAddr);
            } catch (UnknownHostException e1) {
                log.error("Encountered an invalid address.", e1);
            }
        });

        frame.setLayout(new GridLayout(6, 1));
        frame.add(ipLab);
        frame.add(lanIP);
        frame.add(lanLabel);
        frame.add(lanText);
        frame.add(rememberCheckbox);
        frame.add(start);
        frame.setAlwaysOnTop(true);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

}
