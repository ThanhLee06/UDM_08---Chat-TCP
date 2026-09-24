package vn.edu.ut.udm08.shared.dto;
import java.util.ArrayList;
import java.util.List;
public final class GroupDetails {
    public String convId;
    public String name;
    public List<Member> members = new ArrayList<>();
    public static final class Member {
        public String username;
        public String displayName;
        public String avatar;
        public String role;
    }
}
