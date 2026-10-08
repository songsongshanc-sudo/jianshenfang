package com.gym.self.common.api;

public final class ErrorCode {

    public static final int OK = 0;
    public static final int PARAM = 40000;
    public static final int UNAUTHORIZED = 40100;
    public static final int FORBIDDEN = 40300;
    public static final int SERVER = 50000;
    public static final int REJECT = 41000;

    private ErrorCode() {
    }
}
