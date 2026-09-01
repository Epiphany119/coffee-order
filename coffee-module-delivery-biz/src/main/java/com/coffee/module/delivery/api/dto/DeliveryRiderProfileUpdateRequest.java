package com.coffee.module.delivery.api.dto;

import java.time.LocalDate;

/** 配送员个人资料更新请求。 */
public class DeliveryRiderProfileUpdateRequest {
    private String nickname;
    private String phone;
    private LocalDate birthday;
    private String email;
    private String otherInfo;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getOtherInfo() { return otherInfo; }
    public void setOtherInfo(String otherInfo) { this.otherInfo = otherInfo; }
}
