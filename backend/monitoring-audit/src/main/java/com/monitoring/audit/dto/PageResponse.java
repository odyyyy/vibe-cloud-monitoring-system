package com.monitoring.audit.dto;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope for list endpoints.
 *
 * <p>Deliberately a custom DTO rather than Spring Data's {@code Page}: the serialized
 * {@code Page} exposes many extra internal fields. The frontend contract is exactly
 * {@code content, totalElements, totalPages, page, size}.
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {

    public static <E, T> PageResponse<T> of(org.springframework.data.domain.Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
