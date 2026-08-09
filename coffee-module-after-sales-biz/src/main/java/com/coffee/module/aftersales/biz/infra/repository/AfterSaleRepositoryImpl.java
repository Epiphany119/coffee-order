package com.coffee.module.aftersales.biz.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coffee.module.aftersales.biz.domain.AfterSale;
import com.coffee.module.aftersales.biz.domain.Feedback;
import com.coffee.module.aftersales.biz.domain.repository.AfterSaleRepository;
import com.coffee.module.aftersales.biz.infra.persistence.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后仓库实现
 */
@Repository
public class AfterSaleRepositoryImpl implements AfterSaleRepository {

    private final AfterSaleMapper afterSaleMapper;
    private final FeedbackMapper feedbackMapper;

    public AfterSaleRepositoryImpl(AfterSaleMapper afterSaleMapper, FeedbackMapper feedbackMapper) {
        this.afterSaleMapper = afterSaleMapper;
        this.feedbackMapper = feedbackMapper;
    }

    @Override
    public AfterSale save(AfterSale afterSale) {
        AfterSalePO po = new AfterSalePO();
        po.setId(afterSale.getId());
        po.setOrderId(afterSale.getOrderId());
        po.setUserId(afterSale.getUserId());
        po.setType(afterSale.getType());
        po.setReason(afterSale.getReason());
        po.setStatus(afterSale.getStatus());
        po.setHandlerNote(afterSale.getHandlerNote());
        if (afterSale.getId() == null) {
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedAt(now);
            po.setUpdatedAt(now);
            afterSaleMapper.insert(po);
        } else {
            po.setCreatedAt(afterSale.getCreatedAt());
            po.setUpdatedAt(LocalDateTime.now());
            afterSaleMapper.updateById(po);
        }
        afterSale.setId(po.getId());
        afterSale.setCreatedAt(po.getCreatedAt());
        afterSale.setUpdatedAt(po.getUpdatedAt());
        return afterSale;
    }

    @Override
    public Feedback saveFeedback(Feedback feedback) {
        FeedbackPO po = new FeedbackPO();
        po.setId(feedback.getId());
        po.setOrderId(feedback.getOrderId());
        po.setProductId(feedback.getProductId());
        po.setUserId(feedback.getUserId());
        po.setContent(feedback.getContent());
        po.setRating(feedback.getRating());
        po.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(po);
        feedback.setId(po.getId());
        feedback.setCreatedAt(po.getCreatedAt());
        return feedback;
    }

    @Override
    public List<AfterSale> findByUserId(Long userId) {
        return afterSaleMapper.selectByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<AfterSale> findByStoreId(Long storeId, String status) {
        return afterSaleMapper.selectByStoreId(storeId, status).stream().map(this::toDomain).toList();
    }

    @Override
    public AfterSale findById(Long afterSaleId) {
        AfterSalePO po = afterSaleMapper.selectById(afterSaleId);
        return po == null ? null : toDomain(po);
    }

    @Override
    public List<Feedback> findFeedbackByUserId(Long userId) {
        return feedbackMapper.selectByUserId(userId).stream().map(this::toFeedbackDomain).toList();
    }

    @Override
    public List<Feedback> findFeedbackByOrderId(Long orderId) {
        return feedbackMapper.selectByOrderId(orderId).stream().map(this::toFeedbackDomain).toList();
    }

    @Override
    public List<Feedback> findFeedbackByProductId(Long productId) {
        return feedbackMapper.selectByProductId(productId).stream().map(this::toFeedbackDomain).toList();
    }

    @Override
    public List<com.coffee.module.aftersales.biz.infra.persistence.FeedbackVO> findFeedbackVOByOrderId(Long orderId) {
        return feedbackMapper.selectByOrderId(orderId);
    }

    @Override
    public List<com.coffee.module.aftersales.biz.infra.persistence.FeedbackVO> findFeedbackVOByProductId(Long productId) {
        return feedbackMapper.selectByProductId(productId);
    }

    @Override
    public AfterSale findByUserIdAndOrderId(Long userId, Long orderId) {
        AfterSalePO po = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSalePO>()
                .eq(AfterSalePO::getUserId, userId)
                .eq(AfterSalePO::getOrderId, orderId)
                .last("LIMIT 1"));
        return po == null ? null : toDomain(po);
    }

    private AfterSale toDomain(AfterSalePO po) {
        AfterSale d = new AfterSale();
        d.setId(po.getId());
        d.setOrderId(po.getOrderId());
        d.setUserId(po.getUserId());
        d.setType(po.getType());
        d.setReason(po.getReason());
        d.setStatus(po.getStatus());
        d.setHandlerNote(po.getHandlerNote());
        d.setCreatedAt(po.getCreatedAt());
        d.setUpdatedAt(po.getUpdatedAt());
        return d;
    }

    private Feedback toFeedbackDomain(FeedbackPO po) {
        Feedback d = new Feedback();
        d.setId(po.getId());
        d.setOrderId(po.getOrderId());
        d.setProductId(po.getProductId());
        d.setUserId(po.getUserId());
        d.setContent(po.getContent());
        d.setRating(po.getRating());
        d.setCreatedAt(po.getCreatedAt());
        return d;
    }
}
