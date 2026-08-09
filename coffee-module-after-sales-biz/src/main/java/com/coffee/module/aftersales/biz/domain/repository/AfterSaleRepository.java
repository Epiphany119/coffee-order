package com.coffee.module.aftersales.biz.domain.repository;

import com.coffee.module.aftersales.biz.domain.AfterSale;
import com.coffee.module.aftersales.biz.domain.Feedback;

import java.util.List;

/**
 * 售后仓库
 */
public interface AfterSaleRepository {

    /** 保存售后单（返回带 id 的售后单） */
    AfterSale save(AfterSale afterSale);

    /** 保存订单反馈 */
    Feedback saveFeedback(Feedback feedback);

    /** 按用户查售后单列表 */
    List<AfterSale> findByUserId(Long userId);

    /** 按店铺查售后单（关联 user_order.store_id）。 */
    List<AfterSale> findByStoreId(Long storeId, String status);

    AfterSale findById(Long afterSaleId);

    /** 按用户查反馈列表 */
    List<Feedback> findFeedbackByUserId(Long userId);

    /** 按订单查反馈列表（商家端订单明细展示用） */
    List<Feedback> findFeedbackByOrderId(Long orderId);

    /** 按商品 id 查反馈列表（点餐界面商品下方展示用） */
    List<Feedback> findFeedbackByProductId(Long productId);

    /** 按订单查反馈（含订单信息 + 用户名，商家端订单明细展示用） */
    List<com.coffee.module.aftersales.biz.infra.persistence.FeedbackVO> findFeedbackVOByOrderId(Long orderId);

    /** 按商品 id 查反馈（含订单信息 + 用户名，点餐界面商品下方展示用） */
    List<com.coffee.module.aftersales.biz.infra.persistence.FeedbackVO> findFeedbackVOByProductId(Long productId);

    /** 按用户+订单查售后单（防止同一订单重复提交） */
    AfterSale findByUserIdAndOrderId(Long userId, Long orderId);
}
