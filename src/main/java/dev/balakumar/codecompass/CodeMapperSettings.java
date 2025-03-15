package dev.balakumar.codecompass;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CodeMapperSettings implements Configurable {
    private final Project project;
    private final CodeMapperSettingsState settings;
    private JComboBox<String> providerComboBox;
    private JBCheckBox startupIndexingCheckBox;

    public CodeMapperSettings(Project project) {
        this.project = project;
        this.settings = CodeMapperSettingsState.getInstance(project);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "CodeMapper";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        providerComboBox = new JComboBox<>(new String[]{"OLLAMA", "GEMINI", "OPENROUTER"});
        providerComboBox.setSelectedItem(settings.aiProvider);
        startupIndexingCheckBox = new JBCheckBox("Enable startup indexing", settings.enableStartupIndexing);
        FormBuilder builder = FormBuilder.createFormBuilder();
        builder.addLabeledComponent(new JBLabel("AI Provider:"), providerComboBox);
        builder.addComponent(startupIndexingCheckBox);
        return builder.getPanel();
    }

    @Override
    public boolean isModified() {
        return !providerComboBox.getSelectedItem().equals(settings.aiProvider) ||
                startupIndexingCheckBox.isSelected() != settings.enableStartupIndexing;
    }

    @Override
    public void apply() throws ConfigurationException {
        settings.aiProvider = (String) providerComboBox.getSelectedItem();
        settings.enableStartupIndexing = startupIndexingCheckBox.isSelected();
    }

    @Override
    public void reset() {
        providerComboBox.setSelectedItem(settings.aiProvider);
        startupIndexingCheckBox.setSelected(settings.enableStartupIndexing);
    }
}