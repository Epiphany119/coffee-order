package com.coffee.module.merchantagent.biz.application;

import java.util.*;

/** 把店长问题转成数据关注面；不会把自然语言直接当成可执行指令。 */
final class MerchantGrowthIntentParser {
    Intent parse(String raw) {
        String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        LinkedHashSet<Focus> focuses = new LinkedHashSet<>();
        if (has(text, "库存", "缺货", "秒杀", "商品", "品类")) focuses.add(Focus.INVENTORY);
        if (has(text, "排队", "堆积", "制作", "履约", "出品", "等候")) focuses.add(Focus.FULFILLMENT);
        if (has(text, "复购", "老客", "会员", "流失", "召回")) focuses.add(Focus.RETENTION);
        if (has(text, "券", "优惠", "活动", "拉新", "促销", "增长")) focuses.add(Focus.PROMOTION);
        if (has(text, "营业额", "收入", "营收", "客单", "金额")) focuses.add(Focus.REVENUE);
        if (has(text, "订单", "单量", "客流")) focuses.add(Focus.ORDERS);
        if (focuses.isEmpty()) focuses.add(Focus.OVERVIEW);
        return new Intent(List.copyOf(focuses), summary(focuses));
    }
    private boolean has(String text, String... words) { return Arrays.stream(words).anyMatch(text::contains); }
    private String summary(Set<Focus> focuses) { return "关注：" + focuses.stream().map(Focus::label).reduce((a,b) -> a + "、" + b).orElse("经营概览"); }
    enum Focus { INVENTORY("商品与库存"), FULFILLMENT("履约与出品"), RETENTION("会员复购"), PROMOTION("营销活动"), REVENUE("营业额"), ORDERS("订单量"), OVERVIEW("经营概览"); private final String label; Focus(String label){this.label=label;} String label(){return label;} }
    record Intent(List<Focus> focuses, String summary) { boolean has(Focus focus){ return focuses.contains(focus); } }
}
