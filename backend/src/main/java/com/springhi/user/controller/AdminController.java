package com.springhi.user.controller;

import com.springhi.user.dto.AdminUserDto;
import com.springhi.user.model.User;
import com.springhi.user.model.UserIpAddress;
import com.springhi.user.repository.UserEmailHistoryRepository;
import com.springhi.user.repository.UserIpAddressRepository;
import com.springhi.user.repository.UserPhoneHistoryRepository;
import com.springhi.user.repository.UserRepository;
import com.springhi.user.service.UserIpAddressService;
import com.springhi.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserIpAddressService userIpAddressService;
    private final UserEmailHistoryRepository emailHistoryRepository;
    private final UserPhoneHistoryRepository phoneHistoryRepository;
    private final UserIpAddressRepository userIpAddressRepository;

    public AdminController(UserService userService, UserRepository userRepository,
                           UserIpAddressService userIpAddressService,
                           UserEmailHistoryRepository emailHistoryRepository,
                           UserPhoneHistoryRepository phoneHistoryRepository,
                           UserIpAddressRepository userIpAddressRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userIpAddressService = userIpAddressService;
        this.emailHistoryRepository = emailHistoryRepository;
        this.phoneHistoryRepository = phoneHistoryRepository;
        this.userIpAddressRepository = userIpAddressRepository;
    }

    private boolean isAdmin(UserDetails userDetails) {
        if (userDetails instanceof User u) {
            return u.getUserType() == 10;
        }
        return false;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> getAllUsers(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/stats/users")
    public ResponseEntity<Map<String, Object>> getUserStats(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int daysOffset) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday    = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfWeek     = now.toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime startOfMonth    = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfYear     = now.toLocalDate().withDayOfYear(1).atStartOfDay();

        long today     = userRepository.findByCreatedAtBetween(startOfToday, now.plusDays(1)).size();
        long thisWeek  = userRepository.findByCreatedAtBetween(startOfWeek, now.plusDays(1)).size();
        long thisMonth = userRepository.findByCreatedAtBetween(startOfMonth, now.plusDays(1)).size();
        long thisYear  = userRepository.findByCreatedAtBetween(startOfYear, now.plusDays(1)).size();

        LocalDateTime windowEnd   = startOfToday.minusDays(daysOffset);
        LocalDateTime windowStart = windowEnd.minusDays(29);
        List<User> recent = userRepository.findByCreatedAtBetween(windowStart, windowEnd.plusDays(1));

        Map<String, Long> dailyCounts = new LinkedHashMap<>();
        for (int i = 29; i >= 0; i--) {
            dailyCounts.put(windowEnd.minusDays(i).toLocalDate().toString(), 0L);
        }
        for (User u : recent) {
            String day = u.getCreatedAt().toLocalDate().toString();
            dailyCounts.computeIfPresent(day, (k, v) -> v + 1);
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        dailyCounts.forEach((date, count) -> daily.add(Map.of("date", date, "count", count)));

        return ResponseEntity.ok(Map.of(
                "today", today,
                "thisWeek", thisWeek,
                "thisMonth", thisMonth,
                "thisYear", thisYear,
                "daily", daily
        ));
    }

    @PutMapping("/users/{id}/type")
    public ResponseEntity<?> updateUserType(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        Integer newType = body.get("userType");
        if (newType == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "userType is required"));
        }
        try {
            AdminUserDto updated = userService.updateUserType(id, newType);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<?> changeUserPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "password is required"));
        }
        try {
            userService.changeUserPassword(id, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/email")
    public ResponseEntity<?> changeUserEmail(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        String newEmail = body.get("email");
        if (newEmail == null || newEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "email is required"));
        }
        try {
            AdminUserDto updated = userService.changeUserEmail(id, newEmail);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/notes")
    public ResponseEntity<?> updateAdminNotes(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        String notes = body.getOrDefault("notes", "");
        try {
            AdminUserDto updated = userService.updateAdminNotes(id, notes);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/suspend-chargebacks")
    public ResponseEntity<?> suspendForChargebacks(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        try {
            AdminUserDto updated = userService.suspendForChargebacks(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/impersonate")
    public ResponseEntity<?> impersonateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        try {
            String token = userService.generateImpersonationToken(id);
            return ResponseEntity.ok(Map.of("token", token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/users/{id}/ip-addresses")
    public ResponseEntity<List<Map<String, Object>>> getUserIpAddresses(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }
        List<UserIpAddress> ips = userIpAddressService.getForUser(id);
        List<Map<String, Object>> result = ips.stream().map(ip -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ipAddress", ip.getIpAddress());
            m.put("firstSeen", ip.getFirstSeen());
            m.put("lastSeen", ip.getLastSeen());
            m.put("requestCount", ip.getRequestCount());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/{id}/linked-accounts")
    public ResponseEntity<List<Map<String, Object>>> getLinkedAccounts(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) {
            return ResponseEntity.status(403).build();
        }

        Optional<User> targetOpt = userRepository.findById(id);
        if (targetOpt.isEmpty()) return ResponseEntity.notFound().build();
        User target = targetOpt.get();

        Set<String> allEmails = new HashSet<>();
        if (target.getEmail() != null) allEmails.add(target.getEmail());
        emailHistoryRepository.findByUserId(id).forEach(h -> allEmails.add(h.getEmail()));

        Set<String> allPhones = new HashSet<>();
        if (target.getPhone() != null) allPhones.add(target.getPhone());
        phoneHistoryRepository.findByUserId(id).forEach(h -> allPhones.add(h.getPhone()));

        Set<String> allIps = userIpAddressRepository.findByUserIdOrderByLastSeenDesc(id)
                .stream().map(UserIpAddress::getIpAddress).collect(Collectors.toSet());

        Map<Long, Set<String>> sharedMap = new HashMap<>();

        if (!allEmails.isEmpty()) {
            emailHistoryRepository.findByEmailIn(allEmails).stream()
                    .filter(h -> !h.getUserId().equals(id))
                    .forEach(h -> sharedMap.computeIfAbsent(h.getUserId(), k -> new LinkedHashSet<>())
                            .add("email (history): " + h.getEmail()));
            userRepository.findByEmailIn(allEmails).stream()
                    .filter(u -> !u.getId().equals(id))
                    .forEach(u -> sharedMap.computeIfAbsent(u.getId(), k -> new LinkedHashSet<>())
                            .add("email: " + u.getEmail()));
        }

        if (!allPhones.isEmpty()) {
            phoneHistoryRepository.findByPhoneIn(allPhones).stream()
                    .filter(h -> !h.getUserId().equals(id))
                    .forEach(h -> sharedMap.computeIfAbsent(h.getUserId(), k -> new LinkedHashSet<>())
                            .add("phone (history): " + h.getPhone()));
            userRepository.findByPhoneIn(allPhones).stream()
                    .filter(u -> !u.getId().equals(id) && u.getPhone() != null)
                    .forEach(u -> sharedMap.computeIfAbsent(u.getId(), k -> new LinkedHashSet<>())
                            .add("phone: " + u.getPhone()));
        }

        if (!allIps.isEmpty()) {
            userIpAddressRepository.findByIpAddressIn(allIps).stream()
                    .filter(ip -> !ip.getUserId().equals(id))
                    .forEach(ip -> sharedMap.computeIfAbsent(ip.getUserId(), k -> new LinkedHashSet<>())
                            .add("IP: " + ip.getIpAddress()));
        }

        if (sharedMap.isEmpty()) return ResponseEntity.ok(List.of());

        Map<Long, User> linkedUsers = userRepository.findAllById(sharedMap.keySet())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = sharedMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    User u = linkedUsers.get(e.getKey());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId", e.getKey());
                    m.put("username", u != null ? u.getUsername() : "unknown");
                    m.put("email", u != null ? u.getEmail() : null);
                    m.put("sharedValues", new ArrayList<>(e.getValue()));
                    return m;
                }).toList();

        return ResponseEntity.ok(result);
    }
}
