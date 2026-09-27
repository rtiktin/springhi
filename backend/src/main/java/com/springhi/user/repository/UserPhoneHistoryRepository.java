package com.springhi.user.repository;

import com.springhi.user.model.UserPhoneHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserPhoneHistoryRepository extends JpaRepository<UserPhoneHistory, Long> {
    List<UserPhoneHistory> findByUserId(Long userId);
    List<UserPhoneHistory> findByPhoneIn(Collection<String> phones);

    @Query(value = "SELECT DISTINCT user_id FROM springhi.user_phone_history WHERE regexp_replace(phone, '[^0-9]', '', 'g') IN (:numbers)", nativeQuery = true)
    List<Long> findUserIdsByPhoneDigitsIn(@Param("numbers") Collection<String> numbers);
}
