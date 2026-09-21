package vn.edu.ut.udm08.server.handler;

import vn.edu.ut.udm08.server.auth.IOtpService;
import vn.edu.ut.udm08.server.service.UserLoginService;
import vn.edu.ut.udm08.server.service.UserRegisterService;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.dto.*;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

public class RegisterHandler {
    private final UserRegisterService registerService;
    private final UserLoginService loginService;
    private final IOtpService otpService;

    public RegisterHandler(UserRegisterService registerService, UserLoginService loginService, IOtpService otpService) {
        this.registerService = registerService;
        this.loginService = loginService;
        this.otpService = otpService;
    }

    public void handleRegisterInit(ClientSession session, ProtocolMessage message) {
        try {
            RegisterInitRequest req = JsonUtil.fromJson(message.content, RegisterInitRequest.class);
            if (req == null) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "INVALID_REQUEST", "Thông tin đăng ký không hợp lệ");
                return;
            }

            RegisterRequest regReq = new RegisterRequest();
            regReq.setUsername(req.getUsername());
            regReq.setPhoneNumber(req.getPhoneNumber());
            regReq.setEmail(req.getEmail());
            regReq.setPassword(req.getPassword());
            regReq.setAvatarType(req.getAvatarType());
            regReq.setAvatarPath(req.getAvatarPath());

            RegisterResponse response = registerService.register(regReq);

            if (!response.isSuccess()) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "REGISTER_FAILED", response.getMessage());
                return;
            }

            ProtocolMessage res = new ProtocolMessage(MessageType.AUTH_REGISTER_OTP_REQUIRED);
            res.messageId = message.messageId;
            res.requestId = message.requestId;
            res.sender = "SERVER";
            res.content = JsonUtil.toJson(response);
            res.timestamp = System.currentTimeMillis();
            session.sendMessage(res);

        } catch (Exception e) {
            sendError(session, message.requestId != null ? message.requestId : message.messageId, "SERVER_ERROR", "Lỗi xử lý đăng ký: " + e.getMessage());
        }
    }

    public void handleVerifyOtp(ClientSession session, ProtocolMessage message) {
        try {
            RegisterOtpVerifyRequest req = JsonUtil.fromJson(message.content, RegisterOtpVerifyRequest.class);
            if (req == null || req.getRegistrationId() == null || req.getOtpCode() == null) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "INVALID_REQUEST", "Thông tin mã OTP không hợp lệ");
                return;
            }

            RegisterResponse response = registerService.verifyRegistration(req.getRegistrationId(), req.getOtpCode());

            if (!response.isSuccess()) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "VERIFY_FAILED", response.getMessage());
                return;
            }

            ProtocolMessage res = new ProtocolMessage(MessageType.AUTH_REGISTER_OK);
            res.messageId = message.messageId;
            res.requestId = message.requestId;
            res.sender = "SERVER";
            res.content = JsonUtil.toJson(new AuthUserDto(response.getUser()));
            res.timestamp = System.currentTimeMillis();
            session.sendMessage(res);

        } catch (Exception e) {
            sendError(session, message.requestId != null ? message.requestId : message.messageId, "SERVER_ERROR", "Lỗi xác thực OTP: " + e.getMessage());
        }
    }

    public void handleResendOtp(ClientSession session, ProtocolMessage message) {
        try {
            String registrationId = message.content;
            if (registrationId == null || registrationId.isBlank()) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "INVALID_REQUEST", "Mã phiên đăng ký không hợp lệ");
                return;
            }

            RegisterResponse response = registerService.resendOtp(registrationId.trim());

            if (!response.isSuccess()) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "RESEND_FAILED", response.getMessage());
                return;
            }

            ProtocolMessage res = new ProtocolMessage(MessageType.AUTH_REGISTER_OTP_REQUIRED);
            res.messageId = message.messageId;
            res.requestId = message.requestId;
            res.sender = "SERVER";
            res.content = JsonUtil.toJson(response);
            res.timestamp = System.currentTimeMillis();
            session.sendMessage(res);

        } catch (Exception e) {
            sendError(session, message.requestId != null ? message.requestId : message.messageId, "SERVER_ERROR", "Lỗi gửi lại OTP: " + e.getMessage());
        }
    }

    public void handleForgotInit(ClientSession session, ProtocolMessage message) {
        try {
            ForgotInitRequest req = JsonUtil.fromJson(message.content, ForgotInitRequest.class);
            if (req == null || req.getPhoneOrEmail() == null || req.getPhoneOrEmail().isBlank()) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "INVALID_REQUEST", "Vui lòng nhập Email hoặc Số điện thoại");
                return;
            }

            loginService.requestPasswordReset(req.getPhoneOrEmail().trim(), otpService);

            ProtocolMessage res = new ProtocolMessage(MessageType.AUTH_FORGOT_OTP_REQUIRED);
            res.messageId = message.messageId;
            res.requestId = message.requestId;
            res.sender = "SERVER";
            res.content = "Đã gửi mã OTP đặt lại mật khẩu thành công";
            res.timestamp = System.currentTimeMillis();
            session.sendMessage(res);

        } catch (Exception e) {
            sendError(session, message.requestId != null ? message.requestId : message.messageId, "FORGOT_FAILED", e.getMessage());
        }
    }

    public void handleForgotReset(ClientSession session, ProtocolMessage message) {
        try {
            ForgotResetRequest req = JsonUtil.fromJson(message.content, ForgotResetRequest.class);
            if (req == null || req.getResetId() == null || req.getOtpCode() == null || req.getNewPassword() == null) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "INVALID_REQUEST", "Thông tin đặt lại mật khẩu không hợp lệ");
                return;
            }

            boolean success = loginService.resetPassword(req.getResetId(), req.getOtpCode(), req.getNewPassword(), otpService);

            if (!success) {
                sendError(session, message.requestId != null ? message.requestId : message.messageId, "RESET_FAILED", "Mã OTP không đúng hoặc đặt lại mật khẩu không thành công");
                return;
            }

            ProtocolMessage res = new ProtocolMessage(MessageType.AUTH_FORGOT_OK);
            res.messageId = message.messageId;
            res.requestId = message.requestId;
            res.sender = "SERVER";
            res.content = "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.";
            res.timestamp = System.currentTimeMillis();
            session.sendMessage(res);

        } catch (Exception e) {
            sendError(session, message.requestId != null ? message.requestId : message.messageId, "SERVER_ERROR", "Lỗi đặt lại mật khẩu: " + e.getMessage());
        }
    }

    private void sendError(ClientSession session, String messageId, String errorCode, String errorMessage) {
        ProtocolMessage err = new ProtocolMessage(MessageType.ERROR);
        err.messageId = messageId;
        err.requestId = messageId;
        err.sender = "SERVER";
        err.errorCode = errorCode;
        err.errorMessage = errorMessage;
        err.timestamp = System.currentTimeMillis();
        try {
            session.sendMessage(err);
        } catch (Exception ignored) {
        }
    }
}
