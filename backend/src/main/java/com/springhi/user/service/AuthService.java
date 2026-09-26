package com.springhi.user.service;

import com.springhi.user.dto.AuthRequest;
import com.springhi.user.dto.AuthResponse;
import com.springhi.user.dto.SignupRequest;
import com.springhi.user.model.PasswordResetToken;
import com.springhi.user.model.User;
import com.springhi.user.model.UserEmailHistory;
import com.springhi.user.model.UserPhoneHistory;
import com.springhi.user.repository.PasswordResetTokenRepository;
import com.springhi.user.repository.UserEmailHistoryRepository;
import com.springhi.user.repository.UserPhoneHistoryRepository;
import com.springhi.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import com.springhi.user.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final UserEmailHistoryRepository emailHistoryRepository;
    private final UserPhoneHistoryRepository phoneHistoryRepository;
    private final SendGridEmailService emailService;
    private final TelnyxService telnyxService;
    private final ReferralService referralService;
    private final AdService adService;
    private final GoogleOAuthService googleOAuthService;

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
                       UserEmailHistoryRepository emailHistoryRepository,
                       UserPhoneHistoryRepository phoneHistoryRepository,
                       SendGridEmailService emailService,
                       TelnyxService telnyxService,
                       ReferralService referralService,
                       AdService adService,
                       GoogleOAuthService googleOAuthService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.resetTokenRepository = resetTokenRepository;
        this.emailHistoryRepository = emailHistoryRepository;
        this.phoneHistoryRepository = phoneHistoryRepository;
        this.emailService = emailService;
        this.telnyxService = telnyxService;
        this.referralService = referralService;
        this.adService = adService;
        this.googleOAuthService = googleOAuthService;
    }

    @Transactional
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

        referralService.attributeSignup(user.getId(), request.getReferralCode());
        adService.attributeSignup(user.getId(), request.getAdCode());

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

    @Transactional
    public AuthResponse googleLogin(String code, String referralCode, String adCode) {
        GoogleOAuthService.GoogleUserInfo info = googleOAuthService.exchange(code);
        String email = info.email();

        Optional<User> existing = repository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getUserType() == 4) {
                throw new RuntimeException("Your account has been suspended. You cannot log in.");
            }
            if (user.getUserType() == 6) {
                throw new RuntimeException("Your account has been closed. You cannot log in.");
            }
            return new AuthResponse(jwtService.generateToken(user));
        }

        if (repository.existsSuspendedByEmailOrName(
                email,
                info.givenName() != null ? info.givenName() : "",
                info.familyName() != null ? info.familyName() : "")) {
            throw new RuntimeException("Account registration is not permitted.");
        }

        User user = createGoogleUser(info);
        referralService.attributeSignup(user.getId(), referralCode);
        adService.attributeSignup(user.getId(), adCode);
        return new AuthResponse(jwtService.generateToken(user));
    }

    private User createGoogleUser(GoogleOAuthService.GoogleUserInfo info) {
        String base = info.givenName();
        String sanitized = (base == null || base.isBlank())
                ? "user"
                : base.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (sanitized.isEmpty()) {
            sanitized = "user";
        }
        String prefix = "sh_memb_" + sanitized + "_";
        for (int n = 1; n <= 1000; n++) {
            String username = prefix + n;
            if (repository.findByUsername(username).isPresent()) {
                continue;
            }
            User user = new User();
            user.setUsername(username);
            user.setEmail(info.email());
            user.setPassword(passwordEncoder.encode(UUID.randomUUID() + "-" + RANDOM.nextLong()));
            user.setFirstName(info.givenName());
            user.setLastName(info.familyName());
            user.setEmailVerified(true);
            return repository.save(user);
        }
        throw new RuntimeException("Unable to create account. Please try again.");
    }

    public AuthResponse sendEmailVerification(String username, String newEmail) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found."));

        String targetEmail = (newEmail != null && !newEmail.isBlank()) ? newEmail.trim().toLowerCase() : user.getEmail();

        String previousEmail = null;
        if (!targetEmail.equals(user.getEmail())) {
            if (user.isEmailVerified()) {
                throw new RuntimeException("Your email address is already verified and cannot be changed. Please contact support to update it.");
            }
            if (repository.findByEmail(targetEmail).isPresent()) {
                throw new RuntimeException("That email address is already in use by another account.");
            }
            previousEmail = user.getEmail();
            user.setEmail(targetEmail);
            repository.save(user);
        }

        resetTokenRepository.deleteAllByEmail(targetEmail);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(targetEmail);
        token.setCode(code);
        if (previousEmail != null) {
            token.setPreviousEmail(previousEmail);
        }
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
        emailService.send(message);

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

        if (token.getPreviousEmail() != null) {
            UserEmailHistory history = new UserEmailHistory();
            history.setUserId(user.getId());
            history.setEmail(token.getPreviousEmail());
            history.setReplacedAt(LocalDateTime.now());
            emailHistoryRepository.save(history);
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
        emailService.send(message);
    }

    @Transactional
    public AuthResponse sendPhoneVerification(String username, String newPhone) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found."));
        String phone = (newPhone != null && !newPhone.isBlank()) ? newPhone.trim() : user.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new RuntimeException("Please enter a cell phone number.");
        }
        String normalizedPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");
        if (!normalizedPhone.startsWith("+")) {
            normalizedPhone = "+1" + normalizedPhone;
        }
        String previousPhone = null;
        if (!normalizedPhone.equals(user.getPhone())) {
            previousPhone = user.getPhone();
            user.setPhone(normalizedPhone);
            user.setPhoneVerified(false);
            repository.save(user);
        }
        resetTokenRepository.deleteAllByEmail(normalizedPhone);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(normalizedPhone);
        token.setCode(code);
        if (previousPhone != null) {
            token.setPreviousPhone(previousPhone);
        }
        token.setExpiresAt(LocalDateTime.now().plusMinutes(resetCodeExpiryMinutes));
        resetTokenRepository.save(token);
        telnyxService.sendSms(normalizedPhone, "Your SpringHi.ai verification code is: " + code);
        return new AuthResponse(jwtService.generateToken(user));
    }

    @Transactional
    public AuthResponse verifyPhone(String username, String code) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found."));
        String phone = user.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new RuntimeException("No cell phone number on file.");
        }
        String normalizedPhone = phone.trim().replaceAll("[\\s\\-\\(\\)]", "");
        if (!normalizedPhone.startsWith("+")) {
            normalizedPhone = "+1" + normalizedPhone;
        }
        PasswordResetToken token = resetTokenRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(normalizedPhone)
                .orElseThrow(() -> new RuntimeException("No active verification code found. Please request a new one."));
        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new RuntimeException("Verification code has expired. Please request a new one.");
        }
        if (!token.getCode().equals(code.trim())) {
            throw new RuntimeException("Invalid verification code.");
        }
        if (token.getPreviousPhone() != null) {
            UserPhoneHistory history = new UserPhoneHistory();
            history.setUserId(user.getId());
            history.setPhone(token.getPreviousPhone());
            history.setReplacedAt(LocalDateTime.now());
            phoneHistoryRepository.save(history);
        }
        user.setPhoneVerified(true);
        repository.save(user);
        token.setUsed(true);
        resetTokenRepository.save(token);
        return new AuthResponse(jwtService.generateToken(user));
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
