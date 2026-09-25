package vn.edu.ut.udm08.server.handler;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import vn.edu.ut.udm08.server.repository.*;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.dto.Attachment;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.protocol.*;
public final class AttachmentHandler {
    private final AttachmentRepository files;
    private final IConversationDao conversations;
    private final Path directory;
    private final Map<String,Upload> uploads=new HashMap<>();
    private static final class Upload {
        final Attachment file;
        final long owner;
        final long expires=System.currentTimeMillis()+600000;
        Upload(Attachment file,long owner){this.file=file;this.owner=owner;}
    }
    public AttachmentHandler(AttachmentRepository files,IConversationDao conversations,Path directory){this.files=files;this.conversations=conversations;this.directory=directory;}
    public synchronized void handle(ClientSession session,ProtocolMessage request){
        ProtocolMessage response=new ProtocolMessage(MessageType.FILE_RESPONSE);response.requestId=request.requestId;
        try{
            Files.createDirectories(directory);
            var iterator=uploads.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(entry.getValue().expires<System.currentTimeMillis()){Files.deleteIfExists(directory.resolve(entry.getKey()+".part"));iterator.remove();}}
            Attachment command=JsonUtil.fromJson(request.content,Attachment.class);
            if(command==null||command.action==null)throw new IllegalArgumentException("Thiếu thao tác tệp");
            long owner=session.getUser().getId();Attachment result;
            if("BEGIN".equals(command.action)){
                requireAccess(owner,command.convId);
                if(command.size<1||command.size>5*1024*1024)throw new IllegalArgumentException("Tệp cần từ 1 byte đến 5 MB");
                if(command.name==null||command.name.isBlank()||command.name.length()>150||command.name.matches(".*[\\/:*?\"<>|].*")||command.name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Tên tệp không hợp lệ");
                if(uploads.size()>=20||uploads.values().stream().filter(u->u.owner==owner).count()>=2)throw new IllegalArgumentException("Đang có quá nhiều tệp được gửi. Thử lại sau.");
                command.id=UUID.randomUUID().toString();command.data=null;command.action=null;
                Files.createFile(directory.resolve(command.id+".part"));uploads.put(command.id,new Upload(command,owner));result=command;
            }else if("GET".equals(command.action)){
                result=files.find(command.id);requireAccess(owner,result.convId);
                if(command.offset<0||command.offset>=result.size)throw new IllegalArgumentException("Vị trí tải tệp không hợp lệ");
                try(RandomAccessFile file=new RandomAccessFile(directory.resolve(result.id).toFile(),"r")){
                    file.seek(command.offset);byte[] bytes=new byte[(int)Math.min(65536,result.size-command.offset)];file.readFully(bytes);result.data=Base64.getEncoder().encodeToString(bytes);result.offset=command.offset;
                }
            }else if("COPY".equals(command.action)){
                if(command.id==null||command.id.isBlank())throw new IllegalArgumentException("Thiếu tệp nguồn");
                if(command.convId==null||command.convId.isBlank())throw new IllegalArgumentException("Thiếu hội thoại đích");
                Attachment source=files.find(command.id);
                requireAccess(owner,source.convId);
                requireAccess(owner,command.convId);
                Attachment copied=new Attachment();
                copied.id=UUID.randomUUID().toString();
                copied.convId=command.convId;
                copied.name=source.name;
                copied.size=source.size;
                Path sourcePath=directory.resolve(source.id);
                Path copiedPath=directory.resolve(copied.id);
                Files.copy(sourcePath,copiedPath);
                try{
                    files.save(copied,owner);
                }catch(RuntimeException e){
                    Files.deleteIfExists(copiedPath);
                    throw e;
                }
                result=copied;
            }else{
                Upload upload=uploads.get(command.id);
                if(upload==null||upload.owner!=owner)throw new IllegalArgumentException("Phiên tải tệp không hợp lệ hoặc đã hết hạn");
                requireAccess(owner,upload.file.convId);Path part=directory.resolve(command.id+".part");result=upload.file;
                switch(command.action){
                    case "CHUNK" -> {
                        if(command.data==null||command.data.length()>90000)throw new IllegalArgumentException("Gói tệp quá lớn");
                        byte[] bytes=Base64.getDecoder().decode(command.data);long size=Files.size(part);
                        if(bytes.length==0||command.offset!=size||size+bytes.length>result.size)throw new IllegalArgumentException("Thứ tự hoặc kích thước gói tệp không đúng");
                        Files.write(part,bytes,StandardOpenOption.APPEND);result.offset=size+bytes.length;
                    }
                    case "FINISH" -> {
                        if(Files.size(part)!=result.size)throw new IllegalArgumentException("Tệp chưa được tải đủ");
                        Files.move(part,directory.resolve(result.id));
                        try{files.save(result,owner);}catch(RuntimeException e){Files.deleteIfExists(directory.resolve(result.id));throw e;}
                        uploads.remove(result.id);
                    }
                    case "ABORT" -> {Files.deleteIfExists(part);uploads.remove(result.id);}
                    default -> throw new IllegalArgumentException("Thao tác tệp không hợp lệ");
                }
            }
            response.content=JsonUtil.toJson(result);
        }catch(Exception e){response.type=MessageType.ERROR;response.errorCode="FILE_ERROR";response.errorMessage=e instanceof IllegalArgumentException?e.getMessage():"Không xử lý được tệp. Vui lòng thử lại.";}
        try{session.sendMessage(response);}catch(IOException e){session.close();}
    }
    private void requireAccess(long user,String id){if(id==null||conversations.findById(id).isEmpty()||!ConvId.isPublicRoom(id)&&!conversations.isMember(id,user))throw new IllegalArgumentException("Không có quyền dùng tệp trong hội thoại này");}
}
