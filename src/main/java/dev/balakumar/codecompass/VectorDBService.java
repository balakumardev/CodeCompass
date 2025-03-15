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
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    JsonObject result = jsonResponse.getAsJsonObject("result");
                    if (result != null && result.has("vectors_count") && !result.get("vectors_count").isJsonNull()) {
                        // Here we assume that the response contains the field “vectors_count”
                        // for document statistics; the collection configuration is stored elsewhere.
                        // (This does not yield the vector size, so we check config if needed.)
                        JsonObject config = result.getAsJsonObject("config");
                        if (config != null) {
                            JsonObject params = config.getAsJsonObject("params");
                            if (params != null) {
                                JsonObject vectors = params.getAsJsonObject("vectors");
                                if (vectors != null && vectors.has("size") && !vectors.get("size").isJsonNull()) {
                                    return vectors.get("size").getAsInt();
                                }
                            }
                        }
                    }
                    return -1;
                }
                return -1;
            }
        } catch (Exception e) {
            System.err.println("Error getting collection dimension: " + e.getMessage());
            return -1;
        }
    }

    private void createCollection() {
        try {
            JsonObject vectorsConfig = new JsonObject();
            vectorsConfig.addProperty("size", dimensions);
            vectorsConfig.addProperty("distance", "Cosine");
            JsonObject createRequest = new JsonObject();
            createRequest.add("vectors", vectorsConfig);
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
                        if (result != null && result.has("vectors_count") && !result.get("vectors_count").isJsonNull()) {
                            int count = result.get("vectors_count").getAsInt();
                            documentCount.set(count);
                        } else {
                            System.err.println("vectors_count not found in collection result");
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
            for (float value : embedding) { vector.add(value); }
            pointRequest.add("vector", vector);
            JsonObject payload = new JsonObject();
            payload.addProperty("filePath", filePath);
            payload.addProperty("summary", summary);
            JsonObject metadataJson = new JsonObject();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                metadataJson.addProperty(entry.getKey(), entry.getValue());
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
            for (float value : queryEmbedding) { vector.add(value); }
            searchRequest.add("vector", vector);
            searchRequest.addProperty("limit", limit);
            searchRequest.addProperty("with_payload", true);
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
                        if (payload.has("metadata")) {
                            JsonObject metadataJson = payload.getAsJsonObject("metadata");
                            for (Map.Entry<String, JsonElement> entry : metadataJson.entrySet()) {
                                if (entry.getValue().isJsonPrimitive()) {
                                    metadata.put(entry.getKey(), entry.getValue().getAsString());
                                }
                            }
                        }
                        searchResults.add(new CodeSearchResult(pointId, filePath, summary, score, metadata));
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
}