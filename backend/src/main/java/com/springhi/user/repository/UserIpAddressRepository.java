package com.springhi.user.repository;

import com.springhi.user.model.UserIpAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserIpAddressRepository extends JpaRepository<UserIpAddress, Long> {

    Optional<UserIpAddress> findByUserIdAndIpAddress(Long userId, String ipAddress);

    List<UserIpAddress> findByUserIdOrderByLastSeenDesc(Long userId);
}
