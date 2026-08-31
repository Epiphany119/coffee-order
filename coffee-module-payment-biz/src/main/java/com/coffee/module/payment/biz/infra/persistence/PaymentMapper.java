package com.coffee.module.payment.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 支付单 Mapper
 */
@Mapper
public interface PaymentMapper extends BaseMapper<PaymentPO> {

    @Select("SELECT * FROM payment WHERE payment_no = #{paymentNo}")
    PaymentPO selectByPaymentNo(@Param("paymentNo") String paymentNo);

    @Select("SELECT * FROM payment WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1")
    PaymentPO selectByOrderId(@Param("orderId") Long orderId);

    @Update("UPDATE payment SET status = 'PROCESSING', updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int tryStartProcessing(@Param("id") Long id);

    @Update("UPDATE payment SET channel = #{channel}, transaction_no = #{transactionNo}, status = 'PAID', " +
            "paid_at = #{paidAt}, updated_at = #{paidAt} WHERE id = #{id} AND status = 'PROCESSING'")
    int markPaidIfProcessing(@Param("id") Long id, @Param("channel") String channel,
                             @Param("transactionNo") String transactionNo,
                             @Param("paidAt") LocalDateTime paidAt);

    @Update("UPDATE payment SET channel = #{channel}, transaction_no = #{transactionNo}, status = 'PAID', " +
            "paid_at = #{paidAt}, updated_at = #{paidAt} WHERE id = #{id} AND status IN ('PENDING', 'PROCESSING')")
    int markPaidIfPendingOrProcessing(@Param("id") Long id, @Param("channel") String channel,
                                      @Param("transactionNo") String transactionNo,
                                      @Param("paidAt") LocalDateTime paidAt);

    @Update("UPDATE payment SET status = 'PENDING', updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'PROCESSING'")
    int resetProcessing(@Param("id") Long id);

    @Update("UPDATE payment SET status = 'REFUNDED', updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'PAID'")
    int markRefundedIfPaid(@Param("id") Long id);
}
