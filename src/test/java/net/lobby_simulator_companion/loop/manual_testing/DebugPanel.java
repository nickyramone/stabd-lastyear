package net.lobby_simulator_companion.loop.manual_testing;

import lombok.extern.slf4j.Slf4j;
import net.lobby_simulator_companion.loop.DevModeConfigurer;
import net.lobby_simulator_companion.loop.Factory;
import net.lobby_simulator_companion.loop.domain.Connection;
import net.lobby_simulator_companion.loop.domain.Killer;
import net.lobby_simulator_companion.loop.domain.RealmMap;
import net.lobby_simulator_companion.loop.domain.stats.KillerStats;
import net.lobby_simulator_companion.loop.domain.stats.MapStats;
import net.lobby_simulator_companion.loop.domain.stats.Stats;
import net.lobby_simulator_companion.loop.service.GameEvent;
import net.lobby_simulator_companion.loop.service.GameStateManager;
import net.lobby_simulator_companion.loop.service.LoopDataService;
import net.lobby_simulator_companion.loop.util.TimeUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Timer;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static net.lobby_simulator_companion.loop.util.LangUtil.unchecked;

/**
 * A panel for debugging purposes.
 * Simulated connections and host user discovery.
 *
 * @author NickyRamone
 */
@Slf4j
public class DebugPanel extends JPanel {

    private JFrame frame;
    private LoopDataService dataService;
    private GameStateManager gameStateManager;

    private Random random = new Random();

    private boolean connected;


    public DebugPanel(LoopDataService dataService, GameStateManager gameStateManager) throws Exception {
        this.dataService = dataService;
        this.gameStateManager = gameStateManager;

        DevModeConfigurer.configureMockSteamProfileDaoResponse("1", "Dummy Name 1");
        DevModeConfigurer.configureMockSteamProfileDaoResponse("2", "Dummy Name 2");

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        setOpaque(true);
        frame = new JFrame("Debug Panel");

        /** Since this frame will be always on top, having the focus state in true
         * will make other windows unclickable so we need to set it to false. */
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBackground(Color.WHITE);
        frame.setAlwaysOnTop(true);
        frame.pack();
        frame.setLocation(900, 300);
        frame.setSize(new Dimension(500, 300));
        frame.setVisible(true);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        addComponents(contentPanel);

        frame.setContentPane(contentPanel);

        startPingSimulator();
    }

    private void startPingSimulator() {
        java.util.Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (connected) {
                    Factory.mainWindow().updatePing(random.nextInt(200));
                }
            }
        }, 0, 500);
    }


    private void addComponents(JPanel container) {
        JButton button;

//        JPanel connPanel = new JPanel();
//        connPanel.setLayout(new BoxLayout(connPanel, BoxLayout.X_AXIS));
//        container.add(connPanel);
//        button = new JButton("Search Match");
//        button.addActionListener(e -> simulateMatchSearch());
//        connPanel.add(button);
//        button = new JButton("Cancel Match Search");
//        button.addActionListener(e -> simulateMatchSearchCancel());
//        connPanel.add(button);
//
//        JPanel lobbyPanel = new JPanel();
//        lobbyPanel.setLayout(new BoxLayout(lobbyPanel, BoxLayout.X_AXIS));
//        container.add(lobbyPanel);
//        button = new JButton("Leave Lobby");
//        button.addActionListener(e -> simulateLobbyLeave());
//        lobbyPanel.add(button);

//        JPanel matchPanel = new JPanel();
//        matchPanel.setLayout(new BoxLayout(matchPanel, BoxLayout.X_AXIS));
//        container.add(matchPanel);
//        button = new JButton("Generate Map");
//        button.addActionListener(e -> simulateMapGeneration());
//        matchPanel.add(button);
//        button = new JButton("Start Match");
//        button.addActionListener(e -> simulateMatchStart());
//        matchPanel.add(button);
//        button = new JButton("End Match");
//        button.addActionListener(e -> simulateMatchEnd());
//        matchPanel.add(button);

        JPanel serverPanel = new JPanel();
        serverPanel.setLayout(new BoxLayout(serverPanel, BoxLayout.X_AXIS));
        container.add(serverPanel);
        button = new JButton("Connect to Server");
        button.addActionListener(e -> simulateServerConnect());
        serverPanel.add(button);
        button = new JButton("Disconnect from Server");
        button.addActionListener(e -> simulateServerDisconnect());
        serverPanel.add(button);

//        JPanel killerPanel = new JPanel();
//        killerPanel.setLayout(new BoxLayout(killerPanel, BoxLayout.X_AXIS));
//        container.add(killerPanel);
//        button = new JButton("Detect Killer Player A");
//        button.addActionListener(e -> simulateNewKillerPlayer(1));
//        killerPanel.add(button);
//        button = new JButton("Detect Killer Player B");
//        button.addActionListener(e -> simulateNewKillerPlayer(2));
//        killerPanel.add(button);
//        button = new JButton("Killer Character");
//        button.addActionListener(e -> simulateRandomKillerCharacter());
//        killerPanel.add(button);

        JPanel statsPanel = new JPanel();
        container.add(statsPanel);
        button = new JButton("Export map stats to clipboard");
        button.addActionListener(e -> exportMapStats());
        statsPanel.add(button);

        button = new JButton("Export killer stats to clipboard");
        button.addActionListener(e -> exportKillerStats());
        statsPanel.add(button);
    }

    private void simulateServerConnect() {
        Connection connection;
        try {
            connection = new Connection(InetAddress.getByName("127.0.0.1"), 1000, InetAddress.getByName("1.2.3.4"), 2000);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        connection.setLatency(random.nextInt(1500));
//        gameStateManager.fireEvent(GameEvent.CONNECTED_TO_LOBBY, connection);
        gameStateManager.handleServerConnect(connection);
        connected = true;
    }

    private void simulateServerDisconnect() {
        connected = false;
        gameStateManager.fireEvent(GameEvent.DISCONNECTED);
    }


    private void exportMapStats() {
        Map<RealmMap, MapStats> mapStats = dataService.getStats().get(Stats.Period.YEARLY).getMapStats();

        int totalMatches = mapStats.entrySet().stream()
                .map(e -> e.getValue().getEscapes() + e.getValue().getDeaths())
                .reduce(0, Integer::sum);

        String statsText = "Map Name, Matches Played , Occurrence Rate, Survival Rate, Average Match Time\n" +
                mapStats.entrySet().stream()
                        .map(e -> mapStatToCsvRow(e.getKey(), e.getValue(), totalMatches))
                        .collect(Collectors.joining("\n"));


        StringSelection stringSelection = new StringSelection(statsText);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        log.info("Stats copied to clipboard.");
    }

    private String mapStatToCsvRow(RealmMap realmMap, MapStats mapStats, int totalMatches) {
        int matches = mapStats.getEscapes() + mapStats.getDeaths();
        float survivalRate = (float) mapStats.getEscapes() / matches;
        int averageMatchDuration = mapStats.getMatchTime() / matches;

        List<Object> values = new ArrayList<>();
        values.add("\"" + realmMap + "\"");
        values.add(matches);
        values.add((double) matches / totalMatches);
        values.add(survivalRate);
        values.add("\"" + TimeUtil.formatTimeUpToHours(averageMatchDuration) + "\"");

        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void exportKillerStats() {
        Map<Killer, KillerStats> stats = dataService.getStats().get(Stats.Period.YEARLY).getKillersStats();
        List<Map.Entry<Killer, KillerStats>> entries = stats.entrySet().stream().filter(e -> e.getKey().isIdentified()).collect(toList());

        int totalMatches = entries.stream()
                .map(e -> e.getValue().getEscapes() + e.getValue().getDeaths())
                .reduce(0, Integer::sum);

        String statsText = "Killer Name, Matches Played Against, Occurrence Rate, Survival Rate, Average Match Time\n" +
                entries.stream()
                        .map(e -> killerStatToCsvRow(e.getKey(), e.getValue(), totalMatches))
                        .collect(Collectors.joining("\n"));


        StringSelection stringSelection = new StringSelection(statsText);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        log.info("Stats copied to clipboard.");
    }


    private String killerStatToCsvRow(Killer killer, KillerStats stats, int totalMatches) {
        int matches = stats.getEscapes() + stats.getDeaths();
        float survivalRate = (float) stats.getEscapes() / matches;
        int averageMatchDuration = stats.getMatchTime() / matches;

        List<Object> values = new ArrayList<>();
        values.add("\"" + killer.alias() + "\"");
        values.add(matches);
        values.add((double) matches / totalMatches);
        values.add(survivalRate);
        values.add("\"" + TimeUtil.formatTimeUpToHours(averageMatchDuration) + "\"");

        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

}
