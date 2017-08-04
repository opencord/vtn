/*
 * Copyright 2017-present Open Networking Foundation
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
package org.opencord.cordvtn.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.opencord.cordvtn.rest.XosVtnNetworkingClient;

/**
 * Synchronizes network states with XOS VTN service.
 * This command can be used to actively synchronize XOS network with VTN
 * service network.
 */
@Command(scope = "onos", name = "cordvtn-sync-xos-states",
        description = "Synchronizes network states with XOS")
public class CordVtnSyncXosStatesCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "endpoint", description = "XOS VTN service endpoint",
            required = true, multiValued = false)
    private String endpoint = null;

    @Argument(index = 1, name = "user", description = "XOS admin user name",
            required = true, multiValued = false)
    private String user = null;

    @Argument(index = 2, name = "password", description = "XOS admin user password",
            required = true, multiValued = false)
    private String password = null;

    @Override
    protected void execute() {
        XosVtnNetworkingClient client = XosVtnNetworkingClient.builder()
                .endpoint(endpoint)
                .user(user)
                .password(password)
                .build();

        print("Requesting state synchronization");
        client.requestSync();
    }
}
