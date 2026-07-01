package com.arencloud.balance.dto;

import java.util.Set;

public record UserProfileDto(
        String username,
        String displayName,
        String email,
        Set<String> roles,
        boolean canViewBalances,
        boolean canApprove,
        boolean canAudit,
        boolean canAdminister) {
}
