package com.coffee.web.controller;

import com.coffee.module.location.api.LocationService;
import com.coffee.module.location.api.dto.*;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
public class LocationController {
    private final LocationService locationService;
    public LocationController(LocationService locationService){this.locationService=locationService;}

    /** 登录用户上报定位（前端应在获得浏览器/微信定位授权后调用） */
    @PostMapping("/user")
    public Map<String,Object> save(@RequestBody LocationRequest request){
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        AccessGuard.requireUser(request.getUserId());
        locationService.saveUserLocation(request);
        return Map.of("success",true);
    }

    /** 按实时坐标推荐最近的营业门店 */
    @GetMapping("/recommend")
    public List<StoreRecommendation> recommend(@RequestParam Double latitude,@RequestParam Double longitude,@RequestParam(defaultValue="5") int limit,@RequestParam(required=false) Long userId){
        if (userId != null) AccessGuard.requireUser(userId);
        LocationRequest request=new LocationRequest();request.setLatitude(latitude);request.setLongitude(longitude);request.setUserId(userId);return locationService.recommend(request,limit);
    }
    @GetMapping("/user/{userId}/recommend")
    public List<StoreRecommendation> recommendForUser(@PathVariable Long userId,@RequestParam(defaultValue="5") int limit){AccessGuard.requireUser(userId);return locationService.recommendForUser(userId,limit);}
}
