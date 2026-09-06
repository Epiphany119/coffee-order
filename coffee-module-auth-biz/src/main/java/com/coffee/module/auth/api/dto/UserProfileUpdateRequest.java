package com.coffee.module.auth.api.dto;

import java.time.LocalDate;

/** 顾客个人资料更新请求。空字符串可用于清空可选资料。 */
public class UserProfileUpdateRequest {
    private String nickname;
    private String phone;
    private LocalDate birthday;
    private String wechatId;
    private String qqNumber;
    private String otherInfo;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getWechatId() { return wechatId; }
    public void setWechatId(String wechatId) { this.wechatId = wechatId; }
    public String getQqNumber() { return qqNumber; }
    public void setQqNumber(String qqNumber) { this.qqNumber = qqNumber; }
    public String getOtherInfo() { return otherInfo; }
    public void setOtherInfo(String otherInfo) { this.otherInfo = otherInfo; }
}
