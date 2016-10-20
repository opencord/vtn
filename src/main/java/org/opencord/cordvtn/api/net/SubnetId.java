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
package org.opencord.cordvtn.api.net;

import org.onlab.util.Identifier;

/**
 * Representation of the subnet identifier.
 */
public final class SubnetId extends Identifier<String> {

    /**
     * Default constructor.
     *
     * @param id string subnet identifier
     */
    private SubnetId(String id) {
        super(id);
    }

    /**
     * Returns the subnet identifier with the supplied value.
     *
     * @param id string subnet identifier
     * @return subnet identifier
     */
    public static SubnetId of(String id) {
        return new SubnetId(id);
    }
}
