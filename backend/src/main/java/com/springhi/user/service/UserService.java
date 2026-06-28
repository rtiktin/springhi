package com.springhi.user.service;

import com.springhi.user.dto.ProfileRequest;
import com.springhi.user.dto.ProfileResponse;
import com.springhi.user.model.User;
import com.springhi.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public Map<Long, String> getDisplayNames(List<Long> ids) {
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> {
                            String fn = u.getFirstName();
                            String ln = u.getLastName();
                            if (fn != null && !fn.isBlank()) {
                                return ln != null && !ln.isBlank() ? fn + " " + ln : fn;
                            }
                            return u.getUsername();
                        }
                ));
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

    @Transactional
    public ProfileResponse updateProfile(String username, ProfileRequest request) {
        log.debug("Updating profile for username={}", username);
        User user = repository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found on update: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equals(user.getEmail())) {
                repository.findByEmail(newEmail).ifPresent(existing -> {
                    throw new RuntimeException("Email address is already in use by another account.");
                });
                user.setEmail(newEmail);
            }
        }

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
