package vn.edu.ut.udm08.client.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import vn.edu.ut.udm08.client.controller.ChatController;
import vn.edu.ut.udm08.shared.model.UserProfile;

import java.util.List;

public class ChatUIPreview extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("Chat TCP - UDM_08");
        primaryStage.setScene(scene);
        primaryStage.show();

        // DỮ LIỆU GIẢ để test riêng màn hình này, xóa khi tích hợp network thật
        ChatController controller = loader.getController();
        controller.setCurrentUsername("me");
        controller.updateOnlineUsers(List.of(
                new UserProfile("An", null),
                new UserProfile("Binh", null),
                new UserProfile("Chi", null)
        ));
        controller.setSendListener(msg ->
                System.out.println("Gửi đi: " + msg.sender + " -> " + msg.target + ": " + msg.content));
    }

    public static void main(String[] args) {
        launch(args);
    }
}