package dev.balakumar.codecompass;

import java.io.File;

public class CleanupService {
    /**
     * Delete all index files to force a fresh start
     */
    public static void cleanupIndexFiles(String projectPath) {
        try {
            File dbDir = new File(projectPath, ".codemapper");
            if (dbDir.exists()) {
                // We only need to delete the config file as Qdrant
                // manages the actual vector data
                File configFile = new File(dbDir, "codemapper_config.json");
                if (configFile.exists()) {
                    boolean deleted = configFile.delete();
                    System.out.println("Deleted configuration file: " + deleted);
                }
                // Delete old index files if they exist (from a previous version)
                File indexFile = new File(dbDir, "codemapper_index.dat");
                File docsFile = new File(dbDir, "codemapper_docs.dat");
                if (indexFile.exists()) {
                    boolean deleted = indexFile.delete();
                    System.out.println("Deleted old index file: " + deleted);
                }
                if (docsFile.exists()) {
                    boolean deleted = docsFile.delete();
                    System.out.println("Deleted old documents file: " + deleted);
                }
            }
        } catch (Exception e) {
            System.err.println("Error cleaning up index files: " + e.getMessage());
        }
    }
}