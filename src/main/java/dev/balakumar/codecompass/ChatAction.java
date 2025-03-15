package dev.balakumar.codecompass;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

public class ChatAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeCompass Chat");
            if (toolWindow != null) {
                toolWindow.show();
            }
        }
    }
}
