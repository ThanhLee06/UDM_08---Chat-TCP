package vn.edu.ut.udm08.client.controller;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
public final class OtpInputController {
    private static final int OTP_LENGTH = 6;
    private static final int COOLDOWN_SECONDS = 60;
    private final List<TextField> boxes;
    private final Button resendButton;
    private final Button backButton;
    private final Consumer<String> verifyAction;
    private final Runnable resendAction;
    private final Clock clock;
    private final Timeline timer;
    private Instant resendAllowedAt;
    private boolean active;
    private boolean busy;
    private boolean updatingBoxes;
    private int sessionVersion;
    public OtpInputController(List<TextField> boxes, Button resendButton, Button backButton,
                              Consumer<String> verifyAction, Runnable resendAction) {
        this(boxes, resendButton, backButton, verifyAction, resendAction, Clock.systemUTC());
    }
    OtpInputController(List<TextField> boxes, Button resendButton, Button backButton,
                       Consumer<String> verifyAction, Runnable resendAction, Clock clock) {
        if (boxes.size() != OTP_LENGTH) {
            throw new IllegalArgumentException("OTP cần đúng 6 ô nhập");
        }
        this.boxes = List.copyOf(boxes);
        this.resendButton = resendButton;
        this.backButton = backButton;
        this.verifyAction = verifyAction;
        this.resendAction = resendAction;
        this.clock = clock;
        this.timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshControls()));
        timer.setCycleCount(Timeline.INDEFINITE);
        setupBoxes();
        refreshControls();
    }
    public void start() {
        stop();
        active = true;
        clear();
        restartCooldown();
    }
    public void stop() {
        sessionVersion++;
        active = false;
        busy = false;
        resendAllowedAt = null;
        timer.stop();
        refreshControls();
    }
    public void setBusy(boolean busy) {
        this.busy = busy;
        refreshControls();
    }
    public void restartCooldown() {
        if (!active) {
            return;
        }
        resendAllowedAt = clock.instant().plusSeconds(COOLDOWN_SECONDS);
        timer.playFromStart();
        refreshControls();
    }
    public void requestResend() {
        refreshControls();
        if (resendButton.isDisabled()) {
            return;
        }
        setBusy(true);
        resendAction.run();
    }
    public void clear() {
        updatingBoxes = true;
        try {
            for (TextField box : boxes) {
                box.clear();
            }
        } finally {
            updatingBoxes = false;
        }
        if (active) {
            boxes.get(0).requestFocus();
        }
    }
    void refreshControls() {
        long remaining = 0;
        if (resendAllowedAt != null) {
            long milliseconds = java.time.Duration.between(clock.instant(), resendAllowedAt).toMillis();
            remaining = Math.max(0, (milliseconds + 999) / 1000);
        }
        for (TextField box : boxes) {
            box.setDisable(!active || busy);
        }
        backButton.setDisable(busy);
        resendButton.setDisable(!active || busy || remaining > 0);
        resendButton.setText(remaining > 0 ? "Gửi lại (" + remaining + "s)" : "Gửi lại OTP");
        if (remaining == 0) {
            timer.stop();
        }
    }
    private void setupBoxes() {
        for (int i = 0; i < boxes.size(); i++) {
            final int index = i;
            TextField box = boxes.get(i);
            box.setTextFormatter(new TextFormatter<String>(change -> {
                if (!updatingBoxes && change.getText().matches("[0-9]{6}")) {
                    String pastedCode = change.getText();
                    int version = sessionVersion;
                    Platform.runLater(() -> {
                        if (active && !busy && version == sessionVersion) {
                            fillPastedCode(pastedCode);
                        }
                    });
                    return null;
                }
                return change.getControlNewText().matches("[0-9]?") ? change : null;
            }));
            box.textProperty().addListener((observable, oldValue, newValue) -> {
                if (updatingBoxes || !active || busy || newValue.isEmpty()) {
                    return;
                }
                if (index < boxes.size() - 1) {
                    boxes.get(index + 1).requestFocus();
                }
                verifyCompleteCode();
            });
            box.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.BACK_SPACE && box.getText().isEmpty() && index > 0) {
                    boxes.get(index - 1).requestFocus();
                    boxes.get(index - 1).clear();
                    event.consume();
                }
            });
        }
    }
    private void fillPastedCode(String code) {
        updatingBoxes = true;
        try {
            for (int i = 0; i < OTP_LENGTH; i++) {
                boxes.get(i).setText(String.valueOf(code.charAt(i)));
            }
        } finally {
            updatingBoxes = false;
        }
        verifyCompleteCode();
    }
    private void verifyCompleteCode() {
        if (!active || busy) {
            return;
        }
        StringBuilder code = new StringBuilder();
        for (TextField box : boxes) {
            if (!box.getText().matches("[0-9]")) {
                return;
            }
            code.append(box.getText());
        }
        setBusy(true);
        verifyAction.accept(code.toString());
    }
}
