package de.j4velin.huenotifier;

import android.content.Context;
import android.util.TypedValue;

abstract class Util {

    private Util() {
    }

    static int dpToPx(final Context c, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics());
    }

    static int[] getColors(final String pattern) {
        return toIntArray(pattern.split(Database.PATTERN_DELIMITER)[1]);
    }

    static int[] getLights(final String pattern) {
        return toIntArray(pattern.split(Database.PATTERN_DELIMITER)[0]);
    }

    static int[] toIntArray(final String csv) {
        String[] values = csv.split(",");
        int[] re = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            re[i] = Integer.parseInt(values[i]);
        }
        return re;
    }

    static int getLightIcon(final String model) {
        switch (model) {
            case "LLC006":
            case "LLC010":
            case "LLC001":
                return R.drawable.ic_iris;
            case "LLC005":
            case "LLC011":
            case "LLC012":
            case "LLC007":
                return R.drawable.ic_bloom;
            case "LLC014":
                return R.drawable.ic_aura;
            case "LLC013":
                return R.drawable.ic_storylight;
            case "LLC020":
                return R.drawable.ic_go;
            case "LST001":
            case "LST002":
            default:
                return R.drawable.ic_light;
        }
    }
}
