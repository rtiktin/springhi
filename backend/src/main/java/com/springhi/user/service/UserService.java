package com.springhi.user.service;

import com.springhi.user.dto.ProfileRequest;
import com.springhi.user.dto.ProfileResponse;
import com.springhi.user.model.User;
import com.springhi.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public ProfileResponse getProfile(String username) {
        log.debug("Loading profile for username={}", username);
        User user = repository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });
        log.debug("Profile loaded for username={} id={}", username, user.getId());
        return toResponse(user);
    }

    public ProfileResponse updateProfile(String username, ProfileRequest request) {
        log.debug("Updating profile for username={}", username);
        User user = repository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found on update: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setBio(request.getBio());
        user.setPhone(request.getPhone());
        user.setAddressLine1(request.getAddressLine1());
        user.setAddressLine2(request.getAddressLine2());
        user.setCity(request.getCity());
        user.setState(request.getState());
        user.setPostalCode(request.getPostalCode());
        user.setCountry(request.getCountry());
        user.setDateOfBirth(request.getDateOfBirth());

        repository.save(user);
        log.debug("Profile updated for username={}", username);
        return toResponse(user);
    }

    private ProfileResponse toResponse(User user) {
        ProfileResponse r = new ProfileResponse();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setEmail(user.getEmail());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setBio(user.getBio());
        r.setPhone(user.getPhone());
        r.setAddressLine1(user.getAddressLine1());
        r.setAddressLine2(user.getAddressLine2());
        r.setCity(user.getCity());
        r.setState(user.getState());
        r.setPostalCode(user.getPostalCode());
        r.setCountry(user.getCountry());
        r.setDateOfBirth(user.getDateOfBirth());
        r.setCreatedAt(user.getCreatedAt());
        r.setUpdatedAt(user.getUpdatedAt());
        return r;
    }
}
