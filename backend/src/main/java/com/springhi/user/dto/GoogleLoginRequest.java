package com.springhi.user.dto;

public class GoogleLoginRequest {
    private String code;
    private String referralCode;
    private String adCode;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public String getAdCode() { return adCode; }
    public void setAdCode(String adCode) { this.adCode = adCode; }
}
