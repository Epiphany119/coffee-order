package com.coffee.module.order.biz.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 订单聚合根
 */
@Data
public class Order {
    private Long id;
    /** 详细订单号：YYMMDD-商家6位-类目3位-店铺当日顺序3位（如 260806-687257-007-001） */
    private String orderNo;
    private Long userId;
    private String guestId;
    /** 下单店铺 id（用户端选店后绑定） */
    private Long storeId;
    /** 取餐方式（PICKUP 到店自取 / DINE_IN 店内用餐 / DELIVERY 外卖配送） */
    private String fulfillmentType;
    /** 订单备注 */
    private String note;
    private String beverageName;
    private String size;
    /** 定制尺寸输入（如 "300"），size=CUSTOM 时有效 */
    private String customSize;
    private String condiments;
    private Double originalPrice;
    private Double finalPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedReadyTime;
    private String strategyName;
    private String memberLevel;
    private Integer earnedPoints;
    private Double memberDiscount;
    private String couponName;
    private Double couponDiscount;
    /** 卡券包券码；固定权益券不落库，取消订单时据此返还兑换券。 */
    private String voucherNo;
    private Integer totalCups;
    private String categoryCode;
    /** 订单明细（order_item，1 订单 N 明细；批量订单一行 = 购物车行级） */
    private java.util.List<OrderItem> items;
    public enum OrderStatus {
        UNPAID("待支付"),
        PENDING("等待商家接单"),
        ACCEPTED("商家已接单，等待制作"),
        PREPARING("制作中"),
        READY_FOR_DELIVERY("商家制作完毕，待骑手接单"),
        RIDER_ASSIGNED("骑手已接单"),
        DELIVERING("骑手配送中"),
        DELIVERED("骑手已送达，请取餐"),
        COMPLETED("已完成"),
        CANCELED("已取消");

        private final String description;
        OrderStatus(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    public boolean isUserOrder() {
        return userId != null && userId > 0;
    }

    public void transitionTo(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public void addSpending(double amount) {
        if (this.originalPrice == null) this.originalPrice = 0.0;
        this.originalPrice += amount;
    }
}
