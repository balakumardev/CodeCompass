package dev.balakumar.codecompass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressIndicator;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleIndexer {
    private final AIService aiService;
    private VectorDBService vectorDBService;
    private Project project;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    private static final float DEFAULT_SIMILARITY_THRESHOLD = 0.5f;

    public SimpleIndexer(Project project) {
        this.project = project;
        this.aiService = ProviderSettings.getAIService(project);
        try {
            this.vectorDBService = new VectorDBService(project.getBasePath(), aiService);
        } catch (IOException e) {
            System.err.println("Error initializing vector database: " + e.getMessage());
        }
    }

    public List<CodeSearchResult> search(String query, int limit) {
        return search(query, limit, null, DEFAULT_SIMILARITY_THRESHOLD);
    }

    public List<CodeSearchResult> search(String query, int limit, Map<String, String> filters) {
        return search(query, limit, filters, DEFAULT_SIMILARITY_THRESHOLD);
    }

    public List<CodeSearchResult> search(String query, int limit, Map<String, String> filters, float similarityThreshold) {
        if (project == null) {
            System.err.println("Error: Project is null in search method");
            return Collections.emptyList();
        }

        if (vectorDBService == null) {
            try {
                vectorDBService = new VectorDBService(project.getBasePath(), aiService);
            } catch (IOException e) {
                System.err.println("Error initializing vector database for search: " + e.getMessage());
                return Collections.emptyList();
            }
        }

        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                List<CodeSearchResult> results = vectorDBService.search(query, limit, filters, similarityThreshold);
                return results;
            } catch (Exception e) {
                retries++;
                System.err.println("Error during search (attempt " + retries + " of " + MAX_RETRIES + "): " + e.getMessage());

                if (retries < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    public void indexProject(Project project, ProgressIndicator indicator) {
        this.project = project;
        AtomicBoolean serviceError = new AtomicBoolean(false);

        try {
            // Test AI service connection with retries
            boolean connected = testAIServiceWithRetry();
            System.out.println("AI Service connection test: " + (connected ? "SUCCESS" : "FAILED"));

            if (!connected) {
                indicator.setText("Failed to connect to AI Service. Make sure it's running.");
                return;
            }

            // Test vector DB connection
            if (vectorDBService == null) {
                try {
                    vectorDBService = new VectorDBService(project.getBasePath(), aiService);
                } catch (IOException e) {
                    System.err.println("Failed to initialize vector database: " + e.getMessage());
                    indicator.setText("Failed to initialize vector database: " + e.getMessage());
                    return;
                }
            }

            if (!vectorDBService.isConnected()) {
                indicator.setText("Failed to connect to Vector Database. Make sure Qdrant is running.");
                return;
            }

            List<IndexedFile> files = collectProjectFiles(project);
            indicator.setText("Indexing files with AI...");
            indicator.setIndeterminate(false);
            int total = files.size();
            int current = 0;
            int processed = 0;
            int batchSize = 5;
            int errorCount = 0;

            System.out.println("Starting to index " + total + " files");

            for (int i = 0; i < files.size(); i += batchSize) {
                if (indicator.isCanceled()) {
                    break;
                }

                int endIndex = Math.min(i + batchSize, files.size());
                for (int j = i; j < endIndex; j++) {
                    IndexedFile file = files.get(j);
                    String filePath = file.virtualFile.getPath();
                    indicator.setText("Processing: " + filePath);
                    indicator.setFraction((double) current / total);

                    if (file.indexer.project == null) {
                        file.indexer.project = project;
                    }

                    if (vectorDBService == null && file.indexer.vectorDBService != null) {
                        vectorDBService = file.indexer.vectorDBService;
                    }

                    try {
                        indexSingleFile(file.virtualFile, file.indexer);
                        current++;
                        processed++;
                    } catch (Exception e) {
                        errorCount++;
                        System.err.println("Error indexing file " + filePath + ": " + e.getMessage());

                        // Check if we're having persistent service errors
                        if (e.getMessage() != null &&
                                (e.getMessage().contains("connection") ||
                                        e.getMessage().contains("timeout") ||
                                        e.getMessage().contains("unavailable"))) {

                            serviceError.set(true);

                            // Try to reconnect to services
                            if (testAIServiceWithRetry() && vectorDBService.isConnected()) {
                                serviceError.set(false);
                            } else {
                                // If reconnection failed, abort indexing
                                indicator.setText("Service connection lost. Aborting indexing.");
                                break;
                            }
                        }

                        current++;
                    }

                    // If we're having persistent service errors, abort
                    if (serviceError.get()) {
                        break;
                    }
                }

                if (serviceError.get()) {
                    break;
                }

                if (vectorDBService != null) {
                    try {
                        vectorDBService.saveIndex();
                    } catch (Exception e) {
                        System.err.println("Error saving index: " + e.getMessage());
                    }
                }

                System.out.println("Processed batch. Total processed: " + processed + " / " + total);
                System.gc();
            }

            if (vectorDBService != null) {
                System.out.println("Indexing completed. Total documents: " + vectorDBService.getDocumentCount());
                if (errorCount > 0) {
                    indicator.setText("Indexing completed with " + errorCount + " errors. Total documents: " +
                            vectorDBService.getDocumentCount());
                } else {
                    indicator.setText("Indexing completed successfully. Total documents: " +
                            vectorDBService.getDocumentCount());
                }
            } else {
                System.err.println("Indexing completed but vector database is null");
                indicator.setText("Indexing completed but vector database is unavailable.");
            }
        } catch (Exception e) {
            System.err.println("Error during indexing: " + e.getMessage());
            e.printStackTrace();
            indicator.setText("Error during indexing: " + e.getMessage());
        }
    }

    private boolean testAIServiceWithRetry() {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                boolean connected = aiService.testConnection();
                if (connected) {
                    return true;
                }
            } catch (Exception e) {
                System.err.println("AI service connection test failed (attempt " + (retries + 1) +
                        " of " + MAX_RETRIES + "): " + e.getMessage());
            }

            retries++;
            if (retries < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    public static List<IndexedFile> collectProjectFiles(Project project) {
        List<IndexedFile> result = new ArrayList<>();
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            SimpleIndexer indexer = new SimpleIndexer(project);
            collectDirectory(baseDir, indexer, result);
        }
        return result;
    }

    private static void collectDirectory(VirtualFile directory, SimpleIndexer indexer, List<IndexedFile> collector) {
        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                if (shouldSkipDirectory(file.getName())) {
                    continue;
                }
                collectDirectory(file, indexer, collector);
            } else {
                if (isCodeFile(file)) {
                    collector.add(new IndexedFile(file, indexer));
                }
            }
        }
    }

    private static boolean shouldSkipDirectory(String dirName) {
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

    public void indexSingleFile(VirtualFile file, SimpleIndexer indexer) {
        int retries = 0;

        while (retries < MAX_RETRIES) {
            try {
                if (file.getLength() > 500000) {
                    System.out.println("Skipping large file: " + file.getPath());
                    return;
                }

                String content = new String(file.contentsToByteArray());
                if (isBinaryFile(content)) {
                    System.out.println("Skipping binary file: " + file.getPath());
                    return;
                }

                Map<String, String> metadata = extractMetadata(file, content);
                String language = getLanguageFromFileName(file.getName());

                // Create enhanced text with metadata and content
                StringBuilder enhancedText = new StringBuilder();
                enhancedText.append("File: ").append(file.getName()).append("\n");
                enhancedText.append("Language: ").append(language).append("\n");

                if (metadata.containsKey("functions")) {
                    enhancedText.append("Functions: ").append(metadata.get("functions")).append("\n");
                }

                if (metadata.containsKey("classes")) {
                    enhancedText.append("Classes: ").append(metadata.get("classes")).append("\n");
                }

                enhancedText.append("Code:\n").append(content);

                String summary = aiService.generateSummary(content, file.getName());
                vectorDBService.addOrUpdateDocument(file.getPath(), enhancedText.toString(), file.getPath(), summary, metadata);

                // Success, exit retry loop
                return;

            } catch (Exception e) {
                retries++;
                System.err.println("Error indexing " + file.getPath() + " (attempt " + retries +
                        " of " + MAX_RETRIES + "): " + e.getMessage());

                if (retries < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // If we get here, all retries failed
        System.err.println("Failed to index " + file.getPath() + " after " + MAX_RETRIES + " attempts");
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

    private Map<String, String> extractMetadata(VirtualFile file, String content) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("filename", file.getName());
        metadata.put("extension", file.getExtension() != null ? file.getExtension() : "");
        metadata.put("size", String.valueOf(file.getLength()));
        metadata.put("lastModified", String.valueOf(file.getTimeStamp()));

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
        String packagePattern = "package\\s+([\\w.]+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(packagePattern);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            metadata.put("package", matcher.group(1));
        }

        String classPattern = "class\\s+(\\w+)";
        pattern = java.util.regex.Pattern.compile(classPattern);
        matcher = pattern.matcher(content);
        StringBuilder classes = new StringBuilder();
        while (matcher.find()) {
            if (classes.length() > 0) classes.append(", ");
            classes.append(matcher.group(1));
        }
        metadata.put("classes", classes.toString());

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
        String importPattern = "import\\s+(\\w+)|from\\s+(\\w+)\\s+import";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(importPattern);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        StringBuilder imports = new StringBuilder();
        while (matcher.find()) {
            if (imports.length() > 0) imports.append(", ");
            String imp = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            imports.append(imp);
        }
        metadata.put("imports", imports.toString());

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

        String importPattern = "import\\s+\\{?([^{}]+)\\}?\\s+from|require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)";
        pattern = java.util.regex.Pattern.compile(importPattern);
        matcher = pattern.matcher(content);
        StringBuilder imports = new StringBuilder();
        while (matcher.find()) {
            if (imports.length() > 0) imports.append(", ");
            String imp = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            imports.append(imp);
        }
        metadata.put("imports", imports.toString());
    }

    private static boolean isBinaryFile(String content) {
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
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                return aiService.generateCodeContext(query, results);
            } catch (IOException e) {
                retries++;
                System.err.println("Error generating search context (attempt " + retries +
                        " of " + MAX_RETRIES + "): " + e.getMessage());

                if (retries < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return "Failed to generate context after " + MAX_RETRIES + " attempts.";
    }

    public int getDocumentCount() {
        if (vectorDBService != null) {
            return vectorDBService.getDocumentCount();
        }
        return 0;
    }

    public void reindexAll(Project project, ProgressIndicator indicator) {
        try {
            if (vectorDBService != null) {
                vectorDBService.close();
            }

            CleanupService.cleanupIndexFiles(project.getBasePath());

            try {
                vectorDBService = new VectorDBService(project.getBasePath(), aiService);
            } catch (IOException e) {
                System.err.println("Error reinitializing vector database: " + e.getMessage());
                indicator.setText("Error reinitializing vector database: " + e.getMessage());
                return;
            }

            indexProject(project, indicator);

        } catch (Exception e) {
            System.err.println("Error during reindexing: " + e.getMessage());
            e.printStackTrace();
            indicator.setText("Error during reindexing: " + e.getMessage());
        }
    }

    public static class IndexedFile {
        public final VirtualFile virtualFile;
        public final SimpleIndexer indexer;

        public IndexedFile(VirtualFile virtualFile, SimpleIndexer indexer) {
            this.virtualFile = virtualFile;
            this.indexer = indexer;
        }
    }
}