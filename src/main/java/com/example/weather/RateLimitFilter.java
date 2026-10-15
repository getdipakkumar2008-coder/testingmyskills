package com.example.weather;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, in-memory, per-client-IP limiter for /api/**. Protects the free upstream quota.
 * Behind a reverse proxy set server.forward-headers-strategy=native so the real client IP is used.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private record Window(long startMs, int count) { }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;

    public RateLimitFilter(@Value("${weather.rate-limit.requests-per-minute:60}") int limit) {
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.startMs() >= WINDOW_MS);
        }
        Window w = windows.compute(request.getRemoteAddr(),
                (k, cur) -> cur == null || now - cur.startMs() >= WINDOW_MS
                        ? new Window(now, 1) : new Window(cur.startMs(), cur.count() + 1));
        if (w.count() > limit) {
            long retryAfter = Math.max(1, (WINDOW_MS - (now - w.startMs()) + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\","
                    + "\"status\":429,\"detail\":\"Rate limit exceeded; retry later\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
