package com.dharanijayachandran.clinicbooking.config;

import com.dharanijayachandran.clinicbooking.auth.JwtAuthenticationFilter;
import com.dharanijayachandran.clinicbooking.auth.JwtService;
import com.dharanijayachandran.clinicbooking.user.UserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final String frontendOrigin;

    public SecurityConfig(@Value("${app.frontend-origin:http://localhost:4200}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: adaptive cost, salts automatically, ships with Spring
        // Security. Argon2id is OWASP's current top pick, but needs its own
        // dependency and manual memory/parallelism tuning; BCrypt is the
        // safer default and still fully defensible.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserRepository userRepository, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(email -> userRepository.findByEmailIgnoreCase(email)
                .map(com.dharanijayachandran.clinicbooking.auth.UserPrincipal::new)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(email)));
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, UserRepository userRepository) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Stateless JWT auth still needs CSRF protection because the
                // token travels in an auto-attached cookie, not a header the
                // browser only sends when JS explicitly asks it to. register/
                // login are exempted: there's no pre-existing authenticated
                // session for either to ride on, so the risk CSRF exists to
                // prevent isn't present — and exempting them is what lets the
                // Swagger UI demo actually work without a manual token dance.
                // Every endpoint that acts on an authenticated session
                // (refresh, logout, and everything from Phase 3 on) keeps
                // full CSRF enforcement.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/auth/register", "/api/auth/login"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                                "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Required for cookies to be sent/received cross-origin (Angular on
        // :4200, API on :8080 during local dev).
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
