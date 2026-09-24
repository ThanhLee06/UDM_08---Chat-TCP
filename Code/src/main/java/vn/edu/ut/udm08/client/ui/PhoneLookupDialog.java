package vn.edu.ut.udm08.client.ui;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;
import vn.edu.ut.udm08.client.network.*;
import vn.edu.ut.udm08.shared.model.*;
public final class PhoneLookupDialog {
    public static void show(ChatClient client,Consumer<ConversationSummary> opened){
        Dialog<Void> dialog=new Dialog<>();dialog.setTitle("Tìm bạn bằng số điện thoại");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextField phone=new TextField();phone.setPromptText("Nhập đủ số điện thoại, ví dụ 0901234567");
        Label status=new Label();status.setWrapText(true);Button find=new Button("Tìm người dùng");Button chat=new Button("Nhắn tin");chat.setVisible(false);chat.setManaged(false);
        VBox box=new VBox(12,new Label("Số điện thoại"),phone,find,status,chat);box.setStyle("-fx-padding: 16;");dialog.getDialogPane().setContent(box);dialog.getDialogPane().setPrefWidth(430);
        Runnable search=()->{find.setDisable(true);chat.setVisible(false);chat.setManaged(false);status.setGraphic(null);status.setText("Đang tìm…");client.requestFeature(MessageType.PHONE_LOOKUP,phone.getText()).whenComplete((response,error)->Platform.runLater(()->{
            find.setDisable(false);if(error!=null){status.setText(error.getMessage());return;}if(response.users==null||response.users.isEmpty()){status.setText("Không tìm thấy tài khoản khác với số điện thoại này.");return;}
            UserProfile user=response.users.get(0);status.setText(user.displayName+" (@"+user.username+")");status.setGraphic(AvatarImages.view(user.avatarId,48));chat.setVisible(true);chat.setManaged(true);
            chat.setOnAction(event->{chat.setDisable(true);try{client.openDirectMessage(user.username,new OpenDmCallback(){public void onSuccess(OpenDmResult result){Platform.runLater(()->{opened.accept(result.getConversation());dialog.close();});}public void onFailure(String requestId,String code,String message){Platform.runLater(()->{chat.setDisable(false);status.setText(message);});}});}catch(Exception e){chat.setDisable(false);status.setText("Không mở được hội thoại. Hãy kết nối lại.");}});
        }));};find.setOnAction(e->search.run());phone.setOnAction(e->{if(!find.isDisabled())search.run();});dialog.show();
    }
}
