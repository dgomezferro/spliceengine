/*
 * Copyright (c) 2012 - 2020 Splice Machine, Inc.
 *
 * This file is part of Splice Machine.
 * Splice Machine is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3, or (at your option) any later version.
 * Splice Machine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with Splice Machine.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.splicemachine.derby.test.framework;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SpliceProcedureWatcher extends AbstractSpliceFunctionWatcher {

    public SpliceProcedureWatcher(String procedureName,String schemaName, String createString) {
        super(procedureName, schemaName, createString);
    }
    public SpliceProcedureWatcher(String procedureName,String schemaName, String createString, String userName, String password) {
        super(procedureName, schemaName, createString, userName, password);
    }

    @Override
    protected String functionType() {
        return "procedure";
    }

    @Override
    protected void dropIfExists(Connection connection) throws SQLException {
        try(ResultSet rs = connection.getMetaData().getProcedures(null, schemaName, functionName)) {
            if (rs.next()) {
                executeDrop(schemaName,functionName);
            }
        }
        connection.commit();
    }
}
