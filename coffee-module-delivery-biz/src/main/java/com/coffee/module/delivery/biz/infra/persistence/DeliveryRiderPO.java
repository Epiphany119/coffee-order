package com.coffee.module.delivery.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("delivery_rider")
public class DeliveryRiderPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String phone;
    private String avatarUrl;
    private LocalDate birthday;
    private String email;
    private String otherInfo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
