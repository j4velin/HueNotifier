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
