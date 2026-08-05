package com.coffee.order.domain.order.valueobject;

/**
 * 订单标识值对象 - 区分用户订单和游客订单
 */
public record OrderIdentity(
    Long userId,
    String guestId,
    OrderOwnerType ownerType
) {
    public enum OrderOwnerType {
        REGISTERED_USER,
        GUEST
    }

    public static OrderIdentity forUser(Long userId) {
        return new OrderIdentity(userId, null, OrderOwnerType.REGISTERED_USER);
    }

    public static OrderIdentity forGuest(String guestId) {
        return new OrderIdentity(null, guestId, OrderOwnerType.GUEST);
    }

    public boolean isUserOrder() {
        return ownerType == OrderOwnerType.REGISTERED_USER;
    }
}
