package dev.balakumar.codecompass;

import java.util.Map;

public class CodeSearchResult implements Comparable<CodeSearchResult> {
    private final String id;
    private final String filePath;
    private final String summary;
    private final float similarity;
    private final Map<String, String> metadata;

    public CodeSearchResult(String id, String filePath, String summary, float similarity, Map<String, String> metadata) {
        this.id = id;
        this.filePath = filePath;
        this.summary = summary;
        this.similarity = similarity;
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public String getFilePath() { return filePath; }
    public String getSummary() { return summary; }
    public float getSimilarity() { return similarity; }
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public int compareTo(CodeSearchResult other) {
        return Float.compare(other.similarity, this.similarity);
    }
}