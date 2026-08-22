package com.university.lms.common.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Transport shape for a page of results.
 *
 * <p>Deliberately not Spring's {@code Page}: that type serialises its internal {@code Pageable} and
 * {@code Sort} structure, which would leak persistence-layer detail into a public contract that we
 * then could not change freely.
 *
 * @param content       the items on this page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching items across all pages
 * @param totalPages    total number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    /** Maps a persistence {@link Page} of entities into a transport page of DTOs. */
    public static <E, T> PageResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast());
    }
}
