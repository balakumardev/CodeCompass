package dev.balakumar.codecompass;

import java.io.IOException;
import java.util.List;

public interface AIService {
    float[] getEmbedding(String text) throws IOException;
    String generateSummary(String codeContent, String fileName) throws IOException;
    String generateCodeContext(String query, List<CodeSearchResult> results) throws IOException;
    String askQuestion(String question, List<CodeSearchResult> relevantFiles) throws IOException;
    boolean testConnection();
}
