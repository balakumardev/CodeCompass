package dev.balakumar.codecompass;

import com.intellij.openapi.project.Project;

public class ProviderSettings {
    public static AIService getAIService(Project project) {
        CodeMapperSettingsState settings = CodeMapperSettingsState.getInstance(project);
        String provider = settings.aiProvider;
        switch (provider) {
            case "OLLAMA":
                return new OllamaService();
            case "GEMINI":
                return new GoogleGeminiService();
            case "OPENROUTER":
                return new OpenRouterService();
            default:
                return new OpenRouterService();
        }
    }
}