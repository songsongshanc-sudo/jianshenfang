package com.gym.self.common.api;

import com.gym.self.common.web.TraceIds;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK, "ok", data, TraceIds.current());
    }

    public static ApiResponse<Void> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceIds.current());
    }
}
