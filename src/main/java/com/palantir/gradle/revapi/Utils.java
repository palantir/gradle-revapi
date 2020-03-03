/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.revapi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

class Utils {
    private Utils() {}

    public static String resourceToString(Class<?> clazz, String resourcePath) {
        InputStream resourceAsStream = clazz.getClassLoader().getResourceAsStream(resourcePath);

        if (resourceAsStream == null) {
            throw new IllegalArgumentException(String.format(
                    "Could not load resource '%s' for class '%s'", resourcePath, clazz.getCanonicalName()));
        }

        return new BufferedReader(new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
    }
}
