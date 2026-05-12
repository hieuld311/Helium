package com.hieuld.helium.annotations;

/**
 * Predefined annotation highlight colors.
 *
 * FREE_COLORS: colors available without Pro subscription.
 * PRO_COLORS:  full color palette for Pro users (superset of FREE_COLORS).
 *
 * Values are ARGB integers stored without the alpha byte (alpha is applied at render time).
 */
public class AnnotationColors {

    /** Colors available to all users. */
    public static final int[] FREE_COLORS = {
            0xF7D77C, // yellow
            0xADF06C, // green
            0x44E787, // mint
            0x7CDB77, // light green
            0xF7C67C, // orange-yellow
            0xB6BBFF  // lavender
    };

    /** Extended palette unlocked with Pro. */
    public static final int[] PRO_COLORS = {
            0xF7D77C, // yellow
            0xADF06C, // green
            0x44E787, // mint
            0x7CDB77, // light green
            0x5493FF, // blue
            0x54EEFF, // cyan
            0xF7C67C, // orange-yellow
            0xF28335, // orange
            0xF28F59, // peach
            0xFF9137, // amber
            0xB6BBFF  // lavender
    };
}
