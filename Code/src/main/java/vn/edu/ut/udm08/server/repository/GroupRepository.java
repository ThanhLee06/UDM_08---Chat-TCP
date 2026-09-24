package vn.edu.ut.udm08.server.repository;
import java.sql.*;
import java.util.*;
import vn.edu.ut.udm08.server.config.DatabaseConnectionFactory;
import vn.edu.ut.udm08.shared.dto.*;
public final class GroupRepository {
    private final DatabaseConnectionFactory factory;
    public GroupRepository(DatabaseConnectionFactory factory) { this.factory=factory; }
    public synchronized GroupDetails execute(long actor, GroupCommand command) {
        if (command == null || command.action == null) throw new IllegalArgumentException("Thiếu thao tác nhóm");
        try (Connection c=factory.getConnection()) {
            c.setAutoCommit(false);
            try {
                String id=command.convId;
                if ("CREATE".equals(command.action)) {
                    id="room:"+UUID.randomUUID();
                    update(c,"INSERT INTO conversations(conv_id,type,name) VALUES(?,'GROUP',?)",id,name(command.name));
                    update(c,"INSERT INTO conversation_members(conv_id,user_id,role) VALUES(?,?,'owner')",id,actor);
                    add(c,id,command.users);
                    if (members(c,id).members.size()<2) throw new IllegalArgumentException("Chọn ít nhất một người khác để tạo nhóm");
                } else {
                    String role=role(c,id,actor);
                    if (role == null) throw new IllegalArgumentException("Bạn không còn là thành viên nhóm");
                    boolean owner="owner".equals(role);
                    switch (command.action) {
                        case "GET" -> {}
                        case "RENAME" -> { requireOwner(owner); update(c,"UPDATE conversations SET name=? WHERE conv_id=?",name(command.name),id); }
                        case "ADD" -> { requireOwner(owner); add(c,id,command.users); }
                        case "REMOVE" -> {
                            requireOwner(owner);
                            if (command.users == null || command.users.size()!=1) throw new IllegalArgumentException("Chọn một thành viên cần xóa");
                            long target=resolve(c,command.users.get(0));
                            if (target==actor) throw new IllegalArgumentException("Dùng nút rời nhóm để rời khỏi nhóm");
                            update(c,"DELETE FROM conversation_members WHERE conv_id=? AND user_id=?",id,target);
                        }
                        case "LEAVE" -> {
                            update(c,"DELETE FROM conversation_members WHERE conv_id=? AND user_id=?",id,actor);
                            if (owner) update(c,"UPDATE conversation_members SET role='owner' WHERE conv_id=? AND user_id=(SELECT MIN(user_id) FROM conversation_members WHERE conv_id=?)",id,id);
                        }
                        default -> throw new IllegalArgumentException("Thao tác nhóm không hợp lệ");
                    }
                }
                GroupDetails result=members(c,id);
                if (result.members.size()>50) throw new IllegalArgumentException("Nhóm tối đa 50 thành viên");
                if (result.members.isEmpty()) update(c,"DELETE FROM conversations WHERE conv_id=?",id);
                c.commit(); return result;
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Không cập nhật được nhóm",e); }
    }
    private static void requireOwner(boolean owner) { if (!owner) throw new IllegalArgumentException("Chỉ trưởng nhóm được thực hiện thao tác này"); }
    private static String name(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length()>60 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Tên nhóm cần từ 1 đến 60 ký tự");
        return name.trim();
    }
    private String role(Connection c,String id,long user) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("SELECT m.role FROM conversation_members m JOIN conversations g ON g.conv_id=m.conv_id WHERE m.conv_id=? AND m.user_id=? AND g.type='GROUP'")) {
            p.setString(1,id);p.setLong(2,user);try(ResultSet rs=p.executeQuery()){return rs.next()?rs.getString(1):null;}
        }
    }
    private long resolve(Connection c,String account) throws SQLException {
        if (account==null || account.isBlank()) throw new IllegalArgumentException("Thiếu tài khoản thành viên");
        try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE username=? OR phone_number=?")) {
            p.setString(1,account.trim());p.setString(2,account.trim());try(ResultSet rs=p.executeQuery()){
                if(!rs.next())throw new IllegalArgumentException("Không tìm thấy tài khoản: "+account);
                return rs.getLong(1);
            }
        }
    }
    private void add(Connection c,String id,List<String> users) throws SQLException {
        if(users==null || users.isEmpty() || users.size()>49)throw new IllegalArgumentException("Chọn từ 1 đến 49 thành viên");
        for(String user:new LinkedHashSet<>(users)) update(c,"INSERT OR IGNORE INTO conversation_members(conv_id,user_id,role) VALUES(?,?,'member')",id,resolve(c,user));
    }
    private GroupDetails members(Connection c,String id) throws SQLException {
        GroupDetails result=new GroupDetails();result.convId=id;
        try(PreparedStatement p=c.prepareStatement("SELECT g.name,u.username,COALESCE(u.display_name,u.username) AS display_name,u.avatar_path,m.role FROM conversations g LEFT JOIN conversation_members m ON m.conv_id=g.conv_id LEFT JOIN users u ON u.id=m.user_id WHERE g.conv_id=? ORDER BY m.role DESC,u.username")) {
            p.setString(1,id);try(ResultSet rs=p.executeQuery()) {while(rs.next()) {
                result.name=rs.getString("name");
                if(rs.getString("username")==null)continue;
                GroupDetails.Member member=new GroupDetails.Member();member.username=rs.getString("username");member.displayName=rs.getString("display_name");member.avatar=rs.getString("avatar_path");member.role=rs.getString("role");result.members.add(member);
            }}
        }
        return result;
    }
    private void update(Connection c,String sql,Object... values) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();}
    }
}
