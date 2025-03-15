package dev.balakumar.codecompass;

import java.util.Map;
import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenRouterService implements AIService {
    private static final String OPENROUTER_GENERATION_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    private static final String GENERATION_MODEL = "google/gemini-2.0-flash-exp:free";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(240, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final String openRouterApiKey = System.getProperty("openrouter.apiKey", "");
    private final GoogleGeminiService geminiService = new GoogleGeminiService();
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int RATE_LIMIT_RETRY_DELAY_MS = 5000;

    @Override
    public float[] getEmbedding(String text) throws IOException {
        int retries = 0;
        while (true) {
            try {
                return geminiService.getEmbedding(text);
            } catch (IOException e) {
                if (shouldRetry(e, retries)) {
                    retries++;
                    int delayMs = isRateLimitError(e) ? RATE_LIMIT_RETRY_DELAY_MS : RETRY_DELAY_MS;
                    System.out.println("Retrying embedding request after error: " + e.getMessage() + " (Attempt " + retries + " of " + MAX_RETRIES + ", waiting " + delayMs + "ms)");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Embedding request interrupted", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
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

        int retries = 0;
        while (true) {
            try {
                Request request = new Request.Builder()
                        .url(OPENROUTER_GENERATION_ENDPOINT)
                        .header("Authorization", "Bearer " + openRouterApiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        throw new IOException("OpenRouter generation API error " + response.code() + ": " + errorBody);
                    }

                    String contentType = response.header("Content-Type");
                    if (contentType == null || !contentType.contains("application/json")) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        throw new IOException("Unexpected response type: " + contentType + ". Body: " + errorBody);
                    }

                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    return jsonResponse.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                }
            } catch (IOException e) {
                if (shouldRetry(e, retries)) {
                    retries++;
                    int delayMs = isRateLimitError(e) ? RATE_LIMIT_RETRY_DELAY_MS : RETRY_DELAY_MS;
                    System.out.println("Retrying summary generation after error: " + e.getMessage() + " (Attempt " + retries + " of " + MAX_RETRIES + ", waiting " + delayMs + "ms)");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Summary generation interrupted", ie);
                    }
                } else {
                    throw new IOException("Failed to process OpenRouter response: " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public String generateCodeContext(String query, List<CodeSearchResult> results) throws IOException {
        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("model", GENERATION_MODEL);
        JsonArray messages = new JsonArray();

        // Create a more detailed prompt with code context
        StringBuilder prompt = new StringBuilder();
        prompt.append("Query: ").append(query).append("\n\n");

        if (!results.isEmpty()) {
            prompt.append("Here are the most relevant files:\n\n");

            for (int i = 0; i < Math.min(5, results.size()); i++) {
                CodeSearchResult result = results.get(i);
                prompt.append("File ").append(i+1).append(": ").append(result.getFilePath()).append("\n");
                prompt.append("Language: ").append(result.getLanguage()).append("\n");
                prompt.append("Summary: ").append(result.getSummary()).append("\n");
                prompt.append("Similarity Score: ").append(String.format("%.2f", result.getSimilarity())).append("\n");

                // Add key metadata if available
                if (result.getMetadata().containsKey("classes")) {
                    prompt.append("Classes: ").append(result.getMetadata().get("classes")).append("\n");
                }
                if (result.getMetadata().containsKey("functions")) {
                    prompt.append("Functions: ").append(result.getMetadata().get("functions")).append("\n");
                }
                prompt.append("\n");
            }

            prompt.append("Based on the query and these files, please explain which files are most relevant to the query and why. Focus on how these files relate to what the user is asking about.");
        } else {
            prompt.append("No relevant files found in the codebase. Please suggest what the user might be looking for and how to refine their search.");
        }

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt.toString());
        messages.add(message);
        jsonRequest.add("messages", messages);

        String jsonRequestString = gson.toJson(jsonRequest);

        int retries = 0;
        while (true) {
            try {
                Request request = new Request.Builder()
                        .url(OPENROUTER_GENERATION_ENDPOINT)
                        .header("Authorization", "Bearer " + openRouterApiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        throw new IOException("OpenRouter generation API error " + response.code() + ": " + errorBody);
                    }

                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    return jsonResponse.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                }
            } catch (IOException e) {
                if (shouldRetry(e, retries)) {
                    retries++;
                    int delayMs = isRateLimitError(e) ? RATE_LIMIT_RETRY_DELAY_MS : RETRY_DELAY_MS;
                    System.out.println("Retrying context generation after error: " + e.getMessage() + " (Attempt " + retries + " of " + MAX_RETRIES + ", waiting " + delayMs + "ms)");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Context generation interrupted", ie);
                    }
                } else {
                    throw new IOException("Failed to process OpenRouter response: " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public String askQuestion(String question, List<CodeSearchResult> relevantFiles) throws IOException {
        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("model", GENERATION_MODEL);
        JsonArray messages = new JsonArray();

        // System message to set context and expectations
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an AI assistant specialized in analyzing and explaining code. " +
                "Answer questions about the codebase using only the provided file contents. " +
                "If you can't answer based on the provided files, say so clearly. " +
                "Include code snippets in your explanations when relevant. " +
                "Format code blocks with triple backticks and specify the language.");
        messages.add(systemMessage);

        // Build context from relevant files
        StringBuilder context = new StringBuilder();
        context.append("I'll answer your question based on these files from the codebase:\n\n");

        // Include file contents for context
        for (int i = 0; i < Math.min(3, relevantFiles.size()); i++) {
            CodeSearchResult file = relevantFiles.get(i);
            context.append("FILE ").append(i+1).append(": ").append(file.getFilePath()).append("\n");
            context.append("```").append(file.getLanguage().toLowerCase()).append("\n");

            // Use content if available, otherwise use metadata
            if (file.getContent() != null && !file.getContent().isEmpty()) {
                // Truncate if too long
                String content = file.getContent();
                if (content.length() > 6000) {
                    content = content.substring(0, 6000) + "\n// ... [content truncated] ...";
                }
                context.append(content);
            } else {
                context.append("// Content not available. Summary: ").append(file.getSummary());

                // Add metadata as comments
                for (Map.Entry<String, String> entry : file.getMetadata().entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        context.append("\n// ").append(entry.getKey()).append(": ").append(entry.getValue());
                    }
                }
            }

            context.append("\n```\n\n");
        }

        // User message with question and context
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", "Question: " + question + "\n\n" + context.toString());
        messages.add(userMessage);

        jsonRequest.add("messages", messages);

        // Add generation parameters
        JsonObject parameters = new JsonObject();
        parameters.addProperty("temperature", 0.2);  // Lower temperature for more factual responses
        parameters.addProperty("max_tokens", 1500);  // Allow longer responses for detailed explanations
        jsonRequest.add("parameters", parameters);

        String jsonRequestString = gson.toJson(jsonRequest);

        int retries = 0;
        while (true) {
            try {
                Request request = new Request.Builder()
                        .url(OPENROUTER_GENERATION_ENDPOINT)
                        .header("Authorization", "Bearer " + openRouterApiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        throw new IOException("OpenRouter generation API error " + response.code() + ": " + errorBody);
                    }

                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    return jsonResponse.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                }
            } catch (IOException e) {
                if (shouldRetry(e, retries)) {
                    retries++;
                    int delayMs = isRateLimitError(e) ? RATE_LIMIT_RETRY_DELAY_MS : RETRY_DELAY_MS;
                    System.out.println("Retrying question answering after error: " + e.getMessage() + " (Attempt " + retries + " of " + MAX_RETRIES + ", waiting " + delayMs + "ms)");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Question answering interrupted", ie);
                    }
                } else {
                    throw new IOException("Failed to process OpenRouter response: " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public boolean testConnection() {
        return true;
//        int retries = 0;
//        while (retries < MAX_RETRIES) {
//            try {
//                Request request = new Request.Builder()
//                        .url(OPENROUTER_GENERATION_ENDPOINT)
//                        .header("Authorization", "Bearer " + openRouterApiKey)
//                        .post(RequestBody.create("", MediaType.get("application/json")))
//                        .build();
//
//                try (Response response = client.newCall(request).execute()) {
//                    // Even a 401/403 means the service is up, just not authorized
//                    return response.isSuccessful() || response.code() == 401 || response.code() == 403;
//                }
//            } catch (IOException e) {
//                System.err.println("OpenRouter connection test failed (attempt " + (retries + 1) +
//                        " of " + MAX_RETRIES + "): " + e.getMessage());
//                retries++;
//
//                try {
//                    Thread.sleep(RETRY_DELAY_MS);
//                } catch (InterruptedException ie) {
//                    Thread.currentThread().interrupt();
//                    return false;
//                }
//            }
//        }
//
//        return false;
    }

    private String getLanguageFromFileName(String fileName) {
        if (fileName.endsWith(".java")) return "Java";
        if (fileName.endsWith(".py")) return "Python";
        if (fileName.endsWith(".js")) return "JavaScript";
        return "Unknown";
    }

    private boolean shouldRetry(IOException e, int currentRetries) {
        if (currentRetries >= MAX_RETRIES) {
            return false;
        }
        String message = e.getMessage();
        if (message == null) {
            return true;
        }
        // Retry on network errors, timeouts, and rate limits
        return message.contains("timeout") ||
                message.contains("Connection") ||
                message.contains("connection") ||
                message.contains("reset") ||
                message.contains("closed") ||
                isRateLimitError(e);
    }

    private boolean isRateLimitError(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("429") ||
                message.contains("rate") ||
                message.contains("limit") ||
                message.contains("quota") ||
                message.contains("capacity") ||
                message.contains("Resource") ||
                message.contains("resource");
    }
}