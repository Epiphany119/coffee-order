package com.coffee.module.location.biz.infra;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("user_location")
public class LocationPO {
    @TableId(type= IdType.AUTO) private Long id; private Long userId; private Double latitude; private Double longitude; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Double getLatitude(){return latitude;} public void setLatitude(Double v){latitude=v;} public Double getLongitude(){return longitude;} public void setLongitude(Double v){longitude=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
