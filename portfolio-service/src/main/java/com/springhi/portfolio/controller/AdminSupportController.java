package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.SupportTicketDto;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.SupportService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/support")
public class AdminSupportController {

    private final SupportService supportService;

    public AdminSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping("/tickets")
    public ResponseEntity<Page<SupportTicketDto.TicketSummary>> getAllTickets(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(supportService.getAllTickets(status, page, size));
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<SupportTicketDto.TicketDetail> getTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(supportService.getTicketDetail(id, null, true));
    }

    @PostMapping("/tickets/{id}/replies")
    public ResponseEntity<SupportTicketDto.TicketDetail> addReply(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(supportService.addReply(id, principal.getId(), principal.getUsername(), true, body.get("message")));
    }

    @PutMapping("/tickets/{id}/status")
    public ResponseEntity<SupportTicketDto.TicketDetail> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(supportService.updateStatus(id, body.get("status")));
    }

    @GetMapping("/tickets/counts")
    public ResponseEntity<Map<String, Long>> getCounts(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(supportService.getTicketCounts());
    }
}
