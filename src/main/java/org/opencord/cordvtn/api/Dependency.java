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

import com.google.common.base.MoreObjects;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of a dependency between two networks, subscriber and provider.
 */
public final class Dependency {

    public enum Type {
        BIDIRECTIONAL,
        UNIDIRECTIONAL
    }

    private final VtnNetwork subscriber;
    private final VtnNetwork provider;
    private final Type type;

    private Dependency(VtnNetwork subscriber, VtnNetwork provider, Type type) {
        this.subscriber = subscriber;
        this.provider = provider;
        this.type = type;
    }

    /**
     * Returns subscriber network.
     *
     * @return vtn network
     */
    public VtnNetwork subscriber() {
        return subscriber;
    }

    /**
     * Returns provider network.
     *
     * @return vtn network
     */
    public VtnNetwork provider() {
        return provider;
    }

    /**
     * Returns direct access type between subscriber and provider networks.
     *
     * @return type
     */
    public Type type() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Dependency) {
            Dependency that = (Dependency) obj;
            if (Objects.equals(subscriber, that.subscriber) &&
                    Objects.equals(provider, that.provider) &&
                    Objects.equals(type, that.type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriber, provider, type);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("subscriber", subscriber.id())
                .add("provider", provider.id())
                .add("type", type)
                .toString();
    }

    /**
     * Returns new dependency builder instance.
     *
     * @return dependency
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of the dependency entities.
     */
    public static final class Builder {
        private VtnNetwork subscriber;
        private VtnNetwork provider;
        private Type type;

        private Builder() {
        }

        /**
         * Builds an immutable dependency.
         *
         * @return dependency instance
         */
        public Dependency build() {
            checkNotNull(subscriber);
            checkNotNull(provider);
            checkNotNull(type);

            return new Dependency(subscriber, provider, type);
        }

        /**
         * Returns dependency with the supplied subscriber.
         *
         * @param subscriber subscriber network
         * @return dependency builder
         */
        public Builder subscriber(VtnNetwork subscriber) {
            this.subscriber = subscriber;
            return this;
        }

        /**
         * Returns dependency with the supplied provider.
         *
         * @param provider provider network
         * @return dependency builder
         */
        public Builder provider(VtnNetwork provider) {
            this.provider = provider;
            return this;
        }

        /**
         * Returns dependency with the supplied type.
         *
         * @param type type
         * @return dependency builder
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }
    }
}
