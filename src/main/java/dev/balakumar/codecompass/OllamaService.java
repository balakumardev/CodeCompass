package dev.balakumar.codecompass;

import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.List;
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
        jsonRequest.addProperty("prompt", "Generate a concise summary (max 3 sentences) of this "
                + getLanguageFromFileName(fileName)
                + " file. Include the main classes, methods, and functionality:\n\n" + truncatedCode);
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
        jsonRequest.addProperty("prompt", "Based on the user query and the matching files, provide a brief explanation of which files are most relevant and why. Focus on functionality:\n\n"
                + contextBuilder.toString());
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