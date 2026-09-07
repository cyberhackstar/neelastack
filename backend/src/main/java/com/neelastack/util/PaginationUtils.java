package com.neelastack.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Clamps client-supplied {@code page}/{@code size} query params before they ever reach a
 * repository call. Every {@code @RequestParam int page} / {@code @RequestParam int size} pair in
 * this codebase used to go straight into {@code PageRequest.of(page, size, ...)} with no upper
 * bound and no negative-value check — a public caller could request {@code ?size=500000} (a
 * single huge DB read/response) or a negative page/size (which {@code PageRequest.of} rejects
 * with an unhandled {@code IllegalArgumentException}, surfacing as an ugly 500 instead of a clean
 * 400). This centralizes the fix so every paginated endpoint gets the same treatment.
 */
public final class PaginationUtils {

    /** Hard ceiling applied when a call site doesn't specify its own tighter max. */
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
    }

    public static Pageable safePageable(int page, int size, int maxSize, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), Math.max(maxSize, 1));
        return sort == null
                ? PageRequest.of(safePage, safeSize)
                : PageRequest.of(safePage, safeSize, sort);
    }

    public static Pageable safePageable(int page, int size, Sort sort) {
        return safePageable(page, size, DEFAULT_MAX_PAGE_SIZE, sort);
    }
}
