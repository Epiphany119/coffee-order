package com.coffee.module.store.config;

import com.coffee.module.store.api.dto.StoreStatus;
import com.coffee.module.store.biz.domain.Store;
import com.coffee.module.store.biz.domain.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 店铺数据初始化：首次启动时生成种子店铺
 * <p>Fika 品牌命名：首店 Fika・静安店 + 方案 A 上海街区 11 家 + 方案 B 北欧意象 9 家 = 21 家
 * <p>方案 B（意象店）无实体地址，address 留空待开店后补充
 */
@Component
public class StoreDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StoreDataInitializer.class);

    /** {code, name, address} */
    private static final String[][] SEED_STORES = {
            // 首店
            {"jingan", "Fika・静安店", "静安区南京西路1266号"},
            // 方案 A：上海街区地名系列（与静安店格式统一）
            {"hengshan", "Fika・衡山路店", "徐汇区衡山路880号"},
            {"xintiandi", "Fika・新天地店", "黄浦区马当路245号"},
            {"yuyuanlu", "Fika・愚园路店", "长宁区愚园路1015号"},
            {"lujiazui", "Fika・陆家嘴店", "浦东新区陆家嘴环路958号"},
            {"wukanglu", "Fika・武康路店", "徐汇区武康路376号"},
            {"xian", "Fika・西岸店", "徐汇区龙腾大道2600号"},
            {"nanjingxilu", "Fika・南京西路店", "静安区南京西路1266号"},
            {"daxuelu", "Fika・大学路店", "杨浦区大学路318号"},
            {"beiwaitan", "Fika・北外滩店", "虹口区东大名路501号"},
            {"yongfulu", "Fika・永福路店", "徐汇区永福路131号"},
            {"huaihai", "Fika・淮海店", "黄浦区淮海中路870号"},
            // 方案 B：北欧意象诗意系列（不绑定地理位置，address 待补充）
            {"linjian", "Fika・林间店", ""},
            {"muguang", "Fika・暮光店", ""},
            {"xingyan", "Fika・星檐店", ""},
            {"songyu", "Fika・松屿店", ""},
            {"qianxu", "Fika・浅叙店", ""},
            {"heshi", "Fika・禾时店", ""},
            {"chengfeng", "Fika・澄风店", ""},
            {"yuezhan", "Fika・月栈店", ""},
            {"boshe", "Fika・柏舍店", ""},
    };

    private final StoreRepository storeRepository;

    public StoreDataInitializer(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (storeRepository.count() > 0) {
            return;
        }

        List<Store> stores = new ArrayList<>();
        for (String[] row : SEED_STORES) {
            Store store = new Store();
            store.setCode(row[0]);
            store.setName(row[1]);
            store.setAddress(row[2].isEmpty() ? null : row[2]);
            store.setPhone("021-00000000");
            store.setBusinessHours("08:00-22:00");
            store.setStatus(StoreStatus.OPEN);
            stores.add(store);
        }
        storeRepository.saveAll(stores);

        log.info("[store] 已初始化 {} 家种子店铺：{}", stores.size(),
                stores.stream().map(Store::getName).toList());
    }
}
