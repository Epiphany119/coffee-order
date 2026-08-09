package com.coffee.module.aftersales.api.dto;

import lombok.Data;

/** 商家处理售后请求。 */
@Data
public class AfterSaleProcessRequest {
    /** PROCESSING / RESOLVED / REJECTED / CLOSED */
    private String status;
    /** 面向用户的处理说明；处理完成或拒绝时必填。 */
    private String handlerNote;
}
