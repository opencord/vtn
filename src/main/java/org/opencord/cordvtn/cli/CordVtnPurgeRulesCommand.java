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
package org.opencord.cordvtn.cli;

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.opencord.cordvtn.api.core.CordVtnPipeline;

/**
 * Purges all flow rules installed by CORD VTN service.
 */
@Command(scope = "onos", name = "cordvtn-purge-rules",
        description = "Purges all flow rules installed by CORD VTN")
public class CordVtnPurgeRulesCommand extends AbstractShellCommand {

    @Override
    protected void execute() {
        CordVtnPipeline pipeline = AbstractShellCommand.get(CordVtnPipeline.class);
        pipeline.cleanupPipeline();
    }
}
