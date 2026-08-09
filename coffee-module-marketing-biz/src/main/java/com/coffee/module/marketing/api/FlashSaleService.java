package com.coffee.module.marketing.api;

import java.util.List;
import java.util.Map;

/** 秒杀活动契约：库存扣减与用户限购由营销域负责。 */
public interface FlashSaleService {
    List<Map<String, Object>> listCurrent(Long storeId);
    Map<String, Object> claim(Long activityId, Long userId, String guestId);
    /** 查询当前身份的抢购资格；已过期、已核销的记录也保留，便于用户追溯。 */
    List<Map<String, Object>> listClaims(Long userId, String guestId);
    /** 订单结算时核销抢购码并返回秒杀价；调用应包在订单本地事务中。 */
    double consumePrice(String claimNo, String productCode, Long userId, String guestId);
}
