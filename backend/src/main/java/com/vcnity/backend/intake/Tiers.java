package com.vcnity.backend.intake;

/**
 * The three tier numbers. Their display names live in one place only,
 * the frontend's src/constants/tiers.js, so the backend deals in numbers.
 */
public final class Tiers {

    public static final int GENERAL = 1;
    public static final int SENSITIVE = 2;
    /** Community-controlled material. Must never be processed automatically. */
    public static final int RESTRICTED = 3;

    private Tiers() {
    }

    public static boolean isValid(Integer tier) {
        return tier != null && tier >= GENERAL && tier <= RESTRICTED;
    }
}
