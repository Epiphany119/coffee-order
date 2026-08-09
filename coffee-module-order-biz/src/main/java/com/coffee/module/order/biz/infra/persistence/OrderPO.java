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
    /** 详细订单号：YYMMDD-商家6位-类目3位-店铺当日顺序3位（唯一） */
    private String orderNo;
    private Long userId;
    private String guestId;
    /** 下单店铺 id（用户端选店后绑定） */
    private Long storeId;
    /** 取餐方式（PICKUP 到店自取 / DINE_IN 店内用餐） */
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
    private String voucherNo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedReadyTime;
}
