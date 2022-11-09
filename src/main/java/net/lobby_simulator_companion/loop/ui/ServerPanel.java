package net.lobby_simulator_companion.loop.ui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lobby_simulator_companion.loop.config.AppProperties;
import net.lobby_simulator_companion.loop.config.Settings;
import net.lobby_simulator_companion.loop.domain.Connection;
import net.lobby_simulator_companion.loop.domain.Server;
import net.lobby_simulator_companion.loop.repository.ServerDao;
import net.lobby_simulator_companion.loop.service.GameEvent;
import net.lobby_simulator_companion.loop.service.GameStateManager;
import net.lobby_simulator_companion.loop.ui.common.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;

import static net.lobby_simulator_companion.loop.ui.common.ResourceFactory.Icon;
import static net.lobby_simulator_companion.loop.ui.common.UiConstants.*;


/**
 * @author NickyRamone
 */
@Slf4j
public class ServerPanel extends JPanel {

    private static final Font font = ResourceFactory.getRobotoFont();

    @RequiredArgsConstructor
    private enum InfoType {
        COUNTRY("Country:"),
        REGION("Region:"),
        CITY("City:"),
        LATENCY("Latency:");

        final String description;


        @Override
        public String toString() {
            return description;
        }
    }

    private final Settings settings;
    private final AppProperties appProperties;
    private final GameStateManager gameStateManager;
    private final UiEventOrchestrator uiEventOrchestrator;
    private final ServerDao serverDao;

    private JLabel summaryLabel;
    private JLabel geoLocationLabel;
    private NameValueInfoPanel detailsPanel;
    @Getter
    private Server server;


    public ServerPanel(Settings settings, AppProperties appProperties, GameStateManager gameStateManager,
                       UiEventOrchestrator uiEventOrchestrator, ServerDao serverDao) {
        this.settings = settings;
        this.appProperties = appProperties;
        this.gameStateManager = gameStateManager;
        this.uiEventOrchestrator = uiEventOrchestrator;
        this.serverDao = serverDao;

        initEventListeners();
        draw();
    }


    private void initEventListeners() {
        gameStateManager.registerListener(GameEvent.CONNECTED_TO_LOBBY,
                evt -> updateServerConnection((Connection) evt.getValue()));
        gameStateManager.registerListener(GameEvent.DISCONNECTED,
                evt -> refreshClear());
    }

    private void draw() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel collapsablePanel = new CollapsablePanel(
                createTitleBar(),
                createDetailsPanel(),
                settings, "ui.panel.server.collapsed");
        collapsablePanel.addPropertyChangeListener(evt ->
                uiEventOrchestrator.fireEvent(UiEventOrchestrator.UiEvent.STRUCTURE_RESIZED));

        add(collapsablePanel);
    }


    private JPanel createTitleBar() {
        JLabel serverLabel = new JLabel("Server:");
        serverLabel.setBorder(ComponentUtils.DEFAULT_BORDER);
        serverLabel.setForeground(UiConstants.COLOR__TITLE_BAR__FG);
        serverLabel.setFont(font);

        summaryLabel = new JLabel();
        summaryLabel.setBorder(ComponentUtils.DEFAULT_BORDER);
        summaryLabel.setFont(font);

        geoLocationLabel = ComponentUtils.createButtonLabel(
                null,
                "Click to visit this location in Google Maps on your browser.",
                Icon.GEO_LOCATION,
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        super.mouseClicked(e);
                        if (server != null) {
                            try {
                                // we need to provide US locale so that the formatter uses "." as a decimal separator
                                String profileUrl = String.format(appProperties.get("google.maps.geolocation.url_template"),
                                        server.getLatitude(), server.getLongitude(), Locale.US);
                                Desktop.getDesktop().browse(new URL(profileUrl).toURI());
                            } catch (IOException e1) {
                                log.error("Failed to open browser at Google Maps.");
                            } catch (URISyntaxException e1) {
                                log.error("Attempted to use an invalid URL for Google Maps.");
                            }
                        }
                    }
                });
        geoLocationLabel.setVisible(false);

        JPanel container = new JPanel();
        container.setPreferredSize(new Dimension(200, 25));
        container.setMinimumSize(new Dimension(300, 25));
        container.setBackground(UiConstants.COLOR__TITLE_BAR__BG);
        container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
        container.add(serverLabel);
        container.add(summaryLabel);
        container.add(geoLocationLabel);
        container.add(Box.createHorizontalGlue());

        return container;
    }

    private JPanel createDetailsPanel() {
        detailsPanel = new NameValueInfoPanel();
        detailsPanel.setSizes(WIDTH__INFO_PANEL__NAME_COLUMN, WIDTH__INFO_PANEL__VALUE_COLUMN, 100);
        detailsPanel.addFields(InfoType.class);

        JPanel container = new JPanel();
        container.setBackground(COLOR__INFO_PANEL__BG);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(Box.createVerticalStrut(5));
        container.add(detailsPanel);

        return container;
    }

    private void updateServerConnection(Connection serverConnection) {
        new Thread(() -> {
            try {
                Server newServerInfo = serverDao.getByIpAddress(serverConnection.getRemoteAddr().getHostAddress());
                newServerInfo.setLatency(serverConnection.getLatency());
                SwingUtilities.invokeLater(() -> {
                    refreshServerOnScreen(newServerInfo);
                    uiEventOrchestrator.fireEvent(UiEventOrchestrator.UiEvent.SERVER_INFO_UPDATED, newServerInfo);
                });
            } catch (IOException e) {
                log.error("Failed to retrieve server information.", e);
            }
        }).start();
    }

    private void refreshServerOnScreen(Server server) {
        refreshClear();
        this.server = server;
        summaryLabel.setText(String.format("%s, %s", server.getCity(), server.getCountry()));
        if (server.getLatitude() != null && server.getLongitude() != null) {
            geoLocationLabel.setVisible(true);
        }

        setServerValue(InfoType.COUNTRY, server.getCountry());
        setServerValue(InfoType.REGION, server.getRegion());
        setServerValue(InfoType.CITY, server.getCity());
        setServerValue(InfoType.LATENCY, server.getLatency() != null ? server.getLatency() + " ms" : "?");
    }

    public void refreshClear() {
        server = null;
        summaryLabel.setText(null);
        geoLocationLabel.setVisible(false);
        setServerValue(InfoType.COUNTRY, null);
        setServerValue(InfoType.REGION, null);
        setServerValue(InfoType.CITY, null);
        setServerValue(InfoType.LATENCY, null);
        uiEventOrchestrator.fireEvent(UiEventOrchestrator.UiEvent.SERVER_INFO_UPDATED);
    }

    private void setServerValue(InfoType type, String value) {
        detailsPanel.getRight(type, JLabel.class)
                .setText(Optional.ofNullable(value).orElse("--"));
    }

}
