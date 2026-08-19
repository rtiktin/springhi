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
@RequestMapping("/api/v1/support")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicketDto.TicketDetail> createTicket(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(supportService.createTicket(
                principal.getId(), principal.getUsername(),
                body.get("subject"), body.get("category"), body.get("message")));
    }

    @GetMapping("/tickets")
    public ResponseEntity<Page<SupportTicketDto.TicketSummary>> getMyTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(supportService.getUserTickets(principal.getId(), page, size));
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<SupportTicketDto.TicketDetail> getTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(supportService.getTicketDetail(id, principal.getId(), false));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/tickets/{id}/replies")
    public ResponseEntity<SupportTicketDto.TicketDetail> addReply(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        try {
            supportService.getTicketDetail(id, principal.getId(), false);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(supportService.addReply(id, principal.getId(), principal.getUsername(), false, body.get("message")));
    }
}
