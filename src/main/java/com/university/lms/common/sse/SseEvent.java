package com.university.lms.common.sse;

import java.util.Map;

/** Live event pushed to connected clients — optimization only, not durable. */
public record SseEvent(String id, String eventType, Map<String, String> data) {}
