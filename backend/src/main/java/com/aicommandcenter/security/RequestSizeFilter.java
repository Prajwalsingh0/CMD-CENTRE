package com.aicommandcenter.security;

import com.aicommandcenter.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses oversized JSON request bodies before Jackson buffers them.
 *
 * <p>Bean Validation runs <em>after</em> deserialisation, so an {@code @Size(max = 2000)} field does
 * not stop a gigabyte of JSON from being read into heap first. The servlet multipart limits do not
 * apply to {@code application/json} at all. This filter closes that gap with a cheap header check.
 *
 * <p>Known limitation, stated plainly: a client that omits {@code Content-Length} and streams
 * chunked encoding is not caught here. Closing that requires byte counting in a wrapping servlet
 * input stream, which costs a copy on every request; the honest place for that defence is a reverse
 * proxy, and nginx in the bundled compose file caps the body at 12 MB.</p>
 */
@Component
@Order(1)
public class RequestSizeFilter extends OncePerRequestFilter {

    /** Generous for the largest legitimate payload (a pasted job description) and far below OOM. */
    private static final long MAX_JSON_BYTES = 1_048_576L;

    private final ObjectMapper objectMapper;

    public RequestSizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            long declared = request.getContentLengthLong();
            if (declared > MAX_JSON_BYTES) {
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getWriter(), ApiError.of(
                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "PAYLOAD_TOO_LARGE",
                        "Request body exceeds the maximum allowed size of " + (MAX_JSON_BYTES / 1024) + " KB",
                        request.getRequestURI()));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
