package com.coffee.module.payment.biz.domain.service.channel;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.payment.biz.domain.Payment;
import com.coffee.module.payment.biz.domain.service.PaymentChannel;
import org.springframework.stereotype.Component;

/**
 * 支付宝支付渠道（骨架占位，待接入）
 *
 * TODO 接入步骤：1) 配置 appId/privateKey/alipayPublicKey 等参数；2) pay 内实现电脑网站/手机网站
 * 支付（alipay.trade.page.pay / wap.pay），返回跳转表单并落 channelResponse；3) handleCallback 内
 * 验签 + 校验金额/支付单号后返回支付宝交易号。
 */
@Component
public class AlipayPayChannel implements PaymentChannel {

    @Override
    public String channelCode() {
        return "ALIPAY";
    }

    @Override
    public String pay(Payment payment) {
        throw new ServiceException(501, "支付宝渠道尚未接入，请先使用模拟支付（MOCK）");
    }

    @Override
    public String handleCallback(Payment payment, String transactionNo) {
        throw new ServiceException(501, "支付宝回调尚未接入");
    }
}
