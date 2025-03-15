package dev.balakumar.codecompass;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "CodeMapperSettings", storages = @Storage("codeMapperSettings.xml"))
public class CodeMapperSettingsState implements PersistentStateComponent<CodeMapperSettingsState> {
    public String aiProvider = "OPENROUTER";
    public boolean enableStartupIndexing = false;

    @Nullable
    @Override
    public CodeMapperSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CodeMapperSettingsState state) {
        this.aiProvider = state.aiProvider;
        this.enableStartupIndexing = state.enableStartupIndexing;
    }

    public static CodeMapperSettingsState getInstance(Project project) {
        return ServiceManager.getService(project, CodeMapperSettingsState.class);
    }
}