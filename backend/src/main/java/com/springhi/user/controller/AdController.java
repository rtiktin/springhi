package com.springhi.user.controller;

import com.springhi.user.model.User;
import com.springhi.user.service.AdService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ads")
public class AdController {

    private final AdService adService;

    public AdController(AdService adService) {
        this.adService = adService;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestParam(required = false) String from,
                                  @RequestParam(required = false) String to) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(adService.comparison(parseDate(from), parseDate(to)));
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal UserDetails userDetails,
                                   @RequestBody Map<String, Object> body) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        try {
            return ResponseEntity.ok(adService.createAd(userId, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@AuthenticationPrincipal UserDetails userDetails,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(adService.updateAd(id, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal UserDetails userDetails,
                                    @PathVariable Long id) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        try {
            adService.deleteAd(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@AuthenticationPrincipal UserDetails userDetails,
                                    @PathVariable Long id,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(adService.detail(id, parseDate(from), parseDate(to)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/stats")
    public ResponseEntity<?> upsertStat(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(adService.upsertStat(id, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/stats/{date}")
    public ResponseEntity<?> deleteStat(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long id,
                                        @PathVariable String date) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        try {
            adService.deleteStat(id, LocalDate.parse(date));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "date must be yyyy-MM-dd"));
        }
    }

    private boolean isAdmin(UserDetails userDetails) {
        if (userDetails instanceof User u) {
            return u.getUserType() == 10;
        }
        return false;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.trim());
    }
}
