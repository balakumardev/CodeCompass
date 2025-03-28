package dev.balakumar.codecompass;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class ChatToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Create the chat panel in a non-blocking way
        ApplicationManager.getApplication().invokeLater(() -> {
            ChatPanel chatPanel = new ChatPanel(project);

            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(chatPanel, "", false);
            toolWindow.getContentManager().addContent(content);
        });
    }
}