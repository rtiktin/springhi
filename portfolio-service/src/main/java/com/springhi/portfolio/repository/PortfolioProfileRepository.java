package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.PortfolioProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PortfolioProfileRepository extends JpaRepository<PortfolioProfile, Long> {
    Optional<PortfolioProfile> findByPortfolioId(Long portfolioId);
}
