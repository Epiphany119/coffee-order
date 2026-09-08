package com.coffee.module.delivery.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("delivery_order")
public class DeliveryOrderPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long storeId;
    private String storeName;
    private Double amount;
    private String itemSummary;
    /** DeliveryOrderItem 列表 JSON 快照。 */
    private String itemDetails;
    private String note;
    private String addressLabel;
    private String receiverName;
    private String receiverPhone;
    private String detailAddress;
    private String status;
    private Long riderId;
    private String riderName;
    private LocalDateTime createdAt;
    private LocalDateTime claimedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime updatedAt;
}
