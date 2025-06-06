package ch.csnc.gui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.*;
import ch.csnc.gui.interactions.DescriptionViewer;
import ch.csnc.pingback.PingbackTableModel;
import ch.csnc.pingback.Pingback;
import ch.csnc.settings.SettingsModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

public class InteractionsTab extends JSplitPane {
    public final String TAB_TITLE = "Interactions";
    private final MontoyaApi api;
    private final SettingsModel settingsModel;

    DescriptionViewer descriptionViewer;
    HttpRequestEditor requestViewer;
    HttpResponseEditor responseViewer;
    HttpRequestEditor collaboratorHTTPRequestViewer;
    HttpResponseEditor collaboratorHTTPResponseViewer;
    WebSocketMessageEditor collaboratorDNSViewer;
    RawEditor collaboratorSMTPViewer;
    JTable table;
    JTabbedPane tabbedPane;
    PingbackTableModel tableModel;

    public InteractionsTab(MontoyaApi api, PingbackTableModel tableModel, SettingsModel settingsModel) {
        this.api = api;
        this.settingsModel = settingsModel;
        this.tableModel = tableModel;

        setName(TAB_TITLE);

        // Define split pane
        this.setOrientation(JSplitPane.VERTICAL_SPLIT);

        // Create pane for log entries
        this.setLeftComponent(getJScrollPane(tableModel));

        // Create pane for details
        tabbedPane = constructLoggerTab();
        this.setRightComponent(tabbedPane);

        // Set initial visibility to false until an entry is selected
        tabbedPane.setVisible(false);
    }

    private JTabbedPane constructLoggerTab()
    {
        // Tabbed pane with request/response viewers
        JTabbedPane pane = new JTabbedPane();

        UserInterface userInterface = api.userInterface();

        descriptionViewer = new DescriptionViewer();

        requestViewer = userInterface.createHttpRequestEditor(EditorOptions.READ_ONLY);
        responseViewer = userInterface.createHttpResponseEditor(EditorOptions.READ_ONLY);

        collaboratorHTTPRequestViewer = userInterface.createHttpRequestEditor(EditorOptions.READ_ONLY);
        collaboratorHTTPResponseViewer = userInterface.createHttpResponseEditor(EditorOptions.READ_ONLY);

        collaboratorDNSViewer = userInterface.createWebSocketMessageEditor(EditorOptions.READ_ONLY, EditorOptions.SHOW_NON_PRINTABLE_CHARACTERS, EditorOptions.WRAP_LINES);

        collaboratorSMTPViewer = userInterface.createRawEditor(EditorOptions.READ_ONLY, EditorOptions.WRAP_LINES);

        pane.addTab("Description", descriptionViewer);
        pane.addTab("Request to server", requestViewer.uiComponent());
        pane.addTab("Response from server", responseViewer.uiComponent());

        return pane;
    }

    InteractionType previousType = null;
    private JScrollPane getJScrollPane(PingbackTableModel tableModel) {
        HashMap<InteractionType, Integer> lastTabActive = new HashMap<>();

        table = new JTable(tableModel)
        {
            @Override
            public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend)
            {
                // Only enable the bottom panel if an entry was selected
                if (previousType == null) {
                    tabbedPane.setVisible(true);
                    setDividerLocation(0.75);
                }

                // Convert index of selected row to index in data object
                int actualRowIndex = convertRowIndexToModel(rowIndex);
                Pingback pingback = tableModel.get(actualRowIndex);

                // Show original request and response that caused the pingback
                requestViewer.setRequest(pingback.request);
                responseViewer.setResponse(pingback.response);

                // Highlight Collaborator ID in request
                requestViewer.setSearchExpression(pingback.interaction.id().toString());

                // Populate the description tab
                descriptionViewer.setText(pingback.getDescription());

                // If interaction type changes, remove all tabs except the first three for description/request/response
                if (!pingback.interaction.type().equals(previousType)) {

                    // Store index of current tab for this interaction type
                    lastTabActive.put(previousType, tabbedPane.getSelectedIndex());

                    for (int i=tabbedPane.getTabCount()-1; i>2; i--) {
                        tabbedPane.removeTabAt(i);
                    }

                    switch (pingback.interaction.type()) {
                        case HTTP -> {
                            tabbedPane.addTab("Request to Collaborator", collaboratorHTTPRequestViewer.uiComponent());
                            tabbedPane.addTab("Response from Collaborator", collaboratorHTTPResponseViewer.uiComponent());
                        }
                        case DNS -> tabbedPane.addTab("Raw DNS Query", collaboratorDNSViewer.uiComponent());
                        case SMTP -> tabbedPane.addTab("Raw SMTP Conversation", collaboratorSMTPViewer.uiComponent());
                    }

                    tabbedPane.setSelectedIndex(lastTabActive.getOrDefault(pingback.interaction.type(), 0));
                }

                // Store current interaction type
                previousType = pingback.interaction.type();

                // Show details for HTTP pingback
                if (pingback.interaction.httpDetails().isPresent()) {
                    collaboratorHTTPRequestViewer.setRequest(pingback.interaction.httpDetails().get().requestResponse().request());
                    collaboratorHTTPResponseViewer.setResponse(pingback.interaction.httpDetails().get().requestResponse().response());
                }

                // Show details for DNS pingback
                else if (pingback.interaction.dnsDetails().isPresent()) {
                    collaboratorDNSViewer.setContents(pingback.interaction.dnsDetails().get().query());
                }

                // Show details for SMTP pingback
                else if (pingback.interaction.smtpDetails().isPresent()) {
                    collaboratorSMTPViewer.setContents(ByteArray.byteArray(pingback.interaction.smtpDetails().get().conversation()));
                }

                super.changeSelection(rowIndex, columnIndex, toggle, extend);
            }
        };

        // Popup menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem clearInteractionsItem = new JMenuItem("Clear all interactions");
        clearInteractionsItem.addActionListener(this::clearInteractions);
        popup.add(clearInteractionsItem);
        table.setComponentPopupMenu(popup);

        table.setAutoCreateRowSorter(true);

        return new JScrollPane(table);
    }

    private void clearInteractions(ActionEvent e) {
        // Confirm with a dialog
        int confirm = JOptionPane.showOptionDialog(api.userInterface().swingUtils().suiteFrame(),
                                                   "Delete all interactions?",
                                                   "Confirm",
                                                   JOptionPane.YES_NO_OPTION,
                                                   JOptionPane.QUESTION_MESSAGE,
                                                   null,
                                                   null,
                                                   null);
        // yeet
        if (confirm == JOptionPane.YES_OPTION) {
            tableModel.clear();
        }
    }
}
