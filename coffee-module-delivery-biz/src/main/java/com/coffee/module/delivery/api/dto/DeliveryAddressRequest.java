package com.coffee.module.delivery.api.dto;

import lombok.Data;

/** 顾客收货地址新增/编辑请求。 */
@Data
public class DeliveryAddressRequest {
    /** 地址标签，如：家、公司、学校。 */
    private String label;
    private String receiverName;
    private String receiverPhone;
    private String detailAddress;
    /** true 时设为默认地址；首次创建地址会自动成为默认地址。 */
    private Boolean isDefault;
}
