package dev.balakumar.codecompass;

import com.intellij.openapi.project.Project;

public class ProviderSettings {
    public static EmbeddingService getEmbeddingService(Project project) {
        CodeMapperSettingsState settings = CodeMapperSettingsState.getInstance(project);
        String provider = settings.embeddingProvider;
        switch (provider) {
            case "GEMINI":
                return new GoogleGeminiService(project);
            case "OLLAMA":
                return new OllamaService(project);
            default:
                throw new IllegalStateException("Invalid embedding provider: " + provider);
        }
    }

    public static GenerationService getGenerationService(Project project) {
        CodeMapperSettingsState settings = CodeMapperSettingsState.getInstance(project);
        String provider = settings.generationProvider;
        switch (provider) {
            case "OPENROUTER":
                return new OpenRouterService(project);
            case "GEMINI":
                return new GoogleGeminiService(project);
            case "OLLAMA":
                return new OllamaService(project);
            default:
                throw new IllegalStateException("Invalid generation provider: " + provider);
        }
    }
}