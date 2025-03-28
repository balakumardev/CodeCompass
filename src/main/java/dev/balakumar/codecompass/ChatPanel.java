package dev.balakumar.codecompass;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import javax.swing.Timer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatPanel extends SimpleToolWindowPanel {
    private final Project project;
    private SimpleIndexer indexer;
    private GenerationService aiService;
    private final JPanel messagesPanel;
    private final JTextPane inputArea;
    private final JScrollPane scrollPane;
    private final JPanel statusPanel;
    private final JLabel statusLabel;
    private final JComboBox<String> languageFilterComboBox;
    private final JSpinner resultLimitSpinner;
    private final List<ChatMessage> messageHistory = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private List<CodeSearchResult> currentResults = Collections.emptyList();
    private final Map<String, String> currentFilters = new HashMap<>();
    private final JButton sendButton;
    private final JButton clearButton;
    private final JButton retryButton;
    private final JButton copyButton;
    private final Color userMessageColor;
    private final Color aiMessageColor;
    private final Color codeBackgroundColor;
    private final Color linkColor;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final JPanel relevantFilesPanel;
    private final JPanel relevantFilesHeaderPanel;
    private final JPanel relevantFilesContentPanel;
    private final JLabel relevantFilesLabel;
    private final JButton expandCollapseButton;
    private boolean relevantFilesPanelExpanded = false;

    public ChatPanel(Project project) {
        super(true);
        this.project = project;
        // Don't initialize these in the constructor - will do it in background
        this.indexer = null;
        this.aiService = null;

        // Get colors from current theme
        EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
        userMessageColor = JBColor.namedColor("Plugins.lightSelectionBackground", new JBColor(new Color(232, 242, 254), new Color(45, 48, 51)));
        aiMessageColor = JBColor.background();
        codeBackgroundColor = JBColor.namedColor("Editor.backgroundColor", new JBColor(new Color(240, 240, 240), new Color(43, 43, 43)));
        linkColor = JBColor.namedColor("Link.activeForeground", JBColor.BLUE);

        // Main layout
        setLayout(new BorderLayout());

        // Messages panel
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBorder(JBUI.Borders.empty(10));

        // Scroll pane for messages
        scrollPane = new JBScrollPane(messagesPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Input area
        inputArea = new JTextPane();
        inputArea.setBorder(JBUI.Borders.empty(8));
        inputArea.setPreferredSize(new Dimension(-1, 80));
        inputArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateSendButton();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateSendButton();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateSendButton();
            }
        });

        // Add keyboard shortcut for sending
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (!isProcessing.get() && !inputArea.getText().trim().isEmpty()) {
                        e.consume();
                        sendMessage();
                    }
                }
            }
        });

        JBScrollPane inputScrollPane = new JBScrollPane(inputArea);
        inputScrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        sendButton = new JButton("Send", AllIcons.Actions.Execute);
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        clearButton = new JButton("Clear Chat", AllIcons.Actions.GC);
        clearButton.addActionListener(e -> clearChat());

        retryButton = new JButton("Retry", AllIcons.Actions.Refresh);
        retryButton.setEnabled(false);
        retryButton.addActionListener(e -> retryLastMessage());

        buttonsPanel.add(retryButton);
        buttonsPanel.add(clearButton);
        buttonsPanel.add(sendButton);

        // Input panel (combines input area and buttons)
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        inputPanel.add(buttonsPanel, BorderLayout.SOUTH);

        // Status panel with fixed layout to prevent overlapping
        statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(JBUI.Borders.empty(5));

        // Status label (left side)
        statusLabel = new JLabel("Initializing services...");
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;
        labelConstraints.weightx = 0.7;
        labelConstraints.fill = GridBagConstraints.HORIZONTAL;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = JBUI.insets(5, 10, 5, 10);
        statusPanel.add(statusLabel, labelConstraints);

        // Filter panel (right side)
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        // Language filter
        filterPanel.add(new JBLabel("Language:"));
        languageFilterComboBox = new ComboBox<>();
        languageFilterComboBox.setPreferredSize(new Dimension(120, languageFilterComboBox.getPreferredSize().height));
        languageFilterComboBox.addItem("All Languages");
        languageFilterComboBox.addActionListener(e -> {
            String selectedLanguage = (String) languageFilterComboBox.getSelectedItem();
            if ("All Languages".equals(selectedLanguage)) {
                currentFilters.remove("language");
            } else {
                currentFilters.put("language", selectedLanguage);
            }
        });
        filterPanel.add(languageFilterComboBox);

        // Results limit
        filterPanel.add(new JBLabel("Max Results:"));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(5, 1, 20, 1);
        resultLimitSpinner = new JSpinner(spinnerModel);
        resultLimitSpinner.setPreferredSize(new Dimension(60, resultLimitSpinner.getPreferredSize().height));
        filterPanel.add(resultLimitSpinner);

        // Add filter panel to status panel
        GridBagConstraints filterConstraints = new GridBagConstraints();
        filterConstraints.gridx = 1;
        filterConstraints.gridy = 0;
        filterConstraints.weightx = 0.3;
        filterConstraints.fill = GridBagConstraints.HORIZONTAL;
        filterConstraints.anchor = GridBagConstraints.EAST;
        statusPanel.add(filterPanel, filterConstraints);

        // Relevant files panel (collapsible at the bottom)
        relevantFilesPanel = new JPanel(new BorderLayout());
        relevantFilesPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // Header panel with expand/collapse button
        relevantFilesHeaderPanel = new JPanel(new BorderLayout());
        relevantFilesHeaderPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 0, 1, JBColor.border()),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        relevantFilesHeaderPanel.setBackground(JBColor.namedColor("Panel.background", JBColor.background()));

        relevantFilesLabel = new JLabel("Relevant Files");
        relevantFilesLabel.setFont(relevantFilesLabel.getFont().deriveFont(Font.BOLD));
        relevantFilesHeaderPanel.add(relevantFilesLabel, BorderLayout.WEST);

        JPanel headerButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        headerButtonsPanel.setOpaque(false);

        copyButton = new JButton("Copy Paths", AllIcons.Actions.Copy);
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyPathsToClipboard());
        headerButtonsPanel.add(copyButton);

        expandCollapseButton = new JButton("Show", AllIcons.Actions.MoveDown);
        expandCollapseButton.addActionListener(e -> toggleRelevantFilesPanel());
        headerButtonsPanel.add(expandCollapseButton);

        relevantFilesHeaderPanel.add(headerButtonsPanel, BorderLayout.EAST);

        // Content panel (initially hidden)
        relevantFilesContentPanel = new JPanel(new BorderLayout());
        relevantFilesContentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 1, 1, JBColor.border()),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        relevantFilesContentPanel.setVisible(false);

        // File list with custom renderer
        DefaultListModel<CodeSearchResult> fileListModel = new DefaultListModel<>();
        JList<CodeSearchResult> relevantFilesList = new JList<>(fileListModel);
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
        relevantFilesContentPanel.add(filesScrollPane, BorderLayout.CENTER);

        relevantFilesPanel.add(relevantFilesHeaderPanel, BorderLayout.NORTH);
        relevantFilesPanel.add(relevantFilesContentPanel, BorderLayout.CENTER);

        // Add welcome message
        addSystemMessage("Welcome to CodeCompass Chat! Initializing services...");

        // Main layout assembly
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(relevantFilesPanel, BorderLayout.SOUTH);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusPanel, BorderLayout.NORTH);
        bottomPanel.add(inputPanel, BorderLayout.CENTER);

        setContent(mainPanel);
        add(bottomPanel, BorderLayout.SOUTH);

        // Initialize services immediately to avoid getting stuck
        initializeServices();
    }

    private void initializeServices() {
        // Set the status to show we're initializing
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Initializing services (this may take a moment)...");
        });

        // Initialize with a more robust approach
        Thread initThread = new Thread(() -> {
            try {
                // First check if Qdrant is running before trying to create services
                boolean qdrantRunning = false;
                try {
                    // Use a simple HTTP client with shorter timeout for health check
                    OkHttpClient httpClient = new OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build();

                    Request request = new Request.Builder()
                            .url("http://localhost:6333/healthz")
                            .get()
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        qdrantRunning = response.isSuccessful();
                        if (qdrantRunning) {
                            SwingUtilities.invokeLater(() -> {
                                statusLabel.setText("Qdrant is running, initializing AI service...");
                            });
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Qdrant health check error: " + e.getMessage());
                    qdrantRunning = false;
                }

                if (!qdrantRunning) {
                    SwingUtilities.invokeLater(() -> {
                        addErrorMessage("⚠️ Vector database (Qdrant) is not running. Please make sure Qdrant is installed and running at http://localhost:6333");
                        statusLabel.setText("⚠️ Vector database unavailable. Chat will not work properly.");
                        initialized.set(true); // Allow UI interaction
                        updateSendButton();
                    });
                    return;
                }

                // Then try to create the AI service
                try {
                    aiService = ProviderSettings.getGenerationService(project);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("AI service initialized, checking connection...");
                    });
                } catch (Exception e) {
                    System.err.println("Error creating AI service: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        addErrorMessage("Failed to initialize AI service: " + e.getMessage());
                        statusLabel.setText("Error initializing AI service. Chat will not work properly.");
                        initialized.set(true); // Allow UI interaction
                        updateSendButton();
                    });
                    return;
                }

                // Check AI service connection
                boolean aiServiceConnected = false;
                try {
                    aiServiceConnected = aiService.testConnection();
                    boolean finalAiServiceConnected = aiServiceConnected;
                    SwingUtilities.invokeLater(() -> {
                        if (finalAiServiceConnected) {
                            statusLabel.setText("AI service connected, initializing indexer...");
                        } else {
                            statusLabel.setText("AI service connection failed, attempting to continue...");
                        }
                    });
                } catch (Exception e) {
                    System.err.println("AI service connection error: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("AI service connection error, attempting to continue...");
                    });
                }

                // Then try to create the indexer
                try {
                    indexer = new SimpleIndexer(project);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Indexer initialized successfully!");
                    });
                } catch (Exception e) {
                    System.err.println("Error creating indexer: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        addErrorMessage("Failed to initialize indexer: " + e.getMessage());
                        statusLabel.setText("Error initializing indexer. Chat will not work properly.");
                        initialized.set(true); // Allow UI interaction
                        updateSendButton();
                    });
                    return;
                }

                // Skip language fetching entirely
                // Just use default "All Languages" option that's already in the combo box

                // Final status update based on connection results
                final boolean finalAiServiceConnected = aiServiceConnected;
                SwingUtilities.invokeLater(() -> {
                    // Update UI based on service status
                    messagesPanel.removeAll();
                    addSystemMessage("Welcome to CodeCompass Chat! Ask questions about your codebase, and I'll help you understand it better.");

                    if (!finalAiServiceConnected) {
                        addErrorMessage("⚠️ AI service is unavailable. Please check your AI provider settings.");
                        statusLabel.setText("⚠️ AI service unavailable. Chat will not work properly.");
                    } else {
                        statusLabel.setText("Ready. Indexed " + indexer.getDocumentCount() + " files.");
                    }

                    initialized.set(true);
                    updateSendButton();
                });

            } catch (Exception e) {
                System.err.println("Error during initialization: " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    addErrorMessage("Error during initialization: " + e.getMessage());
                    statusLabel.setText("Initialization failed. Try restarting the plugin.");
                    initialized.set(true); // Set to true so UI is not blocked
                    updateSendButton();
                });
            }
        });

        initThread.setDaemon(true);
        initThread.start();

        // Add a timeout to prevent getting stuck, but make it longer
        Timer timeoutTimer = new Timer(30000, e -> {
            if (!initialized.get()) {
                SwingUtilities.invokeLater(() -> {
                    addErrorMessage("Initialization timed out after 30 seconds. This usually means Qdrant vector database is not running or is not accessible.");
                    statusLabel.setText("Initialization timed out. Make sure Qdrant is running at http://localhost:6333");
                    initialized.set(true); // Set to true so UI is not blocked
                    updateSendButton();
                });
            }
        });
        timeoutTimer.setRepeats(false);
        timeoutTimer.start();
    }

    // This method is called after the panel is added to the UI
    public void initializeInBackground() {
        // We're already initializing in the constructor, so just return
        if (initialized.get()) {
            return;
        }
    }

    private void toggleRelevantFilesPanel() {
        relevantFilesPanelExpanded = !relevantFilesPanelExpanded;
        relevantFilesContentPanel.setVisible(relevantFilesPanelExpanded);
        expandCollapseButton.setText(relevantFilesPanelExpanded ? "Hide" : "Show");
        expandCollapseButton.setIcon(relevantFilesPanelExpanded ? AllIcons.Actions.MoveUp : AllIcons.Actions.MoveDown);
        relevantFilesPanel.revalidate();
        relevantFilesPanel.repaint();
    }

    private void updateSendButton() {
        boolean enabled = initialized.get() && !isProcessing.get() && !inputArea.getText().trim().isEmpty();
        sendButton.setEnabled(enabled);
    }

    private void sendMessage() {
        if (!initialized.get() || isProcessing.get() || inputArea.getText().trim().isEmpty()) {
            return;
        }

        String userMessage = inputArea.getText().trim();
        addUserMessage(userMessage);
        inputArea.setText("");

        // Start the AI response process
        isProcessing.set(true);
        updateSendButton();
        retryButton.setEnabled(false);

        // Add a placeholder for the AI response
        JPanel aiMessagePanel = createMessagePanel("Thinking...", false, true);
        messagesPanel.add(aiMessagePanel);
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing Question...", false) {
            private String answer;
            private List<CodeSearchResult> results;
            private boolean success = false;
            private String errorMessage = null;
            private int retryCount = 0;
            private static final int MAX_RETRIES = 3;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);

                while (retryCount < MAX_RETRIES && !success) {
                    try {
                        // Get result limit from spinner
                        int limit = (Integer) resultLimitSpinner.getValue();

                        // Determine if this is a follow-up question
                        boolean isFollowUp = isFollowUpQuestion(userMessage, messageHistory);
                        List<CodeSearchResult> searchResults;

                        // If it's a follow-up and we have current results, use them first
                        if (isFollowUp && !currentResults.isEmpty()) {
                            indicator.setText("Using existing context for follow-up question...");
                            searchResults = currentResults;
                        } else {
                            // Otherwise, search for new relevant files
                            indicator.setText("Searching for relevant files...");
                            searchResults = indexer.search(userMessage, limit, currentFilters, 0.55f);
                        }

                        // If we didn't find anything and it's a follow-up, try a broader search
                        if (searchResults.isEmpty() && isFollowUp) {
                            indicator.setText("Broadening search for follow-up question...");
                            searchResults = indexer.search(userMessage, limit, currentFilters, 0.5f); // Lower threshold
                        }

                        results = searchResults;

                        if (!results.isEmpty()) {
                            indicator.setText("Generating answer...");

                            // Convert ChatMessage objects to Maps for the API
                            List<Map<String, Object>> historyMaps = new ArrayList<>();
                            for (ChatMessage msg : messageHistory) {
                                // Only include the last few messages
                                if (messageHistory.size() - messageHistory.indexOf(msg) <= 6) {
                                    Map<String, Object> msgMap = new HashMap<>();
                                    msgMap.put("message", msg.getMessage());
                                    msgMap.put("isUser", msg.isUserMessage());
                                    msgMap.put("timestamp", msg.getTimestamp());
                                    historyMaps.add(msgMap);
                                }
                            }

                            answer = aiService.askQuestionWithHistory(userMessage, results, historyMaps);
                            success = true;
                        } else {
                            answer = "I couldn't find any relevant files in the codebase for your question. Try rephrasing or asking about a different topic.";
                            success = true;
                        }
                    } catch (IOException e) {
                        retryCount++;
                        if (e.getMessage().contains("429") || e.getMessage().contains("resource") || e.getMessage().contains("limit") || e.getMessage().contains("quota")) {
                            // Rate limit or resource exhaustion
                            indicator.setText("Rate limited. Retrying in 5 seconds... (" + retryCount + "/" + MAX_RETRIES + ")");
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        } else if (retryCount >= MAX_RETRIES) {
                            errorMessage = "Error after " + MAX_RETRIES + " attempts: " + e.getMessage();
                            results = Collections.emptyList();
                        } else {
                            indicator.setText("Error occurred. Retrying... (" + retryCount + "/" + MAX_RETRIES + ")");
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }

                if (!success && errorMessage == null) {
                    errorMessage = "Failed to generate answer after " + MAX_RETRIES + " attempts.";
                }
            }

            @Override
            public void onSuccess() {
                // Remove the placeholder message
                messagesPanel.remove(aiMessagePanel);

                if (success) {
                    addAIMessage(answer);

                    // Update relevant files list only if we have results
                    if (!results.isEmpty()) {
                        currentResults = results;
                        updateRelevantFilesList(results);
                    }

                    // Update status
                    statusLabel.setText("Found " + results.size() + " relevant files out of " + indexer.getDocumentCount() + " indexed files");

                    // Show relevant files if we have results
                    if (!results.isEmpty() && !relevantFilesPanelExpanded) {
                        toggleRelevantFilesPanel();
                    }
                } else {
                    addErrorMessage(errorMessage != null ? errorMessage : "An unknown error occurred while processing your question.");
                }

                isProcessing.set(false);
                updateSendButton();
                retryButton.setEnabled(true);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                // Remove the placeholder message
                messagesPanel.remove(aiMessagePanel);
                addErrorMessage("Error: " + error.getMessage());
                isProcessing.set(false);
                updateSendButton();
                retryButton.setEnabled(true);
            }
        });
    }

    /**
     * Determines if the current question is likely a follow-up to previous conversation
     */
    private boolean isFollowUpQuestion(String question, List<ChatMessage> history) {
        if (history.size() < 2) {
            return false; // Need at least one previous exchange
        }

        // Check for pronouns and other follow-up indicators
        String lowercaseQuestion = question.toLowerCase();
        String[] followUpIndicators = {
                "it", "this", "that", "they", "them", "these", "those", "he", "she", "his", "her", "their", "its",
                "what about", "how about", "and", "also", "too", "can you", "could you", "would you", "will you"
        };

        for (String indicator : followUpIndicators) {
            if (lowercaseQuestion.contains(indicator)) {
                return true;
            }
        }

        // Check if it's a very short question (likely follow-up)
        if (question.split("\\s+").length <= 5) {
            return true;
        }

        // Check if it doesn't contain any code-related keywords (less likely to be a new topic)
        String[] codeKeywords = {
                "class", "method", "function", "file", "code", "implement", "java", "python", "javascript",
                "typescript", "c++", "c#", "go", "rust"
        };

        boolean hasCodeKeyword = false;
        for (String keyword : codeKeywords) {
            if (lowercaseQuestion.contains(keyword)) {
                hasCodeKeyword = true;
                break;
            }
        }

        return !hasCodeKeyword;
    }

    private void updateRelevantFilesList(List<CodeSearchResult> results) {
        // Update the file list model
        DefaultListModel<CodeSearchResult> model = new DefaultListModel<>();
        for (CodeSearchResult result : results) {
            model.addElement(result);
        }

        // Find the JList in the relevantFilesContentPanel
        for (Component comp : relevantFilesContentPanel.getComponents()) {
            if (comp instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) comp;
                if (scrollPane.getViewport().getView() instanceof JList) {
                    JList<CodeSearchResult> list = (JList<CodeSearchResult>) scrollPane.getViewport().getView();
                    list.setModel(model);
                }
            }
        }

        // Update the label to show count
        relevantFilesLabel.setText("Relevant Files (" + results.size() + ")");

        // Enable/disable copy button
        copyButton.setEnabled(!results.isEmpty());
    }

    private void retryLastMessage() {
        if (!initialized.get() || isProcessing.get() || messageHistory.isEmpty()) {
            return;
        }

        // Find the last user message
        String lastUserMessage = null;
        for (int i = messageHistory.size() - 1; i >= 0; i--) {
            ChatMessage message = messageHistory.get(i);
            if (message.isUserMessage()) {
                lastUserMessage = message.getMessage();
                break;
            }
        }

        if (lastUserMessage != null) {
            inputArea.setText(lastUserMessage);
            sendMessage();
        }
    }

    private void clearChat() {
        messageHistory.clear();
        messagesPanel.removeAll();
        messagesPanel.revalidate();
        messagesPanel.repaint();

        // Add welcome message again
        addSystemMessage("Welcome to CodeCompass Chat! Ask questions about your codebase, and I'll help you understand it better.");

        // Clear relevant files
        currentResults = Collections.emptyList();
        updateRelevantFilesList(Collections.emptyList());

        // Hide relevant files panel
        if (relevantFilesPanelExpanded) {
            toggleRelevantFilesPanel();
        }

        retryButton.setEnabled(false);
    }

    private void addUserMessage(String message) {
        JPanel messagePanel = createMessagePanel(message, true, false);
        messagesPanel.add(messagePanel);
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();

        // Add to history
        messageHistory.add(new ChatMessage(message, true, LocalDateTime.now()));
    }

    private void addAIMessage(String message) {
        JPanel messagePanel = createMessagePanel(message, false, false);
        messagesPanel.add(messagePanel);
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();

        // Add to history
        messageHistory.add(new ChatMessage(message, false, LocalDateTime.now()));
    }

    private void addSystemMessage(String message) {
        JPanel messagePanel = createSystemMessagePanel(message);
        messagesPanel.add(messagePanel);
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    private void addErrorMessage(String message) {
        JPanel messagePanel = createErrorMessagePanel(message);
        messagesPanel.add(messagePanel);
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    private JPanel createMessagePanel(String message, boolean isUserMessage, boolean isPlaceholder) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 0, 5, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JBColor.border(), 1),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12)
                )
        ));

        // Set background color based on message type
        panel.setBackground(isUserMessage ? userMessageColor : aiMessageColor);

        // Create header with timestamp and user/ai indicator
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel authorLabel = new JLabel(isUserMessage ? "You" : "CodeCompass AI");
        authorLabel.setFont(authorLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(authorLabel, BorderLayout.WEST);

        if (!isPlaceholder) {
            JLabel timeLabel = new JLabel(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 10));
            headerPanel.add(timeLabel, BorderLayout.EAST);
        }

        // Create content
        JComponent contentComponent;
        if (isUserMessage || isPlaceholder) {
            JLabel contentLabel = new JLabel("<html><div style='width: 500px;'>" + message.replace("\n", "<br/>") + "</div></html>");
            contentComponent = contentLabel;
        } else {
            contentComponent = createFormattedTextPane(message);
        }

        // Add copy button for AI messages
        if (!isUserMessage && !isPlaceholder) {
            JPanel messageWithButtonsPanel = new JPanel(new BorderLayout());
            messageWithButtonsPanel.setOpaque(false);
            messageWithButtonsPanel.add(contentComponent, BorderLayout.CENTER);

            JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonsPanel.setOpaque(false);
            JButton copyMessageButton = new JButton(AllIcons.Actions.Copy);
            copyMessageButton.setToolTipText("Copy message");
            copyMessageButton.setBorderPainted(false);
            copyMessageButton.setContentAreaFilled(false);
            copyMessageButton.addActionListener(e -> {
                StringSelection selection = new StringSelection(message);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            });
            buttonsPanel.add(copyMessageButton);
            messageWithButtonsPanel.add(buttonsPanel, BorderLayout.SOUTH);
            panel.add(messageWithButtonsPanel, BorderLayout.CENTER);
        } else {
            panel.add(contentComponent, BorderLayout.CENTER);
        }

        panel.add(headerPanel, BorderLayout.NORTH);

        // Make sure the panel doesn't stretch horizontally
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private JPanel createSystemMessagePanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 0, 5, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JBColor.border(), 1),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12)
                )
        ));

        // Use a different color for system messages
        panel.setBackground(new JBColor(new Color(245, 245, 245), new Color(50, 50, 50)));
        JLabel contentLabel = new JLabel("<html><div style='width: 500px;'>" + message.replace("\n", "<br/>") + "</div></html>");
        contentLabel.setForeground(JBColor.namedColor("Notification.foreground", JBColor.foreground()));
        panel.add(contentLabel, BorderLayout.CENTER);

        // Make sure the panel doesn't stretch horizontally
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private JPanel createErrorMessagePanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 0, 5, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JBColor.RED, 1),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12)
                )
        ));

        // Use a different color for error messages
        panel.setBackground(new JBColor(new Color(255, 240, 240), new Color(70, 40, 40)));
        JLabel contentLabel = new JLabel("<html><div style='width: 500px;'>" + message.replace("\n", "<br/>") + "</div></html>");
        contentLabel.setForeground(JBColor.RED);
        panel.add(contentLabel, BorderLayout.CENTER);

        // Make sure the panel doesn't stretch horizontally
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private JTextPane createFormattedTextPane(String message) {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBorder(BorderFactory.createEmptyBorder());
        textPane.setBackground(null);
        textPane.setOpaque(false);

        // Define styles
        StyledDocument doc = textPane.getStyledDocument();
        Style defaultStyle = textPane.getStyle(StyleContext.DEFAULT_STYLE);

        // Code block style
        Style codeStyle = textPane.addStyle("code", defaultStyle);
        StyleConstants.setFontFamily(codeStyle, "Monospaced");
        StyleConstants.setBackground(codeStyle, codeBackgroundColor);

        // Link style
        Style linkStyle = textPane.addStyle("link", defaultStyle);
        StyleConstants.setForeground(linkStyle, linkColor);
        StyleConstants.setUnderline(linkStyle, true);

        // Header styles
        Style h1Style = textPane.addStyle("h1", defaultStyle);
        StyleConstants.setFontSize(h1Style, StyleConstants.getFontSize(defaultStyle) + 6);
        StyleConstants.setBold(h1Style, true);

        Style h2Style = textPane.addStyle("h2", defaultStyle);
        StyleConstants.setFontSize(h2Style, StyleConstants.getFontSize(defaultStyle) + 4);
        StyleConstants.setBold(h2Style, true);

        Style h3Style = textPane.addStyle("h3", defaultStyle);
        StyleConstants.setFontSize(h3Style, StyleConstants.getFontSize(defaultStyle) + 2);
        StyleConstants.setBold(h3Style, true);

        // Bold and italic styles
        Style boldStyle = textPane.addStyle("bold", defaultStyle);
        StyleConstants.setBold(boldStyle, true);

        Style italicStyle = textPane.addStyle("italic", defaultStyle);
        StyleConstants.setItalic(italicStyle, true);

        try {
            // Process markdown
            String[] lines = message.split("\n");
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

                // Handle headers
                if (line.startsWith("# ")) {
                    doc.insertString(doc.getLength(), line.substring(2), h1Style);
                    doc.insertString(doc.getLength(), "\n", defaultStyle);
                    continue;
                } else if (line.startsWith("## ")) {
                    doc.insertString(doc.getLength(), line.substring(3), h2Style);
                    doc.insertString(doc.getLength(), "\n", defaultStyle);
                    continue;
                } else if (line.startsWith("### ")) {
                    doc.insertString(doc.getLength(), line.substring(4), h3Style);
                    doc.insertString(doc.getLength(), "\n", defaultStyle);
                    continue;
                }

                // Process inline formatting
                int lastIndex = 0;
                StringBuilder formattedLine = new StringBuilder();

                // Process inline code
                while (lastIndex < line.length()) {
                    int codeStart = line.indexOf('`', lastIndex);
                    if (codeStart == -1) {
                        // No more inline code, add the rest of the line
                        formattedLine.append(line.substring(lastIndex));
                        break;
                    }

                    // Add text before the backtick
                    formattedLine.append(line.substring(lastIndex, codeStart));

                    // Find the closing backtick
                    int codeEnd = line.indexOf('`', codeStart + 1);
                    if (codeEnd == -1) {
                        // No closing backtick, treat as normal text
                        formattedLine.append(line.substring(codeStart));
                        break;
                    }

                    // Add the code with style
                    String code = line.substring(codeStart + 1, codeEnd);
                    doc.insertString(doc.getLength(), formattedLine.toString(), defaultStyle);
                    doc.insertString(doc.getLength(), code, codeStyle);

                    formattedLine = new StringBuilder();
                    lastIndex = codeEnd + 1;
                }

                // Add any remaining text
                if (formattedLine.length() > 0) {
                    doc.insertString(doc.getLength(), formattedLine.toString(), defaultStyle);
                }

                // Process bold and italic in the remaining text
                String remainingText = line.substring(lastIndex);
                processBoldAndItalic(doc, remainingText, defaultStyle, boldStyle, italicStyle);

                doc.insertString(doc.getLength(), "\n", defaultStyle);
            }

            // Handle any remaining code block
            if (inCodeBlock && codeBlock.length() > 0) {
                doc.insertString(doc.getLength(), codeBlock.toString(), codeStyle);
            }

            // Add file path links
            addFilePathLinks(textPane, project);

        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        return textPane;
    }

    // Helper method to process bold and italic text
    private void processBoldAndItalic(StyledDocument doc, String text, Style defaultStyle,
                                      Style boldStyle, Style italicStyle) throws BadLocationException {
        int lastIndex = 0;

        // Process bold text
        while (lastIndex < text.length()) {
            int boldStart = text.indexOf("**", lastIndex);
            if (boldStart == -1) {
                // No more bold, add the rest of the text
                doc.insertString(doc.getLength(), text.substring(lastIndex), defaultStyle);
                break;
            }

            // Add text before the bold marker
            doc.insertString(doc.getLength(), text.substring(lastIndex, boldStart), defaultStyle);

            // Find the closing bold marker
            int boldEnd = text.indexOf("**", boldStart + 2);
            if (boldEnd == -1) {
                // No closing marker, treat as normal text
                doc.insertString(doc.getLength(), text.substring(boldStart), defaultStyle);
                break;
            }

            // Add the bold text
            String boldText = text.substring(boldStart + 2, boldEnd);
            doc.insertString(doc.getLength(), boldText, boldStyle);

            lastIndex = boldEnd + 2;
        }

        // Similar process for italic text could be added here
    }

    private void addFilePathLinks(JTextPane textPane, Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        StyledDocument doc = textPane.getStyledDocument();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }

        // Find potential file paths
        String[] lines = text.split("\n");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];

            // Look for common file extensions
            for (String ext : new String[]{".java", ".kt", ".py", ".js", ".ts", ".c", ".cpp", ".h", ".cs", ".go"}) {
                int index = 0;
                while ((index = line.indexOf(ext, index)) >= 0) {
                    // Try to find the start of the path
                    int startIndex = index;
                    while (startIndex > 0 && !Character.isWhitespace(line.charAt(startIndex - 1))) {
                        startIndex--;
                    }

                    // Extract the potential path
                    String potentialPath = line.substring(startIndex, index + ext.length());

                    // Check if this file exists in the project
                    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(basePath + "/" + potentialPath);
                    if (file == null) {
                        file = LocalFileSystem.getInstance().findFileByPath(potentialPath);
                    }

                    if (file != null && file.exists()) {
                        // This is a valid file path, make it a link
                        try {
                            int globalStartIndex = 0;
                            for (int i = 0; i < lineIndex; i++) {
                                globalStartIndex += lines[i].length() + 1; // +1 for the newline
                            }
                            globalStartIndex += startIndex;
                            int globalEndIndex = globalStartIndex + potentialPath.length();

                            // Create a link style
                            Style linkStyle = textPane.addStyle("link", null);
                            StyleConstants.setForeground(linkStyle, linkColor);
                            StyleConstants.setUnderline(linkStyle, true);

                            // Store the file path as an attribute
                            linkStyle.addAttribute("filePath", file.getPath());

                            // Apply the style
                            doc.setCharacterAttributes(globalStartIndex, potentialPath.length(), linkStyle, false);
                        } catch (Exception e) {
                            // Ignore errors in link creation
                        }
                    }
                    index = index + ext.length();
                }
            }
        }

        // Add mouse listener for the links
        textPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int offset = textPane.viewToModel2D(e.getPoint());
                if (offset >= 0) {
                    AttributeSet attrs = textPane.getStyledDocument().getCharacterElement(offset).getAttributes();
                    Object filePath = attrs.getAttribute("filePath");
                    if (filePath != null) {
                        openFile(filePath.toString());
                    }
                }
            }
        });

        // Change cursor when hovering over links
        textPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int offset = textPane.viewToModel2D(e.getPoint());
                if (offset >= 0) {
                    AttributeSet attrs = textPane.getStyledDocument().getCharacterElement(offset).getAttributes();
                    if (attrs.getAttribute("filePath") != null) {
                        textPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    } else {
                        textPane.setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        });
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void openFile(String filePath) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (file != null) {
            FileEditorManager.getInstance(project).openFile(file, true);
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

        StringSelection selection = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        statusLabel.setText("Copied " + currentResults.size() + " file paths to clipboard");
    }

    private static class ChatMessage {
        private final String message;
        private final boolean userMessage;
        private final LocalDateTime timestamp;

        public ChatMessage(String message, boolean userMessage, LocalDateTime timestamp) {
            this.message = message;
            this.userMessage = userMessage;
            this.timestamp = timestamp;
        }

        public String getMessage() {
            return message;
        }

        public boolean isUserMessage() {
            return userMessage;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}