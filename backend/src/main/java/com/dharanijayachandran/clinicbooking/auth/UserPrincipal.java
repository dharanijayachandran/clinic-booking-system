package com.dharanijayachandran.clinicbooking.auth;

import com.dharanijayachandran.clinicbooking.user.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Bridges our plain {@link User} entity to Spring Security's contract,
 * instead of making the entity itself implement UserDetails — keeps the
 * persistence model decoupled from the security framework.
 */
public final class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public UUID id() {
        return user.getId();
    }

    public User user() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
