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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

public class EnhancedSearchDialog extends DialogWrapper {
    private final SimpleIndexer indexer;
    private JTextField searchField;
    private JBList<CodeSearchResult> resultList;
    private JTextArea summaryArea;
    private JTextArea contextArea;
    private final Project project;
    private JLabel statusLabel;
    private String lastQuery = "";

    public EnhancedSearchDialog(Project project) {
        super(project);
        this.project = project;
        // Initialize the indexer with the project
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

        // Search panel at top
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        searchField.addActionListener(e -> performSearch());
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> performSearch());
        JButton reindexButton = new JButton("Reindex");
        reindexButton.addActionListener(e -> reindexProject());

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.add(searchPanel, BorderLayout.CENTER);
        topPanel.add(reindexButton, BorderLayout.EAST);

        // Main content panel (results + details)
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));

        // Results list on the left
        resultList = new JBList<>();
        resultList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
            ) {
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

        // Add selection listener to show summary
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CodeSearchResult selectedResult = resultList.getSelectedValue();
                if (selectedResult != null) {
                    summaryArea.setText(selectedResult.getSummary());

                    // Display metadata
                    StringBuilder metadataText = new StringBuilder();
                    for (String key : selectedResult.getMetadata().keySet()) {
                        String value = selectedResult.getMetadata().get(key);
                        if (value != null && !value.isEmpty()) {
                            metadataText.append(key).append(": ").append(value).append("\n");
                        }
                    }

                    // Add metadata to summary
                    if (metadataText.length() > 0) {
                        summaryArea.setText(selectedResult.getSummary() + "\n\n--- Metadata ---\n" + metadataText);
                    }
                } else {
                    summaryArea.setText("");
                }
            }
        });

        // Add double-click to open files
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

        // Details panel on the right
        JPanel detailsPanel = new JPanel(new BorderLayout(0, 10));

        // Summary area
        summaryArea = new JTextArea();
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setRows(8);
        JScrollPane summaryScrollPane = new JScrollPane(summaryArea);
        summaryScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "File Summary"));

        // Context area
        contextArea = new JTextArea();
        contextArea.setEditable(false);
        contextArea.setLineWrap(true);
        contextArea.setWrapStyleWord(true);
        contextArea.setRows(12);
        JScrollPane contextScrollPane = new JScrollPane(contextArea);
        contextScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Search Context"));

        detailsPanel.add(summaryScrollPane, BorderLayout.NORTH);
        detailsPanel.add(contextScrollPane, BorderLayout.CENTER);

        // Split panel for results and details
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resultsScrollPane, detailsPanel);
        splitPane.setResizeWeight(0.3);

        contentPanel.add(splitPane, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        // Status label at the bottom
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

    // In performSearch() method of EnhancedSearchDialog
    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            return;
        }

        lastQuery = query;

        // Set wait cursor
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
                    // Search for files
                    results = indexer.search(query, 20);

                    // Generate context if we have results
                    if (!results.isEmpty()) {
                        searchContext = indexer.generateSearchContext(query, results);
                    } else {
                        searchContext = "No matching files found for query: " + query;
                    }
                } catch (Exception e) {
                    results = Collections.emptyList();
                    searchContext = "Error during search: " + e.getMessage();
                    e.printStackTrace();
                }
            }

            @Override
            public void onSuccess() {
                resultList.setListData(results.toArray(new CodeSearchResult[0]));
                contextArea.setText(searchContext);
                statusLabel.setText("Found " + results.size() + " results out of " +
                        indexer.getDocumentCount() + " indexed files");

                // Reset cursor
                JRootPane rootPane = SwingUtilities.getRootPane(getContentPane());
                if (rootPane != null) {
                    rootPane.setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void onFinished() {
                // Reset cursor
                JRootPane rootPane = SwingUtilities.getRootPane(getContentPane());
                if (rootPane != null) {
                    rootPane.setCursor(Cursor.getDefaultCursor());
                }
            }
        });
    }


    private void reindexProject() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Reindexing Project") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // Clean up old index files
                CleanupService.cleanupIndexFiles(project.getBasePath());

                // Reindex
                indexer.reindexAll(project, indicator);

                // Update status on EDT
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Indexed " + indexer.getDocumentCount() + " files");

                    // Re-run the last search if available
                    if (!lastQuery.isEmpty()) {
                        searchField.setText(lastQuery);
                        performSearch();
                    }
                });
            }
        });
    }

}

