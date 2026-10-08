package com.gym.self.modules.geo;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/geo")
public class GeoAdminController {

    private final GeoService geoService;

    public GeoAdminController(GeoService geoService) {
        this.geoService = geoService;
    }

    @GetMapping("/reverse")
    public ApiResponse<GeoService.Place> reverse(@RequestParam BigDecimal latitude, @RequestParam BigDecimal longitude) {
        return ApiResponse.ok(geoService.reverse(CurrentAdmin.get(), latitude, longitude));
    }

    @GetMapping("/search")
    public ApiResponse<List<GeoService.Place>> search(@RequestParam String keyword) {
        return ApiResponse.ok(geoService.search(CurrentAdmin.get(), keyword));
    }
}
