package vn.edu.ut.udm08.client.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

public final class AppDialog {

    private AppDialog() {}

    /**
     * Show a custom confirmation dialog with Vietnamese buttons (Hủy / Xác nhận or custom text)
     */
    public static boolean confirm(Window owner, String title, String message, String confirmButtonText, boolean isDanger) {
        Dialog<ButtonType> dialog = createBaseDialog(title);

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.getStyleClass().add("app-dialog-message");

        VBox body = new VBox(msgLabel);
        body.setPadding(new Insets(16, 20, 16, 20));
        dialog.getDialogPane().setContent(body);

        ButtonType confirmType = new ButtonType(confirmButtonText != null ? confirmButtonText : "Xác nhận", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(confirmType, cancelType);

        styleButtons(dialog, confirmType, cancelType, isDanger);

        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == confirmType;
    }

    public static boolean confirm(String title, String message) {
        return confirm(null, title, message, "Xác nhận", false);
    }

    public static boolean confirmDanger(String title, String message, String confirmText) {
        return confirm(null, title, message, confirmText, true);
    }

    /**
     * Show a custom text input dialog
     */
    public static Optional<String> prompt(Window owner, String title, String message, String placeholder, String initialValue) {
        Dialog<String> dialog = createBaseDialog(title);

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.getStyleClass().add("app-dialog-message");

        TextField inputField = new TextField(initialValue != null ? initialValue : "");
        if (placeholder != null) {
            inputField.setPromptText(placeholder);
        }
        inputField.getStyleClass().add("app-dialog-input-field");

        VBox body = new VBox(12, msgLabel, inputField);
        body.setPadding(new Insets(16, 20, 16, 20));
        dialog.getDialogPane().setContent(body);

        ButtonType confirmType = new ButtonType("Xác nhận", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(confirmType, cancelType);
        styleButtons(dialog, confirmType, cancelType, false);

        dialog.setResultConverter(button -> button == confirmType ? inputField.getText() : null);

        return dialog.showAndWait();
    }

    public static Optional<String> prompt(String title, String message, String placeholder) {
        return prompt(null, title, message, placeholder, "");
    }

    /**
     * Show an alert/info dialog
     */
    public static void info(Window owner, String title, String message) {
        Dialog<ButtonType> dialog = createBaseDialog(title);

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.getStyleClass().add("app-dialog-message");

        VBox body = new VBox(msgLabel);
        body.setPadding(new Insets(16, 20, 16, 20));
        dialog.getDialogPane().setContent(body);

        ButtonType closeType = new ButtonType("Đóng", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(closeType);

        Button closeBtn = (Button) dialog.getDialogPane().lookupButton(closeType);
        if (closeBtn != null) {
            closeBtn.getStyleClass().addAll("app-dialog-btn", "app-dialog-btn-primary");
        }

        dialog.showAndWait();
    }

    public static void info(String title, String message) {
        info(null, title, message);
    }

    public static void warning(String title, String message) {
        info(null, title, message);
    }

    private static <T> Dialog<T> createBaseDialog(String title) {
        Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("dialog-pane");

        try {
            var cssUrl = AppDialog.class.getResource("/css/dialog.css");
            if (cssUrl != null) {
                pane.getStylesheets().add(cssUrl.toExternalForm());
            }
            var baseCssUrl = AppDialog.class.getResource("/css/base.css");
            if (baseCssUrl != null) {
                pane.getStylesheets().add(baseCssUrl.toExternalForm());
            }
        } catch (Exception ignored) {}

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #172b4d;");
        titleLabel.setPadding(new Insets(16, 20, 4, 20));
        pane.setHeader(titleLabel);

        return dialog;
    }

    private static void styleButtons(Dialog<?> dialog, ButtonType confirmType, ButtonType cancelType, boolean isDanger) {
        DialogPane pane = dialog.getDialogPane();
        Button confirmBtn = (Button) pane.lookupButton(confirmType);
        Button cancelBtn = (Button) pane.lookupButton(cancelType);

        if (confirmBtn != null) {
            confirmBtn.getStyleClass().addAll("app-dialog-btn", isDanger ? "app-dialog-btn-danger" : "app-dialog-btn-primary");
        }
        if (cancelBtn != null) {
            cancelBtn.getStyleClass().add("app-dialog-btn");
        }
    }
}
