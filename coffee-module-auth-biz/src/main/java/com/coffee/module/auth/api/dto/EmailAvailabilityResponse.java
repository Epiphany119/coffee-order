package com.coffee.module.auth.api.dto;

/** 邮箱占用检查结果，不返回邮箱所属用户的任何信息。 */
public class EmailAvailabilityResponse {
    private boolean success = true;
    private boolean available;
    private boolean bound;
    private String message;

    public EmailAvailabilityResponse() {}

    public EmailAvailabilityResponse(boolean available, boolean bound, String message) {
        this.available = available;
        this.bound = bound;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public boolean isBound() { return bound; }
    public void setBound(boolean bound) { this.bound = bound; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
