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
package org.opencord.cordvtn.api.net;

import com.google.common.base.Strings;
import org.onlab.util.Identifier;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Representation of the network identifier.
 */
public final class NetworkId extends Identifier<String> {

    /**
     * Default constructor.
     *
     * @param id string network identifier
     */
    private NetworkId(String id) {
        super(id);
    }

    /**
     * Returns the network identifier with the supplied value.
     *
     * @param id string network identifier
     * @return network identifier
     */
    public static NetworkId of(String id) {
        checkArgument(!Strings.isNullOrEmpty(id));
        return new NetworkId(id);
    }
}
