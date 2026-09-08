package com.dharanijayachandran.clinicbooking.auth;

import com.dharanijayachandran.clinicbooking.auth.dto.LoginRequest;
import com.dharanijayachandran.clinicbooking.auth.dto.RegisterRequest;
import com.dharanijayachandran.clinicbooking.auth.dto.UserResponse;
import com.dharanijayachandran.clinicbooking.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        User user = authService.login(request, response);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<UserResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = CookieService.readCookie(request.getCookies(), CookieService.REFRESH_TOKEN_COOKIE);
        User user = authService.refresh(refreshToken, response);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.noContent().build();
    }

    /** Lets the Angular app ask "who am I?" on load, e.g. after a page refresh. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(UserResponse.from(principal.user()));
    }
}
