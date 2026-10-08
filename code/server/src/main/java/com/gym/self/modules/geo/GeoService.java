package com.gym.self.modules.geo;

import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeoService {

    private final PlaceLookup placeLookup;

    public GeoService(PlaceLookup placeLookup) {
        this.placeLookup = placeLookup;
    }

    public Place reverse(AdminPrincipal actor, BigDecimal latitude, BigDecimal longitude) {
        StoreScope.requireMaster(actor);
        validate(latitude, longitude);
        PlaceLookup.LookupHit hit = lookupReverse(latitude.doubleValue(), longitude.doubleValue());
        return new Place(hit.province(), hit.city(), hit.address(), scale(longitude), scale(latitude));
    }

    public List<Place> search(AdminPrincipal actor, String keyword) {
        StoreScope.requireMaster(actor);
        if (keyword == null || keyword.isBlank()) {
            throw BizException.badRequest("请输入地址");
        }
        List<Place> places = new ArrayList<>();
        for (PlaceLookup.LookupHit hit : placeLookup.search(keyword.trim())) {
            double latitude = hit.latitude();
            double longitude = hit.longitude();
            if (placeLookup.coordinatesAreWgs84()) {
                double[] gcj = Gcj02.wgs84ToGcj02(latitude, longitude);
                latitude = gcj[0];
                longitude = gcj[1];
            }
            places.add(new Place(hit.province(), hit.city(), hit.address(), scale(longitude), scale(latitude)));
        }
        if (places.isEmpty()) {
            throw BizException.badRequest("没有找到这个地址");
        }
        return places;
    }

    private PlaceLookup.LookupHit lookupReverse(double latitude, double longitude) {
        if (!placeLookup.coordinatesAreWgs84()) {
            return placeLookup.reverse(latitude, longitude);
        }
        double[] wgs = Gcj02.gcj02ToWgs84(latitude, longitude);
        return placeLookup.reverse(wgs[0], wgs[1]);
    }

    private static void validate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null
                || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw BizException.badRequest("经纬度不正确");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    public record Place(String province, String city, String address, BigDecimal longitude, BigDecimal latitude) {
    }
}
