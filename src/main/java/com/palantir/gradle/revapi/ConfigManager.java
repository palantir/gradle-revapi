/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.gradle.revapi.config.GradleRevapiConfig;
import java.io.File;
import java.io.IOException;
import java.util.function.UnaryOperator;

final class ConfigManager {
    private static final ObjectMapper OBJECT_MAPPER = GradleRevapiConfig.newRecommendedObjectMapper();

    private final Object configFileLock = new Object();
    private final File configFile;

    ConfigManager(File configFile) {
        this.configFile = configFile;
    }

    public void modifyConfigFile(UnaryOperator<GradleRevapiConfig> transformer) {
        synchronized (configFileLock) {
            GradleRevapiConfig oldGradleRevapiConfig = fromFileOrEmptyIfDoesNotExist();
            GradleRevapiConfig newGradleRevapiConfig = transformer.apply(oldGradleRevapiConfig);

            configFile.getParentFile().mkdirs();

            try {
                OBJECT_MAPPER.writeValue(configFile, newGradleRevapiConfig);
            } catch (IOException e) {
                throw new RuntimeException("Failed to modify revapi config file: " + configFile, e);
            }
        }
    }

    public GradleRevapiConfig fromFileOrEmptyIfDoesNotExist() {
        if (!configFile.exists()) {
            return GradleRevapiConfig.empty();
        }

        try {
            synchronized (configFileLock) {
                return OBJECT_MAPPER.readValue(configFile, GradleRevapiConfig.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read revapi config file: " + configFile, e);
        }
    }
}
