package com.youtube.dto;

import lombok.*;

import java.util.List;

/**
 * Generic paginated response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

    private List<T> items;
    private int page;
    private int pageSize;
    private long totalItems;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    // Cursor-based pagination (recommended for scale)
    private String nextCursor;
    private String prevCursor;

    /**
     * Create paged response with offset-based pagination
     */
    public static <T> PagedResponse<T> of(List<T> items, int page, int pageSize, long totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        return PagedResponse.<T>builder()
                .items(items)
                .page(page)
                .pageSize(pageSize)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    /**
     * Create paged response with cursor-based pagination
     */
    public static <T> PagedResponse<T> ofCursor(List<T> items, String nextCursor, String prevCursor) {
        return PagedResponse.<T>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .hasNext(nextCursor != null)
                .hasPrevious(prevCursor != null)
                .build();
    }
}
