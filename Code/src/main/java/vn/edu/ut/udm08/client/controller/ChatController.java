package vn.edu.ut.udm08.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

import java.util.List;
import java.util.UUID;

public class ChatController {

    @FXML private ListView<UserProfile> onlineUsersList;
    @FXML private Label chatPartnerName;
    @FXML private Label chatPartnerInitial;
    @FXML private StackPane chatPartnerAvatar;
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageContainer;   
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private VBox emptyStatePane;    

    // Danh sách người dùng online, gắn trực tiếp vào ListView.
    private final ObservableList<UserProfile> onlineUsers = FXCollections.observableArrayList();

    private String currentUsername;
    private UserProfile selectedUser; 


   
    private static final String[] AVATAR_COLORS = {
            "#0068ff", "#00c853", "#ff6d00", "#e91e63",
            "#9c27b0", "#00acc1", "#f4511e", "#5e35b1"
    };

  
    private MessageSendListener sendListener;

    public interface MessageSendListener {
        void onSendMessage(ProtocolMessage message);
    }

    @FXML
    public void initialize() {
       
        onlineUsersList.setItems(onlineUsers);
     
        onlineUsersList.setCellFactory(list -> new UserListCell());

      
        onlineUsersList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectUser(newVal);
            }
        });

       
        sendButton.setDisable(true);
        messageInput.setDisable(true);

  
        messageInput.setOnAction(e -> handleSend());

      
        emptyStatePane.setVisible(true);
    }


    // Gọi sau khi đăng nhập thành công
    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public void setSendListener(MessageSendListener listener) {
        this.sendListener = listener;
    }

    // Gọi mỗi khi server gửi xuống danh sách user đang online (
    public void updateOnlineUsers(List<UserProfile> users) {
       
        Platform.runLater(() -> {
           
            onlineUsers.setAll(users);
        });
    }

    // Gọi mỗi khi server gửi xuống 1 tin nhắn CHAT 
   
    public void receiveMessage(ProtocolMessage message) {
        Platform.runLater(() -> {
          
            boolean isMine = message.sender != null && message.sender.equals(currentUsername);

        
            boolean belongsToCurrentChat = selectedUser != null &&
                    (message.sender.equals(selectedUser.username) || isMine);
            if (belongsToCurrentChat) {
             
                addMessageBubble(message.content, isMine);
            }
        });
    }

    // Xử lý khi người dùng click chọn 1 người để mở khung chat 
    private void selectUser(UserProfile user) {
        this.selectedUser = user;

  
        chatPartnerName.setText(user.username);
        chatPartnerInitial.setText(user.username.substring(0, 1).toUpperCase());
        chatPartnerAvatar.setStyle("-fx-background-color: " + avatarColorFor(user.username) + ";");

   
        messageContainer.getChildren().clear();

      
        sendButton.setDisable(false);
        messageInput.setDisable(false);
        emptyStatePane.setVisible(false);
    }

   
    @FXML
    private void handleSend() {
        String content = messageInput.getText().trim();
        if (content.isEmpty() || selectedUser == null) return; 

      
        ProtocolMessage message = new ProtocolMessage(MessageType.CHAT);
        message.messageId = UUID.randomUUID().toString();   
        message.sender = currentUsername;                    
        message.target = selectedUser.username;             
        message.content = content;                           
        message.timestamp = System.currentTimeMillis();      

     hơn
        addMessageBubble(content, true);
        messageInput.clear(); 

        
        if (sendListener != null) {
            sendListener.onSendMessage(message);
        }
    }

    // Tạo 1 bubble tin nhắn và thêm vào khung chat
    private void addMessageBubble(String content, boolean isMine) {
        Label bubble = new Label(content);
        bubble.getStyleClass().add(isMine ? "message-bubble-sent" : "message-bubble-received");
        bubble.setWrapText(true);   
        bubble.setMaxWidth(400);   
      
        HBox row = new HBox(bubble);
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageContainer.getChildren().add(row);

       
        messageScrollPane.layout();
        messageScrollPane.setVvalue(1.0);
    }

    // Sinh màu avatar ổn định theo username
   
    private static String avatarColorFor(String username) {
      
        int index = Math.abs(username.hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    // Cell tùy chỉnh cho ListView,
    private static class UserListCell extends ListCell<UserProfile> {
        @Override
        protected void updateItem(UserProfile user, boolean empty) {
            super.updateItem(user, empty);

          
            if (empty || user == null) {
                setText(null);
                setGraphic(null);
            } else {
               
                Label initial = new Label(user.username.substring(0, 1).toUpperCase());
                initial.getStyleClass().add("avatar-text-small");

            
                StackPane avatar = new StackPane(initial);
                avatar.getStyleClass().add("avatar-circle-small");
                avatar.setStyle("-fx-background-color: " + avatarColorFor(user.username) + ";");

                
                Label name = new Label(user.username);
                name.getStyleClass().add("cell-name");

               
                HBox box = new HBox(10, avatar, name);
                box.setAlignment(Pos.CENTER_LEFT);

                setGraphic(box); 
                setText(null);
            }
        }
    }
}