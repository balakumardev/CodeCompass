package dev.balakumar.codecompass;

import java.io.IOException;

public interface EmbeddingService {
    float[] getEmbedding(String text) throws IOException;
    boolean testConnection();
}