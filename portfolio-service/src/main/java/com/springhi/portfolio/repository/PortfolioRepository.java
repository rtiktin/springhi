package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUserIdOrderByCreatedAtAsc(Long userId);
    List<Portfolio> findByCompetitionMonthNotNull();
    List<Portfolio> findByCompetitionMonth(LocalDate competitionMonth);
    List<Portfolio> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);
}
