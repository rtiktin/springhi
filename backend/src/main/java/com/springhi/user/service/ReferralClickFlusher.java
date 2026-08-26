package com.springhi.user.service;

import com.springhi.user.model.ReferralClickDaily;
import com.springhi.user.repository.ReferralClickDailyRepository;
import com.springhi.user.repository.ReferralCodeRepository;
import com.springhi.user.model.ReferralCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class ReferralClickFlusher {

    private final ReferralClickDailyRepository referralClickDailyRepository;
    private final ReferralCodeRepository referralCodeRepository;

    public ReferralClickFlusher(ReferralClickDailyRepository referralClickDailyRepository,
                                ReferralCodeRepository referralCodeRepository) {
        this.referralClickDailyRepository = referralClickDailyRepository;
        this.referralCodeRepository = referralCodeRepository;
    }

    @Transactional
    public void upsertDaily(Long codeId, LocalDate day, int clicks, int uniqueClicks) {
        ReferralClickDaily row = referralClickDailyRepository.findByReferralCodeIdAndDay(codeId, day)
                .orElseGet(() -> {
                    ReferralClickDaily r = new ReferralClickDaily();
                    r.setReferralCodeId(codeId);
                    r.setDay(day);
                    r.setClicks(0);
                    r.setUniqueClicks(0);
                    return r;
                });
        row.setClicks(row.getClicks() + clicks);
        row.setUniqueClicks(row.getUniqueClicks() + uniqueClicks);
        referralClickDailyRepository.save(row);

        referralCodeRepository.findById(codeId).ifPresent(rc -> {
            rc.setClicksCount(rc.getClicksCount() + clicks);
            rc.setUniqueClicksCount(rc.getUniqueClicksCount() + uniqueClicks);
            referralCodeRepository.save(rc);
        });
    }
}
