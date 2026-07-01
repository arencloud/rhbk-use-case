package com.arencloud.balance.dto;

import jakarta.validation.constraints.NotBlank;

public record BalanceCheckRequest(@NotBlank String reason) {
}
