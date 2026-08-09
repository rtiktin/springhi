package com.springhi.user.repository;

import com.springhi.user.model.UserPhoneHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserPhoneHistoryRepository extends JpaRepository<UserPhoneHistory, Long> {
    List<UserPhoneHistory> findByUserId(Long userId);
    List<UserPhoneHistory> findByPhoneIn(Collection<String> phones);
}
