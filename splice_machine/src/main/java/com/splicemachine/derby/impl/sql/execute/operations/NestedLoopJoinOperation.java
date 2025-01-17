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

import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperationContext;
import com.splicemachine.derby.stream.function.NLJAntiJoinFunction;
import com.splicemachine.derby.stream.function.NLJInnerJoinFunction;
import com.splicemachine.derby.stream.function.NLJOneRowInnerJoinFunction;
import com.splicemachine.derby.stream.function.NLJOuterJoinFunction;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.DataSetProcessor;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.loader.GeneratedMethod;
import com.splicemachine.db.iapi.sql.Activation;
import org.apache.log4j.Logger;
import java.io.IOException;

public class NestedLoopJoinOperation extends JoinOperation {
        private static Logger LOG = Logger.getLogger(NestedLoopJoinOperation.class);
        protected boolean isHash;
        protected static final String NAME = NestedLoopJoinOperation.class.getSimpleName().replaceAll("Operation","");
        @Override
        public String getName() {
                return NAME;
        }
        public NestedLoopJoinOperation() {
                super();
        }

        public NestedLoopJoinOperation(SpliceOperation leftResultSet,
                                       int leftNumCols,
                                       SpliceOperation rightResultSet,
                                       int rightNumCols,
                                       Activation activation,
                                       GeneratedMethod restriction,
                                       int resultSetNumber,
                                       boolean oneRowRightSide,
                                       byte semiJoinType,
                                       boolean rightFromSSQ,
                                       double optimizerEstimatedRowCount,
                                       double optimizerEstimatedCost,
                                       String userSuppliedOptimizerOverrides,
                                       String sparkExpressionTreeAsString) throws StandardException {
                super(leftResultSet,leftNumCols,rightResultSet,rightNumCols,activation,restriction,
                      resultSetNumber,oneRowRightSide, semiJoinType,rightFromSSQ,optimizerEstimatedRowCount,
                      optimizerEstimatedCost,userSuppliedOptimizerOverrides,sparkExpressionTreeAsString);
                this.isHash = false;
                init();
        }

        @Override
        public void init(SpliceOperationContext context) throws IOException, StandardException{
                super.init(context);
        }

        @Override
        public String toString() {
                return "NestedLoop"+super.toString();
        }

        @Override
        public String prettyPrint(int indentLevel) {
                return "NestedLoopJoin:" + super.prettyPrint(indentLevel);
        }


    public DataSet<ExecRow> getDataSet(DataSetProcessor dsp) throws StandardException {
        if (!isOpen)
            throw new IllegalStateException("Operation is not open");

        dsp.incrementOpDepth();
        DataSet<ExecRow> left = leftResultSet.getDataSet(dsp);
        OperationContext<NestedLoopJoinOperation> operationContext = dsp.createOperationContext(this);

        DataSet<ExecRow> right = null;
        if (dsp.isSparkExplain()) {
        // Need to call getDataSet to fully print the spark explain.
            dsp.finalizeTempOperationStrings();
        right = rightResultSet.getDataSet(dsp);
        dsp.decrementOpDepth();
        }
        operationContext.pushScope();
        DataSet<ExecRow> result = null;
        try {
            if (isOuterJoin())
                result = left.mapPartitions(new NLJOuterJoinFunction(operationContext), true);
            else {
                if (isAntiJoin())
                    result = left.mapPartitions(new NLJAntiJoinFunction(operationContext), true);
                else {
                    // if inclusion join or regular inner join with one matching row on right
                    if (oneRowRightSide)
                        result = left.mapPartitions(new NLJOneRowInnerJoinFunction(operationContext), true);
                    else
                        result = left.mapPartitions(new NLJInnerJoinFunction(operationContext), true);
                }
            }
            if (dsp.isSparkExplain()) {
                handleSparkExplain(result, left, right, dsp);
            }
        } finally {
            operationContext.popScope();
        }
        return result;
    }
}
