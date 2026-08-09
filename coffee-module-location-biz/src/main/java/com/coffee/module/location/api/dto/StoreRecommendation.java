package com.coffee.module.location.api.dto;

public class StoreRecommendation {
    private Long storeId; private String code; private String name; private String address;
    private Double latitude; private Double longitude; private Double distanceKm;
    public StoreRecommendation(Long storeId, String code, String name, String address, Double latitude, Double longitude, Double distanceKm) {
        this.storeId=storeId; this.code=code; this.name=name; this.address=address; this.latitude=latitude; this.longitude=longitude; this.distanceKm=distanceKm;
    }
    public Long getStoreId(){return storeId;} public String getCode(){return code;} public String getName(){return name;}
    public String getAddress(){return address;} public Double getLatitude(){return latitude;} public Double getLongitude(){return longitude;} public Double getDistanceKm(){return distanceKm;}
}
