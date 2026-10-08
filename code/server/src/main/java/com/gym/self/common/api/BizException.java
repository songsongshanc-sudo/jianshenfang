package com.gym.self.common.api;

import org.springframework.http.HttpStatus;

public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BizException(int code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BizException unauthorized() {
        return new BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "未登录");
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.PARAM, HttpStatus.BAD_REQUEST, message);
    }

    public static BizException rejected(String message) {
        return new BizException(ErrorCode.REJECT, HttpStatus.BAD_REQUEST, message);
    }
}
