package com.clucknet.backend.security.role;

public final class AuthorityConstants {

    private AuthorityConstants() {
        // Prevent instantiation
    }

    public static final String OWNER = "ROLE_OWNER";
    public static final String FARMER = "ROLE_FARMER";

    // SpEL authority expression helpers
    public static final String HAS_OWNER = "hasRole('OWNER')";
    public static final String HAS_FARMER_OR_OWNER = "hasAnyRole('OWNER', 'FARMER')";
}
