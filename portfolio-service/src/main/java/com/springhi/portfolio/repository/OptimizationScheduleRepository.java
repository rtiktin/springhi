package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.OptimizationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OptimizationScheduleRepository extends JpaRepository<OptimizationSchedule, Long> {

    List<OptimizationSchedule> findByPortfolioId(Long portfolioId);

    List<OptimizationSchedule> findByUserId(Long userId);

    @Query("SELECT s FROM OptimizationSchedule s WHERE s.enabled = true AND s.nextRunAt <= :now")
    List<OptimizationSchedule> findDueSchedules(@Param("now") LocalDateTime now);
}
