package com.gym.self.modules.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gcj02Test {

    @Test
    void beijingPointMatchesKnownOffset() {
        double[] gcj = Gcj02.wgs84ToGcj02(39.915, 116.404);
        assertEquals(39.91640428150164, gcj[0], 1e-6);
        assertEquals(116.41024449916938, gcj[1], 1e-6);
    }

    @Test
    void outsideChinaStaysTheSame() {
        double[] gcj = Gcj02.wgs84ToGcj02(40.7484, -73.9857);
        assertEquals(40.7484, gcj[0], 0);
        assertEquals(-73.9857, gcj[1], 0);
    }

    @Test
    void roundTripStaysWithinAMeter() {
        double[] gcj = Gcj02.wgs84ToGcj02(22.5431, 114.0579);
        double[] back = Gcj02.gcj02ToWgs84(gcj[0], gcj[1]);
        assertTrue(Math.abs(back[0] - 22.5431) < 0.00001);
        assertTrue(Math.abs(back[1] - 114.0579) < 0.00001);
    }
}
