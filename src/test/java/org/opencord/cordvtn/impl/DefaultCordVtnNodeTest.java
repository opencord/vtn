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
package org.opencord.cordvtn.impl;

import com.google.common.testing.EqualsTester;
import org.junit.Test;
import org.onosproject.net.Device;
import org.opencord.cordvtn.api.node.CordVtnNode;

import static org.onlab.junit.ImmutableClassChecker.assertThatClassIsImmutable;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.COMPLETE;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.INIT;

/**
 * Unit test of {@link DefaultCordVtnNode} model entity.
 */
public class DefaultCordVtnNodeTest extends CordVtnNodeTest {

    private static final Device OF_DEVICE_1 = createDevice(1);
    private static final CordVtnNode NODE_1 = createNode("node-01", OF_DEVICE_1, INIT);
    private static final CordVtnNode NODE_2 = createNode("node-01", OF_DEVICE_1, COMPLETE);
    private static final CordVtnNode NODE_3 = createNode("node-03", OF_DEVICE_1, INIT);

    @Test
    public void testImmutability() {
        assertThatClassIsImmutable(DefaultCordVtnNode.class);
    }

    @Test
    public void testEquality() {
        new EqualsTester().addEqualityGroup(NODE_1, NODE_2)
                .addEqualityGroup(NODE_3)
                .testEquals();
    }
}
