package com.springhi.user.service;

import com.springhi.user.dto.AuthRequest;
import com.springhi.user.dto.AuthResponse;
import com.springhi.user.dto.SignupRequest;
import com.springhi.user.model.PasswordResetToken;
import com.springhi.user.model.User;
import com.springhi.user.repository.PasswordResetTokenRepository;
import com.springhi.user.repository.UserRepository;
import com.springhi.user.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final JavaMailSender mailSender;

    @Value("${application.mail.from}")
    private String mailFrom;

    @Value("${application.mail.reset-code-expiry-minutes:15}")
    private int resetCodeExpiryMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    public AuthService(UserRepository repository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       PasswordResetTokenRepository resetTokenRepository,
                       JavaMailSender mailSender) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.resetTokenRepository = resetTokenRepository;
        this.mailSender = mailSender;
    }

    public AuthResponse signup(SignupRequest request) {
        if (repository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already taken");
        }
        if (repository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already taken");
        }
        if (repository.existsSuspendedByEmailOrName(
                request.getEmail(),
                request.getFirstName() != null ? request.getFirstName() : "",
                request.getLastName() != null ? request.getLastName() : "")) {
            throw new RuntimeException("Account registration is not permitted.");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        repository.save(user);

        String jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken);
    }

    public AuthResponse signin(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
        User user = repository.findByUsername(request.getUsername())
                .or(() -> repository.findByEmail(request.getUsername()))
                .orElseThrow();
        if (user.getUserType() == 4) {
            throw new RuntimeException("Your account has been suspended. You cannot log in.");
        }
        if (user.getUserType() == 6) {
            throw new RuntimeException("Your account has been closed. You cannot log in.");
        }
        String jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken);
    }

    public AuthResponse sendEmailVerification(String username, String newEmail) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found."));

        String targetEmail = (newEmail != null && !newEmail.isBlank()) ? newEmail.trim().toLowerCase() : user.getEmail();

        if (!targetEmail.equals(user.getEmail())) {
            if (repository.findByEmail(targetEmail).isPresent()) {
                throw new RuntimeException("That email address is already in use by another account.");
            }
            user.setEmail(targetEmail);
            repository.save(user);
        }

        resetTokenRepository.deleteAllByEmail(targetEmail);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(targetEmail);
        token.setCode(code);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(resetCodeExpiryMinutes));
        resetTokenRepository.save(token);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(targetEmail);
        message.setSubject("SpringHi.ai — Email Verification Code");
        message.setText(
                "Your email verification code is: " + code + "\n\n" +
                "This code expires in " + resetCodeExpiryMinutes + " minutes.\n\n" +
                "If you did not request this, please ignore this email."
        );
        mailSender.send(message);

        return new AuthResponse(jwtService.generateToken(user));
    }

    public AuthResponse verifyEmail(String username, String code) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found."));

        PasswordResetToken token = resetTokenRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(user.getEmail())
                .orElseThrow(() -> new RuntimeException("No active verification code found. Please request a new one."));

        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new RuntimeException("Verification code has expired. Please request a new one.");
        }
        if (!token.getCode().equals(code.trim())) {
            throw new RuntimeException("Invalid verification code.");
        }

        user.setEmailVerified(true);
        repository.save(user);
        token.setUsed(true);
        resetTokenRepository.save(token);

        return new AuthResponse(jwtService.generateToken(user));
    }

    public void forgotPassword(String email) {
        boolean userExists = repository.findByEmail(email).isPresent();
        if (!userExists) {
            return;
        }
        resetTokenRepository.deleteAllByEmail(email);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setCode(code);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(resetCodeExpiryMinutes));
        resetTokenRepository.save(token);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("SpringHi.ai — Password Reset Code");
        message.setText(
                "Your password reset code is: " + code + "\n\n" +
                "This code expires in " + resetCodeExpiryMinutes + " minutes.\n\n" +
                "If you did not request a password reset, please ignore this email."
        );
        mailSender.send(message);
    }

    public void resetPassword(String email, String code, String newPassword) {
        PasswordResetToken token = resetTokenRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new RuntimeException("No active reset code found for this email."));

        if (token.isUsed()) {
            throw new RuntimeException("Reset code has already been used.");
        }
        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new RuntimeException("Reset code has expired. Please request a new one.");
        }
        if (!token.getCode().equals(code)) {
            throw new RuntimeException("Invalid reset code.");
        }

        User user = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));
        user.setPassword(passwordEncoder.encode(newPassword));
        repository.save(user);

        token.setUsed(true);
        resetTokenRepository.save(token);
    }
}
