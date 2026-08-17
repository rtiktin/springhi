package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.SymbolHistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SymbolHistoryStatusRepository extends JpaRepository<SymbolHistoryStatus, String> {
}
