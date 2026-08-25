package vn.edu.ut.udm08.server.session;

import java.text.Normalizer;

public class UsernameValidator {
  private UsernameValidator() {}
  public static boolean isValid(String username) {
    if (username == null || username.isEmpty()) {
      return false;
    }
    String normalized = Normalizer.normalize(username, Normalizer.Form.NFC);
    if(normalized.isEmpty() || normalized.length() > 20) {
      return false;
    }
    for(int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if(!Character.isLetterOrDigit(c)) {
        return false;
      }
    }
    return true;
  }
}
