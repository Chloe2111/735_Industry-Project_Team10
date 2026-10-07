package com.vcnity.backend.intake;

/** The three tiers and their plain-language names (same wording as the consent form). */
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

    public static String label(Integer tier) {
        if (tier == null) return "Not yet classified";
        return switch (tier) {
            case GENERAL -> "General feedback";
            case SENSITIVE -> "Personal or sensitive";
            case RESTRICTED -> "Culturally restricted";
            default -> "Unknown tier";
        };
    }
}
