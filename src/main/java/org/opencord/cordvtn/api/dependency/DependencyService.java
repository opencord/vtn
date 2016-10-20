/*
 * Copyright 2015-present Open Networking Laboratory
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
package org.opencord.cordvtn.api.dependency;

import org.opencord.cordvtn.api.dependency.Dependency.Type;
import org.opencord.cordvtn.api.net.NetworkId;

/**
 * Provides dependency services.
 */
public interface DependencyService {

    /**
     * Creates dependencies for a given tenant service.
     *
     * @param subscriber subscriber network id
     * @param provider   provider network id
     * @param type       bidirectional access type
     */
    void createDependency(NetworkId subscriber, NetworkId provider, Type type);

    /**
     * Removes all dependencies from a given tenant service.
     *
     * @param subscriber subscriber network id
     * @param provider   provider network id
     */
    void removeDependency(NetworkId subscriber, NetworkId provider);
}
