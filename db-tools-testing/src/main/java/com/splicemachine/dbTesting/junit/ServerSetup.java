/*
 * This file is part of Splice Machine.
 * Splice Machine is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3, or (at your option) any later version.
 * Splice Machine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with Splice Machine.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 * Some parts of this source code are based on Apache Derby, and the following notices apply to
 * Apache Derby:
 *
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified the Apache Derby code in this file.
 *
 * All such Splice Machine modifications are Copyright 2012 - 2020 Splice Machine, Inc.,
 * and are licensed to you under the GNU Affero General Public License.
 */
package com.splicemachine.dbTesting.junit;

import junit.framework.Test;

/**
 * Change to a client server configuration based upon the
 * current configuration at setup time. Previous configuration
 * is restored at tearDown time. This only changes the
 * configuration, it does not start any network server.
 *
 */
public final class ServerSetup extends ChangeConfigurationSetup {

    private final String host;
    private final int port;
    private JDBCClient client;
    private String dbPath;
    
    public ServerSetup(Test test, String host, int port) {
        super(test);
        this.host = host;
        this.port = port;
    }

    public ServerSetup(Test test, String host, int port, String dbPath) {
        super(test);
        this.host = host;
        this.port = port;
        this.dbPath = dbPath;
    }

    TestConfiguration getNewConfiguration(TestConfiguration old) {
               
        return new TestConfiguration(old,
            (client == null) ? JDBCClient.DERBYNETCLIENT : client, host, port);
    }

    TestConfiguration getNewConfiguration(TestConfiguration old, String dbPath) {
               
        return new TestConfiguration(old,
            (client == null) ? JDBCClient.DERBYNETCLIENT : client, host, port, 
            dbPath);
    }

    /**
     * Specify a JDBCClient to use in place of the default DERBYNETCLIENT.
     */
    void setJDBCClient(JDBCClient newClient)
    {
        this.client = newClient;
    }
    /**
     * Specify a dbPath to use in place of the default.
     */
    void setDbPath(String dbPath)
    {
        this.dbPath = dbPath;
    }
}
