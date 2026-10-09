package com.gym.self.modules.gate;

import java.util.Map;

public record VendorReply(int result, String msg, Map<String, Object> content) {

    public static VendorReply missing() {
        return new VendorReply(-2, "设备不存在", Map.of());
    }

    public static VendorReply ok() {
        return new VendorReply(0, "", Map.of());
    }

    public static VendorReply of(int result, String msg, Map<String, Object> content) {
        return new VendorReply(result, msg, content);
    }
}
