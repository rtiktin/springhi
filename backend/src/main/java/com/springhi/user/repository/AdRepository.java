package com.springhi.user.repository;

import com.springhi.user.model.Ad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdRepository extends JpaRepository<Ad, Long> {
    List<Ad> findAllByOrderByCreatedAtDesc();
    List<Ad> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
    Optional<Ad> findByTrackingCode(String trackingCode);
    boolean existsByTrackingCode(String trackingCode);
}
