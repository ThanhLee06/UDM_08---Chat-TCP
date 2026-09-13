package vn.edu.ut.udm08.shared.validation;
public interface IPasswordValidator {
    boolean isStrongPassword(String password);
    String getValidationError(String password);
}
