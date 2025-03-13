package dev.balakumar.codecompass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressIndicator;

import java.io.*;
import java.util.*;

// Helper class to hold a reference to the indexer instance and a file
class IndexedFile {
    public final VirtualFile virtualFile;
    public final SimpleIndexer indexer;

    public IndexedFile(VirtualFile virtualFile, SimpleIndexer indexer) {
        this.virtualFile = virtualFile;
        this.indexer = indexer;
    }
}

public class SimpleIndexer {
    private final OllamaService ollamaService = new OllamaService();
    private VectorDBService vectorDBService;
    private Project project;

    public SimpleIndexer() {
    }
    public SimpleIndexer(Project project) {
        this.project = project;
        try {
            this.vectorDBService = new VectorDBService(project.getBasePath(), ollamaService);
        } catch (IOException e) {
            System.err.println("Error initializing vector database: " + e.getMessage());
        }
    }

    public List<CodeSearchResult> search(String query, int limit) {
        if (project == null) {
            System.err.println("Error: Project is null in search method");
            return Collections.emptyList();
        }

        if (vectorDBService == null) {
            try {
                vectorDBService = new VectorDBService(project.getBasePath(), ollamaService);
            } catch (IOException e) {
                System.err.println("Error initializing vector database for search: " + e.getMessage());
                return Collections.emptyList();
            }
        }

        return vectorDBService.search(query, limit);
    }

    public void indexProject(Project project, ProgressIndicator indicator) {
        this.project = project;

        try {
            // Test Ollama connection
            boolean ollamaConnected = ollamaService.testConnection();
            System.out.println("Ollama connection test: " + (ollamaConnected ? "SUCCESS" : "FAILED"));

            if (!ollamaConnected) {
                indicator.setText("Failed to connect to Ollama. Make sure it's running.");
                return;
            }

            // Initialize vector database if not already done
            if (vectorDBService == null) {
                try {
                    vectorDBService = new VectorDBService(project.getBasePath(), ollamaService);
                } catch (IOException e) {
                    System.err.println("Failed to initialize vector database: " + e.getMessage());
                    indicator.setText("Failed to initialize vector database: " + e.getMessage());
                    return;
                }
            }

            List<IndexedFile> files = collectProjectFiles(project);

            indicator.setText("Indexing files with AI...");
            indicator.setIndeterminate(false);

            int total = files.size();
            int current = 0;
            int processed = 0;
            int batchSize = 5; // Process files in smaller batches

            System.out.println("Starting to index " + total + " files");

            for (int i = 0; i < files.size(); i += batchSize) {
                if (indicator.isCanceled()) {
                    break;
                }

                // Process a batch of files
                int endIndex = Math.min(i + batchSize, files.size());
                for (int j = i; j < endIndex; j++) {
                    IndexedFile file = files.get(j);
                    String filePath = file.virtualFile.getPath();

                    indicator.setText("Processing: " + filePath);
                    indicator.setFraction((double) current / total);

                    // Make sure the indexer has a project
                    if (file.indexer.project == null) {
                        file.indexer.project = project;
                    }

                    // If this indexer doesn't have a vectorDBService, use the one from file.indexer
                    if (vectorDBService == null && file.indexer.vectorDBService != null) {
                        vectorDBService = file.indexer.vectorDBService;
                    }

                    indexSingleFile(file.virtualFile, file.indexer);

                    current++;
                    processed++;
                }

                // Save after each batch
                if (vectorDBService != null) {
                    vectorDBService.saveIndex();
                }

                System.out.println("Processed batch. Total processed: " + processed + " / " + total);

                // Force garbage collection between batches
                System.gc();
            }

            if (vectorDBService != null) {
                System.out.println("Indexing completed. Total documents: " + vectorDBService.getDocumentCount());
            } else {
                System.err.println("Indexing completed but vector database is null");
            }

        } catch (Exception e) {
            System.err.println("Error during indexing: " + e.getMessage());
            e.printStackTrace();
        }
    }



    /**
     * Collects all files from the given project to be indexed.
     */
    public static List<IndexedFile> collectProjectFiles(Project project) {
        List<IndexedFile> result = new ArrayList<>();
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            SimpleIndexer indexer = new SimpleIndexer(project);
            collectDirectory(baseDir, indexer, result);
        }
        return result;
    }

    /**
     * Recursively walks the directory structure, adding each file to the list.
     */
    private static void collectDirectory(VirtualFile directory, SimpleIndexer indexer, List<IndexedFile> collector) {
        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                // Skip certain directories
                if (shouldSkipDirectory(file.getName())) {
                    continue;
                }
                collectDirectory(file, indexer, collector);
            } else {
                // Only index code files
                if (isCodeFile(file)) {
                    collector.add(new IndexedFile(file, indexer));
                }
            }
        }
    }

    private static boolean shouldSkipDirectory(String dirName) {
        // Skip common non-code directories
        return dirName.equals("node_modules") ||
                dirName.equals("build") ||
                dirName.equals("dist") ||
                dirName.equals(".git") ||
                dirName.equals("target") ||
                dirName.equals("out") ||
                dirName.equals(".codemapper") ||
                dirName.startsWith(".");
    }

    private static boolean isCodeFile(VirtualFile file) {
        String ext = file.getExtension();
        if (ext == null) return false;

        // Add or remove extensions based on your needs
        return ext.equals("java") ||
                ext.equals("kt") ||
                ext.equals("py") ||
                ext.equals("js") ||
                ext.equals("ts") ||
                ext.equals("c") ||
                ext.equals("cpp") ||
                ext.equals("h") ||
                ext.equals("cs") ||
                ext.equals("go") ||
                ext.equals("rs") ||
                ext.equals("php") ||
                ext.equals("rb") ||
                ext.equals("scala") ||
                ext.equals("groovy") ||
                ext.equals("xml") ||
                ext.equals("json");
    }

    /**
     * Actually indexes (embeds) a single file and generates a summary.
     */
    public void indexSingleFile(VirtualFile file, SimpleIndexer indexer) {
        try {
            // Skip files that are too large
            if (file.getLength() > 500000) { // 500KB limit
                System.out.println("Skipping large file: " + file.getPath());
                return;
            }

            String content = new String(file.contentsToByteArray());

            // Skip binary files or files with unusual characters
            if (isBinaryFile(content)) {
                System.out.println("Skipping binary file: " + file.getPath());
                return;
            }

            // Generate summary
            String summary = ollamaService.generateSummary(content, file.getName());

            // Extract metadata
            Map<String, String> metadata = extractMetadata(file, content);

            // Add to vector database
            vectorDBService.addOrUpdateDocument(
                    file.getPath(),
                    content,
                    file.getPath(),
                    summary,
                    metadata
            );

        } catch (Exception e) {
            System.err.println("Error indexing " + file.getPath() + ": " + e.getMessage());
        }
    }

    private Map<String, String> extractMetadata(VirtualFile file, String content) {
        Map<String, String> metadata = new HashMap<>();

        // Basic file metadata
        metadata.put("filename", file.getName());
        metadata.put("extension", file.getExtension() != null ? file.getExtension() : "");
        metadata.put("size", String.valueOf(file.getLength()));
        metadata.put("lastModified", String.valueOf(file.getTimeStamp()));

        // Extract language-specific metadata
        String ext = file.getExtension();
        if ("java".equals(ext)) {
            extractJavaMetadata(content, metadata);
        } else if ("py".equals(ext)) {
            extractPythonMetadata(content, metadata);
        } else if ("js".equals(ext) || "ts".equals(ext)) {
            extractJavascriptMetadata(content, metadata);
        }

        return metadata;
    }

    private void extractJavaMetadata(String content, Map<String, String> metadata) {
        // Extract package
        String packagePattern = "package\\s+([\\w.]+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(packagePattern);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            metadata.put("package", matcher.group(1));
        }

        // Extract class names
        String classPattern = "class\\s+(\\w+)";
        pattern = java.util.regex.Pattern.compile(classPattern);
        matcher = pattern.matcher(content);
        StringBuilder classes = new StringBuilder();
        while (matcher.find()) {
            if (classes.length() > 0) classes.append(", ");
            classes.append(matcher.group(1));
        }
        metadata.put("classes", classes.toString());

        // Extract imports
        String importPattern = "import\\s+([\\w.]+)";
        pattern = java.util.regex.Pattern.compile(importPattern);
        matcher = pattern.matcher(content);
        StringBuilder imports = new StringBuilder();
        while (matcher.find()) {
            if (imports.length() > 0) imports.append(", ");
            imports.append(matcher.group(1));
        }
        metadata.put("imports", imports.toString());
    }

    private void extractPythonMetadata(String content, Map<String, String> metadata) {
        // Extract imports
        String importPattern = "import\\s+(\\w+)|from\\s+(\\w+)\\s+import";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(importPattern);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        StringBuilder imports = new StringBuilder();
        while (matcher.find()) {
            if (imports.length() > 0) imports.append(", ");
            imports.append(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        metadata.put("imports", imports.toString());

        // Extract function definitions
        String funcPattern = "def\\s+(\\w+)\\s*\\(";
        pattern = java.util.regex.Pattern.compile(funcPattern);
        matcher = pattern.matcher(content);
        StringBuilder functions = new StringBuilder();
        while (matcher.find()) {
            if (functions.length() > 0) functions.append(", ");
            functions.append(matcher.group(1));
        }
        metadata.put("functions", functions.toString());
    }

    private void extractJavascriptMetadata(String content, Map<String, String> metadata) {
        // Extract function definitions
        String funcPattern = "function\\s+(\\w+)|const\\s+(\\w+)\\s*=\\s*\\(|let\\s+(\\w+)\\s*=\\s*\\(|var\\s+(\\w+)\\s*=\\s*\\(";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(funcPattern);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        StringBuilder functions = new StringBuilder();
        while (matcher.find()) {
            if (functions.length() > 0) functions.append(", ");
            for (int i = 1; i <= 4; i++) {
                if (matcher.group(i) != null) {
                    functions.append(matcher.group(i));
                    break;
                }
            }
        }
        metadata.put("functions", functions.toString());

        // Extract imports/requires
        String importPattern = "import\\s+\\{?([^{}]*)\\}?\\s+from|require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)";
        pattern = java.util.regex.Pattern.compile(importPattern);
        matcher = pattern.matcher(content);
        StringBuilder imports = new StringBuilder();
        while (matcher.find()) {
            if (imports.length() > 0) imports.append(", ");
            imports.append(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        metadata.put("imports", imports.toString());
    }

    private static boolean isBinaryFile(String content) {
        // Simple heuristic: if more than 10% of the first 1000 chars are null or control chars, consider it binary
        int checkLength = Math.min(1000, content.length());
        int binaryCount = 0;

        for (int i = 0; i < checkLength; i++) {
            char c = content.charAt(i);
            if (c == 0 || (c < 32 && c != '\t' && c != '\n' && c != '\r')) {
                binaryCount++;
            }
        }

        return (binaryCount * 10 > checkLength);
    }

    public String generateSearchContext(String query, List<CodeSearchResult> results) {
        try {
            return ollamaService.generateCodeContext(query, results);
        } catch (IOException e) {
            return "Failed to generate context: " + e.getMessage();
        }
    }

    public int getDocumentCount() {
        if (vectorDBService != null) {
            return vectorDBService.getDocumentCount();
        }
        return 0;
    }

    public void reindexAll(Project project, ProgressIndicator indicator) {
        try {
            // Close and reinitialize vector database
            if (vectorDBService != null) {
                vectorDBService.close();
            }

            // Clean up old index files
            CleanupService.cleanupIndexFiles(project.getBasePath());

            // Create a new vector database
            try {
                vectorDBService = new VectorDBService(project.getBasePath(), ollamaService);
            } catch (IOException e) {
                System.err.println("Error reinitializing vector database: " + e.getMessage());
                indicator.setText("Error reinitializing vector database: " + e.getMessage());
                return;
            }

            // Reindex all files
            indexProject(project, indicator);
        } catch (Exception e) {
            System.err.println("Error during reindexing: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
