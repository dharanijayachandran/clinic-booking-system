package com.dharanijayachandran.clinicbooking.auth;

import com.dharanijayachandran.clinicbooking.user.User;
import com.dharanijayachandran.clinicbooking.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the access_token cookie (never an Authorization header — the whole
 * point of the httpOnly cookie choice is that Angular never touches the
 * token directly), validates it, and populates the SecurityContext.
 *
 * A missing or invalid token is not an error here — it just leaves the
 * request unauthenticated, and Spring Security's authorization rules decide
 * from there whether that's allowed for the requested endpoint.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = CookieService.readCookie(request.getCookies(), CookieService.ACCESS_TOKEN_COOKIE);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parseAndValidate(token);
            if (!jwtService.isAccessToken(claims)) {
                return; // a refresh token presented here is not a valid credential
            }

            UUID userId = jwtService.extractUserId(claims);
            Optional<User> user = userRepository.findById(userId);
            if (user.isEmpty()) {
                return; // user was deleted after the token was issued
            }

            UserPrincipal principal = new UserPrincipal(user.get());
            var authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            // Expired, malformed, or bad signature — request proceeds unauthenticated.
        }
    }
}
