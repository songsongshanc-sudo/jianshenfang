package com.gym.self.modules.card.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.card.application.CardService;
import com.gym.self.modules.user.auth.CurrentMp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mp/cards")
public class MpCardController {

    private final CardService cardService;

    public MpCardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public ApiResponse<List<CardService.MpCard>> list(@RequestParam String storeId,
                                                      @RequestParam(defaultValue = "ALL") String placement) {
        return ApiResponse.ok(cardService.mpList(parseId(storeId), placement, CurrentMp.optionalFormalUserId()));
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("门店不存在");
        }
    }
}
