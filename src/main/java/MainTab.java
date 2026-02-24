import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainTab extends JPanel {
    private final MontoyaApi api;

    private final JButton loadButton = new JButton("Load .proxymanlogv2");
    private final JButton addAllToSiteMapButton = new JButton("Add ALL to Site Map");
    private final JButton sendAllToRepeaterButton = new JButton("Send ALL to Repeater");

    private final JTextField filterField = new JTextField(28);
    private final JCheckBox autoOpenTabs = new JCheckBox("Open tabs for all requests", true);

    private final JLabel fileLabel = new JLabel("No file loaded");
    private final JLabel countLabel = new JLabel("0 requests");
    private final JProgressBar progressBar = new JProgressBar();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"#", "Method", "Host", "Path", "Status"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    private final JTable table = new JTable(tableModel);
    private final JTabbedPane requestTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

    private final List<ProxymanParser.ParsedEntry> allEntries = new ArrayList<>();
    private final List<Integer> filteredIndexMap = new ArrayList<>(); // table row -> allEntries index

    private final AtomicBoolean isLoading = new AtomicBoolean(false);

    public MainTab(MontoyaApi api) {
        super(new BorderLayout(10, 10));
        this.api = api;

        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new GridBagLayout());
        toolbar.setBorder(new EmptyBorder(0, 0, 6, 0));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 8);
        toolbar.add(loadButton, c);

        c.gridx++;
        toolbar.add(sendAllToRepeaterButton, c);

        c.gridx++;
        toolbar.add(addAllToSiteMapButton, c);

        c.gridx++;
        toolbar.add(new JSeparator(SwingConstants.VERTICAL), c);

        c.gridx++;
        toolbar.add(new JLabel("Filter:"), c);

        c.gridx++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        toolbar.add(filterField, c);

        c.gridx++;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        toolbar.add(autoOpenTabs, c);

        JPanel statusRow = new JPanel(new BorderLayout(10, 0));
        JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftStatus.add(fileLabel);
        leftStatus.add(new JLabel("•"));
        leftStatus.add(countLabel);

        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setVisible(false);

        statusRow.add(leftStatus, BorderLayout.WEST);
        statusRow.add(progressBar, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout());
        north.add(toolbar, BorderLayout.NORTH);
        north.add(statusRow, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Requests"));

        requestTabs.setBorder(BorderFactory.createTitledBorder("Request Details"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, requestTabs);
        split.setResizeWeight(0.45);
        split.setDividerLocation(520);

        add(split, BorderLayout.CENTER);

        sendAllToRepeaterButton.setEnabled(false);
        addAllToSiteMapButton.setEnabled(false);

        try { api.userInterface().applyThemeToComponent(this); } catch (Exception ignored) {}

        loadButton.addActionListener(e -> onLoad());
        sendAllToRepeaterButton.addActionListener(e -> onSendAllToRepeater());
        addAllToSiteMapButton.addActionListener(e -> onAddAllToSiteMap());

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) return;

            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= filteredIndexMap.size()) return;

            int idx = filteredIndexMap.get(modelRow);
            openEntryTab(idx);
        });
    }

    private void onLoad() {
        if (isLoading.get()) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select a .proxymanlogv2 file");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (file == null || !file.exists()) {
            fileLabel.setText("File not found");
            return;
        }

        isLoading.set(true);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Loading…");

        loadButton.setEnabled(false);
        sendAllToRepeaterButton.setEnabled(false);
        addAllToSiteMapButton.setEnabled(false);

        tableModel.setRowCount(0);
        requestTabs.removeAll();
        allEntries.clear();
        filteredIndexMap.clear();

        fileLabel.setText(file.getName());
        countLabel.setText("0 requests");

        SwingWorker<List<ProxymanParser.ParsedEntry>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ProxymanParser.ParsedEntry> doInBackground() throws Exception {
                return ProxymanParser.parse(file);
            }

            @Override
            protected void done() {
                try {
                    List<ProxymanParser.ParsedEntry> parsed = get();
                    allEntries.addAll(parsed);
                    applyFilter();

                    countLabel.setText(allEntries.size() + " requests");
                    sendAllToRepeaterButton.setEnabled(!allEntries.isEmpty());
                    addAllToSiteMapButton.setEnabled(!allEntries.isEmpty());

                    if (autoOpenTabs.isSelected()) {
                        openAllTabsInBackground();
                    }
                } catch (Exception ex) {
                    api.logging().logToError("Failed to load proxyman log: " + ex.getMessage());
                    fileLabel.setText("Failed to load (see Extender errors)");
                } finally {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                    progressBar.setString("");
                    loadButton.setEnabled(true);
                    isLoading.set(false);
                }
            }
        };

        worker.execute();
    }

    private void openAllTabsInBackground() {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(false);
        progressBar.setMinimum(0);
        progressBar.setMaximum(Math.max(1, allEntries.size()));
        progressBar.setValue(0);
        progressBar.setString("Creating tabs…");

        SwingWorker<Void, Integer> tabWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < allEntries.size(); i++) {
                    final int idx = i;
                    SwingUtilities.invokeLater(() -> openEntryTab(idx));
                    publish(i + 1);
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int v = chunks.get(chunks.size() - 1);
                progressBar.setValue(v);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                progressBar.setString("");
            }
        };
        tabWorker.execute();
    }

    private void applyFilter() {
        String q = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);

        tableModel.setRowCount(0);
        filteredIndexMap.clear();

        for (int i = 0; i < allEntries.size(); i++) {
            ProxymanParser.ParsedEntry e = allEntries.get(i);

            String statusText = extractStatusFromResponse(e.response);
            String rowText = (e.method + " " + e.host + " " + e.path + " " + statusText).toLowerCase(Locale.ROOT);

            if (!q.isEmpty() && !rowText.contains(q)) continue;

            filteredIndexMap.add(i);
            tableModel.addRow(new Object[]{
                    i + 1,
                    e.method,
                    e.host,
                    e.path,
                    statusText
            });
        }
    }

    private String extractStatusFromResponse(burp.api.montoya.http.message.responses.HttpResponse resp) {
        try {
            String s = resp.toString();
            int firstLineEnd = s.indexOf("\r\n");
            if (firstLineEnd <= 0) firstLineEnd = s.indexOf("\n");
            String line = firstLineEnd > 0 ? s.substring(0, firstLineEnd) : s;

            String[] parts = line.split("\\s+");
            if (parts.length >= 2) return parts[1];
        } catch (Exception ignored) {}
        return "";
    }

    private void openEntryTab(int idx) {
        if (idx < 0 || idx >= allEntries.size()) return;
        ProxymanParser.ParsedEntry entry = allEntries.get(idx);

        String tabKey = "IDX:" + idx;
        for (int i = 0; i < requestTabs.getTabCount(); i++) {
            if (tabKey.equals(requestTabs.getToolTipTextAt(i))) {
                requestTabs.setSelectedIndex(i);
                return;
            }
        }

        RequestTab tab = new RequestTab(api, entry);

        String title = entry.method + " " + compactPath(entry.path);
        if (title.length() > 48) title = title.substring(0, 45) + "...";

        requestTabs.addTab(title, tab);
        int newIndex = requestTabs.getTabCount() - 1;
        requestTabs.setToolTipTextAt(newIndex, tabKey);

        requestTabs.setTabComponentAt(newIndex, createClosableTab(title));
        requestTabs.setSelectedIndex(newIndex);
    }

    private Component createClosableTab(String title) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);

        JLabel l = new JLabel(title);
        JButton x = new JButton("x");
        x.setMargin(new Insets(0, 6, 0, 6));
        x.setFocusable(false);

        p.add(l);
        p.add(x);

        x.addActionListener(e -> {
            int idx = requestTabs.indexOfTabComponent(p);
            if (idx >= 0) requestTabs.removeTabAt(idx);
        });

        return p;
    }

    private String compactPath(String path) {
        if (path == null || path.isEmpty()) return "/";
        int q = path.indexOf('?');
        if (q > 0) return path.substring(0, q);
        return path;
    }

    private void onSendAllToRepeater() {
        int sent = 0;
        for (int i = 0; i < allEntries.size(); i++) {
            ProxymanParser.ParsedEntry entry = allEntries.get(i);
            try {
                String tabName = String.format("Proxyman[%d] %s %s", i + 1, entry.host, entry.path);
                api.repeater().sendToRepeater(entry.request, tabName);
                sent++;
            } catch (Exception ex) {
                api.logging().logToError("Send ALL to Repeater failed at item " + (i + 1) + ": " + ex.getMessage());
            }
        }
        countLabel.setText(allEntries.size() + " requests • sent " + sent + " to Repeater");
    }

    private void onAddAllToSiteMap() {
        int added = 0;
        for (int i = 0; i < allEntries.size(); i++) {
            ProxymanParser.ParsedEntry entry = allEntries.get(i);
            try {
                HttpRequestResponse rr = HttpRequestResponse.httpRequestResponse(entry.request, entry.response);
                api.siteMap().add(rr);
                added++;
            } catch (Exception ex) {
                api.logging().logToError("Add ALL to Site Map failed at item " + (i + 1) + ": " + ex.getMessage());
            }
        }
        countLabel.setText(allEntries.size() + " requests • added " + added + " to Site Map");
    }
}
