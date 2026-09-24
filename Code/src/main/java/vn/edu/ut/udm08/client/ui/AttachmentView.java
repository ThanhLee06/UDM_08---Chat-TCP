package vn.edu.ut.udm08.client.ui;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import vn.edu.ut.udm08.client.network.*;
import vn.edu.ut.udm08.shared.dto.Attachment;
public final class AttachmentView {
    public static VBox create(ChatClient client,Attachment file){
        Label name=new Label(file.name);name.setWrapText(true);Label size=new Label(String.format(java.util.Locale.ROOT,"%.1f KB",file.size/1024.0));
        Button save=new Button("Tải về");Button preview=new Button("Xem ảnh");Label status=new Label();status.setWrapText(true);VBox box=new VBox(6,name,size,save,status);
        if(file.name!=null&&file.name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpg|jpeg)"))box.getChildren().add(2,preview);
        save.setDisable(client==null);preview.setDisable(client==null);
        save.setOnAction(event->{FileChooser chooser=new FileChooser();chooser.setTitle("Lưu tệp");chooser.setInitialFileName(file.name);java.io.File target=chooser.showSaveDialog(box.getScene().getWindow());if(target==null)return;save.setDisable(true);new AttachmentTransfer(client).download(file,value->Platform.runLater(()->status.setText("Đang tải "+Math.round(value*100)+"%"))).thenAcceptAsync(bytes->{try{java.nio.file.Files.write(target.toPath(),bytes);}catch(java.io.IOException e){throw new java.util.concurrent.CompletionException(e);}}).whenComplete((value,error)->Platform.runLater(()->{save.setDisable(false);status.setText(error==null?"Đã lưu tệp":"Tải thất bại. Nhấn Tải về để thử lại.");}));});
        preview.setOnAction(event->{preview.setDisable(true);new AttachmentTransfer(client).download(file,value->{}).whenComplete((bytes,error)->Platform.runLater(()->{preview.setDisable(false);if(error!=null){status.setText("Không tải được ảnh");return;}javafx.scene.image.Image image=new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes),700,500,true,true);if(image.isError()){status.setText("Tệp này không phải ảnh hợp lệ");return;}Dialog<Void> dialog=new Dialog<>();dialog.setTitle(file.name);dialog.getDialogPane().setContent(new javafx.scene.image.ImageView(image));dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.show();}));});
        return box;
    }
}
