package com.arencloud.balance.dto;

import jakarta.validation.constraints.NotBlank;

public record DecisionRequest(@NotBlank String note) {
}
