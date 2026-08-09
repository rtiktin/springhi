package com.springhi.user.service;

import com.springhi.user.model.UserIpAddress;
import com.springhi.user.repository.UserIpAddressRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserIpAddressService {

    private final UserIpAddressRepository repository;

    public UserIpAddressService(UserIpAddressRepository repository) {
        this.repository = repository;
    }

    @Async
    @Transactional
    public void record(Long userId, String ipAddress) {
        if (userId == null || ipAddress == null || ipAddress.isBlank()) return;
        try {
            repository.findByUserIdAndIpAddress(userId, ipAddress)
                    .ifPresentOrElse(existing -> {
                        existing.setLastSeen(LocalDateTime.now());
                        existing.setRequestCount(existing.getRequestCount() + 1);
                        repository.save(existing);
                    }, () -> {
                        UserIpAddress entry = new UserIpAddress();
                        entry.setUserId(userId);
                        entry.setIpAddress(ipAddress);
                        entry.setFirstSeen(LocalDateTime.now());
                        entry.setLastSeen(LocalDateTime.now());
                        entry.setRequestCount(1);
                        repository.save(entry);
                    });
        } catch (Exception e) {
            // swallow — IP tracking must never break the request
        }
    }

    public List<UserIpAddress> getForUser(Long userId) {
        return repository.findByUserIdOrderByLastSeenDesc(userId);
    }
}
