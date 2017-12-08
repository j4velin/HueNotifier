package de.j4velin.huenotifier;

import android.graphics.Color;

/**
 * Class describing a rule (when this app posts a notification, blink these lights).
 */
class Rule {
    final LightSettings lightSettings;
    final String appName, appPkg, person;

    Rule(String appName, String appPkg, String person, LightSettings lightSettings) {
        this.lightSettings = lightSettings;
        this.appName = appName;
        this.appPkg = appPkg;
        this.person = person;
    }

    int getColor(int lightId) {
        for (int i = 0; i < lightSettings.lights.length; i++) {
            if (lightSettings.lights[i] == lightId) {
                return lightSettings.colors[i];
            }
        }
        return Color.WHITE;
    }

    boolean contains(int lightId) {
        for (int i = 0; i < lightSettings.lights.length; i++) {
            if (lightSettings.lights[i] == lightId) {
                return true;
            }
        }
        return false;
    }
}
