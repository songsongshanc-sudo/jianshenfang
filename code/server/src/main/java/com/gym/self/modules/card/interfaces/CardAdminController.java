package com.gym.self.modules.card.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.card.application.CardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cards")
public class CardAdminController {

    private final CardService cardService;

    public CardAdminController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public ApiResponse<List<CardService.AdminCard>> list(@RequestParam(required = false) String storeId) {
        return ApiResponse.ok(cardService.adminList(CurrentAdmin.get(), parseOptional(storeId)));
    }

    @PostMapping
    public ApiResponse<IdView> create(@Valid @RequestBody CardRequest request) {
        String id = cardService.create(CurrentAdmin.get(), request.toInput(parseRequired(request.storeId())));
        return ApiResponse.ok(new IdView(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable String id, @Valid @RequestBody CardRequest request) {
        cardService.update(CurrentAdmin.get(), parseRequired(id), request.toInput(parseRequired(request.storeId())));
        return ApiResponse.ok(null);
    }

    private static Long parseOptional(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return parseRequired(id);
    }

    private static long parseRequired(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("参数不正确");
        }
    }

    public record IdView(String id) {
    }

    public record CardRequest(
            @NotBlank String storeId,
            @NotBlank String name,
            @NotNull Long priceFen,
            String displayText,
            @NotNull Integer validDays,
            @NotBlank String durationMode,
            Integer stockTotal,
            @NotBlank String unlockType,
            Integer unlockDays,
            @NotNull Integer crossStore,
            @NotNull Integer homeVisible,
            @NotNull Integer sortNo,
            @NotBlank String status) {

        CardService.CardInput toInput(long storeId) {
            return new CardService.CardInput(storeId, name, priceFen, displayText, validDays, durationMode, stockTotal,
                    unlockType, unlockDays, crossStore, homeVisible, sortNo, status);
        }
    }
}
