package vn.edu.ut.udm08.client.controller;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class LoginViewTest {
    @TempDir Path temp;
    @BeforeAll static void initToolkit() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        }
        catch (IllegalStateException alreadyStarted) {  }
    }
    @Test void registrationFormLoadsWithEmailAndNoInlinePhoneOtp() throws Exception {
        String previous = System.getProperty("udm08.db.url");
        System.setProperty("udm08.db.url", "jdbc:sqlite:" + temp.resolve("ui.db"));
        try {
            FutureTask<Void> task = new FutureTask<>(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
                Parent root = loader.load();
                new Scene(root, 420, 680);
                root.applyCss(); root.layout();
                ((Button) root.lookup("#tabRegisterBtn")).fire();
                root.applyCss(); root.layout();
                assertTrue(root.lookup("#registerPane").isVisible());
                assertNotNull(root.lookup("#regEmailField"));
                assertNull(root.lookup("#regOtpField"));
                assertNull(root.lookup("#sendOtpBtn"));
                assertNotNull(root.lookup("#phoneField"));
                var button = root.lookup("#registerBtn");
                assertTrue(button.localToScene(button.getBoundsInLocal()).getMaxY() <= 680);
                WritableImage image = root.snapshot(null, null);
                java.awt.image.BufferedImage png = new java.awt.image.BufferedImage(
                        (int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < png.getHeight(); y++) {
                    for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
                javax.imageio.ImageIO.write(png, "png", Path.of("target", "registration-ui.png").toFile());
                return null;
            });
            Platform.runLater(task);
            task.get(15, TimeUnit.SECONDS);
        } finally {
            if (previous == null) System.clearProperty("udm08.db.url");
            else System.setProperty("udm08.db.url", previous);
        }
    }
}
