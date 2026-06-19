package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.PortfolioProfileDto;
import com.springhi.portfolio.dto.UserProfileResponse;
import com.springhi.portfolio.model.PortfolioProfile;
import com.springhi.portfolio.repository.PortfolioProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PortfolioProfileService {

    private final PortfolioProfileRepository profileRepository;
    private final UserProfileService userProfileService;

    public PortfolioProfileService(PortfolioProfileRepository profileRepository,
                                   UserProfileService userProfileService) {
        this.profileRepository = profileRepository;
        this.userProfileService = userProfileService;
    }

    public PortfolioProfileDto initFromInvestorProfile(Long portfolioId, Long userId) {
        PortfolioProfile p = profileRepository.findByPortfolioId(portfolioId)
                .orElse(new PortfolioProfile());
        p.setPortfolioId(portfolioId);
        userProfileService.getProfile(userId).ifPresent(ip -> {
            p.setRiskLevel(ip.riskLevel());
            p.setGoal(ip.goal());
            p.setHorizonYears(ip.horizonYears());
            p.setLiquidityNeeds(ip.liquidityNeeds());
            p.setAdditionalComments(ip.additionalComments());
            p.setCurrency(ip.currency() != null ? ip.currency() : "USD");
            if (ip.sectorConstraints() != null && !ip.sectorConstraints().isEmpty()) {
                p.setSectorConstraints(String.join(",", ip.sectorConstraints()));
            }
        });
        return PortfolioProfileDto.from(profileRepository.save(p));
    }

    public Optional<PortfolioProfileDto> getProfile(Long portfolioId) {
        return profileRepository.findByPortfolioId(portfolioId)
                .map(PortfolioProfileDto::from);
    }

    public PortfolioProfileDto saveProfile(Long portfolioId, PortfolioProfileDto dto) {
        PortfolioProfile p = profileRepository.findByPortfolioId(portfolioId)
                .orElse(new PortfolioProfile());
        p.setPortfolioId(portfolioId);
        p.setRiskLevel(dto.riskLevel());
        p.setGoal(dto.goal());
        p.setHorizonYears(dto.horizonYears());
        p.setLiquidityNeeds(dto.liquidityNeeds());
        p.setAdditionalComments(dto.additionalComments());
        p.setCurrency(dto.currency() != null ? dto.currency() : "USD");
        p.setSectorConstraints(dto.sectorConstraints() != null && !dto.sectorConstraints().isEmpty()
                ? String.join(",", dto.sectorConstraints()) : null);
        return PortfolioProfileDto.from(profileRepository.save(p));
    }

    public Optional<PortfolioProfile> getRawProfile(Long portfolioId) {
        return profileRepository.findByPortfolioId(portfolioId);
    }
}
