package com.coffee.module.payment.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付单 Mapper
 */
@Mapper
public interface PaymentMapper extends BaseMapper<PaymentPO> {

    @Select("SELECT * FROM payment WHERE payment_no = #{paymentNo}")
    PaymentPO selectByPaymentNo(@Param("paymentNo") String paymentNo);

    @Select("SELECT * FROM payment WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1")
    PaymentPO selectByOrderId(@Param("orderId") Long orderId);
}
