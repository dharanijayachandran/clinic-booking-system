package com.dharanijayachandran.clinicbooking.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the access/refresh token cookies: httpOnly (JS can't read them, so
 * an XSS payload can't exfiltrate the token), Secure (HTTPS only — disabled
 * in local dev, see app.cookies.secure), SameSite=Lax (sent on normal
 * top-level navigation and same-site XHR, withheld on cross-site requests —
 * the practical default that still blocks the CSRF vectors that matter for
 * an API with no state-changing GET requests).
 */
@Component
public class CookieService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final boolean secure;

    public CookieService(@Value("${app.cookies.secure:true}") boolean secure) {
        this.secure = secure;
    }

    public void setAccessTokenCookie(HttpServletResponse response, String token, Duration ttl) {
        addCookie(response, ACCESS_TOKEN_COOKIE, token, ttl, "/api");
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String token, Duration ttl) {
        // Scoped to the one endpoint that actually needs it — no reason for
        // every request to carry a long-lived credential it doesn't use.
        addCookie(response, REFRESH_TOKEN_COOKIE, token, ttl, "/api/auth/refresh");
    }

    public void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", Duration.ZERO, "/api");
        addCookie(response, REFRESH_TOKEN_COOKIE, "", Duration.ZERO, "/api/auth/refresh");
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration ttl, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(ttl)
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Reads a named cookie's value, or null if absent. */
    public static String readCookie(Cookie[] cookies, String name) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
