package vn.edu.ut.udm08.client.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

public final class Toast {

    private Toast() {}

    public static void show(Window owner, String message) {
        if (owner == null) return;
        Platform.runLater(() -> {
            Popup popup = new Popup();
            popup.setAutoHide(true);

            Label label = new Label(message);
            label.setStyle("-fx-background-color: rgba(15, 23, 42, 0.90); " +
                           "-fx-text-fill: #ffffff; " +
                           "-fx-font-size: 13px; " +
                           "-fx-font-weight: bold; " +
                           "-fx-padding: 10 22; " +
                           "-fx-background-radius: 20; " +
                           "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.20), 12, 0, 0, 4);");

            StackPane container = new StackPane(label);
            container.setAlignment(Pos.CENTER);
            popup.getContent().add(container);

            popup.setOpacity(0);
            popup.show(owner);

            // Center toast near bottom of window
            double x = owner.getX() + (owner.getWidth() - label.prefWidth(-1)) / 2.0;
            double y = owner.getY() + owner.getHeight() - 80;
            popup.setX(x);
            popup.setY(y);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), container);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            PauseTransition stay = new PauseTransition(Duration.millis(2400));

            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), container);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);

            SequentialTransition seq = new SequentialTransition(fadeIn, stay, fadeOut);
            seq.setOnFinished(e -> popup.hide());
            seq.play();
        });
    }

    public static void showSuccess(Window owner, String message) {
        show(owner, "✓ " + message);
    }
}
