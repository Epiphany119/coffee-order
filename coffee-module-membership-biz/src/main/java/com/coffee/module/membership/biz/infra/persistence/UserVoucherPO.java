package com.coffee.module.membership.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户卡券持久化对象（user_voucher 表）
 */
@Data
@TableName("user_voucher")
public class UserVoucherPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String voucherNo;
    private String name;
    private Double discount;
    private Double minimum;
    private Integer status;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
