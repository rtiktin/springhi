package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserId(Long userId);

    @Query("""
            SELECT COALESCE(SUM(
                CASE t.type
                    WHEN 'DEPOSIT'    THEN  t.quantity * t.price
                    WHEN 'SELL'       THEN  t.quantity * t.price
                    WHEN 'BUY'        THEN -(t.quantity * t.price)
                    WHEN 'WITHDRAWAL' THEN -(t.quantity * t.price)
                    ELSE 0
                END
            ), 0)
            FROM Transaction t WHERE t.userId = :userId
            """)
    BigDecimal computeCashBalance(@Param("userId") Long userId);
}
