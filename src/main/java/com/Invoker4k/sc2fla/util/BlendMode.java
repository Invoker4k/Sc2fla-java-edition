package com.invoker4k.sc2fla.util;

public final class BlendMode {
    private BlendMode() {}

    public static String toXfl(int scBlendMode) {
        switch (scBlendMode) {
            case 0: return null;
            case 1: return "layer";
            case 2: return "multiply";
            case 3: return "screen";
            case 4: return "lighten";
            case 5: return "darken";
            case 6: return "add";
            case 7: return "subtract";
            case 8: return "difference";
            case 9: return "invert";
            case 10: return "alpha";
            case 11: return "erase";
            case 12: return "overlay";
            case 13: return "hardlight";
            default: return null;
        }
    }
}
