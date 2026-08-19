package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.SupportTicketDto;
import com.springhi.portfolio.model.SupportTicket;
import com.springhi.portfolio.model.SupportTicketReply;
import com.springhi.portfolio.repository.SupportTicketReplyRepository;
import com.springhi.portfolio.repository.SupportTicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class SupportService {

    private final SupportTicketRepository ticketRepo;
    private final SupportTicketReplyRepository replyRepo;

    public SupportService(SupportTicketRepository ticketRepo, SupportTicketReplyRepository replyRepo) {
        this.ticketRepo = ticketRepo;
        this.replyRepo = replyRepo;
    }

    @Transactional
    public SupportTicketDto.TicketDetail createTicket(Long userId, String username,
                                                       String subject, String categoryStr,
                                                       String message) {
        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(userId);
        ticket.setUsername(username);
        ticket.setSubject(subject);
        ticket.setMessage(message);
        try {
            ticket.setCategory(SupportTicket.Category.valueOf(categoryStr));
        } catch (IllegalArgumentException e) {
            ticket.setCategory(SupportTicket.Category.GENERAL);
        }
        return SupportTicketDto.TicketDetail.from(ticketRepo.save(ticket));
    }

    public Page<SupportTicketDto.TicketSummary> getUserTickets(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ticketRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(SupportTicketDto.TicketSummary::from);
    }

    @Transactional
    public SupportTicketDto.TicketDetail getTicketDetail(Long ticketId, Long userId, boolean isAdmin) {
        SupportTicket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        if (!isAdmin && !ticket.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        return SupportTicketDto.TicketDetail.from(ticket);
    }

    @Transactional
    public SupportTicketDto.TicketDetail addReply(Long ticketId, Long responderId,
                                                   String responderName, boolean isAdmin,
                                                   String message) {
        SupportTicket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        SupportTicketReply reply = new SupportTicketReply();
        reply.setTicket(ticket);
        reply.setResponderId(responderId);
        reply.setResponderName(responderName);
        reply.setAdminReply(isAdmin);
        reply.setMessage(message);
        replyRepo.save(reply);
        if (isAdmin && ticket.getStatus() == SupportTicket.Status.OPEN) {
            ticket.setStatus(SupportTicket.Status.IN_PROGRESS);
            ticketRepo.save(ticket);
        }
        return SupportTicketDto.TicketDetail.from(ticketRepo.findById(ticketId).orElseThrow());
    }

    @Transactional
    public SupportTicketDto.TicketDetail updateStatus(Long ticketId, String statusStr) {
        SupportTicket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        ticket.setStatus(SupportTicket.Status.valueOf(statusStr));
        return SupportTicketDto.TicketDetail.from(ticketRepo.save(ticket));
    }

    public Page<SupportTicketDto.TicketSummary> getAllTickets(String statusFilter, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                SupportTicket.Status status = SupportTicket.Status.valueOf(statusFilter);
                return ticketRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
                        .map(SupportTicketDto.TicketSummary::from);
            } catch (IllegalArgumentException ignored) {}
        }
        return ticketRepo.findAllByOrderByCreatedAtDesc(pageable)
                .map(SupportTicketDto.TicketSummary::from);
    }

    public Map<String, Long> getTicketCounts() {
        return Map.of(
                "OPEN", ticketRepo.countByStatus(SupportTicket.Status.OPEN),
                "IN_PROGRESS", ticketRepo.countByStatus(SupportTicket.Status.IN_PROGRESS),
                "RESOLVED", ticketRepo.countByStatus(SupportTicket.Status.RESOLVED),
                "CLOSED", ticketRepo.countByStatus(SupportTicket.Status.CLOSED)
        );
    }
}
