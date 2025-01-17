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

package com.splicemachine.stream;

import com.splicemachine.EngineDriver;
import com.splicemachine.concurrent.Clock;
import com.splicemachine.derby.iapi.sql.execute.RunningOperation;
import com.splicemachine.derby.iapi.sql.olap.DistributedJob;
import com.splicemachine.derby.iapi.sql.olap.OlapStatus;
import com.splicemachine.derby.stream.ActivationHolder;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Created by dgomezferro on 5/20/16.
 */
public class RemoteQueryJob extends DistributedJob implements Serializable {
    private static final long serialVersionUID = 8532788323865054161L;
    final UUID uuid;
    int rootResultSetNumber;
    ActivationHolder ah;
    String host;
    int port;
    String session;
    String userId;
    String sql;
    int streamingBatches;
    int streamingBatchSize;
    int parallelPartitions;
    Integer shufflePartitions;
    transient RunningOperation runningOperation = null;
    int partitionExecutorThreads;


    public RemoteQueryJob(ActivationHolder ah, int rootResultSetNumber, UUID uuid, String host, int port,
                          String session, String userId, String sql,
                          int streamingBatches, int streamingBatchSize, int parallelPartitions,
                          Integer shufflePartitionsProperty, UUID runningOperationUUID,
                          int partitionExecutorThreads) {
        this.ah = ah;
        this.rootResultSetNumber = rootResultSetNumber;
        this.uuid = uuid;
        this.host = host;
        this.port = port;
        this.session = session;
        this.userId = userId;
        this.sql = sql;
        this.streamingBatches = streamingBatches;
        this.streamingBatchSize = streamingBatchSize;
        this.parallelPartitions = parallelPartitions;
        this.shufflePartitions = shufflePartitionsProperty;

        this.partitionExecutorThreads = partitionExecutorThreads;
        RunningOperation runningOperation = null;
        if( runningOperationUUID != null )
            runningOperation = EngineDriver.driver().getOperationManager().getRunningOperation(runningOperationUUID);
        this.runningOperation = runningOperation;
    }

    @Override
    public Callable<Void> toCallable(OlapStatus jobStatus, Clock clock, long clientTimeoutCheckIntervalMs) {
        return new QueryJob(this, jobStatus);
    }

    @Override
    public String getName() {
        return "query-"+uuid;
    }

    @Override
    public void notify(String str) {
        if( runningOperation != null )
            runningOperation.setProgressString(str);
    }
}
