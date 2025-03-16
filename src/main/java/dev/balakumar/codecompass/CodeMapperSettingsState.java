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
    // Provider selections
    public String embeddingProvider = "GEMINI";
    public String generationProvider = "OPENROUTER";
    public boolean enableStartupIndexing = false;

    // API Keys
    public String openRouterApiKey = "";
    public String geminiApiKey = "";
    public String ollamaEndpoint = "http://localhost:11434";

    // Embedding Models
    public String geminiEmbeddingModel = "embedding-001";
    public String ollamaEmbeddingModel = "nomic-embed-text";

    // Generation Models
    public String openRouterGenerationModel = "google/gemini-2.0-flash-exp:free";
    public String geminiGenerationModel = "gemini-1.5-pro";
    public String ollamaGenerationModel = "codellama:7b-code";

    @Nullable
    @Override
    public CodeMapperSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CodeMapperSettingsState state) {
        this.embeddingProvider = state.embeddingProvider;
        this.generationProvider = state.generationProvider;
        this.enableStartupIndexing = state.enableStartupIndexing;
        // API Keys
        this.openRouterApiKey = state.openRouterApiKey;
        this.geminiApiKey = state.geminiApiKey;
        this.ollamaEndpoint = state.ollamaEndpoint;
        // Embedding Models
        this.geminiEmbeddingModel = state.geminiEmbeddingModel;
        this.ollamaEmbeddingModel = state.ollamaEmbeddingModel;
        // Generation Models
        this.openRouterGenerationModel = state.openRouterGenerationModel;
        this.geminiGenerationModel = state.geminiGenerationModel;
        this.ollamaGenerationModel = state.ollamaGenerationModel;
    }

    public static CodeMapperSettingsState getInstance(Project project) {
        return ServiceManager.getService(project, CodeMapperSettingsState.class);
    }
}