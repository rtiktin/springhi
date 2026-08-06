package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.DividendEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DividendEventRepository extends JpaRepository<DividendEvent, Long> {

    boolean existsByPortfolioIdAndSymbolAndExDate(Long portfolioId, String symbol, LocalDate exDate);

    @Query("SELECT MAX(d.exDate) FROM DividendEvent d WHERE d.portfolioId = :portfolioId")
    Optional<LocalDate> findLastProcessedExDate(@Param("portfolioId") Long portfolioId);
}
