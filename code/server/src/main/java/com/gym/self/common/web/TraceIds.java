package com.gym.self.common.web;

import org.slf4j.MDC;

public final class TraceIds {

    public static final String MDC_KEY = "traceId";

    private TraceIds() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }
}
