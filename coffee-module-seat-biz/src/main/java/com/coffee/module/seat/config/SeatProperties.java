package com.coffee.module.seat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 座位模块配置
 * 可在 application.yml 中覆盖：
 *   coffee.seat.store-name: 静安店
 *   coffee.seat.qr-base-url: http://localhost:5173
 *   coffee.seat.assign-timeout-minutes: 15
 */
@Component
@ConfigurationProperties(prefix = "coffee.seat")
public class SeatProperties {

    /** 店名，座位编号前缀，如 静安店-001 */
    private String storeName = "静安店";

    /** 二维码内容的基础地址（前端系统地址），生产环境改为部署域名 */
    private String qrBaseUrl = "http://localhost:5173";

    /** 分配后未落座的超时释放时间（分钟） */
    private int assignTimeoutMinutes = 15;

    /** 双人桌数量 */
    private int twoSeats = 70;

    /** 四人桌数量 */
    private int fourSeats = 20;

    /** 多人桌数量（6-8 人） */
    private int multiSeats = 9;

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getQrBaseUrl() { return qrBaseUrl; }
    public void setQrBaseUrl(String qrBaseUrl) { this.qrBaseUrl = qrBaseUrl; }
    public int getAssignTimeoutMinutes() { return assignTimeoutMinutes; }
    public void setAssignTimeoutMinutes(int assignTimeoutMinutes) { this.assignTimeoutMinutes = assignTimeoutMinutes; }
    public int getTwoSeats() { return twoSeats; }
    public void setTwoSeats(int twoSeats) { this.twoSeats = twoSeats; }
    public int getFourSeats() { return fourSeats; }
    public void setFourSeats(int fourSeats) { this.fourSeats = fourSeats; }
    public int getMultiSeats() { return multiSeats; }
    public void setMultiSeats(int multiSeats) { this.multiSeats = multiSeats; }
}
