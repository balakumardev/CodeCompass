package dev.balakumar.codecompass;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;

public class ErrorHandler {
    private static final String NOTIFICATION_GROUP_ID = "CodeCompass";

    public static void showError(Project project, String title, String message) {
        Notification notification = new Notification(
                NOTIFICATION_GROUP_ID,
                title,
                message,
                NotificationType.ERROR
        );
        Notifications.Bus.notify(notification, project);

        // Also log the error
        System.err.println(title + ": " + message);
    }

    public static void showWarning(Project project, String title, String message) {
        Notification notification = new Notification(
                NOTIFICATION_GROUP_ID,
                title,
                message,
                NotificationType.WARNING
        );
        Notifications.Bus.notify(notification, project);

        // Also log the warning
        System.out.println(title + ": " + message);
    }

    public static void showInfo(Project project, String title, String message) {
        Notification notification = new Notification(
                NOTIFICATION_GROUP_ID,
                title,
                message,
                NotificationType.INFORMATION
        );
        Notifications.Bus.notify(notification, project);
    }

    public static void handleApiException(Project project, String serviceName, Exception e) {
        String message = e.getMessage();
        String title = serviceName + " Error";

        if (message == null) {
            showError(project, title, "Unknown error occurred");
            return;
        }

        if (message.contains("429")) {
            showError(project, title, "Rate limit exceeded. Please try again later.");
        } else if (message.contains("401") || message.contains("403")) {
            showError(project, title, "Authentication error. Please check your API key in settings.");
        } else if (message.contains("timeout") || message.contains("connect")) {
            showError(project, title, "Connection error. Please check your internet connection.");
        } else {
            showError(project, title, "Error: " + message);
        }
    }
}
