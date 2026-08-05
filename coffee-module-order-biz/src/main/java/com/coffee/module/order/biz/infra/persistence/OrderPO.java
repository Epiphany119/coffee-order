package com.coffee.module.order.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 订单持久化对象
 */
@Data
@TableName("user_order")
public class OrderPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String guestId;
    private String beverageName;
    private String size;
    private String condiments;
    private Double originalPrice;
    private Double finalPrice;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedReadyTime;
}
