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

package com.splicemachine.access.hbase;

import com.splicemachine.access.api.PartitionCreator;
import com.splicemachine.concurrent.Clock;
import com.splicemachine.db.iapi.store.access.conglomerate.Conglomerate;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.storage.ClientPartition;
import com.splicemachine.storage.Partition;
import com.splicemachine.storage.PartitionInfoCache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.log4j.Logger;
import org.apache.hadoop.hbase.client.*;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Scott Fines
 *         Date: 12/28/15
 */
public class HPartitionCreator implements PartitionCreator{
    private static final Logger LOG = Logger.getLogger(HPartitionCreator.class);

    private TableDescriptorBuilder descriptorBuilder;
    private TableName tableName;
    private final Connection connection;
    private final ColumnFamilyDescriptor userDataFamilyDescriptor;
    private final Clock clock;
    private final HBaseTableInfoFactory tableInfoFactory;
    private final PartitionInfoCache partitionInfoCache;
    private byte[][] splitKeys;

    public HPartitionCreator(HBaseTableInfoFactory tableInfoFactory, Connection connection, Clock clock,
                             ColumnFamilyDescriptor userDataFamilyDescriptor, PartitionInfoCache partitionInfoCache){
        this.connection = connection;
        this.userDataFamilyDescriptor = userDataFamilyDescriptor;
        this.tableInfoFactory = tableInfoFactory;
        this.clock = clock;
        this.partitionInfoCache = partitionInfoCache;
    }

    @Override
    public PartitionCreator withName(String name){
        return withName(name, Conglomerate.Priority.NORMAL);
    }

    public static int getHBasePriority(Conglomerate.Priority priority)
    {
        switch(priority){
            case NORMAL:    return HBaseTableDescriptor.NORMAL_TABLE_PRIORITY;
            case HIGH:      return HBaseTableDescriptor.HIGH_TABLE_PRIORITY;
            default:        throw new RuntimeException("Not implemented priority " + priority);
        }
    }

    @Override
    public PartitionCreator withName(String name, Conglomerate.Priority priority){
        assert tableName == null;
        tableName = tableInfoFactory.getTableInfo(name);
        descriptorBuilder = TableDescriptorBuilder.newBuilder(tableName);
        descriptorBuilder.setPriority(getHBasePriority(priority));
        return this;
    }

    @Override
    public PartitionCreator withDisplayNames(String[] displayNames){
        assert descriptorBuilder!=null: "Programmer error: must specify name first!";
        if (displayNames[0] != null) {
            descriptorBuilder.setValue(SIConstants.SCHEMA_DISPLAY_NAME_ATTR, displayNames[0]);
        }
        descriptorBuilder.setValue(SIConstants.TABLE_DISPLAY_NAME_ATTR, displayNames[1] != null ? displayNames[1] : tableName.getNameAsString());
        if (displayNames[2] != null) {
            descriptorBuilder.setValue(SIConstants.INDEX_DISPLAY_NAME_ATTR, displayNames[2]);
        }
        return this;
    }

    @Override
    public PartitionCreator withPartitionSize(long partitionSize){
        assert descriptorBuilder!=null: "Programmer error: must specify name first!";
        descriptorBuilder.setMaxFileSize(partitionSize*1024*1024);
        return this;
    }

    @Override
    public PartitionCreator withCoprocessor(String coprocessor) throws IOException{
        assert descriptorBuilder!=null: "Programmer error: must specify name first!";
        descriptorBuilder.setCoprocessor(coprocessor);
        return this;
    }

    @Override
    public PartitionCreator withTransactionId(long txnId) throws IOException {
        assert descriptorBuilder!=null: "Programmer error: must specify name first!";
        descriptorBuilder.setValue(SIConstants.TRANSACTION_ID_ATTR, Long.toString(txnId));
        return this;
    }

    @Override
    public PartitionCreator withCatalogVersion(String version) {
        descriptorBuilder.setValue(SIConstants.CATALOG_VERSION_ATTR, version);
        return this;
    }

    @Override
    public Partition create() throws IOException{
        try {
            return createAsync().get();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public Future<Partition> createAsync() throws IOException {
        assert descriptorBuilder!=null: "No table to create!";
        descriptorBuilder.setColumnFamily(userDataFamilyDescriptor);
        TableDescriptor descriptor = descriptorBuilder.build();
        try(Admin admin = connection.getAdmin()){
            Future<Void> future = admin.createTableAsync(descriptor, splitKeys);
            Partition result = new ClientPartition(connection,tableName,connection.getTable(tableName),clock,partitionInfoCache);
            return new Future<Partition>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return future.cancel(mayInterruptIfRunning);
                }

                @Override
                public boolean isCancelled() {
                    return future.isCancelled();
                }

                @Override
                public boolean isDone() {
                    return future.isDone();
                }

                @Override
                public Partition get() throws InterruptedException, ExecutionException {
                    try {
                        future.get(admin.getOperationTimeout()*10, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        throw new RuntimeException(e);
                    }
                    return result;
                }

                @Override
                public Partition get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    future.get(timeout, unit);
                    return result;
                }
            };
        }
    }

    @Override
    @SuppressFBWarnings(value="EI_EXPOSE_REP2", justification="DB-9371")
    public PartitionCreator withSplitKeys(byte[][] splitKeys) {
        this.splitKeys = splitKeys;
        return this;
    }
}
