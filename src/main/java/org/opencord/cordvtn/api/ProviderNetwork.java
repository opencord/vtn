/*
 * Copyright 2016-present Open Networking Laboratory
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
package org.opencord.cordvtn.api;

import org.opencord.cordvtn.api.Dependency.Type;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of a provider network.
 */
public final class ProviderNetwork {

    private final NetworkId id;
    private final Type type;

    private ProviderNetwork(NetworkId id, Type type) {
        this.id = id;
        this.type = type;
    }

    /**
     * Returns network id.
     *
     * @return network id
     */
    public NetworkId id() {
        return id;
    }

    /**
     * Returns the direct access type with this provider network.
     *
     * @return direct access type
     */
    public Type type() {
        return type;
    }

    public static ProviderNetwork of(NetworkId id, Type type) {
        checkNotNull(id);
        return new ProviderNetwork(id, type);
    }
}
