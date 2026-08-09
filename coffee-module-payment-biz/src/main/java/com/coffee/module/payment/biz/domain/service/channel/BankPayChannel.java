package com.coffee.module.payment.biz.domain.service.channel;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.payment.biz.domain.Payment;
import com.coffee.module.payment.biz.domain.service.PaymentChannel;
import org.springframework.stereotype.Component;

/**
 * 银行支付渠道（骨架占位，待接入）
 *
 * TODO 接入步骤：1) 与银行/聚合支付服务商签约获取商户号/证书；2) pay 内实现下单跳转（返回收银台
 * URL 并落 channelResponse）；3) handleCallback 内验签 + 校验金额/支付单号后返回银行流水号。
 */
@Component
public class BankPayChannel implements PaymentChannel {

    @Override
    public String channelCode() {
        return "BANK";
    }

    @Override
    public String pay(Payment payment) {
        throw new ServiceException(501, "银行支付渠道尚未接入，请先使用模拟支付（MOCK）");
    }

    @Override
    public String handleCallback(Payment payment, String transactionNo) {
        throw new ServiceException(501, "银行支付回调尚未接入");
    }
}
