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

public class Light {
    public LightState state;
    String name;
    public String modelid;

    @Override
    public String toString() {
        return "name=" + name + ", state=" + state + ", modelid=" + modelid;
    }

    public static class LightState {
        public boolean on;
        public float[] xy;

        @Override
        public String toString() {
            return "on=" + on + ", x=" + xy[0] + ", y=" + xy[1];
        }
    }
}
