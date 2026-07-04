package com.springhi.user.repository;

import com.springhi.user.model.UserEmailHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserEmailHistoryRepository extends JpaRepository<UserEmailHistory, Long> {
}
