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

package com.splicemachine.db.impl.jdbc;

import java.sql.SQLException;


/* ---- New jdbc 2.0 types ----- */
import java.sql.Array;
import java.sql.Ref;

/**
 * This class extends the EmbedPreparedStatement class in order to support new
 * methods and classes that come with JDBC 2.0.
  <P><B>Supports</B>
   <UL>
   <LI> JDBC 2.0
   </UL>
 *	@see EmbedPreparedStatement
 *
 */
public abstract class EmbedPreparedStatement20
	extends EmbedPreparedStatement {

	//////////////////////////////////////////////////////////////
	//
	// CONSTRUCTORS
	//
	//////////////////////////////////////////////////////////////
	/*
		Constructor assumes caller will setup context stack
		and restore it.
	    @exception SQLException on error
	 */
	public EmbedPreparedStatement20 (EmbedConnection conn, String sql, boolean forMetaData,
									  int resultSetType,
									  int resultSetConcurrency,
									  int resultSetHoldability,
									  int autoGeneratedKeys,
									  int[] columnIndexes,
									  String[] columnNames)
		throws SQLException {

		super(conn, sql, forMetaData, resultSetType, resultSetConcurrency, resultSetHoldability,
		autoGeneratedKeys, columnIndexes, columnNames);
	}

    /**
     * JDBC 2.0
     *
     * Set a REF(&lt;structured-type&gt;) parameter.
     *
     * @param i the first parameter is 1, the second is 2, ...
     * @param x an object representing data of an SQL REF Type
     * @exception SQLException Feature not implemented for now.
     */
    public void setRef (int i, Ref x) throws SQLException {
		throw Util.notImplemented();
	}




    /**
     * JDBC 2.0
     *
     * Set an Array parameter.
     *
     * @param i the first parameter is 1, the second is 2, ...
     * @param x an object representing an SQL array
     * @exception SQLException Feature not implemented for now.
     */
    public void setArray (int i, Array x) throws SQLException {
		setObject(i,x);
	}

}

