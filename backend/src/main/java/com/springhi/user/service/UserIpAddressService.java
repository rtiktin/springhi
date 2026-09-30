package com.springhi.user.service;

import com.springhi.user.model.UserIpAddress;
import com.springhi.user.repository.UserIpAddressRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserIpAddressService {

    private final UserIpAddressRepository repository;

    public UserIpAddressService(UserIpAddressRepository repository) {
        this.repository = repository;
    }

    @Async
    public void record(Long userId, String ipAddress) {
        if (userId == null || ipAddress == null || ipAddress.isBlank()) return;
        try {
            repository.record(userId, ipAddress);
        } catch (Exception e) {
            // swallow — IP tracking must never break the request
        }
    }

    public List<UserIpAddress> getForUser(Long userId) {
        return repository.findByUserIdOrderByLastSeenDesc(userId);
    }
}
