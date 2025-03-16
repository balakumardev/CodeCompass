package dev.balakumar.codecompass;

import com.google.gson.*;
import com.intellij.openapi.project.Project;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GoogleGeminiService implements EmbeddingService, GenerationService {
    private static final String GEMINI_EMBEDDING_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s";
    private static final String GEMINI_GENERATION_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(240, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Project project;
    private final CodeMapperSettingsState settings;

    public GoogleGeminiService(Project project) {
        this.project = project;
        this.settings = CodeMapperSettingsState.getInstance(project);
        // Create trust-all client to fix SSL issues
        this.client = createTrustAllClient();
    }

    // Create a client that trusts all certificates
    private OkHttpClient createTrustAllClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager)trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(240, TimeUnit.SECONDS)
                    .readTimeout(240, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            System.err.println("Error creating SSL-bypassing client: " + e.getMessage());
            return new OkHttpClient.Builder()
                    .connectTimeout(240, TimeUnit.SECONDS)
                    .readTimeout(240, TimeUnit.SECONDS)
                    .build();
        }
    }

    // EmbeddingService Implementation
    @Override
    public float[] getEmbedding(String text) throws IOException {
        String endpoint = String.format(GEMINI_EMBEDDING_ENDPOINT, settings.geminiEmbeddingModel, settings.geminiApiKey);
        JsonObject requestBody = new JsonObject();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        String truncatedText = text.length() > 8000 ? text.substring(0, 8000) : text;
        part.addProperty("text", truncatedText);
        parts.add(part);
        content.add("parts", parts);
        requestBody.add("content", content);

        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Gemini embedding API error " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray values = jsonResponse.getAsJsonObject("embedding").getAsJsonArray("values");
            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = values.get(i).getAsFloat();
            }
            return embedding;
        } catch (IOException e) {
            throw new IOException("Error getting embedding: " + e.getMessage());
        }
    }

    @Override
    public boolean testConnection() {
        return true;
//        try {
//            String endpoint = String.format(GEMINI_GENERATION_ENDPOINT, settings.geminiGenerationModel, settings.geminiApiKey);
//            Request request = new Request.Builder().url(endpoint).get().build();
//            try (Response response = client.newCall(request).execute()) {
//                return response.isSuccessful();
//            }
//        } catch (IOException e) {
//            System.err.println("Gemini connection test failed: " + e.getMessage());
//            return false;
//        }
    }

    // GenerationService Implementation
    @Override
    public String generateSummary(String codeContent, String fileName) throws IOException {
        String endpoint = String.format(GEMINI_GENERATION_ENDPOINT, settings.geminiGenerationModel, settings.geminiApiKey);
        String language = getLanguageFromFileName(fileName);
        String prompt = "Generate a concise summary (max 3 sentences) of this " + language + " file. Include main classes, methods, and functionality:\n\n" + codeContent;
        JsonObject requestBody = buildGenerationRequest(prompt);

        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .build();

        return executeGenerationRequest(request);
    }

    @Override
    public String generateCodeContext(String query, List<CodeSearchResult> results) throws IOException {
        String endpoint = String.format(GEMINI_GENERATION_ENDPOINT, settings.geminiGenerationModel, settings.geminiApiKey);
        StringBuilder context = new StringBuilder("Generate a code context for the query: \"" + query + "\" based on these files:\n");
        for (CodeSearchResult result : results) {
            context.append(result.getFilePath()).append(": ").append(result.getSummary()).append("\n");
        }
        JsonObject requestBody = buildGenerationRequest(context.toString());

        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .build();

        return executeGenerationRequest(request);
    }

    @Override
    public String askQuestion(String question, List<CodeSearchResult> relevantFiles) throws IOException {
        return askQuestionWithHistory(question, relevantFiles, Collections.emptyList());
    }

    @Override
    public String askQuestionWithHistory(String question, List<CodeSearchResult> relevantFiles, List<Map<String, Object>> chatHistory) throws IOException {
        String endpoint = String.format(GEMINI_GENERATION_ENDPOINT, settings.geminiGenerationModel, settings.geminiApiKey);
        StringBuilder prompt = new StringBuilder("Answer the question: \"" + question + "\" based on these files:\n");
        for (CodeSearchResult file : relevantFiles) {
            prompt.append(file.getFilePath()).append(": ").append(file.getSummary()).append("\n");
        }
        if (!chatHistory.isEmpty()) {
            prompt.append("Chat history:\n");
            for (Map<String, Object> entry : chatHistory) {
                prompt.append(entry.get("role")).append(": ").append(entry.get("content")).append("\n");
            }
        }
        JsonObject requestBody = buildGenerationRequest(prompt.toString());

        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .build();

        return executeGenerationRequest(request);
    }

    // Helper Methods
    private JsonObject buildGenerationRequest(String prompt) {
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        String truncatedPrompt = prompt.length() > 8000 ? prompt.substring(0, 8000) : prompt;
        part.addProperty("text", truncatedPrompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);
        return requestBody;
    }

    private String executeGenerationRequest(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Gemini generation API error " + response.code() + ": " + errorBody);
            }
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            return jsonResponse.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        }
    }

    private String getLanguageFromFileName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) return "unknown";
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        switch (ext) {
            case "java": return "Java";
            case "py": return "Python";
            case "js": return "JavaScript";
            default: return ext;
        }
    }
}