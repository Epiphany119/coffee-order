package com.coffee.module.payment.biz.domain.service.channel;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.payment.biz.domain.Payment;
import com.coffee.module.payment.biz.domain.service.PaymentChannel;
import org.springframework.stereotype.Component;

/**
 * 微信支付渠道（骨架占位，待接入）
 *
 * TODO 接入步骤：1) 配置 appid/mchid/apiKey 等参数；2) pay 内实现 Native/JSAPI 预下单，
 * 返回预支付参数并落 channelResponse；3) handleCallback 内验签 + 校验金额/支付单号后返回微信交易号；
 * 4) 回调成功幂等：仅 PENDING 状态回写 PAID。
 */
@Component
public class WechatPayChannel implements PaymentChannel {

    @Override
    public String channelCode() {
        return "WECHAT";
    }

    @Override
    public String pay(Payment payment) {
        throw new ServiceException(501, "微信支付渠道尚未接入，请先使用模拟支付（MOCK）");
    }

    @Override
    public String handleCallback(Payment payment, String transactionNo) {
        throw new ServiceException(501, "微信支付回调尚未接入");
    }
}
