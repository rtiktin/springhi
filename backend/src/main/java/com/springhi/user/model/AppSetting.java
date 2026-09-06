package com.springhi.user.model;

import jakarta.persistence.*;

/** Generic key/value application setting (e.g. admin-toggled feature flags). */
@Entity
@Table(name = "app_settings", schema = "springhi")
public class AppSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 80)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String settingValue;

    public AppSetting() {}

    public AppSetting(String settingKey, String settingValue) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
    }

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }
}
