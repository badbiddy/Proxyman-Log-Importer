import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class RequestTab extends JPanel {
    private final MontoyaApi api;
    private final ProxymanParser.ParsedEntry entry;

    public RequestTab(MontoyaApi api, ProxymanParser.ParsedEntry entry) {
        super(new BorderLayout(10, 10));
        this.api = api;
        this.entry = entry;

        setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton sendToRepeater = new JButton("Send to Repeater");
        JButton addToSiteMap = new JButton("Add to Site Map");

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonBar.add(sendToRepeater);
        buttonBar.add(addToSiteMap);

        HttpRequestEditor requestEditor = api.userInterface().createHttpRequestEditor();
        HttpResponseEditor responseEditor = api.userInterface().createHttpResponseEditor();

        requestEditor.setRequest(entry.request);
        responseEditor.setResponse(entry.response);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                requestEditor.uiComponent(),
                responseEditor.uiComponent());
        split.setResizeWeight(0.5);

        add(buttonBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        try { api.userInterface().applyThemeToComponent(this); } catch (Exception ignored) {}

        sendToRepeater.addActionListener(e -> {
            try {
                String tabName = "Proxyman " + entry.host + " " + entry.method + " " + entry.path;
                api.repeater().sendToRepeater(entry.request, tabName);
            } catch (Exception ex) {
                api.logging().logToError("Send to Repeater failed: " + ex.getMessage());
            }
        });

        addToSiteMap.addActionListener(e -> {
            try {
                HttpRequestResponse rr = HttpRequestResponse.httpRequestResponse(entry.request, entry.response);
                api.siteMap().add(rr);
            } catch (Exception ex) {
                api.logging().logToError("Add to Site Map failed: " + ex.getMessage());
            }
        });
    }
}
