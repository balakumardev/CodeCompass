package dev.balakumar.codecompass;

public class ProviderSettings {
    public static AIService getAIService() {
        // Read the provider from the system property "ai.provider" (defaults to OLLAMA)
        String provider = System.getProperty("ai.provider", "GEMINI");
        if (provider.equalsIgnoreCase("GEMINI")) {
            return new GoogleGeminiService();
        }
        return new OllamaService();
    }
}