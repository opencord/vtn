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
package org.opencord.cordvtn.api.node;

import org.onosproject.store.Store;

import java.util.Set;

/**
 * Manages the inventory of cordvtn nodes; not intended for direct use.
 */
public interface CordVtnNodeStore extends Store<CordVtnNodeEvent, CordVtnNodeStoreDelegate> {

    /**
     * Returns all nodes.
     *
     * @return set of nodes; empty set if no node presents
     */
    Set<CordVtnNode> nodes();

    /**
     * Returns the node with the given hostname.
     *
     * @param hostname hostname of the node
     * @return cordvtn node; null if no node present with the hostname
     */
    CordVtnNode node(String hostname);

    /**
     * Creates a new node.
     *
     * @param node cordvtn node
     */
    void createNode(CordVtnNode node);

    /**
     * Updates the node.
     *
     * @param node cordvtn node
     */
    void updateNode(CordVtnNode node);

    /**
     * Removes the node with the supplied hostname.
     *
     * @param hostname hostname of the node
     * @return removed node; null if it failed
     */
    CordVtnNode removeNode(String hostname);
}
