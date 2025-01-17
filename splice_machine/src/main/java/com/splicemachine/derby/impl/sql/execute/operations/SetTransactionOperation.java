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

package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.Activation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


/**
 * This is a wrapper class which invokes the Execution-time logic for
 * SET TRANSACTION statements. The real Execution-time logic lives inside the
 * executeConstantAction() method of the Execution constant.
 */

@SuppressFBWarnings(value="SE_NO_SUITABLE_CONSTRUCTOR_FOR_EXTERNALIZATION", justification="Serialization"+
        "of this class is a mistake, but we inherit externalizability from SpliceBaseOperation")
public class SetTransactionOperation extends MiscOperation{
    /**
     * Construct a SetTransactionResultSet
     *
     * @param activation Describes run-time environment.
     */
    public SetTransactionOperation(Activation activation) throws StandardException{
        super(activation);
    }

    /**
     * Does this ResultSet cause a commit or rollback.
     *
     * @return Whether or not this ResultSet cause a commit or rollback.
     */
    public boolean doesCommit(){
        return true;
    }
}
