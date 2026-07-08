package com.arencloud.balancesaml.model;

import java.util.Set;

public record UserView(
        String username,
        String displayName,
        String email,
        Set<String> roles,
        boolean canViewBalances,
        boolean canRequestApproval,
        boolean canApprove,
        boolean canAudit,
        boolean canAdminister) {
}
