package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    List<PortfolioSnapshot> findByUserIdOrderBySnapshotDateAsc(Long userId);
    Optional<PortfolioSnapshot> findByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);

    List<PortfolioSnapshot> findByPortfolioIdOrderBySnapshotDateAsc(Long portfolioId);
    Optional<PortfolioSnapshot> findFirstByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);
    List<PortfolioSnapshot> findByPortfolioIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            Long portfolioId, LocalDate startDate);
}
