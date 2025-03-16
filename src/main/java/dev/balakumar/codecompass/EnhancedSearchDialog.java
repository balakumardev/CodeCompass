package dev.balakumar.codecompass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import javax.swing.text.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

public class EnhancedSearchDialog extends DialogWrapper {
    private final SimpleIndexer indexer;
    private JTextField searchField;
    private JBList<CodeSearchResult> resultList;
    private JTextArea summaryArea;
    private JTextPane contextArea; // Changed from JTextArea to JTextPane to support styled document
    private final Project project;
    private JLabel statusLabel;
    private String lastQuery = "";
    private List<CodeSearchResult> currentResults = Collections.emptyList();
    private JButton copyButton;

    public EnhancedSearchDialog(Project project) {
        super(project);
        this.project = project;
        this.indexer = new SimpleIndexer(project);
        setTitle("AI Code Search");
        setSize(900, 700);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setPreferredSize(new Dimension(900, 700));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top panel with search field and buttons
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        searchField.addActionListener(e -> performSearch());
        topPanel.add(searchField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> performSearch());
        buttonPanel.add(searchButton);

        copyButton = new JButton("Copy Paths");
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyPathsToClipboard());
        buttonPanel.add(copyButton);

        JButton reindexButton = new JButton("Reindex");
        reindexButton.addActionListener(e -> reindexProject());
        buttonPanel.add(reindexButton);

        topPanel.add(buttonPanel, BorderLayout.EAST);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        resultList = new JBList<>();
        resultList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value instanceof CodeSearchResult) {
                    CodeSearchResult result = (CodeSearchResult) value;
                    String displayPath = getDisplayPath(result.getFilePath());
                    String text = String.format("%s (%.2f)", displayPath, result.getSimilarity());
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }

            private String getDisplayPath(String path) {
                String basePath = project.getBasePath();
                if (basePath != null && path.startsWith(basePath)) {
                    return path.substring(basePath.length() + 1);
                }
                return path;
            }
        });
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CodeSearchResult selectedResult = resultList.getSelectedValue();
                if (selectedResult != null) {
                    summaryArea.setText(selectedResult.getSummary());
                    StringBuilder metadataText = new StringBuilder();
                    for (String key : selectedResult.getMetadata().keySet()) {
                        String value = selectedResult.getMetadata().get(key);
                        if (value != null && !value.isEmpty()) {
                            metadataText.append(key).append(": ").append(value).append("\n");
                        }
                    }
                    if (metadataText.length() > 0) {
                        summaryArea.setText(selectedResult.getSummary() + "\n\n--- Metadata ---\n" + metadataText);
                    }
                } else {
                    summaryArea.setText("");
                }
            }
        });
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    CodeSearchResult selectedResult = resultList.getSelectedValue();
                    if (selectedResult != null) {
                        openFile(selectedResult.getFilePath());
                    }
                }
            }
        });
        JScrollPane resultsScrollPane = new JScrollPane(resultList);
        resultsScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Results"));
        resultsScrollPane.setPreferredSize(new Dimension(300, 400));

        JPanel detailsPanel = new JPanel(new BorderLayout(0, 10));
        summaryArea = new JTextArea();
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setRows(8);
        JScrollPane summaryScrollPane = new JScrollPane(summaryArea);
        summaryScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "File Summary"));

        contextArea = new JTextPane(); // Changed from JTextArea to JTextPane
        contextArea.setEditable(false);
        // JTextPane doesn't have setLineWrap and setWrapStyleWord methods
        // But it wraps text by default
        JScrollPane contextScrollPane = new JScrollPane(contextArea);
        contextScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Search Context"));

        detailsPanel.add(summaryScrollPane, BorderLayout.NORTH);
        detailsPanel.add(contextScrollPane, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resultsScrollPane, detailsPanel);
        splitPane.setResizeWeight(0.3);
        contentPanel.add(splitPane, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);
        statusLabel = new JLabel("Ready to search. Indexed " + indexer.getDocumentCount() + " files.");
        panel.add(statusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void openFile(String filePath) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (file != null) {
            FileEditorManager.getInstance(project).openFile(file, true);
        }
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            return;
        }

        lastQuery = query;
        JRootPane rootPane = SwingUtilities.getRootPane(this.getContentPane());
        if (rootPane != null) {
            rootPane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching...") {
            private List<CodeSearchResult> results;
            private String searchContext;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    results = indexer.search(query, 20);
                    if (!results.isEmpty()) {
                        searchContext = indexer.generateSearchContext(query, results);
                    } else {
                        searchContext = "No matching files found for query: " + query;
                    }
                } catch (Exception e) {
                    results = Collections.emptyList();
                    searchContext = "Error during search: " + e.getMessage();
                    e.printStackTrace();
                    ErrorHandler.handleApiException(project, "Search", e);
                }
            }

            @Override
            public void onSuccess() {
                currentResults = results;
                resultList.setListData(results.toArray(new CodeSearchResult[0]));
                copyButton.setEnabled(!results.isEmpty());
                // Use the displayFormattedContext method instead of setText
                displayFormattedContext(searchContext);
                statusLabel.setText("Found " + results.size() + " results out of " + indexer.getDocumentCount() + " indexed files");

                JRootPane rootPane = SwingUtilities.getRootPane(getContentPane());
                if (rootPane != null) {
                    rootPane.setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void onFinished() {
                JRootPane rootPane = SwingUtilities.getRootPane(getContentPane());
                if (rootPane != null) {
                    rootPane.setCursor(Cursor.getDefaultCursor());
                }
            }
        });
    }

    // Add this method to EnhancedSearchDialog.java
    // Fixed method to use JTextPane's getStyledDocument
    private void displayFormattedContext(String context) {
        // Create a styled document for the context area
        contextArea.setText("");
        StyledDocument doc = contextArea.getStyledDocument();

        // Define styles
        Style defaultStyle = contextArea.getStyle(StyleContext.DEFAULT_STYLE);

        Style codeStyle = contextArea.addStyle("code", defaultStyle);
        StyleConstants.setFontFamily(codeStyle, "Monospaced");
        StyleConstants.setBackground(codeStyle, new Color(240, 240, 240));

        Style fileStyle = contextArea.addStyle("file", defaultStyle);
        StyleConstants.setBold(fileStyle, true);
        StyleConstants.setForeground(fileStyle, new Color(0, 102, 204));

        Style highlightStyle = contextArea.addStyle("highlight", defaultStyle);
        StyleConstants.setBackground(highlightStyle, new Color(255, 255, 204));

        try {
            String[] lines = context.split("\n");
            boolean inCodeBlock = false;
            StringBuilder codeBlock = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];

                // Handle code blocks
                if (line.startsWith("```")) {
                    if (inCodeBlock) {
                        // End of code block
                        doc.insertString(doc.getLength(), codeBlock.toString(), codeStyle);
                        doc.insertString(doc.getLength(), "\n", defaultStyle);
                        codeBlock = new StringBuilder();
                        inCodeBlock = false;
                    } else {
                        // Start of code block
                        inCodeBlock = true;
                    }
                    continue;
                }

                if (inCodeBlock) {
                    codeBlock.append(line).append("\n");
                    continue;
                }

                // Highlight file paths
                if (line.matches(".*File \\d+:.*")) {
                    int colonIndex = line.indexOf(":");
                    if (colonIndex > 0) {
                        doc.insertString(doc.getLength(), line.substring(0, colonIndex + 1), defaultStyle);
                        doc.insertString(doc.getLength(), line.substring(colonIndex + 1), fileStyle);
                        doc.insertString(doc.getLength(), "\n", defaultStyle);
                        continue;
                    }
                }

                // Process inline code
                int lastIndex = 0;
                while (lastIndex < line.length()) {
                    int codeStart = line.indexOf('`', lastIndex);
                    if (codeStart == -1) {
                        // No more inline code, add the rest of the line
                        doc.insertString(doc.getLength(), line.substring(lastIndex), defaultStyle);
                        break;
                    }

                    // Add text before the backtick
                    doc.insertString(doc.getLength(), line.substring(lastIndex, codeStart), defaultStyle);

                    // Find the closing backtick
                    int codeEnd = line.indexOf('`', codeStart + 1);
                    if (codeEnd == -1) {
                        // No closing backtick, treat as normal text
                        doc.insertString(doc.getLength(), line.substring(codeStart), defaultStyle);
                        break;
                    }

                    // Add the code with style
                    doc.insertString(doc.getLength(), line.substring(codeStart + 1, codeEnd), codeStyle);
                    lastIndex = codeEnd + 1;
                }

                doc.insertString(doc.getLength(), "\n", defaultStyle);
            }

            // Handle any remaining code block
            if (inCodeBlock && codeBlock.length() > 0) {
                doc.insertString(doc.getLength(), codeBlock.toString(), codeStyle);
            }

        } catch (BadLocationException e) {
            e.printStackTrace();
            // Fallback to plain text if styling fails
            contextArea.setText(context);
        }
    }

    private void copyPathsToClipboard() {
        if (currentResults.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (CodeSearchResult result : currentResults) {
            sb.append(result.getFilePath()).append("\n");
        }
        String paths = sb.toString();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(paths), null);
        statusLabel.setText("Copied " + currentResults.size() + " file paths to clipboard");
    }

    private void reindexProject() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Reindexing Project") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                CleanupService.cleanupIndexFiles(project.getBasePath());
                indexer.reindexAll(project, indicator);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Indexed " + indexer.getDocumentCount() + " files");
                    if (!lastQuery.isEmpty()) {
                        searchField.setText(lastQuery);
                        performSearch();
                    }
                });
            }
        });
    }
}
