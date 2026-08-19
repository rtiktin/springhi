package com.springhi.portfolio.dto;

import com.springhi.portfolio.model.SupportTicket;
import com.springhi.portfolio.model.SupportTicketReply;
import java.time.LocalDateTime;
import java.util.List;

public class SupportTicketDto {

    public record ReplyDto(
            Long id,
            String responderName,
            boolean adminReply,
            String message,
            LocalDateTime createdAt
    ) {
        public static ReplyDto from(SupportTicketReply r) {
            return new ReplyDto(r.getId(), r.getResponderName(), r.isAdminReply(), r.getMessage(), r.getCreatedAt());
        }
    }

    public record TicketSummary(
            Long id,
            String subject,
            String category,
            String status,
            String username,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            int replyCount
    ) {
        public static TicketSummary from(SupportTicket t) {
            return new TicketSummary(
                    t.getId(), t.getSubject(), t.getCategory().name(), t.getStatus().name(),
                    t.getUsername(), t.getCreatedAt(), t.getUpdatedAt(),
                    t.getReplies().size()
            );
        }
    }

    public record TicketDetail(
            Long id,
            String subject,
            String category,
            String status,
            String username,
            Long userId,
            String message,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<ReplyDto> replies
    ) {
        public static TicketDetail from(SupportTicket t) {
            return new TicketDetail(
                    t.getId(), t.getSubject(), t.getCategory().name(), t.getStatus().name(),
                    t.getUsername(), t.getUserId(), t.getMessage(),
                    t.getCreatedAt(), t.getUpdatedAt(),
                    t.getReplies().stream().map(ReplyDto::from).toList()
            );
        }
    }
}
