package com.coffee.module.delivery.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("delivery_address")
public class DeliveryAddressPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String label;
    private String receiverName;
    private String receiverPhone;
    private String detailAddress;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
