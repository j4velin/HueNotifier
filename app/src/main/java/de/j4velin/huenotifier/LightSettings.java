package de.j4velin.huenotifier;

import android.widget.CheckBox;

import java.util.List;

/**
 * Container class to store light ids and their corresponding alert colors
 */
class LightSettings {
    final int[] lights, colors;

    LightSettings(int[] lights, int[] colors) {
        this.lights = lights;
        this.colors = colors;
    }

    LightSettings(String lights, String colors) {
        this(Util.toIntArray(lights), Util.toIntArray(colors));
    }

    LightSettings() {
        lights = new int[0];
        colors = new int[0];
    }

    LightSettings(List<CheckBox> checkBoxes) {
        String lights = null;
        String colors = null;
        try {
            for (CheckBox cb : checkBoxes) {
                if (cb.isChecked()) {
                    if (lights == null) {
                        lights = String
                                .valueOf(((int[]) cb.getTag())[0]);
                        colors = String
                                .valueOf(((int[]) cb.getTag())[1]);
                    } else {
                        lights += "," + ((int[]) cb.getTag())[0];
                        colors += "," + ((int[]) cb.getTag())[1];
                    }
                }
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Logger.log(
                        "Can not construct LightSettings: light=" + lights + ", colors=" + colors);
                Logger.log(e);
            }
        }
        this.lights = Util.toIntArray(lights);
        this.colors = Util.toIntArray(colors);
    }
}
