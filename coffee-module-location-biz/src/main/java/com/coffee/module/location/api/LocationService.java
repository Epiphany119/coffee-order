package com.coffee.module.location.api;

import com.coffee.module.location.api.dto.*;
import java.util.List;

public interface LocationService {
    void saveUserLocation(LocationRequest request);
    List<StoreRecommendation> recommend(LocationRequest request, int limit);
    List<StoreRecommendation> recommendForUser(Long userId, int limit);
}
