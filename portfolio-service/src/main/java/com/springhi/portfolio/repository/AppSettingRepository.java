package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {}
