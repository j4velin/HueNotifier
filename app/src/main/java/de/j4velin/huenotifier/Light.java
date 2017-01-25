package de.j4velin.huenotifier;

public class Light {
    LightState state;
    String name;
    String modelid;

    @Override
    public String toString() {
        return "name=" + name + ", state=" + state + ", modelid=" + modelid;
    }

    public static class LightState {
        boolean on;
        float[] xy;

        @Override
        public String toString() {
            return "on=" + on + ", x=" + xy[0] + ", y=" + xy[1];
        }
    }
}
