package com.springhi.user.service;

import com.springhi.user.dto.AdminUserDto;
import com.springhi.user.dto.ProfileRequest;
import com.springhi.user.dto.ProfileResponse;
import com.springhi.user.model.User;
import com.springhi.user.repository.UserRepository;
import com.springhi.user.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<Long, String> getDisplayNames(List<Long> ids) {
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
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

    public List<AdminUserDto> getAllUsers() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(AdminUserDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminUserDto updateUserType(Long userId, int newType) {
        Set<Integer> valid = Set.of(4, 6, 8, 10);
        if (!valid.contains(newType)) {
            throw new IllegalArgumentException("Invalid user type: " + newType);
        }
        User user = repository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        user.setUserType(newType);
        repository.save(user);
        log.info("Admin updated userType for userId={} to {}", userId, newType);
        return AdminUserDto.from(user);
    }

    @Transactional
    public void changeUserPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        User user = repository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        user.setPassword(passwordEncoder.encode(newPassword));
        repository.save(user);
        log.info("Admin changed password for userId={}", userId);
    }

    @Transactional
    public AdminUserDto changeUserEmail(Long userId, String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        String normalized = newEmail.trim().toLowerCase();
        User user = repository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        if (!normalized.equals(user.getEmail())) {
            repository.findByEmail(normalized).ifPresent(existing -> {
                throw new IllegalArgumentException("That email address is already in use by another account.");
            });
            user.setEmail(normalized);
            repository.save(user);
            log.info("Admin changed email for userId={} to {}", userId, normalized);
        }
        return AdminUserDto.from(user);
    }

    public String generateImpersonationToken(Long userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        log.info("Admin generating impersonation token for userId={} username={}", userId, user.getUsername());
        return jwtService.generateToken(user);
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
