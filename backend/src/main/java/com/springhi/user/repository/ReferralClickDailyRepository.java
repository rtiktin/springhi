package com.springhi.user.repository;

import com.springhi.user.model.ReferralClickDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralClickDailyRepository extends JpaRepository<ReferralClickDaily, Long> {
    Optional<ReferralClickDaily> findByReferralCodeIdAndDay(Long referralCodeId, LocalDate day);
    List<ReferralClickDaily> findByReferralCodeIdInAndDayBetween(List<Long> referralCodeIds, LocalDate start, LocalDate end);
}
