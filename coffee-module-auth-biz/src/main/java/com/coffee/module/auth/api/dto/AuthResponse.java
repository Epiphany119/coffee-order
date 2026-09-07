package com.coffee.module.auth.api.dto;

import com.coffee.module.member.api.dto.MemberLevelDTO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证响应（注册/登录/用户信息通用）
 */
public class AuthResponse {
    private boolean success;
    private String message;
    private Long id;
    /** 对外展示的 FIKA 账号号码，不等同于数据库自增 id。 */
    private String accountNo;
    private String username;
    private String nickname;
    private Double totalSpent;
    private String memberLevel;
    private String avatarUrl;
    private String phone;
    private LocalDate birthday;
    private String wechatId;
    private String qqNumber;
    private String email;
    /** 当前用户全部已验证邮箱；email 保留为首选邮箱兼容旧客户端。 */
    private List<String> emails = new ArrayList<>();
    private String otherInfo;
    /** Bearer 会话令牌；仅注册/登录成功时返回。 */
    private String accessToken;

    public AuthResponse() {}

    public AuthResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AuthResponse ok(Long userId, String username, String nickname, double totalSpent) {
        AuthResponse r = new AuthResponse();
        r.success = true;
        r.message = "操作成功";
        r.id = userId;
        r.username = username;
        r.nickname = nickname;
        r.totalSpent = totalSpent;
        r.memberLevel = MemberLevelDTO.fromTotalSpent(totalSpent).getLabel();
        return r;
    }

    public static AuthResponse fail(String message) {
        return new AuthResponse(false, message);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public String getMemberLevel() { return memberLevel; }
    public void setMemberLevel(String memberLevel) { this.memberLevel = memberLevel; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getWechatId() { return wechatId; }
    public void setWechatId(String wechatId) { this.wechatId = wechatId; }
    public String getQqNumber() { return qqNumber; }
    public void setQqNumber(String qqNumber) { this.qqNumber = qqNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<String> getEmails() { return emails; }
    public void setEmails(List<String> emails) {
        this.emails = emails == null ? new ArrayList<>() : new ArrayList<>(emails);
    }
    public String getOtherInfo() { return otherInfo; }
    public void setOtherInfo(String otherInfo) { this.otherInfo = otherInfo; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
