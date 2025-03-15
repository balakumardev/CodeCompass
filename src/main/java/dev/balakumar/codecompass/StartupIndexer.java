package dev.balakumar.codecompass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProgressIndicator;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StartupIndexer implements ProjectActivity {
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (project == null || project.getBasePath() == null) {
            System.err.println("Cannot start indexing: project or project path is null");
            return Unit.INSTANCE;
        }
        new Task.Backgroundable(project, "Indexing with CodeMapper") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Starting AI-based indexing...");
                indicator.setIndeterminate(true);
                try {
                    CleanupService.cleanupIndexFiles(project.getBasePath());
                    SimpleIndexer indexer = new SimpleIndexer(project);
                    indexer.indexProject(project, indicator);
                    indicator.setText("Indexing completed (CodeMapper).");
                } catch (Exception e) {
                    indicator.setText("Error during indexing: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.queue();
        return Unit.INSTANCE;
    }
}