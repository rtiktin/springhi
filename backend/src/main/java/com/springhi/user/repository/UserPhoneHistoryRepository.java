package com.springhi.user.repository;

import com.springhi.user.model.UserPhoneHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPhoneHistoryRepository extends JpaRepository<UserPhoneHistory, Long> {
}
