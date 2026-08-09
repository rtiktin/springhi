package com.springhi.user.repository;

import com.springhi.user.model.UserEmailHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserEmailHistoryRepository extends JpaRepository<UserEmailHistory, Long> {
    List<UserEmailHistory> findByUserId(Long userId);
    List<UserEmailHistory> findByEmailIn(Collection<String> emails);
}
