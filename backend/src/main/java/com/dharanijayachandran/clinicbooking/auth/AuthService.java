package com.dharanijayachandran.clinicbooking.auth;

import com.dharanijayachandran.clinicbooking.auth.dto.LoginRequest;
import com.dharanijayachandran.clinicbooking.auth.dto.RegisterRequest;
import com.dharanijayachandran.clinicbooking.user.Role;
import com.dharanijayachandran.clinicbooking.user.User;
import com.dharanijayachandran.clinicbooking.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CookieService cookieService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            CookieService cookieService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.cookieService = cookieService;
    }

    /**
     * Always creates a PATIENT — see RegisterRequest's javadoc for why there
     * is no role field to abuse here.
     *
     * The existsByEmailIgnoreCase() check is a fast-path UX nicety, not the
     * correctness guarantee: two concurrent registrations for the same email
     * have exactly the TOCTOU race Phase 1 dealt with for bookings. The real
     * guarantee is idx_users_email_lower (the UNIQUE index) — if a race
     * slips past the check, the INSERT itself fails and we translate that
     * DataIntegrityViolationException into the same 409 response.
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.PATIENT)
                .build();

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException(request.email());
        }
    }

    public User login(LoginRequest request, HttpServletResponse response) {
        UserPrincipal principal;
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            principal = (UserPrincipal) authentication.getPrincipal();
        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // Same exception either way — see InvalidCredentialsException's javadoc.
            throw new InvalidCredentialsException();
        }

        issueTokens(principal, response);
        return principal.user();
    }

    /** Reads the refresh_token cookie, and if valid, issues a fresh access token. */
    public User refresh(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null) {
            throw new InvalidCredentialsException();
        }

        try {
            Claims claims = jwtService.parseAndValidate(refreshToken);
            if (!jwtService.isRefreshToken(claims)) {
                throw new InvalidCredentialsException();
            }

            User user = userRepository.findById(jwtService.extractUserId(claims))
                    .orElseThrow(InvalidCredentialsException::new);

            UserPrincipal principal = new UserPrincipal(user);
            String accessToken = jwtService.generateAccessToken(principal);
            cookieService.setAccessTokenCookie(response, accessToken, jwtService.accessTokenTtl());
            return user;
        } catch (JwtException e) {
            throw new InvalidCredentialsException();
        }
    }

    public void logout(HttpServletResponse response) {
        cookieService.clearAuthCookies(response);
    }

    private void issueTokens(UserPrincipal principal, HttpServletResponse response) {
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);
        cookieService.setAccessTokenCookie(response, accessToken, jwtService.accessTokenTtl());
        cookieService.setRefreshTokenCookie(response, refreshToken, jwtService.refreshTokenTtl());
    }
}
