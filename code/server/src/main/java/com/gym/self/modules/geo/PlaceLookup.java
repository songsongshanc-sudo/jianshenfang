package com.gym.self.modules.geo;

import java.util.List;

public interface PlaceLookup {

    LookupHit reverse(double latitude, double longitude);

    List<LookupHit> search(String keyword);

    boolean coordinatesAreWgs84();

    record LookupHit(String province, String city, String address, double latitude, double longitude) {
    }
}
