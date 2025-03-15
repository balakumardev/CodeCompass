package dev.balakumar.codecompass;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VectorDBService {
    private static final String COLLECTION_NAME = "codemapper";
    private static final String CONFIG_FILE = "codemapper_config.json";
    private static final String QDRANT_URL = "http://localhost:6333";
    private final Path dbPath;
    private final AIService aiService;
    private final OkHttpClient client;
    private final Gson gson;
    private final AtomicInteger documentCount = new AtomicInteger(0);
    private int dimensions;
    private boolean collectionExists = false;

    public VectorDBService(String projectPath, AIService aiService) throws IOException {
        this.aiService = aiService;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        dbPath = Path.of(projectPath, ".codemapper");
        Files.createDirectories(dbPath);

        try {
            float[] testEmbedding = aiService.getEmbedding("test embedding");
            dimensions = testEmbedding.length;
            System.out.println("Detected embedding dimension: " + dimensions);
            saveDimension();
        } catch (Exception e) {
            if (!loadDimension()) {
                System.err.println("Could not determine embedding dimension: " + e.getMessage());
                throw new IOException("Failed to determine embedding dimension", e);
            }
        }

        if (!isQdrantRunning()) {
            System.err.println("Qdrant is not running. Please start Qdrant first.");
            throw new IOException("Qdrant server is not running");
        }

        initializeCollection();
        updateDocumentCount();
        System.out.println("VectorDBService initialized with " + documentCount.get() + " documents");
    }

    private boolean isQdrantRunning() {
        try {
            Request request = new Request.Builder()
                    .url(QDRANT_URL + "/healthz")
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            System.err.println("Error checking Qdrant health: " + e.getMessage());
            return false;
        }
    }

    private void saveDimension() {
        try {
            JsonObject config = new JsonObject();
            config.addProperty("dimensions", dimensions);
            File configFile = dbPath.resolve(CONFIG_FILE).toFile();
            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(config, writer);
            }
        } catch (Exception e) {
            System.err.println("Error saving dimension: " + e.getMessage());
        }
    }

    private boolean loadDimension() {
        File configFile = dbPath.resolve(CONFIG_FILE).toFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject config = gson.fromJson(reader, JsonObject.class);
                if (config != null && config.has("dimensions")) {
                    dimensions = config.get("dimensions").getAsInt();
                    System.out.println("Loaded dimension from config: " + dimensions);
                    return true;
                }
            } catch (Exception e) {
                System.err.println("Error loading dimension: " + e.getMessage());
            }
        }
        return false;
    }

    private void initializeCollection() {
        try {
            if (collectionExists()) {
                collectionExists = true;
                System.out.println("Collection already exists");
                int collectionDimensions = getCollectionDimension();
                if (collectionDimensions != dimensions) {
                    System.out.println("Dimension mismatch! Collection: " + collectionDimensions + ", Current: " + dimensions + ". Recreating collection.");
                    deleteCollection();
                    createCollection();
                }
            } else {
                createCollection();
            }
        } catch (Exception e) {
            System.err.println("Error initializing collection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean collectionExists() {
        try {
            Request request = new Request.Builder()
                    .url(QDRANT_URL + "/collections/" + COLLECTION_NAME)
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.code() == 200;
            }
        } catch (Exception e) {
            System.err.println("Error checking if collection exists: " + e.getMessage());
            return false;
        }
    }

    private int getCollectionDimension() {
        try {
            Request request = new Request.Builder()
                    .url(QDRANT_URL + "/collections/" + COLLECTION_NAME)
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Failed to fetch collection info: " + response.code());
                    return -1;
                }
                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonObject result = jsonResponse.getAsJsonObject("result");
                if (result == null) {
                    System.err.println("No 'result' field in response");
                    return -1;
                }
                JsonObject config = result.getAsJsonObject("config");
                if (config == null) {
                    System.err.println("No 'config' field in response");
                    return -1;
                }
                JsonObject params = config.getAsJsonObject("params");
                if (params == null) {
                    System.err.println("No 'params' field in response");
                    return -1;
                }
                JsonObject vectors = params.getAsJsonObject("vectors");
                if (vectors == null || !vectors.has("size")) {
                    System.err.println("No valid 'vectors.size' field in response");
                    return -1;
                }
                return vectors.get("size").getAsInt();
            }
        } catch (Exception e) {
            System.err.println("Error getting collection dimension: " + e.getMessage());
            return -1;
        }
    }

    private void createCollection() {
        try {
            // Create a collection with optimized settings
            JsonObject vectorsConfig = new JsonObject();
            vectorsConfig.addProperty("size", dimensions);
            vectorsConfig.addProperty("distance", "Cosine");

            // Add optimized index settings
            JsonObject hnsw = new JsonObject();
            hnsw.addProperty("m", 16);  // More connections per node for better recall
            hnsw.addProperty("ef_construct", 100);  // Higher value for better index quality
            hnsw.addProperty("full_scan_threshold", 10000);  // Threshold for full scan vs index
            vectorsConfig.add("hnsw_config", hnsw);

            // Add optimized quantization for faster search
            JsonObject quantization = new JsonObject();
            quantization.addProperty("scalar", true);
            vectorsConfig.add("quantization_config", quantization);

            JsonObject createRequest = new JsonObject();
            createRequest.add("vectors", vectorsConfig);

            // Add optimized schema for payload
            JsonObject optimizedSchema = new JsonObject();

            // Indexed fields for faster filtering
            JsonObject filePathField = new JsonObject();
            filePathField.addProperty("type", "keyword");
            optimizedSchema.add("filePath", filePathField);

            JsonObject languageField = new JsonObject();
            languageField.addProperty("type", "keyword");
            optimizedSchema.add("language", languageField);

            JsonObject fileTypeField = new JsonObject();
            fileTypeField.addProperty("type", "keyword");
            optimizedSchema.add("fileType", fileTypeField);

            JsonObject contentField = new JsonObject();
            contentField.addProperty("type", "text");
            optimizedSchema.add("content", contentField);

            createRequest.add("schema", optimizedSchema);

            Request request = new Request.Builder()
                    .url(QDRANT_URL + "/collections/" + COLLECTION_NAME)
                    .put(RequestBody.create(gson.toJson(createRequest), MediaType.parse("application/json")))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    collectionExists = true;
                    System.out.println("Created new collection with dimension: " + dimensions);
                } else {
                    System.err.println("Failed to create collection: " + response.code() + " " + response.message());
                    System.err.println("Response: " + (response.body() != null ? response.body().string() : "null"));
                }
            }
        } catch (Exception e) {
            System.err.println("Error creating collection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deleteCollection() {
        try {
            Request request = new Request.Builder()
                    .url(QDRANT_URL + "/collections/" + COLLECTION_NAME)
                    .delete()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("Deleted existing collection");
                    collectionExists = false;
                } else {
                    System.err.println("Failed to delete collection: " + response.code() + " " + response.message());
                }
            }
        } catch (Exception e) {
            System.err.println("Error deleting collection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateDocumentCount() {
        try {
            if (collectionExists) {
                Request request = new Request.Builder()
                        .url(QDRANT_URL + "/collections/" + COLLECTION_NAME)
                        .get()
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                        JsonObject result = jsonResponse.getAsJsonObject("result");
                        if (result != null && result.has("points_count") && !result.get("points_count").isJsonNull()) {
                            int count = result.get("points_count").getAsInt();
                            documentCount.set(count);
                        } else {
                            System.err.println("points_count not found in collection result");
                            documentCount.set(0);
                        }
                    } else {
                        documentCount.set(0);
                    }
                }
            } else {
                documentCount.set(0);
            }
        } catch (Exception e) {
            System.err.println("Error getting document count: " + e.getMessage());
            documentCount.set(0);
        }
    }

    public void addOrUpdateDocument(String id, String content, String filePath, String summary, Map<String, String> metadata) {
        try {
            float[] embedding = aiService.getEmbedding(content);
            if (embedding.length != dimensions) {
                System.out.println("Warning: Embedding dimension mismatch. Expected: " + dimensions + ", Got: " + embedding.length + ". Recreating collection.");
                dimensions = embedding.length;
                saveDimension();
                deleteCollection();
                createCollection();
            }

            long pointId = Math.abs(id.hashCode());
            JsonObject pointRequest = new JsonObject();
            pointRequest.addProperty("id", pointId);

            JsonArray vector = new JsonArray();
            for (float value : embedding) {
                vector.add(value);
            }
            pointRequest.add("vector", vector);

            // Enhanced payload with more structured data
            JsonObject payload = new JsonObject();
            payload.addProperty("filePath", filePath);
            payload.addProperty("summary", summary);
            payload.addProperty("content", content);  // Store full content for RAG

            // Extract file type from path
            String fileType = "unknown";
            if (filePath.contains(".")) {
                fileType = filePath.substring(filePath.lastIndexOf(".") + 1);
            }
            payload.addProperty("fileType", fileType);

            // Extract language from metadata or default to fileType
            String language = metadata.getOrDefault("language", fileType);
            payload.addProperty("language", language);

            // Add structured code elements
            if (metadata.containsKey("classes")) {
                payload.addProperty("classes", metadata.get("classes"));
            }

            if (metadata.containsKey("functions")) {
                payload.addProperty("functions", metadata.get("functions"));
            }

            if (metadata.containsKey("imports")) {
                payload.addProperty("imports", metadata.get("imports"));
            }

            if (metadata.containsKey("package")) {
                payload.addProperty("package", metadata.get("package"));
            }

            // Add timestamp for versioning
            payload.addProperty("indexedAt", System.currentTimeMillis());

            // Add remaining metadata
            JsonObject metadataJson = new JsonObject();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (!entry.getKey().equals("classes") &&
                        !entry.getKey().equals("functions") &&
                        !entry.getKey().equals("imports") &&
                        !entry.getKey().equals("package") &&
                        !entry.getKey().equals("language")) {
                    metadataJson.addProperty(entry.getKey(), entry.getValue());
                }
            }
            payload.add("metadata", metadataJson);

            pointRequest.add("payload", payload);

            JsonArray points = new JsonArray();
            points.add(pointRequest);

            JsonObject requestBody = new JsonObject();
            requestBody.add("points", points);

            Request request = new Request.Builder()
                    .url(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points")
                    .put(RequestBody.create(gson.toJson(requestBody), MediaType.parse("application/json")))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("Added/updated document: " + id);
                    updateDocumentCount();
                } else {
                    System.err.println("Failed to add document: " + response.code() + " " + response.message());
                    System.err.println("Response: " + (response.body() != null ? response.body().string() : "null"));
                }
            }
        } catch (Exception e) {
            System.err.println("Error adding document to vector DB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<CodeSearchResult> search(String query, int limit) {
        return search(query, limit, null);
    }

    public List<CodeSearchResult> search(String query, int limit, Map<String, String> filters) {
        try {
            if (!collectionExists) {
                System.out.println("Warning: Collection does not exist");
                return Collections.emptyList();
            }

            float[] queryEmbedding = aiService.getEmbedding(query);
            if (queryEmbedding.length != dimensions) {
                System.out.println("Warning: Query embedding dimension (" + queryEmbedding.length + ") doesn't match index dimension (" + dimensions + ").");
                return Collections.emptyList();
            }

            JsonObject searchRequest = new JsonObject();
            JsonArray vector = new JsonArray();
            for (float value : queryEmbedding) {
                vector.add(value);
            }
            searchRequest.add("vector", vector);
            searchRequest.addProperty("limit", limit);
            searchRequest.addProperty("with_payload", true);

            // Add filters if provided
            if (filters != null && !filters.isEmpty()) {
                JsonObject filter = new JsonObject();
                JsonArray must = new JsonArray();

                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    JsonObject condition = new JsonObject();
                    JsonObject match = new JsonObject();
                    match.addProperty(entry.getKey(), entry.getValue());
                    condition.add("match", match);
                    must.add(condition);
                }

                filter.add("must", must);
                searchRequest.add("filter", filter);
            }

            Request request = new Request.Builder()
                    .url(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points/search")
                    .post(RequestBody.create(gson.toJson(searchRequest), MediaType.parse("application/json")))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    JsonArray results = jsonResponse.getAsJsonArray("result");
                    List<CodeSearchResult> searchResults = new ArrayList<>();
                    for (int i = 0; i < results.size(); i++) {
                        JsonObject result = results.get(i).getAsJsonObject();
                        String pointId = result.get("id").getAsString();
                        float score = result.get("score").getAsFloat();
                        JsonObject payload = result.getAsJsonObject("payload");
                        String filePath = payload.get("filePath").getAsString();
                        String summary = payload.get("summary").getAsString();
                        Map<String, String> metadata = new HashMap<>();

                        // Extract content for RAG if available
                        String content = "";
                        if (payload.has("content") && !payload.get("content").isJsonNull()) {
                            content = payload.get("content").getAsString();
                        }

                        // Extract all top-level string fields as metadata
                        for (Map.Entry<String, JsonElement> entry : payload.entrySet()) {
                            if (entry.getValue().isJsonPrimitive() &&
                                    !entry.getKey().equals("filePath") &&
                                    !entry.getKey().equals("summary") &&
                                    !entry.getKey().equals("content")) {
                                metadata.put(entry.getKey(), entry.getValue().getAsString());
                            }
                        }

                        // Extract nested metadata if present
                        if (payload.has("metadata") && payload.get("metadata").isJsonObject()) {
                            JsonObject metadataJson = payload.getAsJsonObject("metadata");
                            for (Map.Entry<String, JsonElement> entry : metadataJson.entrySet()) {
                                if (entry.getValue().isJsonPrimitive()) {
                                    metadata.put(entry.getKey(), entry.getValue().getAsString());
                                }
                            }
                        }

                        // Create enhanced search result with content
                        CodeSearchResult searchResult = new CodeSearchResult(pointId, filePath, summary, score, metadata);
                        searchResult.setContent(content);
                        searchResults.add(searchResult);
                    }
                    return searchResults;
                } else {
                    System.err.println("Failed to search: " + response.code() + " " + response.message());
                    return Collections.emptyList();
                }
            }
        } catch (Exception e) {
            System.err.println("Error searching vector DB: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public void saveIndex() {
        // Qdrant persists data, so nothing extra to do.
    }

    public void deleteAll() {
        try {
            deleteCollection();
            createCollection();
            documentCount.set(0);
        } catch (Exception e) {
            System.err.println("Error deleting all documents: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getDocumentCount() {
        return documentCount.get();
    }

    public void close() {
        // Nothing to close for the HTTP client.
    }

    public List<String> getUniqueLanguages() {
        try {
            if (!collectionExists) {
                return Collections.emptyList();
            }

            // Create a request to get unique language values
            JsonObject aggregateRequest = new JsonObject();
            JsonArray fields = new JsonArray();
            fields.add("language");
            aggregateRequest.add("fields", fields);
            aggregateRequest.addProperty("limit", 100);

            Request request = new Request.Builder()
                    .url(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points/group")
                    .post(RequestBody.create(gson.toJson(aggregateRequest), MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                    List<String> languages = new ArrayList<>();
                    if (jsonResponse.has("result") && jsonResponse.get("result").isJsonArray()) {
                        JsonArray groups = jsonResponse.getAsJsonArray("result");
                        for (JsonElement group : groups) {
                            if (group.isJsonObject() && group.getAsJsonObject().has("hits")) {
                                JsonArray hits = group.getAsJsonObject().getAsJsonArray("hits");
                                if (hits.size() > 0) {
                                    JsonObject hit = hits.get(0).getAsJsonObject();
                                    if (hit.has("payload") && hit.getAsJsonObject("payload").has("language")) {
                                        String language = hit.getAsJsonObject("payload").get("language").getAsString();
                                        languages.add(language);
                                    }
                                }
                            }
                        }
                    }
                    return languages;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting unique languages: " + e.getMessage());
        }
        return Collections.emptyList();
    }
}

