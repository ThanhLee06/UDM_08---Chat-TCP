package vn.edu.ut.udm08.client.ui;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.shared.dto.*;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
public final class GroupDialog {
    private final ChatClient client;
    private final Runnable changed;
    private final Dialog<Void> dialog=new Dialog<>();
    private final VBox content=new VBox(12);
    private final Label status=new Label();
    public GroupDialog(ChatClient client,Runnable changed){this.client=client;this.changed=changed;dialog.getDialogPane().setContent(content);dialog.getDialogPane().setPrefWidth(430);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);status.setWrapText(true);content.setStyle("-fx-padding: 16;");}
    public void create(){
        dialog.setTitle("Tạo nhóm mới");
        TextField name=new TextField();name.setPromptText("Tên nhóm (tối đa 60 ký tự)");
        TextField users=new TextField();users.setPromptText("Tên tài khoản hoặc số điện thoại, cách nhau bằng dấu phẩy");
        Button create=new Button("Tạo nhóm");create.setDefaultButton(true);
        create.setOnAction(event->{GroupCommand command=new GroupCommand();command.action="CREATE";command.name=name.getText();command.users=java.util.Arrays.stream(users.getText().split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();send(command,true);});
        content.getChildren().setAll(new Label("Tên nhóm"),name,new Label("Thành viên"),users,status,create);dialog.show();
    }
    public void show(String id){dialog.setTitle("Thông tin nhóm");content.getChildren().setAll(status);dialog.show();GroupCommand command=new GroupCommand();command.action="GET";command.convId=id;send(command,false);}
    private void send(GroupCommand command,boolean close){
        content.setDisable(true);status.setText("Đang xử lý…");
        client.requestFeature(MessageType.GROUP_REQUEST,JsonUtil.toJson(command)).whenComplete((response,error)->Platform.runLater(()->{
            content.setDisable(false);
            if(error!=null){status.setText(error.getCause()!=null?error.getCause().getMessage():error.getMessage());return;}
            status.setText("");changed.run();
            if(close){dialog.close();return;}
            try{render(JsonUtil.fromJson(response.content,GroupDetails.class));}catch(Exception e){status.setText("Không đọc được thông tin nhóm");}
        }));
    }
    private void render(GroupDetails details){
        boolean owner=details.members.stream().anyMatch(m->m.username.equals(client.getUsername())&&"owner".equals(m.role));
        Label title=new Label(details.name+" · "+details.members.size()+" thành viên");title.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        ListView<GroupDetails.Member> list=new ListView<>();list.getItems().setAll(details.members);list.setPrefHeight(220);
        list.setCellFactory(view->new ListCell<>(){protected void updateItem(GroupDetails.Member m,boolean empty){super.updateItem(m,empty);setText(empty||m==null?null:m.displayName+" (@"+m.username+")"+("owner".equals(m.role)?" · Trưởng nhóm":""));setGraphic(empty||m==null?null:AvatarImages.view(m.avatar,30));}});
        content.getChildren().setAll(title,list,status);
        if(owner){
            Button rename=new Button("Đổi tên nhóm");rename.setOnAction(e->prompt(details,"RENAME","Tên nhóm mới"));
            Button add=new Button("Thêm thành viên");add.setOnAction(e->prompt(details,"ADD","Tên tài khoản hoặc số điện thoại"));
            Button remove=new Button("Xóa thành viên đã chọn");remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());remove.setOnAction(e->{var member=list.getSelectionModel().getSelectedItem();if(member!=null&&confirm("Xóa "+member.displayName+" khỏi nhóm?")){GroupCommand command=new GroupCommand();command.action="REMOVE";command.convId=details.convId;command.users=java.util.List.of(member.username);send(command,false);}});
            content.getChildren().addAll(rename,add,remove);
        }
        Button leave=new Button("Rời nhóm");leave.setOnAction(e->{if(confirm(owner?"Rời nhóm và tự chuyển quyền trưởng nhóm cho thành viên còn lại?":"Rời khỏi nhóm này?")){GroupCommand command=new GroupCommand();command.action="LEAVE";command.convId=details.convId;send(command,true);}});content.getChildren().add(leave);
    }
    private boolean confirm(String text){return new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.OK,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    private void prompt(GroupDetails details,String action,String title){TextInputDialog input=new TextInputDialog();input.setHeaderText(title);input.showAndWait().filter(s->!s.isBlank()).ifPresent(value->{GroupCommand command=new GroupCommand();command.action=action;command.convId=details.convId;command.name=value;command.users=java.util.List.of(value);send(command,false);});}
}
