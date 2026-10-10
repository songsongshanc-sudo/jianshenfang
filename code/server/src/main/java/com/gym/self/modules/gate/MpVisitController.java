package com.gym.self.modules.gate;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.modules.user.auth.CurrentMp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mp")
public class MpVisitController {

    private final GateService gateService;

    public MpVisitController(GateService gateService) {
        this.gateService = gateService;
    }

    @GetMapping("/visits")
    public ApiResponse<List<GateService.VisitView>> visits() {
        return ApiResponse.ok(gateService.myVisits(CurrentMp.formal().userId()));
    }
}
