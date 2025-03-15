package dev.balakumar.codecompass;

import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenRouterService implements AIService {
    private static final String OPENROUTER_GENERATION_ENDPOINT = "https://openrouter.ai/api/v1/generate";
    private static final String GENERATION_MODEL = "google/gemini-2.0-flash-exp:free";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(240, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final String openRouterApiKey = System.getProperty("openrouter.apiKey", "apikey");
    private final GoogleGeminiService geminiService = new GoogleGeminiService(); // For embeddings

    @Override
    public float[] getEmbedding(String text) throws IOException {
        // Use Gemini for embeddings
        return geminiService.getEmbedding(text);
    }

    @Override
    public String generateSummary(String codeContent, String fileName) throws IOException {
        String truncatedCode = codeContent.length() > 8000 ? codeContent.substring(0, 8000) + "..." : codeContent;
        String language = getLanguageFromFileName(fileName);
        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("model", GENERATION_MODEL);
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", "Generate a concise summary (max 3 sentences) of this " + language + " file. Include main classes, methods, and functionality:\n\n" + truncatedCode);
        messages.add(message);
        jsonRequest.add("messages", messages);
        String jsonRequestString = gson.toJson(jsonRequest);
        Request request = new Request.Builder()
                .url(OPENROUTER_GENERATION_ENDPOINT)
                .header("Authorization", "Bearer " + openRouterApiKey)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("OpenRouter generation API error " + response + ": " + errorBody);
            }
            String responseBody = response.body().string();
            System.out.println("OpenRouter raw response: " + responseBody); // Log the raw response

            String summary;
            try {
                // Try parsing as a JSON object with the expected structure
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                summary = jsonResponse.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            } catch (JsonSyntaxException e) {
                // Fallback: treat it as a plain string or primitive
                if (responseBody.startsWith("\"") && responseBody.endsWith("\"")) {
                    summary = responseBody.substring(1, responseBody.length() - 1); // Remove surrounding quotes
                } else {
                    summary = responseBody; // Use the raw response as-is
                }
            }
            if (summary.length() > 500) {
                summary = summary.substring(0, 497) + "...";
            }
            return summary;
        } catch (Exception e) {
            throw new IOException("Failed to process OpenRouter response: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateCodeContext(String query, List<CodeSearchResult> results) throws IOException {
        if (results.isEmpty()) {
            return "No matching files found for query: " + query;
        }
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Query: ").append(query).append("\n\n");
        contextBuilder.append("Top matching files:\n\n");
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            CodeSearchResult result = results.get(i);
            contextBuilder.append("File: ").append(result.getFilePath()).append("\n");
            contextBuilder.append("Summary: ").append(result.getSummary()).append("\n");
            contextBuilder.append("Similarity: ").append(String.format("%.2f", result.getSimilarity())).append("\n\n");
        }
        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("model", GENERATION_MODEL);
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", "Based on the query and matching files, provide a brief explanation of " +
                "which files are most relevant and why:\n\n" + contextBuilder.toString());
        messages.add(message);
        jsonRequest.add("messages", messages);
        String jsonRequestString = gson.toJson(jsonRequest);
        Request request = new Request.Builder()
                .url(OPENROUTER_GENERATION_ENDPOINT)
                .header("Authorization", "Bearer " + openRouterApiKey)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("OpenRouter generation API error " + response + ": " + errorBody);
            }
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            return jsonResponse.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse OpenRouter code context response: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean testConnection() {
        try {
            // Test OpenRouter connection
            JsonObject jsonRequest = new JsonObject();
            jsonRequest.addProperty("model", GENERATION_MODEL);
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", "test");
            messages.add(message);
            jsonRequest.add("messages", messages);
            String jsonRequestString = gson.toJson(jsonRequest);
            Request request = new Request.Builder()
                    .url(OPENROUTER_GENERATION_ENDPOINT)
                    .header("Authorization", "Bearer " + openRouterApiKey)
                    .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }
            }
            // Test Gemini connection
            return geminiService.testConnection();
        } catch (Exception e) {
            System.err.println("OpenRouter or Gemini connection test failed: " + e.getMessage());
            return false;
        }
    }

    private String getLanguageFromFileName(String fileName) {
        if (fileName.endsWith(".java")) return "Java";
        if (fileName.endsWith(".kt")) return "Kotlin";
        if (fileName.endsWith(".py")) return "Python";
        if (fileName.endsWith(".js")) return "JavaScript";
        if (fileName.endsWith(".ts")) return "TypeScript";
        if (fileName.endsWith(".c") || fileName.endsWith(".cpp") || fileName.endsWith(".h")) return "C/C++";
        if (fileName.endsWith(".cs")) return "C#";
        if (fileName.endsWith(".go")) return "Go";
        if (fileName.endsWith(".rs")) return "Rust";
        if (fileName.endsWith(".php")) return "PHP";
        if (fileName.endsWith(".rb")) return "Ruby";
        if (fileName.endsWith(".scala")) return "Scala";
        if (fileName.endsWith(".groovy")) return "Groovy";
        if (fileName.endsWith(".xml")) return "XML";
        if (fileName.endsWith(".json")) return "JSON";
        return "code";
    }
}