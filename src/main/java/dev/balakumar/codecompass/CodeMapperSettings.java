package dev.balakumar.codecompass;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class CodeMapperSettings implements Configurable {
    private final Project project;
    private final CodeMapperSettingsState settings;

    // General settings
    private JComboBox<String> providerComboBox;
    private JBCheckBox startupIndexingCheckBox;

    // API keys
    private JBTextField openRouterApiKeyField;
    private JBTextField geminiApiKeyField;
    private JBTextField ollamaEndpointField;

    // OpenRouter models
    private JBTextField openRouterEmbeddingModelField;
    private JBTextField openRouterGenerationModelField;

    // Gemini models
    private JBTextField geminiEmbeddingModelField;
    private JBTextField geminiGenerationModelField;

    // Ollama models
    private JBTextField ollamaEmbeddingModelField;
    private JBTextField ollamaGenerationModelField;

    public CodeMapperSettings(Project project) {
        this.project = project;
        this.settings = CodeMapperSettingsState.getInstance(project);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "CodeCompass";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JBTabbedPane tabbedPane = new JBTabbedPane();

        // General Settings Tab
        tabbedPane.add("General", createGeneralSettingsPanel());

        // Provider Settings Tab
        tabbedPane.add("Provider Settings", createProviderSettingsPanel());

        // Model Settings Tab
        tabbedPane.add("Model Settings", createModelSettingsPanel());

        return tabbedPane;
    }

    private JPanel createGeneralSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        providerComboBox = new JComboBox<>(new String[]{"OPENROUTER", "GEMINI", "OLLAMA"});
        providerComboBox.setSelectedItem(settings.aiProvider);

        startupIndexingCheckBox = new JBCheckBox("Enable startup indexing", settings.enableStartupIndexing);

        FormBuilder builder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("AI Provider:"), providerComboBox)
                .addComponent(startupIndexingCheckBox)
                .addComponentFillVertically(new JPanel(), 0);

        panel.add(builder.getPanel(), BorderLayout.NORTH);
        return panel;
    }

    private JPanel createProviderSettingsPanel() {
        // Create a main panel that will contain the scroll pane
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create the content panel with the settings
        JPanel contentPanel = new JPanel(new GridLayout(3, 1, 0, 10));
        contentPanel.setBorder(JBUI.Borders.empty(10));

        // OpenRouter API Key
        JPanel openRouterPanel = new JPanel(new BorderLayout());
        openRouterPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "OpenRouter",
                TitledBorder.LEFT, TitledBorder.TOP));

        openRouterApiKeyField = new JBTextField(settings.openRouterApiKey);

        FormBuilder openRouterBuilder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("API Key:"), openRouterApiKeyField)
                .addComponentFillVertically(new JPanel(), 0);

        openRouterPanel.add(openRouterBuilder.getPanel(), BorderLayout.NORTH);

        // Gemini API Key
        JPanel geminiPanel = new JPanel(new BorderLayout());
        geminiPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Google Gemini",
                TitledBorder.LEFT, TitledBorder.TOP));

        geminiApiKeyField = new JBTextField(settings.geminiApiKey);

        FormBuilder geminiBuilder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("API Key:"), geminiApiKeyField)
                .addComponentFillVertically(new JPanel(), 0);

        geminiPanel.add(geminiBuilder.getPanel(), BorderLayout.NORTH);

        // Ollama Endpoint
        JPanel ollamaPanel = new JPanel(new BorderLayout());
        ollamaPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Ollama",
                TitledBorder.LEFT, TitledBorder.TOP));

        ollamaEndpointField = new JBTextField(settings.ollamaEndpoint);

        FormBuilder ollamaBuilder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Endpoint URL:"), ollamaEndpointField)
                .addComponentFillVertically(new JPanel(), 0);

        ollamaPanel.add(ollamaBuilder.getPanel(), BorderLayout.NORTH);

        // Add all panels to the content panel
        contentPanel.add(openRouterPanel);
        contentPanel.add(geminiPanel);
        contentPanel.add(ollamaPanel);

        // Add the content panel to a scroll pane
        JBScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setBorder(null);

        // Add the scroll pane to the main panel
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createModelSettingsPanel() {
        // Create a main panel that will contain the scroll pane
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create the content panel with the settings
        JPanel contentPanel = new JPanel(new GridLayout(3, 1, 0, 10));
        contentPanel.setBorder(JBUI.Borders.empty(10));

        // OpenRouter Models
        JPanel openRouterPanel = new JPanel(new BorderLayout());
        openRouterPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "OpenRouter Models",
                TitledBorder.LEFT, TitledBorder.TOP));

        openRouterEmbeddingModelField = new JBTextField(settings.openRouterEmbeddingModel);
        openRouterGenerationModelField = new JBTextField(settings.openRouterGenerationModel);

        FormBuilder openRouterBuilder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Embedding Model:"), openRouterEmbeddingModelField)
                .addLabeledComponent(new JBLabel("Generation Model:"), openRouterGenerationModelField)
                .addComponentFillVertically(new JPanel(), 0);

        openRouterPanel.add(openRouterBuilder.getPanel(), BorderLayout.NORTH);

        // Gemini Models
        JPanel geminiPanel = new JPanel(new BorderLayout());
        geminiPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Google Gemini Models",
                TitledBorder.LEFT, TitledBorder.TOP));

        geminiEmbeddingModelField = new JBTextField(settings.geminiEmbeddingModel);
        geminiGenerationModelField = new JBTextField(settings.geminiGenerationModel);

        FormBuilder geminiBuilder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Embedding Model:"), geminiEmbeddingModelField)
                .addLabeledComponent(new JBLabel("Generation Model:"), geminiGenerationModelField)
                .addComponentFillVertically(new JPanel(), 0);

        geminiPanel.add(geminiBuilder.getPanel(), BorderLayout.NORTH);

        // Ollama Models
        JPanel ollamaPanel = new JPanel(new BorderLayout());
        ollamaPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Ollama Models",
                TitledBorder.LEFT, TitledBorder.TOP));

        ollamaEmbeddingModelField = new JBTextField(settings.ollamaEmbeddingModel);
        ollamaGenerationModelField = new JBTextField(settings.ollamaGenerationModel);

        FormBuilder ollamaBuilder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Embedding Model:"), ollamaEmbeddingModelField)
                .addLabeledComponent(new JBLabel("Generation Model:"), ollamaGenerationModelField)
                .addComponentFillVertically(new JPanel(), 0);

        ollamaPanel.add(ollamaBuilder.getPanel(), BorderLayout.NORTH);

        // Add all panels to the content panel
        contentPanel.add(openRouterPanel);
        contentPanel.add(geminiPanel);
        contentPanel.add(ollamaPanel);

        // Add the content panel to a scroll pane
        JBScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setBorder(null);

        // Add the scroll pane to the main panel
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        // General settings
        if (!providerComboBox.getSelectedItem().equals(settings.aiProvider)) return true;
        if (startupIndexingCheckBox.isSelected() != settings.enableStartupIndexing) return true;

        // API Keys
        if (!openRouterApiKeyField.getText().equals(settings.openRouterApiKey)) return true;
        if (!geminiApiKeyField.getText().equals(settings.geminiApiKey)) return true;
        if (!ollamaEndpointField.getText().equals(settings.ollamaEndpoint)) return true;

        // OpenRouter models
        if (!openRouterEmbeddingModelField.getText().equals(settings.openRouterEmbeddingModel)) return true;
        if (!openRouterGenerationModelField.getText().equals(settings.openRouterGenerationModel)) return true;

        // Gemini models
        if (!geminiEmbeddingModelField.getText().equals(settings.geminiEmbeddingModel)) return true;
        if (!geminiGenerationModelField.getText().equals(settings.geminiGenerationModel)) return true;

        // Ollama models
        if (!ollamaEmbeddingModelField.getText().equals(settings.ollamaEmbeddingModel)) return true;
        if (!ollamaGenerationModelField.getText().equals(settings.ollamaGenerationModel)) return true;

        return false;
    }

    @Override
    public void apply() throws ConfigurationException {
        // General settings
        settings.aiProvider = (String) providerComboBox.getSelectedItem();
        settings.enableStartupIndexing = startupIndexingCheckBox.isSelected();

        // API Keys
        settings.openRouterApiKey = openRouterApiKeyField.getText().trim();
        settings.geminiApiKey = geminiApiKeyField.getText().trim();
        settings.ollamaEndpoint = ollamaEndpointField.getText().trim();

        // OpenRouter models
        settings.openRouterEmbeddingModel = openRouterEmbeddingModelField.getText().trim();
        settings.openRouterGenerationModel = openRouterGenerationModelField.getText().trim();

        // Gemini models
        settings.geminiEmbeddingModel = geminiEmbeddingModelField.getText().trim();
        settings.geminiGenerationModel = geminiGenerationModelField.getText().trim();

        // Ollama models
        settings.ollamaEmbeddingModel = ollamaEmbeddingModelField.getText().trim();
        settings.ollamaGenerationModel = ollamaGenerationModelField.getText().trim();
    }

    @Override
    public void reset() {
        // General settings
        providerComboBox.setSelectedItem(settings.aiProvider);
        startupIndexingCheckBox.setSelected(settings.enableStartupIndexing);

        // API Keys
        openRouterApiKeyField.setText(settings.openRouterApiKey);
        geminiApiKeyField.setText(settings.geminiApiKey);
        ollamaEndpointField.setText(settings.ollamaEndpoint);

        // OpenRouter models
        openRouterEmbeddingModelField.setText(settings.openRouterEmbeddingModel);
        openRouterGenerationModelField.setText(settings.openRouterGenerationModel);

        // Gemini models
        geminiEmbeddingModelField.setText(settings.geminiEmbeddingModel);
        geminiGenerationModelField.setText(settings.geminiGenerationModel);

        // Ollama models
        ollamaEmbeddingModelField.setText(settings.ollamaEmbeddingModel);
        ollamaGenerationModelField.setText(settings.ollamaGenerationModel);
    }
}