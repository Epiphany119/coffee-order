package com.coffee.module.aftersales.api;

import com.coffee.module.aftersales.api.dto.AfterSaleRequest;
import com.coffee.module.aftersales.api.dto.AfterSaleResponse;
import com.coffee.module.aftersales.api.dto.AfterSaleProcessRequest;
import com.coffee.module.aftersales.api.dto.FeedbackRequest;
import com.coffee.module.aftersales.api.dto.FeedbackResponse;

import java.util.List;

/**
 * 售后服务接口（售后单 / 订单反馈）
 */
public interface AfterSaleService {

    /** 创建售后单（仅已完成订单可售后，同订单防重复提交） */
    AfterSaleResponse createAfterSale(AfterSaleRequest request);

    /** 按用户查售后单列表 */
    List<AfterSaleResponse> listAfterSales(Long userId);

    /** 商家按店铺查询售后工作台。 */
    List<AfterSaleResponse> listAfterSalesByStore(Long storeId, String status);

    /** 商家更新处理状态与处理说明。 */
    AfterSaleResponse processAfterSale(Long afterSaleId, Long storeId, AfterSaleProcessRequest request);

    /** 提交订单反馈（仅已完成订单可反馈） */
    FeedbackResponse createFeedback(FeedbackRequest request);

    /** 按用户查反馈列表 */
    List<FeedbackResponse> listFeedbacks(Long userId);

    /** 按订单查反馈列表（商家端订单明细展示用） */
    List<FeedbackResponse> listFeedbacksByOrder(Long orderId);

    /** 按商品 id 查反馈列表（点餐界面商品下方展示用） */
    List<FeedbackResponse> listFeedbacksByProduct(Long productId);
}
