package com.springhi.user.repository;

import com.springhi.user.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<PaymentMethod> findFirstByUserIdAndIsDefaultTrue(Long userId);
}
