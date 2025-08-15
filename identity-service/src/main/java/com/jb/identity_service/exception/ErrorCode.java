package com.jb.identity_service.exception;

public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized exception"),
    USER_EXISTED(1002, "User already exists"),
    USERNAME_INVALID(1003, "Username is invalid"),
    PASSWORD_INVALID(1004, "Password is invalid"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
