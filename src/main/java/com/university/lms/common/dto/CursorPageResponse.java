package com.university.lms.common.dto;

import java.util.List;

/** Cursor-based page — no offset; {@code nextCursor} is opaque to clients. */
public record CursorPageResponse<T>(List<T> content, String nextCursor, boolean hasMore) {}
