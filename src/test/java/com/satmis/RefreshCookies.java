package com.satmis;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// The refresh token only travels in the httpOnly cookie; these send it the way the frontend's browser does
public final class RefreshCookies {

    public static final String NAME = "satmis_refresh";
    public static final String FRONTEND_ORIGIN = "http://localhost:5173";

    private RefreshCookies() {
    }

    public static String from(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(NAME);
        if (cookie == null) {
            throw new AssertionError("Response has no " + NAME + " cookie");
        }
        return cookie.getValue();
    }

    public static String from(ResultActions result) {
        return from(result.andReturn());
    }

    public static MockHttpServletRequestBuilder refresh(String token) {
        return withCookie(post("/api/auth/refresh"), token);
    }

    public static MockHttpServletRequestBuilder logout(String token) {
        return withCookie(post("/api/auth/logout"), token);
    }

    private static MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, String token) {
        return request.cookie(new Cookie(NAME, token)).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN);
    }
}
