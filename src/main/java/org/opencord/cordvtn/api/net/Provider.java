/*
 * Copyright 2017-present Open Networking Laboratory
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
package org.opencord.cordvtn.api.net;

import com.google.common.base.MoreObjects;
import org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of a provider network.
 */
public final class Provider {

    private final ServiceNetwork provider;
    private final DependencyType type;

    private Provider(ServiceNetwork provider, DependencyType type) {
        this.provider = provider;
        this.type = type;
    }

    /**
     * Returns provider network.
     *
     * @return service network
     */
    public ServiceNetwork provider() {
        return provider;
    }

    /**
     * Returns direct access type between subscriber and provider networks.
     *
     * @return type
     */
    public DependencyType type() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Provider) {
            Provider that = (Provider) obj;
            if (Objects.equals(provider, that.provider) &&
                    Objects.equals(type, that.type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, type);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("provider", provider.id())
                .add("type", type)
                .toString();
    }

    /**
     * Returns new provider network builder instance.
     *
     * @return provider network
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of the provider network entities.
     */
    public static final class Builder {
        private ServiceNetwork provider;
        private DependencyType type;

        private Builder() {
        }

        /**
         * Builds an immutable provider network.
         *
         * @return provider network instance
         */
        public Provider build() {
            checkNotNull(provider);
            checkNotNull(type);

            return new Provider(provider, type);
        }

        /**
         * Returns provider network with the supplied provider.
         *
         * @param provider provider network
         * @return provider network builder
         */
        public Builder provider(ServiceNetwork provider) {
            this.provider = provider;
            return this;
        }

        /**
         * Returns provider network with the supplied type.
         *
         * @param type type
         * @return provider network builder
         */
        public Builder type(DependencyType type) {
            this.type = type;
            return this;
        }
    }
}
