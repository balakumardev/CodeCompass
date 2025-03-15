package dev.balakumar.codecompass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CodeQuestionDialog extends DialogWrapper {
    private final SimpleIndexer indexer;
    private JTextField questionField;
    private JEditorPane answerPane;
    private JBList<CodeSearchResult> relevantFilesList;
    private final Project project;
    private JLabel statusLabel;
    private List<CodeSearchResult> currentResults = Collections.emptyList();
    private JButton askButton;
    private JButton copyButton;
    private JComboBox<String> languageFilterComboBox;
    private JSpinner resultLimitSpinner;
    private Map<String, String> currentFilters = new HashMap<>();

    public CodeQuestionDialog(Project project) {
        super(project);
        this.project = project;
        this.indexer = new SimpleIndexer(project);
        setTitle("Ask Questions About Your Code");
        setSize(1000, 800);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setPreferredSize(new Dimension(1000, 800));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top panel with question field and buttons
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        questionField = new JTextField();
        questionField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateAskButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateAskButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateAskButtonState();
            }
        });
        topPanel.add(questionField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        askButton = new JButton("Ask");
        askButton.setEnabled(false);
        askButton.addActionListener(e -> askQuestion());
        buttonPanel.add(askButton);

        copyButton = new JButton("Copy Answer");
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyAnswerToClipboard());
        buttonPanel.add(copyButton);

        topPanel.add(buttonPanel, BorderLayout.EAST);

        // Filter panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterPanel.add(new JLabel("Language:"));

        languageFilterComboBox = new JComboBox<>();
        languageFilterComboBox.addItem("All Languages");

        // Populate language filter with available languages
        VectorDBService vectorDB = null;
        try {
            AIService aiService = ProviderSettings.getAIService(project);
            vectorDB = new VectorDBService(project.getBasePath(), aiService);
            List<String> languages = vectorDB.getUniqueLanguages();
            for (String language : languages) {
                languageFilterComboBox.addItem(language);
            }
        } catch (Exception e) {
            System.err.println("Error loading languages: " + e.getMessage());
        } finally {
            if (vectorDB != null) {
                vectorDB.close();
            }
        }

        languageFilterComboBox.addActionListener(e -> {
            String selectedLanguage = (String) languageFilterComboBox.getSelectedItem();
            if ("All Languages".equals(selectedLanguage)) {
                currentFilters.remove("language");
            } else {
                currentFilters.put("language", selectedLanguage);
            }
        });
        filterPanel.add(languageFilterComboBox);

        filterPanel.add(new JLabel("Max Results:"));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(10, 1, 50, 1);
        resultLimitSpinner = new JSpinner(spinnerModel);
        filterPanel.add(resultLimitSpinner);

        topPanel.add(filterPanel, BorderLayout.SOUTH);

        // Main content panel
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));

        // Left panel with relevant files
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Relevant Files"));

        relevantFilesList = new JBList<>();
        relevantFilesList.setCellRenderer(new DefaultListCellRenderer() {
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

        relevantFilesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    CodeSearchResult selectedResult = relevantFilesList.getSelectedValue();
                    if (selectedResult != null) {
                        openFile(selectedResult.getFilePath());
                    }
                }
            }
        });

        JBScrollPane filesScrollPane = new JBScrollPane(relevantFilesList);
        leftPanel.add(filesScrollPane, BorderLayout.CENTER);

        // Right panel with answer
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Answer"));

        answerPane = new JEditorPane();
        answerPane.setEditable(false);
        answerPane.setContentType("text/html");

        // Configure HTML styling
        HTMLEditorKit kit = new HTMLEditorKit();
        answerPane.setEditorKit(kit);
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: Arial, sans-serif; margin: 10px; }");
        styleSheet.addRule("pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; }");
        styleSheet.addRule("code { font-family: monospace; }");

        JBScrollPane answerScrollPane = new JBScrollPane(answerPane);
        rightPanel.add(answerScrollPane, BorderLayout.CENTER);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.3);
        contentPanel.add(splitPane, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready to answer questions. Indexed " + indexer.getDocumentCount() + " files.");
        panel.add(statusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void updateAskButtonState() {
        askButton.setEnabled(!questionField.getText().trim().isEmpty());
    }

    private void openFile(String filePath) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (file != null) {
            FileEditorManager.getInstance(project).openFile(file, true);
        }
    }

    private void askQuestion() {
        String question = questionField.getText().trim();
        if (question.isEmpty()) {
            return;
        }

        JRootPane rootPane = SwingUtilities.getRootPane(this.getContentPane());
        if (rootPane != null) {
            rootPane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        answerPane.setText("<html><body><p>Searching for relevant files and generating answer...</p></body></html>");
        copyButton.setEnabled(false);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Answering Question...") {
            private List<CodeSearchResult> results;
            private String answer;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Searching for relevant files...");

                try {
                    // Get result limit from spinner
                    int limit = (Integer) resultLimitSpinner.getValue();

                    // Search for relevant files with filters
                    results = indexer.search(question, limit, currentFilters);

                    if (!results.isEmpty()) {
                        indicator.setText("Generating answer...");
                        AIService aiService = ProviderSettings.getAIService(project);
                        answer = aiService.askQuestion(question, results);
                    } else {
                        answer = "No relevant files found in the codebase for your question. Try rephrasing or asking about a different topic.";
                    }
                } catch (Exception e) {
                    results = Collections.emptyList();
                    answer = "Error generating answer: " + e.getMessage();
                    e.printStackTrace();
                }
            }

            @Override
            public void onSuccess() {
                currentResults = results;
                relevantFilesList.setListData(results.toArray(new CodeSearchResult[0]));

                // Format answer with HTML
                String formattedAnswer = formatAnswerAsHtml(answer);
                answerPane.setText(formattedAnswer);
                answerPane.setCaretPosition(0);

                copyButton.setEnabled(true);
                statusLabel.setText("Found " + results.size() + " relevant files out of " + indexer.getDocumentCount() + " indexed files");

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

    private String formatAnswerAsHtml(String answer) {
        if (answer == null || answer.isEmpty()) {
            return "<html><body><p>No answer generated.</p></body></html>";
        }

        // Convert markdown-style code blocks to HTML
        StringBuilder html = new StringBuilder("<html><body>");

        // Process code blocks
        String[] lines = answer.split("\n");
        boolean inCodeBlock = false;
        StringBuilder codeBlock = new StringBuilder();
        String codeLanguage = "";

        for (String line : lines) {
            if (line.startsWith("```")) {
                if (inCodeBlock) {
                    // End of code block
                    html.append("<pre><code>");
                    html.append(escapeHtml(codeBlock.toString()));
                    html.append("</code></pre>");
                    codeBlock = new StringBuilder();
                    inCodeBlock = false;
                } else {
                    // Start of code block
                    inCodeBlock = true;
                    codeLanguage = line.length() > 3 ? line.substring(3).trim() : "";
                }
            } else if (inCodeBlock) {
                codeBlock.append(line).append("\n");
            } else {
                // Regular text - handle inline code
                String processedLine = line;

                // Replace inline code
                while (processedLine.contains("`")) {
                    int start = processedLine.indexOf("`");
                    int end = processedLine.indexOf("`", start + 1);

                    if (end > start) {
                        String before = processedLine.substring(0, start);
                        String code = processedLine.substring(start + 1, end);
                        String after = processedLine.substring(end + 1);

                        processedLine = before + "<code>" + escapeHtml(code) + "</code>" + after;
                    } else {
                        break;
                    }
                }

                // Add paragraph breaks
                if (processedLine.trim().isEmpty()) {
                    html.append("<br/><br/>");
                } else {
                    html.append("<p>").append(processedLine).append("</p>");
                }
            }
        }

        // Handle any remaining code block
        if (inCodeBlock && codeBlock.length() > 0) {
            html.append("<pre><code>");
            html.append(escapeHtml(codeBlock.toString()));
            html.append("</code></pre>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void copyAnswerToClipboard() {
        String plainText = answerPane.getText();

        // Strip HTML tags for plain text
        plainText = plainText.replaceAll("<[^>]*>", "");

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(plainText), null);
        statusLabel.setText("Answer copied to clipboard");
    }
}
