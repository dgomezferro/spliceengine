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

package com.splicemachine.db.impl.tools.ij;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.Vector;
import java.util.List;

/**
 * This is a wrapper for results coming out of the
 * ij parser.
 *
 *
 */
interface ijResult {
	boolean isConnection();
	boolean isStatement();
	boolean isResultSet() throws SQLException;
	boolean isUpdateCount() throws SQLException;
	boolean isNextRowOfResultSet();
	boolean isVector();
	boolean isMulti();
	boolean isException();
	boolean isMultipleResultSetResult();
	boolean isUnsupportedCommand();
	boolean hasWarnings() throws SQLException ;

	Connection getConnection();
	Statement getStatement();
	int getUpdateCount() throws SQLException;
	ResultSet getResultSet() throws SQLException;
	List getMultipleResultSets();
	ResultSet getNextRowOfResultSet();
	Vector getVector();
	SQLException getException();
	int[] getColumnDisplayList();
	int[] getColumnWidthList();

	void closeStatement() throws SQLException ;

	/*
		Since they will all need to do warning calls/clears, may as
		well stick it here.
	 */
	SQLWarning getSQLWarnings() throws SQLException ;
	void clearSQLWarnings() throws SQLException ;
}
