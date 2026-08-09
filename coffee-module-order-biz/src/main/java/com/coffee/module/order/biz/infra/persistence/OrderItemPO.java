package com.coffee.module.order.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订单明细持久化对象（order_item，1 订单 N 明细）
 */
@Data
@TableName("order_item")
public class OrderItemPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属订单 id（FK → user_order.id） */
    private Long orderId;
    /** 商品 id（FK → menu_item.id） */
    private Long productId;
    private String productName;
    /** 数量（购物车行级，如燕麦拿铁 x2 = quantity 2） */
    private Integer quantity;
    /** 单件折后价 */
    private Double unitPrice;
    /** 单件原价（折前，明细行划线展示用） */
    private Double originalUnitPrice;
    /** 行小计（折后，合计 = 订单实付） */
    private Double subtotal;
}
