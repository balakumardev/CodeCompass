package dev.balakumar.codecompass;

import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class OllamaService implements AIService {
    private static final String OLLAMA_EMBEDDING_ENDPOINT = "http://localhost:11434/api/embeddings";
    private static final String OLLAMA_GENERATION_ENDPOINT = "http://localhost:11434/api/generate";
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final String GENERATION_MODEL = "codellama:7b-code";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(240, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();
    private int embeddingDimension = 384;

    @Override
    public float[] getEmbedding(String text) throws IOException {
        String truncatedText = text.length() > 4000 ? text.substring(0, 4000) : text;

        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("model", EMBEDDING_MODEL);
        jsonRequest.addProperty("prompt", "Represent this code for retrieval: " + truncatedText);

        String jsonRequestString = gson.toJson(jsonRequest);

        Request request = new Request.Builder()
                .url(OLLAMA_EMBEDDING_ENDPOINT)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Unexpected code " + response + ": " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray embeddingArray = jsonResponse.getAsJsonArray("embedding");

            if (embeddingArray == null) {
                throw new IOException("No embedding found in response: " + jsonResponse);
            }

            float[] embedding = new float[embeddingArray.size()];
            for (int i = 0; i < embeddingArray.size(); i++) {
                embedding[i] = embeddingArray.get(i).getAsFloat();
            }

            embeddingDimension = embedding.length;
            System.out.println("Embedding dimension: " + embeddingDimension);
            return embedding;
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateSummary(String codeContent, String fileName) throws IOException {
        String truncatedCode = codeContent.length() > 8000 ? codeContent.substring(0, 8000) + "..." : codeContent;

        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("model", GENERATION_MODEL);
        jsonRequest.addProperty("prompt", "Generate a concise summary (max 3 sentences) of this " + getLanguageFromFileName(fileName) + " file. Include the main classes, methods, and functionality:\n\n" + truncatedCode);
        jsonRequest.addProperty("stream", false);

        String jsonRequestString = gson.toJson(jsonRequest);

        Request request = new Request.Builder()
                .url(OLLAMA_GENERATION_ENDPOINT)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body()!= null ? response.body().string() : "";
                throw new IOException("Unexpected code " + response + ": " + errorBody);
            }

            JsonObject jsonResponse = gson.fromJson(response.body().charStream(), JsonObject.class);
            String summary = jsonResponse.get("response").getAsString();
            summary = summary.replaceAll("^[\"']|[\"']$", "").trim();

            if (summary.length() > 500) {
                summary = summary.substring(0, 497) + "...";
            }

            return summary;
        } catch (Exception e) {
            return "Failed to generate summary: " + e.getMessage();
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
        jsonRequest.addProperty("prompt", "Based on the user query and the matching files, provide a brief explanation of which files are most relevant and why. Focus on functionality:\n\n" + contextBuilder.toString());
        jsonRequest.addProperty("stream", false);

        String jsonRequestString = gson.toJson(jsonRequest);

        Request request = new Request.Builder()
                .url(OLLAMA_GENERATION_ENDPOINT)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body()!= null ? response.body().string() : "";
                throw new IOException("Unexpected code " + response + ": " + errorBody);
            }

            JsonObject jsonResponse = gson.fromJson(response.body().charStream(), JsonObject.class);
            return jsonResponse.get("response").getAsString();
        } catch (Exception e) {
            return "Failed to generate context: " + e.getMessage();
        }
    }

    @Override
    public String askQuestionWithHistory(String question, List<CodeSearchResult> relevantFiles, List<Map<String, Object>> chatHistory) throws IOException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a code assistant. Answer the following question about the codebase using the provided file contents and conversation history.\n\n");

        // Add previous messages from chat history (up to a reasonable limit)
        int historyLimit = Math.min(chatHistory.size(), 6); // Limit to last 6 messages
        if (historyLimit > 0) {
            prompt.append("Previous conversation:\n\n");
            for (int i = chatHistory.size() - historyLimit; i < chatHistory.size(); i++) {
                Map<String, Object> msg = chatHistory.get(i);
                boolean isUser = (Boolean)msg.get("isUser");
                String message = (String)msg.get("message");
                prompt.append(isUser ? "User: " : "Assistant: ").append(message).append("\n\n");
            }
            prompt.append("Current question:\n");
        }

        prompt.append("Question: ").append(question).append("\n\n");

        if (relevantFiles.isEmpty()) {
            prompt.append("I couldn't find any directly relevant files for your question. Please answer based on our conversation history.");
        } else {
            prompt.append("Here are the relevant files from the codebase:\n\n");
            for (int i = 0; i < Math.min(3, relevantFiles.size()); i++) {
                CodeSearchResult file = relevantFiles.get(i);
                prompt.append("FILE ").append(i+1).append(": ").append(file.getFilePath()).append("\n");

                // Use content if available
                if (file.getContent() != null && !file.getContent().isEmpty()) {
                    String content = file.getContent();
                    if (content.length() > 4000) {
                        content = content.substring(0, 4000) + "\n// ... [content truncated] ...";
                    }
                    prompt.append("```\n").append(content).append("\n```\n\n");
                } else {
                    prompt.append("Summary: ").append(file.getSummary()).append("\n\n");
                }
            }
            prompt.append("Based on these files and our conversation history, please answer the question. Include code snippets in your explanation when relevant.");
        }

        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("model", GENERATION_MODEL);
        jsonRequest.addProperty("prompt", prompt.toString());
        jsonRequest.addProperty("stream", false);
        jsonRequest.addProperty("temperature", 0.2);

        String jsonRequestString = gson.toJson(jsonRequest);

        Request request = new Request.Builder()
                .url(OLLAMA_GENERATION_ENDPOINT)
                .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body()!= null ? response.body().string() : "";
                throw new IOException("Unexpected code " + response + ": " + errorBody);
            }

            JsonObject jsonResponse = gson.fromJson(response.body().charStream(), JsonObject.class);
            return jsonResponse.get("response").getAsString();
        } catch (Exception e) {
            return "Failed to generate answer: " + e.getMessage();
        }
    }


    @Override
    public String askQuestion(String question, List<CodeSearchResult> relevantFiles) throws IOException {
        return askQuestionWithHistory(question, relevantFiles, Collections.emptyList());
    }

    @Override
    public boolean testConnection() {
        try {
            JsonObject jsonRequest = new JsonObject();
            jsonRequest.addProperty("model", EMBEDDING_MODEL);
            jsonRequest.addProperty("prompt", "test");

            String jsonRequestString = gson.toJson(jsonRequest);

            Request request = new Request.Builder()
                    .url(OLLAMA_EMBEDDING_ENDPOINT)
                    .post(RequestBody.create(jsonRequestString, MediaType.get("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            System.err.println("Ollama connection test failed: " + e.getMessage());
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