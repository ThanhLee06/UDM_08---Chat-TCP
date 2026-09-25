package vn.edu.ut.udm08.server.repository;
import java.sql.*;
import vn.edu.ut.udm08.server.config.DatabaseConnectionFactory;
import vn.edu.ut.udm08.shared.dto.Attachment;
public final class AttachmentRepository {
    private final DatabaseConnectionFactory factory;
    public AttachmentRepository(DatabaseConnectionFactory factory){this.factory=factory;}
    public void save(Attachment file,long owner){
        try(Connection c=factory.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO attachments(id,conv_id,owner_id,name,size) VALUES(?,?,?,?,?)")){
            p.setString(1,file.id);p.setString(2,file.convId);p.setLong(3,owner);p.setString(4,file.name);p.setLong(5,file.size);p.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("Không lưu được tệp",e);}
    }
    public Attachment find(String id){
        try(Connection c=factory.getConnection();PreparedStatement p=c.prepareStatement("SELECT id,conv_id,name,size FROM attachments WHERE id=?")){
            p.setString(1,id);try(ResultSet rs=p.executeQuery()){
                if(!rs.next())throw new IllegalArgumentException("Tệp không tồn tại");
                Attachment file=new Attachment();file.id=rs.getString(1);file.convId=rs.getString(2);file.name=rs.getString(3);file.size=rs.getLong(4);return file;
            }
        }catch(SQLException e){throw new IllegalStateException("Không tải được tệp",e);}
    }
}
