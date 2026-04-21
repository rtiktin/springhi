package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.UserProfileRequest;
import com.springhi.portfolio.dto.UserProfileResponse;
import com.springhi.portfolio.model.UserProfile;
import com.springhi.portfolio.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public Optional<UserProfileResponse> getProfile(Long userId) {
        return userProfileRepository.findByUserId(userId).map(this::toResponse);
    }

    public UserProfileResponse saveProfile(Long userId, UserProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(new UserProfile());

        profile.setUserId(userId);
        profile.setRiskLevel(request.riskLevel());
        profile.setGoal(request.goal());
        profile.setHorizonYears(request.horizonYears());
        profile.setLiquidityNeeds(request.liquidityNeeds());
        profile.setKnowledgeLevel(request.knowledgeLevel());
        profile.setAdditionalComments(request.additionalComments());
        profile.setAvailableCash(request.availableCash());
        profile.setCurrency(request.currency() != null ? request.currency() : "USD");

        if (request.sectorConstraints() != null) {
            profile.setSectorConstraints(String.join(",", request.sectorConstraints()));
        } else {
            profile.setSectorConstraints(null);
        }

        return toResponse(userProfileRepository.save(profile));
    }

    private UserProfileResponse toResponse(UserProfile p) {
        List<String> sectors = (p.getSectorConstraints() != null && !p.getSectorConstraints().isBlank())
                ? Arrays.asList(p.getSectorConstraints().split(","))
                : Collections.emptyList();

        return new UserProfileResponse(
                p.getId(),
                p.getUserId(),
                p.getRiskLevel(),
                p.getGoal(),
                p.getHorizonYears(),
                p.getLiquidityNeeds(),
                p.getKnowledgeLevel(),
                p.getAdditionalComments(),
                p.getAvailableCash(),
                p.getCurrency(),
                sectors
        );
    }
}
