/*
 * Copyright 2016-present Open Networking Foundation
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
package org.opencord.cordvtn.api.core;

/**
 * Handles service instance detection and removal.
 */
public interface InstanceHandler {

    /**
     * Handles newly detected instance.
     *
     * @param instance instance
     */
    void instanceDetected(Instance instance);

    /**
     * Handles updated instance.
     *
     * @param instance instance
     */
    void instanceUpdated(Instance instance);

    /**
     * Handles removed instance.
     *
     * @param instance instance
     */
    void instanceRemoved(Instance instance);
}
