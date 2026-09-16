package com.dharanijayachandran.clinicbooking.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Makes cookie-based CSRF actually work for a single-page app.
 *
 * Spring Security 6 defaults to {@link XorCsrfTokenRequestAttributeHandler},
 * which BREACH-masks the token it renders and therefore expects a *masked*
 * value back. The CookieCsrfTokenRepository, however, writes the *raw* token
 * into the XSRF-TOKEN cookie. A SPA reads that cookie and echoes it verbatim
 * in X-XSRF-TOKEN, so the server unmasks a value that was never masked, the
 * comparison fails, and every state-changing request is rejected.
 *
 * (The rejection also arrives as 401 rather than 403, because CsrfFilter runs
 * before the JWT filter — the request is still anonymous when CSRF says no.
 * That mismatch is what makes this bug so confusing to diagnose.)
 *
 * The fix, which is Spring's own documented recipe: keep masking when
 * rendering, but when a request arrives carrying the header, resolve it
 * plainly. Form posts and SPA requests then both validate correctly.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler masked = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.masked.handle(request, response, csrfToken);
        // Spring Security 6 loads the token lazily; resolving it here forces
        // the repository to write the cookie, so the SPA has something to read
        // on its very first request.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? this.plain : this.masked)
                .resolveCsrfTokenValue(request, csrfToken);
    }
}
