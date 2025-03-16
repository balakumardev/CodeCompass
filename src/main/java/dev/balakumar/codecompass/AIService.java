package dev.balakumar.codecompass;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface AIService {
    /**
     * Generate an embedding vector for the given text
     *
     * @param text The text to embed
     * @return A float array containing the embedding vector
     * @throws IOException If the embedding generation fails
     */
    float[] getEmbedding(String text) throws IOException;

    /**
     * Generate a summary of the given code content
     *
     * @param codeContent The code content to summarize
     * @param fileName The name of the file containing the code
     * @return A summary of the code
     * @throws IOException If the summary generation fails
     */
    String generateSummary(String codeContent, String fileName) throws IOException;

    /**
     * Generate context information for a search query based on the search results
     *
     * @param query The search query
     * @param results The search results
     * @return Context information for the search query
     * @throws IOException If the context generation fails
     */
    String generateCodeContext(String query, List<CodeSearchResult> results) throws IOException;

    /**
     * Ask a question about the codebase using the provided relevant files
     *
     * @param question The question to ask
     * @param relevantFiles The relevant files to use for answering the question
     * @return The answer to the question
     * @throws IOException If the question answering fails
     */
    String askQuestion(String question, List<CodeSearchResult> relevantFiles) throws IOException;

    /**
     * Ask a question about the codebase using the provided relevant files and chat history
     *
     * @param question The question to ask
     * @param relevantFiles The relevant files to use for answering the question
     * @param chatHistory Previous messages in the conversation as a list of maps
     * @return The answer to the question
     * @throws IOException If the question answering fails
     */
    String askQuestionWithHistory(String question, List<CodeSearchResult> relevantFiles, List<Map<String, Object>> chatHistory) throws IOException;

    /**
     * Test the connection to the AI service
     *
     * @return true if the connection is successful, false otherwise
     */
    boolean testConnection();
}