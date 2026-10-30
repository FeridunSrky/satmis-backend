package com.satmis.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.io.IOException;

// Spring rejects a disallowed origin with a plain-text body; clients expect the standard error JSON everywhere
@RequiredArgsConstructor
class JsonCorsProcessor extends DefaultCorsProcessor {

    static final String REJECTED_MESSAGE = "İstek kaynağı doğrulanamadı";

    private final RestSecurityHandler restSecurityHandler;

    @Override
    protected boolean handleInternal(ServerHttpRequest request, ServerHttpResponse response,
                                     CorsConfiguration config, boolean preFlightRequest) throws IOException {
        boolean allowed = super.handleInternal(request, response, config, preFlightRequest);
        if (!allowed && request instanceof ServletServerHttpRequest servletRequest
                && response instanceof ServletServerHttpResponse servletResponse) {
            restSecurityHandler.writeError(servletResponse.getServletResponse(), servletRequest.getServletRequest(),
                    HttpStatus.FORBIDDEN, REJECTED_MESSAGE);
        }
        return allowed;
    }

    // The body is written in handleInternal, where the request path is known
    @Override
    protected void rejectRequest(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
    }
}
