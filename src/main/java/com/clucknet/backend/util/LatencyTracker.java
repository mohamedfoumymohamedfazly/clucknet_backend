package com.clucknet.backend.util;

public class LatencyTracker {
    private static final ThreadLocal<Long> iotReceivedAt = new ThreadLocal<>();
    private static final ThreadLocal<Long> alertGeneratedAt = new ThreadLocal<>();

    public static void setIotReceivedAt(Long timestamp) {
        iotReceivedAt.set(timestamp);
    }

    public static Long getIotReceivedAt() {
        return iotReceivedAt.get();
    }

    public static void setAlertGeneratedAt(Long timestamp) {
        alertGeneratedAt.set(timestamp);
    }

    public static Long getAlertGeneratedAt() {
        return alertGeneratedAt.get();
    }

    public static void clear() {
        iotReceivedAt.remove();
        alertGeneratedAt.remove();
    }
}
