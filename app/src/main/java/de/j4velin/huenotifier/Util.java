/*
 * Copyright 2017 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.j4velin.huenotifier;

import android.content.Context;
import android.util.TypedValue;

import java.util.Arrays;

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

    static String toString(int[] array) {
        String s = Arrays.toString(array);
        s = s.substring(1, s.length() - 1);
        return s.replace(" ", "");
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
