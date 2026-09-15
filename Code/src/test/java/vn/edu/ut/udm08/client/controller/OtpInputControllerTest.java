package vn.edu.ut.udm08.client.controller;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OtpInputControllerTest {
    private List<TextField> boxes;
    private Button resend;
    private Button back;
    private OtpInputController controller;
    private final List<String> verifiedCodes = new ArrayList<>();
    private final AtomicInteger resendRequests = new AtomicInteger();
    private final TestClock clock = new TestClock();
    @BeforeAll
    static void initializeJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
        }
    }
    @BeforeEach
    void setup() throws Exception {
        onFx(() -> {
            boxes = IntStream.range(0, 6).mapToObj(index -> new TextField()).toList();
            resend = new Button();
            back = new Button();
            controller = new OtpInputController(boxes, resend, back,
                    verifiedCodes::add, resendRequests::incrementAndGet, clock);
            controller.start();
        });
    }
    @AfterEach
    void cleanup() throws Exception {
        onFx(() -> controller.stop());
    }
    @Test
    void pastingOverPartialCodeVerifiesOnlyTheCompleteNewCode() throws Exception {
        onFx(() -> {
            boxes.get(0).setText("1");
            boxes.get(1).setText("2");
            boxes.get(3).setText("4");
            boxes.get(4).setText("5");
            boxes.get(5).setText("6");
            boxes.get(0).replaceText(0, 0, "654321");
            assertTrue(verifiedCodes.isEmpty());
        });
        onFx(() -> {
            assertEquals(List.of("654321"), verifiedCodes);
            assertTrue(back.isDisabled());
        });
    }
    @Test
    void queuedPasteCannotEnterAnotherRegistration() throws Exception {
        onFx(() -> {
            boxes.get(0).replaceText(0, 0, "654321");
            controller.stop();
            controller.start();
        });
        onFx(() -> {
            assertTrue(verifiedCodes.isEmpty());
            assertTrue(boxes.stream().allMatch(box -> box.getText().isEmpty()));
        });
    }
    @Test
    void finishingVerificationDoesNotBypassCooldown() throws Exception {
        onFx(() -> {
            controller.setBusy(true);
            clock.advance(10);
            controller.setBusy(false);
            assertTrue(resend.isDisabled());
            controller.requestResend();
            assertEquals(0, resendRequests.get());
            clock.advance(50);
            controller.refreshControls();
            assertFalse(resend.isDisabled());
            controller.requestResend();
            controller.requestResend();
            assertEquals(1, resendRequests.get());
            assertTrue(resend.isDisabled());
        });
    }
    @Test
    void expiredCooldownDoesNotUnlockAnActiveRequest() throws Exception {
        onFx(() -> {
            controller.setBusy(true);
            clock.advance(61);
            controller.refreshControls();
            assertTrue(resend.isDisabled());
            assertTrue(back.isDisabled());
            controller.setBusy(false);
            assertFalse(resend.isDisabled());
        });
    }
    @Test
    void startingAnotherRegistrationReplacesOldDeadline() throws Exception {
        onFx(() -> {
            clock.advance(40);
            controller.stop();
            controller.start();
            clock.advance(20);
            controller.refreshControls();
            assertTrue(resend.isDisabled());
            assertEquals("Gửi lại (40s)", resend.getText());
            clock.advance(40);
            controller.refreshControls();
            assertFalse(resend.isDisabled());
        });
    }
    @Test
    void onlySixValidDigitsTriggerVerification() throws Exception {
        onFx(() -> {
            boxes.get(0).setText("x");
            assertEquals("", boxes.get(0).getText());
            for (int i = 0; i < 5; i++) {
                boxes.get(i).setText(String.valueOf(i + 1));
            }
            assertTrue(verifiedCodes.isEmpty());
            boxes.get(5).setText("6");
            assertEquals(List.of("123456"), verifiedCodes);
        });
    }
    private static void onFx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
        @Override
        public Instant instant() {
            return now;
        }
    }
}
