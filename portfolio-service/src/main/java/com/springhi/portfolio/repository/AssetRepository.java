package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByUserId(Long userId);
    Optional<Asset> findByUserIdAndSymbol(Long userId, String symbol);

    @Query("SELECT DISTINCT a.symbol FROM Asset a")
    List<String> findDistinctSymbols();

    @Query("SELECT DISTINCT a.userId FROM Asset a")
    List<Long> findDistinctUserIds();
}
