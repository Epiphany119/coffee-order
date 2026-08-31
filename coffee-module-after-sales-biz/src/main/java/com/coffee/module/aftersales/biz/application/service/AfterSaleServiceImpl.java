package com.coffee.module.aftersales.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.aftersales.api.AfterSaleService;
import com.coffee.module.aftersales.api.dto.AfterSaleRequest;
import com.coffee.module.aftersales.api.dto.AfterSaleResponse;
import com.coffee.module.aftersales.api.dto.AfterSaleProcessRequest;
import com.coffee.module.aftersales.api.dto.FeedbackRequest;
import com.coffee.module.aftersales.api.dto.FeedbackResponse;
import com.coffee.module.aftersales.biz.domain.AfterSale;
import com.coffee.module.aftersales.biz.domain.Feedback;
import com.coffee.module.aftersales.biz.domain.repository.AfterSaleRepository;
import com.coffee.module.order.api.OrderQueryService;
import com.coffee.module.order.api.dto.OrderBrief;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 售后服务实现
 */
@Service
public class AfterSaleServiceImpl implements AfterSaleService {

    /** 售后类型白名单 */
    private static final Set<String> TYPES = Set.of("REFUND", "REMAKE", "EXCHANGE", "OTHER");

    private final AfterSaleRepository afterSaleRepository;
    private final OrderQueryService orderQueryService;

    public AfterSaleServiceImpl(AfterSaleRepository afterSaleRepository, OrderQueryService orderQueryService) {
        this.afterSaleRepository = afterSaleRepository;
        this.orderQueryService = orderQueryService;
    }

    @Override
    @Transactional
    public AfterSaleResponse createAfterSale(AfterSaleRequest request) {
        if (request == null) throw new ServiceException(400, "请求不能为空");
        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new ServiceException(400, "请先登录后再申请售后");
        }
        if (request.getOrderId() == null) {
            throw new ServiceException(400, "缺少订单信息");
        }
        if (request.getType() == null || !TYPES.contains(request.getType())) {
            throw new ServiceException(400, "请选择售后类型");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < 4) {
            throw new ServiceException(400, "请描述问题（至少 4 个字）");
        }

        OrderBrief order = orderQueryService.getOrderBriefForUser(request.getOrderId(), request.getUserId());
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        if (!isFinalOrderStatus(order.getStatus())) {
            throw new ServiceException(400, "仅已完成或骑手已送达订单可以申请售后");
        }
        if (afterSaleRepository.findByUserIdAndOrderId(request.getUserId(), request.getOrderId()) != null) {
            throw new ServiceException(400, "该订单已提交过售后申请，请耐心等待处理");
        }

        AfterSale afterSale = new AfterSale();
        afterSale.setOrderId(order.getId());
        afterSale.setUserId(request.getUserId());
        afterSale.setType(request.getType());
        afterSale.setReason(reason);
        afterSale.setStatus("PENDING");
        return toResponse(afterSaleRepository.save(afterSale), order);
    }

    @Override
    public List<AfterSaleResponse> listAfterSales(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return afterSaleRepository.findByUserId(userId).stream()
                .map(a -> toResponse(a, null))
                .toList();
    }

    @Override
    public List<AfterSaleResponse> listAfterSalesByStore(Long storeId, String status) {
        if (storeId == null) return List.of();
        return afterSaleRepository.findByStoreId(storeId, status).stream().map(a -> toResponse(a, null)).toList();
    }

    @Override
    @Transactional
    public AfterSaleResponse processAfterSale(Long afterSaleId, Long storeId, AfterSaleProcessRequest request) {
        if (afterSaleId == null || storeId == null) throw new ServiceException(400, "缺少售后单或店铺信息");
        AfterSale afterSale = afterSaleRepository.findById(afterSaleId);
        if (afterSale == null) throw new ServiceException(404, "售后单不存在");
        OrderBrief order = orderQueryService.getOrderBrief(afterSale.getOrderId());
        if (order == null || !storeId.equals(order.getStoreId())) throw new ServiceException(403, "无权处理该售后单");
        String next = request == null || request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        String note = request == null || request.getHandlerNote() == null ? "" : request.getHandlerNote().trim();
        if (!Set.of("PROCESSING", "RESOLVED", "REJECTED", "CLOSED").contains(next)) {
            throw new ServiceException(400, "不支持的售后状态");
        }
        if (Set.of("RESOLVED", "REJECTED", "CLOSED").contains(next) && note.length() < 2) {
            throw new ServiceException(400, "请填写至少 2 个字的处理说明");
        }
        if (Set.of("RESOLVED", "REJECTED", "CLOSED").contains(afterSale.getStatus())) {
            throw new ServiceException(400, "已结束的售后单不能重复处理");
        }
        afterSale.setStatus(next);
        afterSale.setHandlerNote(note.isEmpty() ? afterSale.getHandlerNote() : note);
        return toResponse(afterSaleRepository.save(afterSale), order);
    }

    @Override
    @Transactional
    public FeedbackResponse createFeedback(FeedbackRequest request) {
        if (request == null) throw new ServiceException(400, "请求不能为空");
        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new ServiceException(400, "请先登录后再提交反馈");
        }
        if (request.getOrderId() == null) {
            throw new ServiceException(400, "缺少订单信息");
        }
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.length() < 2) {
            throw new ServiceException(400, "建议内容不能为空");
        }
        if (request.getRating() != null && (request.getRating() < 1 || request.getRating() > 5)) {
            throw new ServiceException(400, "评分范围 1-5");
        }

        OrderBrief order = orderQueryService.getOrderBriefForUser(request.getOrderId(), request.getUserId());
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        if (!isFinalOrderStatus(order.getStatus())) {
            throw new ServiceException(400, "仅已完成或骑手已送达订单可以提交反馈");
        }

        Feedback feedback = new Feedback();
        feedback.setOrderId(order.getId());
        // 反馈归属规则：记录挂在订单第一个品下（服务端自动取，不依赖前端传参）
        feedback.setProductId(order.getFirstProductId());
        feedback.setUserId(request.getUserId());
        feedback.setContent(content);
        feedback.setRating(request.getRating());
        return toFeedbackResponse(afterSaleRepository.saveFeedback(feedback), order);
    }

    @Override
    public List<FeedbackResponse> listFeedbacks(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return afterSaleRepository.findFeedbackByUserId(userId).stream()
                .map(f -> toFeedbackResponse(f, null))
                .toList();
    }

    @Override
    public List<FeedbackResponse> listFeedbacksByOrder(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        return afterSaleRepository.findFeedbackVOByOrderId(orderId).stream()
                .map(vo -> toFeedbackResponseFromVO(vo, true))
                .toList();
    }

    @Override
    public List<FeedbackResponse> listFeedbacksByProduct(Long productId) {
        if (productId == null) {
            return List.of();
        }
        return afterSaleRepository.findFeedbackVOByProductId(productId).stream()
                .map(vo -> toFeedbackResponseFromVO(vo, false))
                .toList();
    }

    private boolean isFinalOrderStatus(String status) {
        return "COMPLETED".equals(status) || "DELIVERED".equals(status);
    }

    private AfterSaleResponse toResponse(AfterSale a, OrderBrief order) {
        AfterSaleResponse r = new AfterSaleResponse();
        r.setId(a.getId());
        r.setOrderId(a.getOrderId());
        r.setType(a.getType());
        r.setReason(a.getReason());
        r.setStatus(a.getStatus());
        r.setHandlerNote(a.getHandlerNote());
        r.setCreatedAt(a.getCreatedAt());
        r.setUpdatedAt(a.getUpdatedAt());
        if (order != null) {
            r.setOrderNo(order.getOrderNo());
            r.setOrderName(order.getBeverageName());
        }
        return r;
    }

    private FeedbackResponse toFeedbackResponse(Feedback f, OrderBrief order) {
        FeedbackResponse r = new FeedbackResponse();
        r.setId(f.getId());
        r.setOrderId(f.getOrderId());
        r.setProductId(f.getProductId());
        r.setContent(f.getContent());
        r.setRating(f.getRating());
        r.setCreatedAt(f.getCreatedAt());
        if (order != null) {
            r.setOrderNo(order.getOrderNo());
            r.setOrderName(order.getBeverageName());
        }
        return r;
    }

    private FeedbackResponse toFeedbackResponseFromVO(
            com.coffee.module.aftersales.biz.infra.persistence.FeedbackVO vo, boolean includeUsername) {
        FeedbackResponse r = new FeedbackResponse();
        r.setId(vo.getId());
        r.setOrderId(vo.getOrderId());
        r.setProductId(vo.getProductId());
        r.setOrderNo(vo.getOrderNo());
        r.setOrderName(vo.getBeverageName());
        r.setUsername(includeUsername ? vo.getUsername() : null);
        r.setContent(vo.getContent());
        r.setRating(vo.getRating());
        r.setCreatedAt(vo.getCreatedAt());
        return r;
    }
}
