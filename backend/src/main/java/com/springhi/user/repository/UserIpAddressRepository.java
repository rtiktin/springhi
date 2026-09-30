package com.springhi.user.repository;

import com.springhi.user.model.UserIpAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserIpAddressRepository extends JpaRepository<UserIpAddress, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO springhi.user_ip_addresses (user_id, ip_address, first_seen, last_seen, request_count) "
            + "VALUES (:userId, :ipAddress, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1) "
            + "ON CONFLICT (user_id, ip_address) DO UPDATE SET "
            + "last_seen = EXCLUDED.last_seen, request_count = springhi.user_ip_addresses.request_count + 1", nativeQuery = true)
    void record(@Param("userId") Long userId, @Param("ipAddress") String ipAddress);

    List<UserIpAddress> findByUserIdOrderByLastSeenDesc(Long userId);

    List<UserIpAddress> findByIpAddressIn(java.util.Collection<String> ipAddresses);
}
