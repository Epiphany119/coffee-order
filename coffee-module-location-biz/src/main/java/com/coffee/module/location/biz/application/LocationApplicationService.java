package com.coffee.module.location.biz.application;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.location.api.LocationService;
import com.coffee.module.location.api.dto.*;
import com.coffee.module.location.biz.infra.*;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreResponse;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LocationApplicationService implements LocationService {
    private final LocationMapper mapper; private final StoreService storeService;
    private static final Map<String,double[]> COORDS = Map.ofEntries(
      Map.entry("jingan",new double[]{31.2297,121.4550}),Map.entry("hengshan",new double[]{31.1998,121.4450}),Map.entry("xintiandi",new double[]{31.2156,121.4747}),Map.entry("yuyuanlu",new double[]{31.2185,121.4308}),Map.entry("lujiazui",new double[]{31.2397,121.4998}),Map.entry("wukanglu",new double[]{31.2052,121.4370}),Map.entry("xian",new double[]{31.1738,121.4590}),Map.entry("nanjingxilu",new double[]{31.2297,121.4550}),Map.entry("daxuelu",new double[]{31.1933,121.5395}),Map.entry("beiwaitan",new double[]{31.2535,121.5075}),Map.entry("yongfulu",new double[]{31.2045,121.4390}),Map.entry("huaihai",new double[]{31.2198,121.4612}),Map.entry("linjian",new double[]{31.2300,121.4700}),Map.entry("muguang",new double[]{31.2200,121.4800}),Map.entry("xingyan",new double[]{31.2400,121.4800}),Map.entry("songyu",new double[]{31.2100,121.4500}),Map.entry("qianxu",new double[]{31.2000,121.4700}),Map.entry("heshi",new double[]{31.2500,121.4700}),Map.entry("chengfeng",new double[]{31.1800,121.4700}),Map.entry("yuezhan",new double[]{31.2200,121.5200}),Map.entry("boshe",new double[]{31.2000,121.5200}));
    public LocationApplicationService(LocationMapper mapper, StoreService storeService){this.mapper=mapper;this.storeService=storeService;}
    public void saveUserLocation(LocationRequest r){validate(r,true); if(r.getUserId()==null) throw new ServiceException(400,"登录用户 id 不能为空"); LocationPO p=mapper.findByUserId(r.getUserId()); if(p==null){p=new LocationPO();p.setUserId(r.getUserId());} p.setLatitude(r.getLatitude());p.setLongitude(r.getLongitude());p.setUpdatedAt(LocalDateTime.now()); if(p.getId()==null)mapper.insert(p);else mapper.updateById(p);}
    public List<StoreRecommendation> recommend(LocationRequest r,int limit){validate(r,false); int n=Math.max(1,Math.min(limit,21)); List<StoreRecommendation> out=new ArrayList<>(); for(StoreResponse s:storeService.listOpenStores()){double[] c=COORDS.get(s.getCode());if(c!=null)out.add(new StoreRecommendation(s.getStoreId(),s.getCode(),s.getName(),s.getAddress(),c[0],c[1],round(distance(r.getLatitude(),r.getLongitude(),c[0],c[1]))));} out.sort(Comparator.comparing(StoreRecommendation::getDistanceKm));return out.stream().limit(n).toList();}
    public List<StoreRecommendation> recommendForUser(Long userId,int limit){if(userId==null)throw new ServiceException(400,"用户 id 不能为空"); LocationPO p=mapper.findByUserId(userId);if(p==null)throw new ServiceException(400,"暂无用户定位，请先上报位置");LocationRequest r=new LocationRequest();r.setLatitude(p.getLatitude());r.setLongitude(p.getLongitude());return recommend(r,limit);}
    private void validate(LocationRequest r,boolean requiredUser){if(r==null||r.getLatitude()==null||r.getLongitude()==null)throw new ServiceException(400,"经纬度不能为空"); if(requiredUser&&r.getUserId()==null)throw new ServiceException(400,"登录用户 id 不能为空"); if(r.getLatitude()<-4||r.getLatitude()>54||r.getLongitude()<73||r.getLongitude()>136)throw new ServiceException(400,"仅支持中国大陆范围内的定位");}
    private double distance(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);return 6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    private double round(double x){return Math.round(x*100.0)/100.0;}
}
