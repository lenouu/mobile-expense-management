package com.expensemanagement.backend.Shared;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * The flat page envelope every paged endpoint returns: {@code {data, page, size, totalElements,
 * totalPages}}.
 *
 * <p>Spring Data's own {@code PagedModel} calls the items {@code content} and nests the metadata
 * under a {@code page} object. The API contract specifies this flat shape instead, so a client
 * parses one envelope regardless of which list it asked for.
 *
 * <p>Using our own type rather than {@code @EnableSpringDataWebSupport(pageSerializationMode =
 * VIA_DTO)} matters: that annotation replaces Boot's {@code Pageable} argument resolver, and
 * that resolver is what enforces {@code spring.data.web.pageable.max-page-size}. Wrapping
 * manually keeps the {@code ?size=} cap in place.
 *
 * @param data          the items of this page
 * @param page          zero-based index of this page
 * @param size          requested page size
 * @param totalElements items across every page
 * @param totalPages    number of pages available
 */
public record PageResponse<T>(
        List<T> data,
        int page,
        int size,
        long totalElements,
        int totalPages)
{
    /** Wraps a Spring Data page, keeping its content and metadata. */
    public static <T> PageResponse<T> of(Page<T> source)
    {
        return new PageResponse<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
