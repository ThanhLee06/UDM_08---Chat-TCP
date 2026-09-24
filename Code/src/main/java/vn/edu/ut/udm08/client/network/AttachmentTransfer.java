package vn.edu.ut.udm08.client.network;
import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleConsumer;
import vn.edu.ut.udm08.shared.dto.Attachment;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
public final class AttachmentTransfer {
    private final ChatClient client;
    public AttachmentTransfer(ChatClient client){this.client=client;}
    private Attachment request(Attachment command){return JsonUtil.fromJson(client.requestFeature(MessageType.FILE_REQUEST,JsonUtil.toJson(command)).join().content,Attachment.class);}
    public CompletableFuture<Attachment> upload(Path path,String convId,DoubleConsumer progress){return CompletableFuture.supplyAsync(()->{
        Attachment file=new Attachment();file.action="BEGIN";file.convId=convId;file.name=path.getFileName().toString();
        try{
            file.size=Files.size(path);if(file.size<1||file.size>5*1024*1024)throw new IllegalArgumentException("Chọn tệp từ 1 byte đến 5 MB");
            file=request(file);
            try(InputStream input=Files.newInputStream(path)){
                long offset=0;byte[] bytes;
                while((bytes=input.readNBytes(65536)).length>0){Attachment chunk=new Attachment();chunk.id=file.id;chunk.action="CHUNK";chunk.offset=offset;chunk.data=Base64.getEncoder().encodeToString(bytes);request(chunk);offset+=bytes.length;progress.accept((double)offset/file.size);}
            }
            file.action="FINISH";return request(file);
        }catch(Exception e){if(file.id!=null){Attachment abort=new Attachment();abort.id=file.id;abort.action="ABORT";client.requestFeature(MessageType.FILE_REQUEST,JsonUtil.toJson(abort));}throw new java.util.concurrent.CompletionException(e);}
    });}
    public CompletableFuture<byte[]> download(Attachment file,DoubleConsumer progress){return CompletableFuture.supplyAsync(()->{
        if(file.size<1||file.size>5*1024*1024)throw new IllegalArgumentException("Kích thước tệp không hợp lệ");
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        while(output.size()<file.size){Attachment command=new Attachment();command.id=file.id;command.action="GET";command.offset=output.size();Attachment response=request(command);byte[] chunk=Base64.getDecoder().decode(response.data);if(chunk.length==0||output.size()+chunk.length>file.size)throw new IllegalStateException("Dữ liệu tải về không hợp lệ");output.writeBytes(chunk);progress.accept((double)output.size()/file.size);}
        return output.toByteArray();
    });}
}
