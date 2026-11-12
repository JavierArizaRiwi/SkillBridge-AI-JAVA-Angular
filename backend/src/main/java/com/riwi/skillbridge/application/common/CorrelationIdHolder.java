package com.riwi.skillbridge.application.common;

public class CorrelationIdHolder {
    private static final ThreadLocal<String> holder = new ThreadLocal<>();

    public static void set(String correlationId) {
        holder.set(correlationId);
    }

    public static String get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}
