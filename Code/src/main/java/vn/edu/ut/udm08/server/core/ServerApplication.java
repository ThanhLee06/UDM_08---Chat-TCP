package vn.edu.ut.udm08.server.core;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.Properties;
import java.util.logging.*;

/** Separate GUI process for configuring and operating the TCP server. */
public final class ServerApplication extends Application {
    private volatile ChatServer server;
    private Handler handler;
    private final Logger logger = Logger.getLogger("vn.edu.ut.udm08.server");
    @Override public void start(Stage stage) {
        TextField port = new TextField("8080");
        Label status = new Label("Đã dừng");
        TextArea log = new TextArea(); log.setEditable(false);
        Button start = new Button("Lưu và bật Server");
        Button stop = new Button("Dừng Server"); stop.setDisable(true);
        try { int configured = ServerConfig.load().getPort(); port.setText(Integer.toString(configured == 0 ? 8080 : configured)); }
        catch (RuntimeException e) { status.setText("Cấu hình lỗi: " + e.getMessage()); }
        handler = new Handler() {
            @Override public void publish(LogRecord record) {
                String line = record.getInstant() + " " + record.getLevel() + " " + record.getMessage() + "\n";
                Platform.runLater(() -> { if (log.getLength() > 100000) log.clear(); log.appendText(line); });
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        start.setOnAction(event -> {
            try {
                int parsed = Integer.parseInt(port.getText().trim());
                if (parsed < 1 || parsed > 65535) throw new IllegalArgumentException("Port phải từ 1 đến 65535");
                Path file = Path.of(System.getProperty("udm08.server.config", "config/server.properties"));
                Files.createDirectories(file.toAbsolutePath().getParent());
                Properties p = new Properties();
                if (Files.exists(file)) try (var in = Files.newInputStream(file)) { p.load(in); }
                p.setProperty("server.port", Integer.toString(parsed));
                try (var out = Files.newOutputStream(file)) { p.store(out, "UDM08 Server"); }
                ServerConfig config = ServerConfig.load();
                server = new ChatServer(config);
                start.setDisable(true); port.setDisable(true); status.setText("Đang khởi động…");
                Thread thread = new Thread(() -> {
                    try { vn.edu.ut.udm08.server.config.SampleAccounts.initialize(config); server.start(); }
                    catch (Exception e) { Platform.runLater(() -> status.setText("Khởi động thất bại: " + e.getMessage())); }
                    finally { Platform.runLater(() -> { start.setDisable(false); port.setDisable(false); stop.setDisable(true); }); }
                }, "ServerAccept");
                thread.setDaemon(true); thread.start();
                javafx.animation.Timeline check = new javafx.animation.Timeline();
                check.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(100), tick -> {
                    if (server.isRunning()) { status.setText("Đang chạy · TCP :" + server.getPort()); stop.setDisable(false); check.stop(); }
                    else if (!thread.isAlive()) check.stop();
                }));
                check.setCycleCount(javafx.animation.Animation.INDEFINITE); check.play();
            } catch (Exception e) { status.setText("Không thể bật Server: " + e.getMessage()); }
        });
        stop.setOnAction(event -> { if (server != null) server.stop(); status.setText("Đã dừng"); });
        VBox root = new VBox(10, new Label("UDM08 · TCP Server"), new Label("Port"), port, start, stop, status, log);
        root.setStyle("-fx-padding: 20;");
        stage.setTitle("UDM08 Server"); stage.setScene(new Scene(root, 700, 520)); stage.show();
    }
    @Override public void stop() { if (server != null) server.stop(); if (handler != null) logger.removeHandler(handler); }
    public static void main(String[] args) { launch(args); }
}
