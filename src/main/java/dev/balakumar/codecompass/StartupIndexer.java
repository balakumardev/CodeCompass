package dev.balakumar.codecompass;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProgressIndicator;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StartupIndexer implements ProjectActivity {
    private static final String NOTIFICATION_GROUP_ID = "CodeCompass";

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        CodeMapperSettingsState settings = CodeMapperSettingsState.getInstance(project);
        if (!settings.enableStartupIndexing) {
            System.out.println("Startup indexing is disabled.");
            return Unit.INSTANCE;
        }

        if (project == null || project.getBasePath() == null) {
            System.err.println("Cannot start indexing: project or project path is null");
            return Unit.INSTANCE;
        }

        // First check if services are available
        checkServicesAvailability(project);

        // Then start indexing if services are available
        new Task.Backgroundable(project, "Indexing with CodeCompass") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Starting AI-based indexing...");
                indicator.setIndeterminate(true);

                try {
                    CleanupService.cleanupIndexFiles(project.getBasePath());
                    SimpleIndexer indexer = new SimpleIndexer(project);
                    indexer.indexProject(project, indicator);

                    if (!indicator.isCanceled()) {
                        indicator.setText("Indexing completed (CodeCompass).");
                        showNotification(project, "CodeCompass Indexing Complete",
                                "Successfully indexed " + indexer.getDocumentCount() + " files.",
                                NotificationType.INFORMATION);
                    }
                } catch (Exception e) {
                    String errorMessage = "Error during indexing: " + e.getMessage();
                    indicator.setText(errorMessage);
                    e.printStackTrace();

                    showNotification(project, "CodeCompass Indexing Failed",
                            errorMessage, NotificationType.ERROR);
                }
            }
        }.queue();

        return Unit.INSTANCE;
    }

    private void checkServicesAvailability(Project project) {
        new Task.Backgroundable(project, "Checking CodeCompass Services") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Checking AI service and vector database availability...");
                indicator.setIndeterminate(true);

                boolean aiServiceAvailable = false;
                boolean vectorDBAvailable = false;
                String aiServiceName = "";

                try {
                    // Check AI service
                    EmbeddingService aiService = ProviderSettings.getEmbeddingService(project);
                    aiServiceName = aiService.getClass().getSimpleName().replace("Service", "");
                    aiServiceAvailable = aiService.testConnection();

                    // Check vector DB
                    try {
                        VectorDBService vectorDBService = new VectorDBService(project.getBasePath(), aiService);
                        vectorDBAvailable = vectorDBService.isConnected();
                        vectorDBService.close();
                    } catch (Exception e) {
                        System.err.println("Vector DB connection error: " + e.getMessage());
                    }

                    // Show appropriate notification based on service status
                    if (!aiServiceAvailable && !vectorDBAvailable) {
                        showNotification(project, "CodeCompass Services Unavailable",
                                "Both " + aiServiceName + " AI service and Qdrant vector database are unavailable. " +
                                        "CodeCompass features will not work correctly.",
                                NotificationType.ERROR);
                    } else if (!aiServiceAvailable) {
                        showNotification(project, "CodeCompass AI Service Unavailable",
                                aiServiceName + " AI service is unavailable. " +
                                        "Search and question answering features will not work correctly.",
                                NotificationType.WARNING);
                    } else if (!vectorDBAvailable) {
                        showNotification(project, "CodeCompass Vector Database Unavailable",
                                "Qdrant vector database is unavailable. " +
                                        "CodeCompass features will not work correctly.",
                                NotificationType.WARNING);
                    }
                } catch (Exception e) {
                    System.err.println("Error checking services: " + e.getMessage());
                    showNotification(project, "CodeCompass Service Check Failed",
                            "Failed to check service availability: " + e.getMessage(),
                            NotificationType.ERROR);
                }
            }
        }.queue();
    }

    private void showNotification(Project project, String title, String content, NotificationType type) {
        Notification notification = new Notification(
                NOTIFICATION_GROUP_ID,
                title,
                content,
                type
        );
        Notifications.Bus.notify(notification, project);
    }
}