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
    private JComboBox<String> embeddingProviderComboBox;
    private JComboBox<String> generationProviderComboBox;
    private JBCheckBox startupIndexingCheckBox;

    // API keys
    private JBTextField openRouterApiKeyField;
    private JBTextField geminiApiKeyField;
    private JBTextField ollamaEndpointField;

    // Embedding models
    private JBTextField geminiEmbeddingModelField;
    private JBTextField ollamaEmbeddingModelField;

    // Generation models
    private JBTextField openRouterGenerationModelField;
    private JBTextField geminiGenerationModelField;
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
        tabbedPane.add("Providers", createProviderSettingsPanel());

        // API Keys Tab
        tabbedPane.add("API Keys", createApiKeysPanel());

        // Model Settings Tab
        tabbedPane.add("Models", createModelSettingsPanel());

        return tabbedPane;
    }

    private JPanel createGeneralSettingsPanel() {
        startupIndexingCheckBox = new JBCheckBox("Enable startup indexing", settings.enableStartupIndexing);
        FormBuilder builder = FormBuilder.createFormBuilder()
                .addComponent(startupIndexingCheckBox)
                .addComponentFillVertically(new JPanel(), 0);
        return builder.getPanel();
    }

    private JPanel createProviderSettingsPanel() {
        embeddingProviderComboBox = new JComboBox<>(new String[]{"GEMINI", "OLLAMA"});
        embeddingProviderComboBox.setSelectedItem(settings.embeddingProvider);

        generationProviderComboBox = new JComboBox<>(new String[]{"OPENROUTER", "GEMINI", "OLLAMA"});
        generationProviderComboBox.setSelectedItem(settings.generationProvider);

        FormBuilder builder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Embedding Provider:"), embeddingProviderComboBox)
                .addLabeledComponent(new JBLabel("Generation Provider:"), generationProviderComboBox)
                .addComponentFillVertically(new JPanel(), 0);
        return builder.getPanel();
    }

    private JPanel createApiKeysPanel() {
        openRouterApiKeyField = new JBTextField(settings.openRouterApiKey);
        geminiApiKeyField = new JBTextField(settings.geminiApiKey);
        ollamaEndpointField = new JBTextField(settings.ollamaEndpoint);

        FormBuilder builder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("OpenRouter API Key:"), openRouterApiKeyField)
                .addLabeledComponent(new JBLabel("Gemini API Key:"), geminiApiKeyField)
                .addLabeledComponent(new JBLabel("Ollama Endpoint:"), ollamaEndpointField)
                .addComponentFillVertically(new JPanel(), 0);
        return builder.getPanel();
    }

    private JPanel createModelSettingsPanel() {
        geminiEmbeddingModelField = new JBTextField(settings.geminiEmbeddingModel);
        ollamaEmbeddingModelField = new JBTextField(settings.ollamaEmbeddingModel);

        openRouterGenerationModelField = new JBTextField(settings.openRouterGenerationModel);
        geminiGenerationModelField = new JBTextField(settings.geminiGenerationModel);
        ollamaGenerationModelField = new JBTextField(settings.ollamaGenerationModel);

        FormBuilder builder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Embedding Models:"), new JLabel(""), true)
                .addLabeledComponent(new JBLabel("Gemini Embedding Model:"), geminiEmbeddingModelField)
                .addLabeledComponent(new JBLabel("Ollama Embedding Model:"), ollamaEmbeddingModelField)
                .addSeparator()
                .addLabeledComponent(new JBLabel("Generation Models:"), new JLabel(""), true)
                .addLabeledComponent(new JBLabel("OpenRouter Generation Model:"), openRouterGenerationModelField)
                .addLabeledComponent(new JBLabel("Gemini Generation Model:"), geminiGenerationModelField)
                .addLabeledComponent(new JBLabel("Ollama Generation Model:"), ollamaGenerationModelField)
                .addComponentFillVertically(new JPanel(), 0);
        return builder.getPanel();
    }

    @Override
    public void apply() throws ConfigurationException {
        settings.embeddingProvider = (String) embeddingProviderComboBox.getSelectedItem();
        settings.generationProvider = (String) generationProviderComboBox.getSelectedItem();
        settings.enableStartupIndexing = startupIndexingCheckBox.isSelected();

        settings.openRouterApiKey = openRouterApiKeyField.getText();
        settings.geminiApiKey = geminiApiKeyField.getText();
        settings.ollamaEndpoint = ollamaEndpointField.getText();

        settings.geminiEmbeddingModel = geminiEmbeddingModelField.getText();
        settings.ollamaEmbeddingModel = ollamaEmbeddingModelField.getText();

        settings.openRouterGenerationModel = openRouterGenerationModelField.getText();
        settings.geminiGenerationModel = geminiGenerationModelField.getText();
        settings.ollamaGenerationModel = ollamaGenerationModelField.getText();
    }

    @Override
    public void reset() {
        embeddingProviderComboBox.setSelectedItem(settings.embeddingProvider);
        generationProviderComboBox.setSelectedItem(settings.generationProvider);
        startupIndexingCheckBox.setSelected(settings.enableStartupIndexing);

        openRouterApiKeyField.setText(settings.openRouterApiKey);
        geminiApiKeyField.setText(settings.geminiApiKey);
        ollamaEndpointField.setText(settings.ollamaEndpoint);

        geminiEmbeddingModelField.setText(settings.geminiEmbeddingModel);
        ollamaEmbeddingModelField.setText(settings.ollamaEmbeddingModel);

        openRouterGenerationModelField.setText(settings.openRouterGenerationModel);
        geminiGenerationModelField.setText(settings.geminiGenerationModel);
        ollamaGenerationModelField.setText(settings.ollamaGenerationModel);
    }

    @Override
    public boolean isModified() {
        return !embeddingProviderComboBox.getSelectedItem().equals(settings.embeddingProvider) ||
                !generationProviderComboBox.getSelectedItem().equals(settings.generationProvider) ||
                startupIndexingCheckBox.isSelected() != settings.enableStartupIndexing ||
                !openRouterApiKeyField.getText().equals(settings.openRouterApiKey) ||
                !geminiApiKeyField.getText().equals(settings.geminiApiKey) ||
                !ollamaEndpointField.getText().equals(settings.ollamaEndpoint) ||
                !geminiEmbeddingModelField.getText().equals(settings.geminiEmbeddingModel) ||
                !ollamaEmbeddingModelField.getText().equals(settings.ollamaEmbeddingModel) ||
                !openRouterGenerationModelField.getText().equals(settings.openRouterGenerationModel) ||
                !geminiGenerationModelField.getText().equals(settings.geminiGenerationModel) ||
                !ollamaGenerationModelField.getText().equals(settings.ollamaGenerationModel);
    }

    @Override
    public void disposeUIResources() {
        // No resources to dispose
    }
}