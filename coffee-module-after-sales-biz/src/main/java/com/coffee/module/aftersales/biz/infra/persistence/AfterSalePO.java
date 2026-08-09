package com.coffee.module.aftersales.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后单 PO（after_sale 表）
 */
@Data
@TableName("after_sale")
public class AfterSalePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联订单 id（user_order.id） */
    private Long orderId;

    /** 售后用户（coffee_user.id），游客单售后暂由客服线下处理 */
    private Long userId;

    /** 售后类型：REFUND 退款 / REMAKE 重做 / EXCHANGE 换货 / OTHER 其他 */
    private String type;

    /** 问题说明 */
    private String reason;

    /** 状态：PENDING 待处理 / PROCESSING 处理中 / RESOLVED 已解决 / REJECTED 已拒绝 / CLOSED 已关闭 */
    private String status;

    /** 商家处理备注 */
    private String handlerNote;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
