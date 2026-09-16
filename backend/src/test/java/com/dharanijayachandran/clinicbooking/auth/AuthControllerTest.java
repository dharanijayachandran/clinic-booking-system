package com.dharanijayachandran.clinicbooking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dharanijayachandran.clinicbooking.support.DatabaseCleaner;
import com.dharanijayachandran.clinicbooking.support.PostgresTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AuthControllerTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private static final String EMAIL = "patient@example.com";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @BeforeEach
    void registerAPatient() throws Exception {
        databaseCleaner.clean();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void registeringTheSameEmailTwiceIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(EMAIL, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationAlwaysCreatesAPatientRegardlessOfWhatTheClientSends() throws Exception {
        // RegisterRequest has no role field at all, so there is nothing for
        // a malicious client to even set — this just documents that fact.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("another@example.com", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void loginWithCorrectCredentialsSetsHttpOnlyCookiesAndReturnsTheUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andReturn();

        Cookie accessToken = result.getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
        Cookie refreshToken = result.getResponse().getCookie(CookieService.REFRESH_TOKEN_COOKIE);

        assertThat(accessToken).isNotNull();
        assertThat(accessToken.isHttpOnly()).isTrue();
        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken.isHttpOnly()).isTrue();
    }

    @Test
    void loginWithWrongPasswordIsRejectedWithoutRevealingWhy() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginForAnEmailThatDoesNotExistGetsTheSameMessageAsAWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"whatever123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void meWithoutAnAccessTokenCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meWithAValidAccessTokenCookieReturnsTheLoggedInUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andReturn();
        Cookie accessToken = loginResult.getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(get("/api/auth/me").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    private String registerBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new java.util.HashMap<>(java.util.Map.of(
                "email", email,
                "password", password,
                "firstName", "Test",
                "lastName", "Patient")));
    }
}
