package com.springhi.portfolio.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;

public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String username;
    private final int userType;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String username, int userType, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.userType = userType;
        this.authorities = authorities;
    }

    public Long getId() { return id; }
    public int getUserType() { return userType; }
    public boolean isAdmin() { return userType == 10; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return ""; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
