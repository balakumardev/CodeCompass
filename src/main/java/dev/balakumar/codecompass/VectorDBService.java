package dev.balakumar.codecompass;

import com.github.jelmerk.knn.DistanceFunction;
import com.github.jelmerk.knn.Item;
import com.github.jelmerk.knn.SearchResult;
import com.github.jelmerk.knn.hnsw.HnswIndex;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class VectorDBService {
    private static final String INDEX_FILE = "codemapper_index.dat";
    private static final String DOCUMENTS_FILE = "codemapper_docs.dat";
    private static final String DIMENSION_FILE = "codemapper_dimension.txt";

    private final Path dbPath;
    private final OllamaService ollamaService;
    private HnswIndex<String, float[], CodeDocument, Float> index;
    private final Map<String, CodeDocument> documents = new HashMap<>();
    private final AtomicInteger documentCount = new AtomicInteger(0);
    private int dimensions;

    public VectorDBService(String projectPath, OllamaService ollamaService) throws IOException {
        this.ollamaService = ollamaService;

        // Create a directory for the vector database
        dbPath = Path.of(projectPath, ".codemapper");
        Files.createDirectories(dbPath);

        // Get the embedding dimension from Ollama service
        try {
            // Try to get a test embedding to determine dimension
            float[] testEmbedding = ollamaService.getEmbedding("test embedding");
            dimensions = testEmbedding.length;
            System.out.println("Detected embedding dimension: " + dimensions);

            // Save the dimension to a file for future reference
            saveDimension();
        } catch (Exception e) {
            // Try to load the dimension from file
            if (!loadDimension()) {
                System.err.println("Could not determine embedding dimension: " + e.getMessage());
                throw new IOException("Failed to determine embedding dimension", e);
            }
        }

        // Try to load existing index and documents
        if (!loadIndex()) {
            // Create a new index if loading fails
            createNewIndex();
        }

        // Make sure document count is set correctly
        documentCount.set(documents.size());
        System.out.println("VectorDBService initialized with " + documentCount.get() + " documents");
    }

    private void saveDimension() {
        try {
            File dimensionFile = dbPath.resolve(DIMENSION_FILE).toFile();
            try (PrintWriter writer = new PrintWriter(dimensionFile)) {
                writer.println(dimensions);
            }
        } catch (Exception e) {
            System.err.println("Error saving dimension: " + e.getMessage());
        }
    }

    private boolean loadDimension() {
        File dimensionFile = dbPath.resolve(DIMENSION_FILE).toFile();
        if (dimensionFile.exists()) {
            try (Scanner scanner = new Scanner(dimensionFile)) {
                if (scanner.hasNextInt()) {
                    dimensions = scanner.nextInt();
                    System.out.println("Loaded dimension from file: " + dimensions);
                    return true;
                }
            } catch (Exception e) {
                System.err.println("Error loading dimension: " + e.getMessage());
            }
        }
        return false;
    }

    private boolean loadIndex() {
        File indexFile = dbPath.resolve(INDEX_FILE).toFile();
        File documentsFile = dbPath.resolve(DOCUMENTS_FILE).toFile();

        if (indexFile.exists() && documentsFile.exists()) {
            try (ObjectInputStream indexIn = new ObjectInputStream(new FileInputStream(indexFile));
                 ObjectInputStream docsIn = new ObjectInputStream(new FileInputStream(documentsFile))) {

                // Load the index
                index = (HnswIndex<String, float[], CodeDocument, Float>) indexIn.readObject();

                // Load the documents
                Map<String, CodeDocument> loadedDocs = (Map<String, CodeDocument>) docsIn.readObject();
                documents.putAll(loadedDocs);

                // Update dimensions based on first document
                if (!documents.isEmpty()) {
                    CodeDocument firstDoc = documents.values().iterator().next();
                    int loadedDimensions = firstDoc.vector().length;

                    // Check if dimensions match
                    if (loadedDimensions != dimensions) {
                        System.out.println("Warning: Loaded index dimension (" + loadedDimensions +
                                ") doesn't match current model dimension (" + dimensions +
                                "). Creating new index.");
                        return false;
                    }

                    System.out.println("Loaded index with vector dimension: " + dimensions);
                }

                documentCount.set(documents.size());
                System.out.println("Loaded vector index with " + documents.size() + " documents");
                return true;

            } catch (Exception e) {
                System.err.println("Error loading vector index: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private void createNewIndex() {
        System.out.println("Creating new index with dimension: " + dimensions);

        // Define distance function (cosine distance)
        DistanceFunction<float[], Float> distanceFunction = (a, b) -> {
            if (a.length != b.length) {
                System.err.println("Vector length mismatch: " + a.length + " vs " + b.length);
                // Return max distance if lengths don't match
                return 1.0f;
            }

            float dot = 0;
            float normA = 0;
            float normB = 0;

            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }

            // Avoid division by zero
            if (normA == 0 || normB == 0) {
                return 1.0f;
            }

            return 1f - (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
        };

        // Create the index
        index = HnswIndex
                .<float[], Float>newBuilder(dimensions, distanceFunction, 10000)
                .withM(16)                // Number of connections per node
                .withEf(200)              // Size of the dynamic list for the nearest neighbors
                .withEfConstruction(200)  // Size of the dynamic list during construction
                .build();

        // Clear documents map
        documents.clear();
    }

    public void saveIndex() {
        try {
            File indexFile = dbPath.resolve(INDEX_FILE).toFile();
            File documentsFile = dbPath.resolve(DOCUMENTS_FILE).toFile();

            try (ObjectOutputStream indexOut = new ObjectOutputStream(new FileOutputStream(indexFile));
                 ObjectOutputStream docsOut = new ObjectOutputStream(new FileOutputStream(documentsFile))) {

                indexOut.writeObject(index);
                docsOut.writeObject(new HashMap<>(documents));

                System.out.println("Saved vector index with " + documents.size() + " documents");
            }
        } catch (Exception e) {
            System.err.println("Error saving vector index: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addOrUpdateDocument(String id, String content, String filePath, String summary, Map<String, String> metadata) {
        try {
            // Generate embedding
            float[] embedding = ollamaService.getEmbedding(content);

            // Check if dimensions match
            if (embedding.length != dimensions) {
                System.out.println("Warning: Embedding dimension mismatch. Expected: " + dimensions +
                        ", Got: " + embedding.length + ". Recreating index.");
                dimensions = embedding.length;
                saveDimension();
                createNewIndex();
            }

            // Convert metadata to JSON string
            String metadataJson = new Gson().toJson(metadata);

            // Create document
            CodeDocument document = new CodeDocument(id, embedding, filePath, summary, metadataJson);

            // Add to documents map
            documents.put(id, document);

            // Add to index (will update if already exists)
            index.add(document);

            // Update count
            documentCount.set(documents.size());

            System.out.println("Added document: " + id + ", total count: " + documentCount.get());

        } catch (Exception e) {
            System.err.println("Error adding document to vector DB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<CodeSearchResult> search(String query, int limit) {
        try {
            // Generate query embedding
            float[] queryEmbedding = ollamaService.getEmbedding(query);

            // Check if we have any documents
            if (documents.isEmpty()) {
                System.out.println("Warning: No documents in index");
                return Collections.emptyList();
            }

            // Check if dimensions match
            if (queryEmbedding.length != dimensions) {
                System.out.println("Warning: Query embedding dimension (" + queryEmbedding.length +
                        ") doesn't match index dimension (" + dimensions + ").");
                return Collections.emptyList();
            }

            // Search the index
            List<SearchResult<CodeDocument, Float>> results = index.findNearest(queryEmbedding, Math.min(limit, documentCount.get()));

            // Convert to CodeSearchResult objects
            List<CodeSearchResult> searchResults = new ArrayList<>();
            Gson gson = new Gson();
            Type metadataType = new TypeToken<Map<String, String>>(){}.getType();

            for (SearchResult<CodeDocument, Float> result : results) {
                CodeDocument doc = result.item();
                float distance = result.distance();

                // Convert JSON metadata back to map
                Map<String, String> metadata = gson.fromJson(doc.metadataJson(), metadataType);

                searchResults.add(new CodeSearchResult(
                        doc.id(),
                        doc.filePath(),
                        doc.summary(),
                        1.0f - distance, // Convert distance to similarity score
                        metadata
                ));
            }

            return searchResults;
        } catch (Exception e) {
            System.err.println("Error searching vector DB: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public void deleteAll() {
        // Recreate the index
        createNewIndex();
        documents.clear();
        documentCount.set(0);
    }

    public int getDocumentCount() {
        return documentCount.get();
    }

    public void close() {
        saveIndex();
    }

    // Document class for the vector index
    public static class CodeDocument implements Item<String, float[]>, Serializable {
        private final String id;
        private final float[] vector;
        private final String filePath;
        private final String summary;
        private final String metadataJson;

        public CodeDocument(String id, float[] vector, String filePath, String summary, String metadataJson) {
            this.id = id;
            this.vector = vector;
            this.filePath = filePath;
            this.summary = summary;
            this.metadataJson = metadataJson;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public float[] vector() {
            return vector;
        }

        @Override
        public int dimensions() {
            return vector.length;
        }

        public String filePath() {
            return filePath;
        }

        public String summary() {
            return summary;
        }

        public String metadataJson() {
            return metadataJson;
        }
    }
}
