package dev.balakumar.codecompass;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface GenerationService {
    String generateSummary(String codeContent, String fileName) throws IOException;
    String generateCodeContext(String query, List<CodeSearchResult> results) throws IOException;
    String askQuestion(String question, List<CodeSearchResult> relevantFiles) throws IOException;
    String askQuestionWithHistory(String question, List<CodeSearchResult> relevantFiles, List<Map<String, Object>> chatHistory) throws IOException;
    boolean testConnection();
}