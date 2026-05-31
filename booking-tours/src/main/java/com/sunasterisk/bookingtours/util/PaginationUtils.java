package com.sunasterisk.bookingtours.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to build a windowed page-number list for UI pagination.
 *
 * <p>The returned list contains 0-based page indices plus the sentinel value
 * {@code -1} which signals an ellipsis ("…") to be rendered in the template.
 *
 * <p>Example – currentPage=10, totalPages=30, delta=2:
 * <pre>  [0, -1, 8, 9, 10, 11, 12, -1, 29]
 * → renders:  1 … 9 10 [11] 12 13 … 30</pre>
 */
public final class PaginationUtils {

    /** Number of pages shown on each side of the current page. */
    private static final int DELTA = 2;

    /** Sentinel value used to represent an ellipsis in the page list. */
    public static final int ELLIPSIS = -1;

    private PaginationUtils() {}

    /**
     * Builds a windowed page-number list.
     *
     * @param currentPage 0-based index of the current page
     * @param totalPages  total number of pages
     * @return ordered list of page indices (≥ 0) and ellipsis sentinels (-1)
     */
    public static List<Integer> getPageNumbers(int currentPage, int totalPages) {
        if (totalPages <= 0) {
            return List.of();
        }

        // When there are few enough pages, show them all without ellipses.
        // Max "windowed" size = first + ellipsis + window(2*DELTA+1) + ellipsis + last = 2*DELTA+5
        if (totalPages <= 2 * DELTA + 5) {
            List<Integer> all = new ArrayList<>(totalPages);
            for (int i = 0; i < totalPages; i++) all.add(i);
            return all;
        }

        return buildWindowedList(currentPage, totalPages);
    }

    /**
     * Builds the windowed list with ellipses for the case where
     * {@code totalPages > 2 * DELTA + 5}.
     *
     * <p>Structure: {@code [0, (ELLIPSIS,) windowStart..windowEnd, (ELLIPSIS,) totalPages-1]}
     */
    private static List<Integer> buildWindowedList(int currentPage, int totalPages) {
        int windowStart = Math.max(1, currentPage - DELTA);
        int windowEnd   = Math.min(totalPages - 2, currentPage + DELTA);

        List<Integer> result = new ArrayList<>();

        // Always include the first page
        result.add(0);

        // Left ellipsis — only when there is a real gap
        if (windowStart > 1) {
            result.add(ELLIPSIS);
        }

        // Window pages (excludes first and last which are handled separately)
        for (int i = windowStart; i <= windowEnd; i++) {
            result.add(i);
        }

        // Right ellipsis — only when there is a real gap
        if (windowEnd < totalPages - 2) {
            result.add(ELLIPSIS);
        }

        // Always include the last page
        result.add(totalPages - 1);

        return result;
    }
}
