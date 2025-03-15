package dev.balakumar.codecompass;

import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GoogleGeminiService implements AIService {
    // Updated endpoints with correct model names
    private static final String GEMINI_EMBEDDING_ENDPOINT = "https://generativelanguage.googleapis.com/v1/models/embedding-001:embedContent";
    private static final String GEMINI_GENERATION_ENDPOINT = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro:generateContent";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(240, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private int embeddingDimension = 768; // Default value (will update based on response)
    // API key (set via -DgoogleGemini.apiKey=YOUR_API_KEY)
    private final String apiKey = System.getProperty("googleGemini.apiKey", "");

    @Override
    public float[] getEmbedding(String text) throws IOException {
        String truncatedText = text.length() > 4000 ? text.substring(0, 4000) : text;
        // Build request JSON payload for embeddings with correct structure
        JsonObject requestPayload = new JsonObject();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", truncatedText);
        parts.add(part);
        content.add("parts", parts);
        requestPayload.add("content", content);

        String jsonRequestString = gson.toJson(requestPayload);
        Request request = new Request.Builder()
                .url(GEMINI_EMBEDDING_ENDPOINT + "?key=" + apiKey)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Gemini embedding API error " + response + ": " + errorBody);
            }
            String responseBody = response.body().string();
            System.out.println("Embedding response: " + responseBody); // Debug output
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            // Extract embedding from the correct response structure
            // The response has a structure like {"embedding": {"values": [...]}}
            JsonObject embeddingObject = jsonResponse.getAsJsonObject("embedding");
            if (embeddingObject == null) {
                throw new IOException("No embedding object found in Gemini response: " + jsonResponse);
            }
            JsonArray embeddingValues = embeddingObject.getAsJsonArray("values");
            if (embeddingValues == null) {
                throw new IOException("No embedding values found in Gemini response: " + jsonResponse);
            }

            float[] embedding = new float[embeddingValues.size()];
            for (int i = 0; i < embeddingValues.size(); i++) {
                embedding[i] = embeddingValues.get(i).getAsFloat();
            }
            embeddingDimension = embedding.length;
            System.out.println("Google Gemini embedding dimension: " + embeddingDimension);
            return embedding;
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse Gemini embedding response: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateSummary(String codeContent, String fileName) throws IOException {
        String truncatedCode = codeContent.length() > 8000 ? codeContent.substring(0, 8000) + "..." : codeContent;
        String language = getLanguageFromFileName(fileName);

        // Create request payload for Gemini Pro
        JsonObject requestPayload = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", "Generate a concise summary (max 3 sentences) of this " + language + " file. Include main classes, methods, and functionality:\n\n" + truncatedCode);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestPayload.add("contents", contents);

        // Add generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", 150);
        requestPayload.add("generationConfig", generationConfig);

        String jsonRequestString = gson.toJson(requestPayload);
        Request request = new Request.Builder()
                .url(GEMINI_GENERATION_ENDPOINT + "?key=" + apiKey)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Gemini generation API error " + response + ": " + errorBody);
            }
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            try {
                // Extract text from the updated response structure
                String output = jsonResponse.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
                if (output.length() > 500) {
                    output = output.substring(0, 497) + "...";
                }
                return output;
            } catch (Exception e) {
                System.err.println("Error parsing generation response: " + e.getMessage());
                System.err.println("Response was: " + responseBody);
                return "Failed to parse summary response: " + e.getMessage();
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse Gemini generation response: " + e.getMessage(), e);
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

        // Create request payload for Gemini Pro
        JsonObject requestPayload = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", "Based on the query and matching files, provide a brief explanation of which files are most relevant and why:\n\n" + contextBuilder.toString());
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestPayload.add("contents", contents);

        // Add generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", 150);
        requestPayload.add("generationConfig", generationConfig);

        String jsonRequestString = gson.toJson(requestPayload);
        Request request = new Request.Builder()
                .url(GEMINI_GENERATION_ENDPOINT + "?key=" + apiKey)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Gemini generation API error " + response + ": " + errorBody);
            }
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            try {
                // Extract text from the updated response structure
                return jsonResponse.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
            } catch (Exception e) {
                System.err.println("Error parsing context response: " + e.getMessage());
                System.err.println("Response was: " + responseBody);
                return "Failed to parse context response: " + e.getMessage();
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse Gemini code context response: " + e.getMessage(), e);
        }
    }

    @Override
    public String askQuestion(String question, List<CodeSearchResult> relevantFiles) throws IOException {
        // Create request payload for Gemini Pro
        JsonObject requestPayload = new JsonObject();
        JsonArray contents = new JsonArray();

        // System message
        JsonObject systemContent = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", "You are a code assistant that helps developers understand their codebase. " +
                "Answer questions using only the information from the provided code files. " +
                "If you can't answer based on the provided files, say so clearly. " +
                "Include relevant code snippets in your explanations.");
        systemParts.add(systemPart);
        systemContent.add("parts", systemParts);
        systemContent.addProperty("role", "user");
        contents.add(systemContent);

        // User message with question and context
        JsonObject userContent = new JsonObject();
        JsonArray userParts = new JsonArray();
        JsonObject userPart = new JsonObject();

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Question: ").append(question).append("\n\n");

        if (relevantFiles.isEmpty()) {
            contextBuilder.append("No relevant files found in the codebase.");
        } else {
            contextBuilder.append("Here are the relevant files from the codebase:\n\n");

            for (int i = 0; i < Math.min(3, relevantFiles.size()); i++) {
                CodeSearchResult file = relevantFiles.get(i);
                contextBuilder.append("FILE ").append(i+1).append(": ").append(file.getFilePath()).append("\n");

                // Use content if available
                if (file.getContent() != null && !file.getContent().isEmpty()) {
                    String content = file.getContent();
                    if (content.length() > 6000) {
                        content = content.substring(0, 6000) + "\n// ... [content truncated] ...";
                    }
                    contextBuilder.append("```\n").append(content).append("\n```\n\n");
                } else {
                    contextBuilder.append("Summary: ").append(file.getSummary()).append("\n\n");

                    // Add metadata as additional context
                    for (Map.Entry<String, String> entry : file.getMetadata().entrySet()) {
                        if (!entry.getValue().isEmpty()) {
                            contextBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                        }
                    }
                    contextBuilder.append("\n");
                }
            }

            contextBuilder.append("Please answer the question based on these files.");
        }

        userPart.addProperty("text", contextBuilder.toString());
        userParts.add(userPart);
        userContent.add("parts", userParts);
        userContent.addProperty("role", "user");
        contents.add(userContent);

        requestPayload.add("contents", contents);

        // Add generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", 1500);
        generationConfig.addProperty("temperature", 0.2);
        requestPayload.add("generationConfig", generationConfig);

        String jsonRequestString = gson.toJson(requestPayload);
        Request request = new Request.Builder()
                .url(GEMINI_GENERATION_ENDPOINT + "?key=" + apiKey)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Gemini generation API error " + response + ": " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            try {
                return jsonResponse.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
            } catch (Exception e) {
                System.err.println("Error parsing question response: " + e.getMessage());
                System.err.println("Response was: " + responseBody);
                return "Failed to parse response: " + e.getMessage();
            }
        } catch (Exception e) {
            throw new IOException("Failed to process Gemini question response: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean testConnection() {
        try {
            // Use a simple embedding call as a connectivity test.
            float[] testEmbedding = getEmbedding("test");
            return testEmbedding != null && testEmbedding.length > 0;
        } catch (Exception e) {
            System.err.println("Gemini connection test failed: " + e.getMessage());
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