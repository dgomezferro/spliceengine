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

package com.splicemachine.derby.procedures;

import splice.com.google.common.collect.Iterables;
import com.splicemachine.EngineDriver;
import com.splicemachine.db.iapi.error.PublicAPI;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.Property;
import com.splicemachine.db.iapi.services.context.ContextManager;
import com.splicemachine.db.iapi.services.context.ContextService;
import com.splicemachine.db.iapi.services.io.FormatableBitSet;
import com.splicemachine.db.iapi.services.property.PropertyUtil;
import com.splicemachine.db.iapi.services.uuid.UUIDFactory;
import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.db.iapi.sql.ResultColumnDescriptor;
import com.splicemachine.db.iapi.sql.conn.Authorizer;
import com.splicemachine.db.iapi.sql.conn.LanguageConnectionContext;
import com.splicemachine.db.iapi.sql.dictionary.*;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.stats.ColumnStatisticsImpl;
import com.splicemachine.db.iapi.stats.FakeColumnStatisticsImpl;
import com.splicemachine.db.iapi.stats.ItemStatistics;
import com.splicemachine.db.iapi.store.access.TransactionController;
import com.splicemachine.db.iapi.types.*;
import com.splicemachine.db.impl.jdbc.EmbedConnection;
import com.splicemachine.db.impl.jdbc.EmbedResultSet40;
import com.splicemachine.db.impl.sql.GenericColumnDescriptor;
import com.splicemachine.db.impl.sql.catalog.Procedure;
import com.splicemachine.db.impl.sql.catalog.SYSCOLUMNSTATISTICSRowFactory;
import com.splicemachine.db.impl.sql.catalog.SYSTABLESTATISTICSRowFactory;
import com.splicemachine.db.impl.sql.execute.IteratorNoPutResultSet;
import com.splicemachine.db.impl.sql.execute.ValueRow;
import com.splicemachine.db.shared.common.reference.SQLState;
import com.splicemachine.ddl.DDLMessage.DDLChange;
import com.splicemachine.derby.ddl.DDLUtils;
import com.splicemachine.derby.impl.store.access.SpliceTransactionManager;
import com.splicemachine.derby.impl.store.access.base.SpliceConglomerate;
import com.splicemachine.derby.stream.iapi.DataSetProcessor;
import com.splicemachine.derby.stream.iapi.DistributedDataSetProcessor;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.derby.stream.iapi.ScanSetBuilder;
import com.splicemachine.derby.utils.EngineUtils;
import com.splicemachine.derby.utils.StatisticsOperation;
import com.splicemachine.metrics.Metrics;
import com.splicemachine.pipeline.ErrorState;
import com.splicemachine.pipeline.Exceptions;
import com.splicemachine.primitives.Bytes;
import com.splicemachine.protobuf.ProtoUtil;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.si.impl.driver.SIDriver;
import com.splicemachine.storage.DataScan;
import com.splicemachine.storage.Partition;
import com.splicemachine.utils.Pair;
import com.splicemachine.utils.SpliceLogUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.log4j.Logger;
import splice.com.google.common.collect.Lists;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.splicemachine.derby.utils.EngineUtils.getSchemaDescriptor;
import static com.splicemachine.derby.utils.EngineUtils.verifyTableExists;


/**
 * @author Scott Fines
 *         Date: 2/26/15
 */
public class StatisticsProcedures extends BaseAdminProcedures {

    private static final int DDL_NOTIFICATION_PARTITION_SIZE = 512;

    public static void addProcedures(List<Procedure> procedures) {
        /*
         * Statistics procedures
         */
        Procedure collectStatsForTable = Procedure.newBuilder().name("COLLECT_TABLE_STATISTICS")
                .numOutputParams(0)
                .numResultSets(1)
                .varchar("schema",128)
                .varchar("table",1024)
                .arg("staleOnly", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BOOLEAN).getCatalogType())
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(collectStatsForTable);

        Procedure collectSampleStatsForTable = Procedure.newBuilder().name("COLLECT_TABLE_SAMPLE_STATISTICS")
                .numOutputParams(0)
                .numResultSets(1)
                .varchar("schema",128)
                .varchar("table",1024)
                .arg("samplePercentage", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.DOUBLE).getCatalogType())
                .arg("staleOnly", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BOOLEAN).getCatalogType())
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(collectSampleStatsForTable);

        Procedure collectNonMergedStatsForTable = Procedure.newBuilder().name("COLLECT_NONMERGED_TABLE_STATISTICS")
                .numOutputParams(0)
                .numResultSets(1)
                .varchar("schema",128)
                .varchar("table",1024)
                .arg("staleOnly", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BOOLEAN).getCatalogType())
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(collectNonMergedStatsForTable);

        Procedure collectNonMergedSampleStatsForTable = Procedure.newBuilder().name("COLLECT_NONMERGED_TABLE_SAMPLE_STATISTICS")
                .numOutputParams(0)
                .numResultSets(1)
                .varchar("schema",1024)
                .varchar("table",1024)
                .arg("samplePercentage", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.DOUBLE).getCatalogType())
                .arg("staleOnly", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BOOLEAN).getCatalogType())
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(collectNonMergedSampleStatsForTable);

        Procedure fakeStatsForTable = Procedure.newBuilder().name("FAKE_TABLE_STATISTICS")
                .numOutputParams(0)
                .numResultSets(1)
                .modifiesSql()
                .catalog("schema")
                .catalog("table")
                .arg("rowCount", DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .arg("meanRowsize", DataTypeDescriptor.getCatalogType(Types.INTEGER))
                .arg("numPartitions", DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(fakeStatsForTable);

        Procedure fakeStatsForColumn = Procedure.newBuilder().name("FAKE_COLUMN_STATISTICS")
                .numOutputParams(0)
                .numResultSets(1)
                .modifiesSql()
                .catalog("schema")
                .catalog("table")
                .varchar("column",1024)
                .arg("nullCountRatio", DataTypeDescriptor.getCatalogType(Types.DOUBLE))
                .arg("rowsPerValue", DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(fakeStatsForColumn);

        Procedure dropStatsForSchema = Procedure.newBuilder().name("DROP_SCHEMA_STATISTICS")
                .numOutputParams(0)
                .numResultSets(0)
                .varchar("schema",128)
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(dropStatsForSchema);

        Procedure dropStatsForTable = Procedure.newBuilder().name("DROP_TABLE_STATISTICS")
                .numOutputParams(0)
                .numResultSets(0)
                .varchar("schema",128)
                .varchar("table",1024)
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(dropStatsForTable);

        Procedure collectStatsForSchema = Procedure.newBuilder().name("COLLECT_SCHEMA_STATISTICS")
                .numOutputParams(0)
                .numResultSets(1)
                .varchar("schema",128)
                .arg("staleOnly", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BOOLEAN).getCatalogType())
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(collectStatsForSchema);

        Procedure enableStatsForColumn = Procedure.newBuilder().name("ENABLE_COLUMN_STATISTICS")
                .numOutputParams(0)
                .numResultSets(0)
                .modifiesSql()
                .varchar("schema",1024)
                .varchar("table",1024)
                .varchar("column",1024)
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(enableStatsForColumn);

        Procedure disableStatsForColumn = Procedure.newBuilder().name("DISABLE_COLUMN_STATISTICS")
                .numOutputParams(0)
                .numResultSets(0)
                .modifiesSql()
                .varchar("schema",1024)
                .varchar("table",1024)
                .varchar("column",1024)
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(disableStatsForColumn);

        Procedure enableStatsForAllColumns = Procedure.newBuilder().name("ENABLE_ALL_COLUMN_STATISTICS")
                .numOutputParams(0)
                .numResultSets(0)
                .modifiesSql()
                .catalog("schema")
                .catalog("table")
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(enableStatsForAllColumns);

        Procedure disableStatsForAllColumns = Procedure.newBuilder().name("DISABLE_ALL_COLUMN_STATISTICS")
                .numOutputParams(0)
                .numResultSets(0)
                .modifiesSql()
                .catalog("schema")
                .catalog("table")
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(disableStatsForAllColumns);

        Procedure setStatsExtrapolationForColumn = Procedure.newBuilder().name("SET_STATS_EXTRAPOLATION_FOR_COLUMN")
                .numOutputParams(0)
                .numResultSets(0)
                .modifiesSql()
                .varchar("schema",1024)
                .varchar("table",1024)
                .varchar("column",1024)
                .smallint("useExtrapolation")
                .ownerClass(StatisticsProcedures.class.getCanonicalName())
                .build();
        procedures.add(setStatsExtrapolationForColumn);
    }

    private static final Logger LOG = Logger.getLogger(StatisticsProcedures.class);
    public static final String TABLEID_FROM_SCHEMA = "select tableid from sysvw.systablesView t where t.schemaid = ?";

    @SuppressWarnings("UnusedDeclaration")
    public static void DISABLE_COLUMN_STATISTICS(String schema,
                                                 String table,
                                                 String columnName) throws SQLException {
        schema = EngineUtils.validateSchema(schema);
        table = EngineUtils.validateTable(table);
        columnName = EngineUtils.validateColumnName(columnName);
        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        try {
            TableDescriptor td = verifyTableExists(conn, schema, table);
            //verify that that column exists
            ColumnDescriptorList columnDescriptorList = td.getColumnDescriptorList();
            for (ColumnDescriptor descriptor : columnDescriptorList) {
                if (descriptor.getColumnName().equalsIgnoreCase(columnName)) {
                    //need to make sure it's not a pk or indexed column
                    ensureNotKeyed(descriptor, td);
                    descriptor.setCollectStatistics(false);
                    LanguageConnectionContext languageConnection=conn.getLanguageConnection();
                    TransactionController transactionCompile=languageConnection.getTransactionCompile();
                    transactionCompile.elevate("dictionary");
                    languageConnection.getDataDictionary().setCollectStats(transactionCompile, td.getUUID(), columnName, false);
                    return;
                }
            }
            throw ErrorState.LANG_COLUMN_NOT_FOUND_IN_TABLE.newException(columnName, schema + "." + table);
        } catch (StandardException e) {
            throw PublicAPI.wrapStandardException(e);
        }
    }

    public static void SET_STATS_EXTRAPOLATION_FOR_COLUMN(String schema,
                                                 String table,
                                                 String columnName,
                                                 short useExtrapolation) throws SQLException {
        schema = EngineUtils.validateSchema(schema);
        table = EngineUtils.validateTable(table);
        columnName = EngineUtils.validateColumnName(columnName);
        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        try {
            TableDescriptor td = verifyTableExists(conn, schema, table);
            //verify that that column exists
            ColumnDescriptorList columnDescriptorList = td.getColumnDescriptorList();
            for (ColumnDescriptor descriptor : columnDescriptorList) {
                if (descriptor.getColumnName().equalsIgnoreCase(columnName)) {
                    byte value = (byte)(useExtrapolation==0 ? 0 : 1);
                    // make sure the column type can support extrapolation
                    if ((value == 1) && !ColumnDescriptor.allowsExtrapolation(descriptor.getType()))
                        throw ErrorState.LANG_STATS_EXTRAPOLATION_NOT_SUPPORTED.newException(columnName, descriptor.getType());
                    descriptor.setUseExtrapolation(value);
                    LanguageConnectionContext languageConnection=conn.getLanguageConnection();
                    TransactionController transactionCompile=languageConnection.getTransactionCompile();
                    transactionCompile.elevate("dictionary");
                    languageConnection.getDataDictionary().setUseExtrapolation(transactionCompile, td.getUUID(), columnName, value);
                    return;
                }
            }
            throw ErrorState.LANG_COLUMN_NOT_FOUND_IN_TABLE.newException(columnName, schema + "." + table);
        } catch (StandardException e) {
            throw PublicAPI.wrapStandardException(e);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static void DISABLE_ALL_COLUMN_STATISTICS(String schema,
                                                 String table) throws SQLException {
        schema = EngineUtils.validateSchema(schema);
        table = EngineUtils.validateTable(table);
        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        try {
            TableDescriptor td = verifyTableExists(conn, schema, table);
            ColumnDescriptorList columnDescriptorList = td.getColumnDescriptorList();
            //get the list of index columns whose stats are mandatory
            boolean[] indexColumns = new boolean[columnDescriptorList.size()];

            IndexLister indexLister = td.getIndexLister();
            if (indexLister != null) {
                IndexRowGenerator[] indexRowGenerators = indexLister.getIndexRowGenerators();
                for (IndexRowGenerator irg : indexRowGenerators) {
                    int[] keyColumns = irg.getIndexDescriptor().baseColumnPositions();
                    for (int keyColumn : keyColumns) {
                        indexColumns[keyColumn - 1] = true;
                    }
                }
            }

            // get the list of columns in PK whose stats are also mandatory
            ReferencedKeyConstraintDescriptor keyDescriptor = td.getPrimaryKey();
            if (keyDescriptor != null) {
                int[] pkColumns = keyDescriptor.getReferencedColumns();
                for (int keyColumn : pkColumns) {
                    indexColumns[keyColumn - 1] = true;
                }
            }

            //go through all columns
            for (ColumnDescriptor descriptor : columnDescriptorList) {
                String columnName = descriptor.getColumnName();
                //need to make sure it's not a pk or indexed column
                if (!indexColumns[descriptor.getPosition() - 1]) {
                    descriptor.setCollectStatistics(false);
                    LanguageConnectionContext languageConnection = conn.getLanguageConnection();
                    TransactionController transactionCompile = languageConnection.getTransactionCompile();
                    transactionCompile.elevate("dictionary");
                    languageConnection.getDataDictionary().setCollectStats(transactionCompile, td.getUUID(), columnName, false);
                }
            }
        } catch (StandardException e) {
            throw PublicAPI.wrapStandardException(e);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static void ENABLE_COLUMN_STATISTICS(String schema,
                                                String table,
                                                String columnName) throws SQLException {
        schema = EngineUtils.validateSchema(schema);
        table = EngineUtils.validateTable(table);
        columnName = EngineUtils.validateColumnName(columnName);
        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        try {
            TableDescriptor td = verifyTableExists(conn, schema, table);
            //verify that that column exists
            ColumnDescriptorList columnDescriptorList = td.getColumnDescriptorList();
            for (ColumnDescriptor descriptor : columnDescriptorList) {
                if (descriptor.getColumnName().equalsIgnoreCase(columnName)) {
                    DataTypeDescriptor type = descriptor.getType();
                    if (!ColumnDescriptor.allowsStatistics(type))
                        throw ErrorState.LANG_COLUMN_STATISTICS_NOT_POSSIBLE.newException(columnName, type.getTypeName());
                    descriptor.setCollectStatistics(true);
                    LanguageConnectionContext languageConnection=conn.getLanguageConnection();
                    TransactionController transactionCompile=languageConnection.getTransactionCompile();
                    transactionCompile.elevate("dictionary");
                    languageConnection.getDataDictionary().setCollectStats(transactionCompile, td.getUUID(), columnName, true);
                    return;
                }
            }
            throw ErrorState.LANG_COLUMN_NOT_FOUND_IN_TABLE.newException(columnName, schema + "." + table);

        } catch (StandardException e) {
            throw PublicAPI.wrapStandardException(e);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static void ENABLE_ALL_COLUMN_STATISTICS(String schema,
                                                String table) throws SQLException {
        schema = EngineUtils.validateSchema(schema);
        table = EngineUtils.validateTable(table);
        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        try {
            TableDescriptor td = verifyTableExists(conn, schema, table);
            //verify that that column exists
            ColumnDescriptorList columnDescriptorList = td.getColumnDescriptorList();
            for (ColumnDescriptor descriptor : columnDescriptorList) {
                String columnName = descriptor.getColumnName();
                DataTypeDescriptor type = descriptor.getType();
                if (!descriptor.collectStatistics() && ColumnDescriptor.allowsStatistics(type)) {
                    descriptor.setCollectStatistics(true);
                    LanguageConnectionContext languageConnection = conn.getLanguageConnection();
                    TransactionController transactionCompile = languageConnection.getTransactionCompile();
                    transactionCompile.elevate("dictionary");
                    languageConnection.getDataDictionary().setCollectStats(transactionCompile, td.getUUID(), columnName, true);
                }
            }
        } catch (StandardException e) {
            throw PublicAPI.wrapStandardException(e);
        }
    }

    private static final ResultColumnDescriptor[] COLLECTED_STATS_OUTPUT_COLUMNS = new GenericColumnDescriptor[]{
        new GenericColumnDescriptor("schemaName", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR)),
        new GenericColumnDescriptor("tableName", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR)),
        new GenericColumnDescriptor("partition", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR)),
        new GenericColumnDescriptor("rowsCollected", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BIGINT)),
        new GenericColumnDescriptor("partitionSize", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BIGINT)),
        new GenericColumnDescriptor("partitionCount", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BIGINT)),
        new GenericColumnDescriptor("statsType", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.INTEGER)),
        new GenericColumnDescriptor("sampleFraction", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.DOUBLE)),
        new GenericColumnDescriptor("skippedColumnIds", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR))
    };

    private static final ResultColumnDescriptor[] COLUMN_STATS_OUTPUT_COLUMNS = new GenericColumnDescriptor[]{
        new GenericColumnDescriptor("schemaName", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR)),
        new GenericColumnDescriptor("tableName", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR)),
        new GenericColumnDescriptor("columnName", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR)),
        new GenericColumnDescriptor("partition", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR)),
        new GenericColumnDescriptor("nullCount", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BIGINT)),
        new GenericColumnDescriptor("totalCount", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BIGINT)),
        new GenericColumnDescriptor("cardinality", DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BIGINT))
    };

    @SuppressWarnings("unused")
    public static void COLLECT_SCHEMA_STATISTICS(String schema, boolean staleOnly, ResultSet[] outputResults) throws
        SQLException {
        EmbedConnection conn = (EmbedConnection)getDefaultConn();
        try {
            if (schema == null)
                throw ErrorState.TABLE_NAME_CANNOT_BE_NULL.newException(); //TODO -sf- change this to proper SCHEMA
                // error?
            schema = EngineUtils.validateSchema(schema);

            LanguageConnectionContext lcc = conn.getLanguageConnection();
            DataDictionary dd = lcc.getDataDictionary();
            dd.startWriting(lcc);

            /* Invalidate dependencies remotely. */

            TransactionController tc = lcc.getTransactionExecute();

            SchemaDescriptor sd = getSchemaDescriptor(schema, lcc, dd);
            //get a list of all the TableDescriptors in the schema
            List<TableDescriptor> tds = getAllTableDescriptors(sd, conn);
            if (tds.isEmpty()) {
                // No point in continuing with empty TableDescriptor list, possible NPE
                return;
            }
            authorize(tds);
            TransactionController transactionExecute = lcc.getTransactionExecute();
            transactionExecute.elevate("statistics");
            dropTableStatistics(tds, dd, tc);
            TxnView txn = ((SpliceTransactionManager) transactionExecute).getRawTransaction().getActiveStateTxn();

            HashMap<Long, Pair<String, String>> display = new HashMap<>();
            ArrayList<StatisticsOperation> statisticsOperations = new ArrayList<>(tds.size());
            for (TableDescriptor td : tds) {
                createCollectStatisticsOperationsForTable(conn, txn, schema, td, false, 0, true, display, statisticsOperations);
            }

            IteratorNoPutResultSet resultsToWrap = wrapResults(conn,
                    displayTableStatistics(statisticsOperations,true, dd, transactionExecute, display), COLLECTED_STATS_OUTPUT_COLUMNS);
            ddlNotificationInPartitions(tc, tds, DDL_NOTIFICATION_PARTITION_SIZE);
            outputResults[0] = new EmbedResultSet40(conn, resultsToWrap, false, null, true);
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        } catch (ExecutionException e) {
            throw PublicAPI.wrapStandardException(Exceptions.parseException(e.getCause()));
        }
    }

    private static void authorize(List<TableDescriptor> tableDescriptorList) throws SQLException, StandardException {
        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        LanguageConnectionContext lcc = conn.getLanguageConnection();
        Authorizer authorizer=lcc.getAuthorizer();
        Activation activation = lcc.getActivationCount()>0?lcc.getLastActivation():null;
        if(activation==null){
            //TODO -sf- this can happen sometimes for some reason
            for(TableDescriptor td : tableDescriptorList){
                authorizer.authorize(Authorizer.INSERT_PRIV);
            }
            return;
        }
        List requiredPermissionsList = activation.getPreparedStatement().getRequiredPermissionsList();
        for (TableDescriptor tableDescriptor : tableDescriptorList) {
            StatementTablePermission key = null;
            try {

                key = new StatementTablePermission(
                        tableDescriptor.getSchemaDescriptor().getUUID(),
                        tableDescriptor.getUUID(),
                        Authorizer.INSERT_PRIV);

                requiredPermissionsList.add(key);
                lcc.getAuthorizer().authorize(activation, 1);
            } catch (StandardException e) {
                if (e.getSqlState().compareTo(SQLState.AUTH_NO_TABLE_PERMISSION) == 0) {
                    throw StandardException.newException(
                        com.splicemachine.db.iapi.reference.SQLState.AUTH_NO_TABLE_PERMISSION_FOR_ANALYZE,
                        lcc.getCurrentUserId(activation),
                        "INSERT",
                        tableDescriptor.getSchemaName(),
                        tableDescriptor.getName());
                } else throw e;
            } finally {
                if (key != null) {
                    requiredPermissionsList.remove(key);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    public static void COLLECT_TABLE_STATISTICS(String schema,
                                                String table,
                                                boolean staleOnly,
                                                ResultSet[] outputResults) throws SQLException {
        doStatsCollectionForTables(schema, table, false, 0.0, staleOnly, true, outputResults);
    }

    public static void COLLECT_TABLE_SAMPLE_STATISTICS(String schema,
                                                       String table,
                                                       double sample,
                                                       boolean staleOnly,
                                                       ResultSet[] outputResults) throws SQLException {
        doStatsCollectionForTables(schema, table, true, sample, staleOnly, true, outputResults);
    }

    @SuppressWarnings({"unchecked"})
    public static void COLLECT_NONMERGED_TABLE_STATISTICS(String schema,
                                                String table,
                                                boolean staleOnly,
                                                ResultSet[] outputResults) throws SQLException {
        doStatsCollectionForTables(schema, table, false, 0.0, staleOnly, false, outputResults);
    }

    public static void COLLECT_NONMERGED_TABLE_SAMPLE_STATISTICS(String schema,
                                                       String table,
                                                       double sample,
                                                       boolean staleOnly,
                                                       ResultSet[] outputResults) throws SQLException {
        doStatsCollectionForTables(schema, table, true, sample, staleOnly, false, outputResults);
    }

    public static void DROP_SCHEMA_STATISTICS(String schema) throws SQLException {
        EmbedConnection conn = (EmbedConnection) getDefaultConn();
        try {
            if (schema == null)
                throw ErrorState.LANG_SCHEMA_DOES_NOT_EXIST.newException();
            schema = schema.toUpperCase();
            LanguageConnectionContext lcc = conn.getLanguageConnection();
            DataDictionary dd = lcc.getDataDictionary();
            SchemaDescriptor sd = getSchemaDescriptor(schema, lcc, dd);
            List<TableDescriptor> tds = getAllTableDescriptors(sd, conn);
            authorize(tds);
            TransactionController tc = conn.getLanguageConnection().getTransactionExecute();
            tc.elevate("statistics");
            dropTableStatistics(tds,dd,tc);
            ddlNotification(tc,tds);
            SpliceLogUtils.debug(LOG, "Done dropping statistics for schema %s.", schema);
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        } finally {
            if (conn != null) conn.close();
        }
    }

    public static void DROP_TABLE_STATISTICS(String schema, String table) throws SQLException {
        EmbedConnection conn = (EmbedConnection) getDefaultConn();
        try {
            schema = EngineUtils.validateSchema(schema);
            table = EngineUtils.validateTable(table);
            TableDescriptor tableDesc = verifyTableExists(conn, schema, table);
            TransactionController tc = conn.getLanguageConnection().getTransactionExecute();
            tc.elevate("statistics");
            DataDictionary dd = conn.getLanguageConnection().getDataDictionary();
            List<TableDescriptor> tds = Collections.singletonList(tableDesc);
            dropTableStatistics(tds,dd,tc);
            ddlNotification(tc,tds);
            SpliceLogUtils.debug(LOG, "Done dropping statistics for table %s.", table);
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        } finally {
            if (conn != null) conn.close();
        }
    }

    public static void FAKE_TABLE_STATISTICS(String schema,
                                             String table,
                                             long rowCount,
                                             int meanRowWidth,
                                             long numPartitions,
                                             ResultSet[] outputResults) throws SQLException {

        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        try {
            schema = EngineUtils.validateSchema(schema);
            table = EngineUtils.validateTable(table);
            TableDescriptor tableDesc = verifyTableExists(conn, schema, table);

            if (rowCount < 0)
                throw ErrorState.LANG_INVALID_FAKE_STATS.newException("table", "row count cannot be a negative value");

            if (meanRowWidth <= 0)
                throw ErrorState.LANG_INVALID_FAKE_STATS.newException("table", "meanRowWidth has to be greater than 0");
            if (numPartitions <= 0)
                throw ErrorState.LANG_INVALID_FAKE_STATS.newException("table", "number of partitions has to be greater than 0");

            List<TableDescriptor> tds = Collections.singletonList(tableDesc);
            authorize(tds);
            DataDictionary dd = conn.getLanguageConnection().getDataDictionary();
            dd.startWriting(conn.getLanguageConnection());
            TransactionController tc = conn.getLanguageConnection().getTransactionExecute();
            tc.elevate("statistics");
            dropTableStatistics(tds,dd,tc);
            ddlNotification(tc, tds);

            // compose the fake table stats row
            ExecRow statsRow;
            int statsType = SYSTABLESTATISTICSRowFactory.FAKE_MERGED_STATS;
            long conglomerateId = tableDesc.getHeapConglomerateId();

            statsRow = StatisticsProcedures.generateRowFromStats(conglomerateId, "-All-", rowCount, rowCount*meanRowWidth, meanRowWidth, numPartitions, statsType, 0.0d);
            dd.addTableStatistics(statsRow, tc);
            ExecRow resultRow = generateOutputRow(schema, table, statsRow, new HashSet<>());

            IteratorNoPutResultSet resultsToWrap = wrapResults(
                    conn,
                    Lists.newArrayList(resultRow), COLLECTED_STATS_OUTPUT_COLUMNS);
            outputResults[0] = new EmbedResultSet40(conn, resultsToWrap, false, null, true);
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        }
    }

    public static void FAKE_COLUMN_STATISTICS(String schema,
                                              String table,
                                              String column,
                                              double nullCountRatio,
                                              long rpv,
                                              ResultSet[] outputResults) throws SQLException {
        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        try {
            schema = EngineUtils.validateSchema(schema);
            table = EngineUtils.validateTable(table);
            column = EngineUtils.validateColumnName(column);
            TableDescriptor td = verifyTableExists(conn, schema, table);
            //verify that that column exists
            int columnId = -1;
            ColumnDescriptor columnDescriptor = null;
            ColumnDescriptorList columnDescriptorList = td.getColumnDescriptorList();
            for (ColumnDescriptor descriptor : columnDescriptorList) {
                if (descriptor.getColumnName().equalsIgnoreCase(column)) {
                    columnId = descriptor.getPosition();
                    columnDescriptor = descriptor;
                    break;
                }
            }
            if (columnId == -1)
                throw ErrorState.LANG_COLUMN_NOT_FOUND_IN_TABLE.newException(column, schema + "." + table);
            List<TableDescriptor> tds = Collections.singletonList(td);
            authorize(tds);
            DataDictionary dd = conn.getLanguageConnection().getDataDictionary();
            dd.startWriting(conn.getLanguageConnection());
            TransactionController tc = conn.getLanguageConnection().getTransactionExecute();
            tc.elevate("statistics");
            // get the row count from table stats
            long totalCount = getRowCountFromTableStats(td.getHeapConglomerateId(), dd, tc);

            if (totalCount < 0)
                throw ErrorState.LANG_INVALID_FAKE_STATS.newException("column", "table stats do not exist, please add table stats first");

            if (nullCountRatio < 0 || nullCountRatio > 1)
                throw ErrorState.LANG_INVALID_FAKE_STATS.newException("column", "null count ratio should be in the range of [0,1]");

            long nullCount = (long)(nullCountRatio * totalCount);

            if (rpv > totalCount - nullCount || rpv < 1)
                throw ErrorState.LANG_INVALID_FAKE_STATS.newException("column", "rows per value shouldn't be less than 1 or larger than the total number of not-null count : " + (totalCount - nullCount));

            long cardinality = (long)(((double)(totalCount- nullCount))/rpv);

            dropColumnStatistics(td.getHeapConglomerateId(), columnId, dd,tc);

            ddlNotification(tc, tds);
            // compose the fake column stats row
            long conglomerateId = td.getHeapConglomerateId();

            FakeColumnStatisticsImpl columnStatistics = new FakeColumnStatisticsImpl(columnDescriptor.getType().getNull(), nullCount, totalCount, cardinality);
            // compose the entry for a given column
            ExecRow statsRow = StatisticsProcedures.generateRowFromStats(conglomerateId, "-All-", columnId, columnStatistics);
            dd.addColumnStatistics(statsRow, tc);

            ExecRow resultRow = generateOutputRowForColumnStats(schema, table, column, "-All-", nullCount, totalCount, cardinality);

            IteratorNoPutResultSet resultsToWrap = wrapResults(
                    conn,
                    Lists.newArrayList(resultRow), COLUMN_STATS_OUTPUT_COLUMNS);
            outputResults[0] = new EmbedResultSet40(conn, resultsToWrap, false, null, true);
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        }

    }

    private static void ddlNotification(TransactionController tc,  List<TableDescriptor> tds) throws StandardException {
        DDLChange ddlChange = ProtoUtil.alterStats(((SpliceTransactionManager) tc).getActiveStateTxn().getTxnId(),tds);
        tc.prepareDataDictionaryChange(DDLUtils.notifyMetadataChange(ddlChange));
    }

    /**
     * When COLLECT_SCHEMA_STATISTICS analyzes a big schema with many tables, it could cause a DDL coordination timeout.
     * The problem happens when the massage with ddlChangeType: ALTER_STATS has many table descriptor ids.
     * To avoid this issue, the method splits the list of table descriptors into smaller partitions with the size given
     * in partitionSize.
     *
     * @param tc
     * @param tds
     * @param partitionSize
     * @throws StandardException
     */
    private static void ddlNotificationInPartitions(TransactionController tc,  List<TableDescriptor> tds, int partitionSize) throws StandardException {
        Iterable<List<TableDescriptor>> tdPartitions = Iterables.partition(tds, partitionSize);
        for (List<TableDescriptor> tdPartition : tdPartitions) {
            DDLChange ddlChange = ProtoUtil.alterStats(((SpliceTransactionManager) tc).getActiveStateTxn().getTxnId(), tdPartition);
            tc.prepareDataDictionaryChange(DDLUtils.notifyMetadataChange(ddlChange));
        }
    }

    /* ****************************************************************************************************************/
    /*private helper methods*/
    private static void doStatsCollectionForTables(String schema,
                                                   String table,
                                                   boolean useSample,
                                                   double samplePercent,
                                                   boolean staleOnly,
                                                   boolean mergeStats,
                                                   ResultSet[] outputResults) throws SQLException {
        EmbedConnection conn = (EmbedConnection) SpliceAdmin.getDefaultConn();
        try {
            schema = EngineUtils.validateSchema(schema);
            table = EngineUtils.validateTable(table);
            TableDescriptor tableDesc = verifyTableExists(conn, schema, table);
            List<TableDescriptor> tds = Collections.singletonList(tableDesc);
            authorize(tds);
            //check if sample fraction is in the valid range
            if (useSample) {
                if (samplePercent<0.0 || samplePercent>100.0)
                    throw ErrorState.LANG_INVALID_VALUE_RANGE.newException("samplePercent value " + samplePercent, "[0,100]");
            }
            DataDictionary dd = conn.getLanguageConnection().getDataDictionary();
            dd.startWriting(conn.getLanguageConnection());
            TransactionController tc = conn.getLanguageConnection().getTransactionExecute();
            dropTableStatistics(tds,dd,tc);
            TxnView txn = ((SpliceTransactionManager) tc).getRawTransaction().getActiveStateTxn();

            HashMap<Long,Pair<String,String>> display = new HashMap<>();
            ArrayList<StatisticsOperation> ops = new ArrayList<>();
            createCollectStatisticsOperationsForTable(conn, txn, schema, tableDesc, useSample, samplePercent, mergeStats, display, ops);

            IteratorNoPutResultSet resultsToWrap = wrapResults(
                    conn,
                    displayTableStatistics(ops, mergeStats, dd, tc, display),
                    COLLECTED_STATS_OUTPUT_COLUMNS);
            ddlNotification(tc, tds);
            outputResults[0] = new EmbedResultSet40(conn, resultsToWrap, false, null, true);
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        } catch (ExecutionException e) {
            throw PublicAPI.wrapStandardException(Exceptions.parseException(e.getCause()));
        }
    }

    private static void createCollectStatisticsOperationsForTable(EmbedConnection conn,
                                                                  TxnView txn,
                                                                  String schemaName,
                                                                  TableDescriptor td,
                                                                  boolean useSample,
                                                                  double samplePercent,
                                                                  boolean mergeStats,
                                                                  HashMap<Long,Pair<String,String>> display,
                                                                  ArrayList<StatisticsOperation> ops)
            throws StandardException, ExecutionException
    {
        display.put(td.getHeapConglomerateId(), Pair.newPair(schemaName, td.getName()));
        ops.add(createCollectTableStatisticsOperation(td, useSample, samplePercent/100, mergeStats, txn, conn));

        List<ConglomerateDescriptor> cds = td.getConglomerateDescriptorList();
        if (cds != null) {
            for (ConglomerateDescriptor cd : cds) {
                IndexRowGenerator irg = cd.getIndexDescriptor();
                if (irg != null && irg.isOnExpression()) {
                    display.put(cd.getConglomerateNumber(), Pair.newPair(schemaName, cd.getConglomerateName()));
                    ops.add(createCollectExprIndexStatisticsOperation(cd, td.getVersion(), useSample, samplePercent/100, mergeStats, txn, conn));
                }
            }
        }
    }

    private static StatisticsOperation createCollectTableStatisticsOperation(TableDescriptor table,
                                                                             boolean useSample,
                                                                             double sampleFraction,
                                                                             boolean mergeStats,
                                                                             TxnView txn,
                                                                             EmbedConnection conn) throws StandardException {
        long heapConglomerateId = table.getHeapConglomerateId();
        Activation activation = conn.getLanguageConnection().getLastActivation();
        DistributedDataSetProcessor dsp = EngineDriver.driver().processorFactory().distributedProcessor();

        ScanSetBuilder ssb = dsp.newScanSet(null,Long.toString(heapConglomerateId));
        ssb.tableVersion(table.getVersion());
        ScanSetBuilder scanSetBuilder = createTableScanner(ssb,conn,table,txn,mergeStats);
        String scope = getScopeName(table);
        // no sample stats support on mem platform
        if (dsp.getType() != DataSetProcessor.Type.SPARK) {
            useSample = false;
            sampleFraction = 0.0d;
        }
        List<ColumnDescriptor> colsToCollect = getCollectedColumns(conn, table);
        DataTypeDescriptor[] dtds = new DataTypeDescriptor[colsToCollect.size()];
        int index = 0;
        for (ColumnDescriptor descriptor : colsToCollect ) {
            dtds[index++] = descriptor.getType();
        }
        StatisticsOperation op = new StatisticsOperation(scanSetBuilder,useSample,sampleFraction,mergeStats,scope,activation,dtds);
        return op;
    }

    private static StatisticsOperation createCollectExprIndexStatisticsOperation(ConglomerateDescriptor cd,
                                                                                 String tableVersion,
                                                                                 boolean useSample,
                                                                                 double sampleFraction,
                                                                                 boolean mergeStats,
                                                                                 TxnView txn,
                                                                                 EmbedConnection conn)
            throws StandardException
    {
        long congId = cd.getConglomerateNumber();
        Activation activation = conn.getLanguageConnection().getLastActivation();
        DistributedDataSetProcessor dsp = EngineDriver.driver().processorFactory().distributedProcessor();

        ScanSetBuilder ssb = dsp.newScanSet(null,Long.toString(congId));
        ssb.tableVersion(tableVersion);
        ScanSetBuilder scanSetBuilder = createExprIndexScanner(ssb,conn,cd,tableVersion,txn,mergeStats);
        String scope = getScopeName(cd.getConglomerateName());
        // no sample stats support on mem platform
        if (dsp.getType() != DataSetProcessor.Type.SPARK) {
            useSample = false;
            sampleFraction = 0.0d;
        }
        DataTypeDescriptor[] dtds = cd.getIndexDescriptor().getIndexColumnTypes();
        return new StatisticsOperation(scanSetBuilder,useSample,sampleFraction,mergeStats,scope,activation,dtds);
    }

    private static String getScopeName(TableDescriptor td) {
        return getScopeName(td.getName());
    }

    private static String getScopeName(String name) {
        return String.format(OperationContext.Scope.COLLECT_STATS.displayName(), name);
    }

    private static DataScan createScan (TxnView txn) {
        DataScan scan=SIDriver.driver().getOperationFactory().newDataScan(txn);
        scan.returnAllVersions(); //make sure that we read all versions of the data
        return scan.startKey(new byte[0]).stopKey(new byte[0]);
    }

    public static int[] getFormatIds(EmbedConnection conn, long columnStatsConglomId) throws StandardException{
        TransactionController transactionExecute = conn.getLanguageConnection().getTransactionExecute();
        SpliceConglomerate conglomerate = (SpliceConglomerate) ((SpliceTransactionManager) transactionExecute)
                .findConglomerate(columnStatsConglomId);
        return conglomerate.getFormat_ids();
    }

    private static ScanSetBuilder createTableScanner(ScanSetBuilder builder,
                                                     EmbedConnection conn,
                                                     TableDescriptor table,
                                                     TxnView txn, boolean mergeStats) throws StandardException{

        List<ColumnDescriptor> colsToCollect = getCollectedColumns(conn, table);
        ExecRow row = new ValueRow(colsToCollect.size());
        BitSet accessedColumns = new BitSet(table.getMaxStorageColumnID());
        int outputCol = 0;
        int[] columnPositionMap = new int[table.getNumberOfColumns()];
        Arrays.fill(columnPositionMap, -1);
        int[] allColumnLengths = new int[table.getMaxStorageColumnID()];
        for (ColumnDescriptor descriptor : colsToCollect) {
            accessedColumns.set(descriptor.getStoragePosition() - 1);
            row.setColumn(outputCol + 1, descriptor.getType().getNull());
            columnPositionMap[outputCol] = descriptor.getPosition();
            outputCol++;
            allColumnLengths[descriptor.getPosition() - 1] = descriptor.getType().getMaximumWidth();
        }

        return setScanSetBuilder(
                builder,
                conn,
                table,
                table.getHeapConglomerateId(),
                row,
                accessedColumns,
                allColumnLengths,
                columnPositionMap,
                table.getVersion(),
                txn,
                mergeStats);
    }

    private static ScanSetBuilder createExprIndexScanner(ScanSetBuilder builder,
                                                         EmbedConnection conn,
                                                         ConglomerateDescriptor index,
                                                         String tableVersion,
                                                         TxnView txn, boolean mergeStats)
            throws StandardException
    {
        IndexRowGenerator irg = index.getIndexDescriptor();
        DataTypeDescriptor[] indexColumnTypes = irg.getIndexColumnTypes();
        int numColumns = indexColumnTypes.length;

        ExecRow row = new ValueRow(numColumns);
        BitSet accessedColumns = new BitSet(numColumns);
        int[] columnPositionMap = new int[numColumns];
        int[] allColumnLengths = new int[numColumns];
        for (int i = 0; i < numColumns; i++) {
            accessedColumns.set(i);
            row.setColumn(i + 1, indexColumnTypes[i].getNull());
            columnPositionMap[i] = i + 1;
            allColumnLengths[i] = indexColumnTypes[i].getMaximumWidth();
        }

        return setScanSetBuilder(
                builder,
                conn,
                null,
                index.getConglomerateNumber(),
                row,
                accessedColumns,
                allColumnLengths,
                columnPositionMap,
                tableVersion,
                txn,
                mergeStats);
    }

    private static ScanSetBuilder setScanSetBuilder(ScanSetBuilder builder,
                                                    EmbedConnection conn,
                                                    TableDescriptor table,
                                                    long conglomId,
                                                    ExecRow templateRow,
                                                    BitSet accessedColumns,
                                                    int[] allColumnLengths,
                                                    int[] columnPositionMap,
                                                    String tableVersion,
                                                    TxnView txn,
                                                    boolean mergeStats)
            throws StandardException
    {
        int numColumns = templateRow.nColumns();
        int[] rowDecodingMap = new int[accessedColumns.length()];
        int[] fieldLengths = new int[accessedColumns.length()];
        Arrays.fill(rowDecodingMap, -1);
        int outputCol = 0;
        for (int i = accessedColumns.nextSetBit(0); i >= 0; i = accessedColumns.nextSetBit(i + 1)) {
            rowDecodingMap[i] = outputCol;
            fieldLengths[outputCol] = allColumnLengths[i];
            outputCol++;
        }
        TransactionController transactionExecute = conn.getLanguageConnection().getTransactionExecute();
        SpliceConglomerate conglomerate = (SpliceConglomerate) ((SpliceTransactionManager) transactionExecute)
                .findConglomerate(conglomId);
        boolean[] keyColumnSortOrder = conglomerate.getAscDescInfo();
        int[] keyColumnEncodingOrder = conglomerate.getColumnOrdering();
        int[] formatIds = conglomerate.getFormat_ids();
        int[] keyColumnTypes = null;
        int[] keyDecodingMap = null;
        FormatableBitSet collectedKeyColumns = null;
        if (keyColumnEncodingOrder != null) {
            keyColumnTypes = new int[keyColumnEncodingOrder.length];
            keyDecodingMap = new int[keyColumnEncodingOrder.length];
            Arrays.fill(keyDecodingMap, -1);
            collectedKeyColumns = new FormatableBitSet(numColumns);
            for (int i = 0; i < keyColumnEncodingOrder.length; i++) {
                int keyColumn = keyColumnEncodingOrder[i];
                keyColumnTypes[i] = formatIds[keyColumn];
                if (accessedColumns.get(keyColumn)) {
                    collectedKeyColumns.set(i);
                    keyDecodingMap[i] = rowDecodingMap[keyColumn];
                    rowDecodingMap[keyColumn] = -1;
                }
            }
        }
        DataScan scan = createScan(txn);
        ScanSetBuilder result = builder.transaction(txn)
                .metricFactory(Metrics.basicMetricFactory())
                .template(templateRow)
                .scan(scan)
                .rowDecodingMap(rowDecodingMap)
                .keyColumnEncodingOrder(keyColumnEncodingOrder)
                .keyColumnSortOrder(keyColumnSortOrder)
                .keyColumnTypes(keyColumnTypes)
                .keyDecodingMap(keyDecodingMap)
                .baseTableConglomId(conglomId)
                .accessedKeyColumns(collectedKeyColumns)
                .tableVersion(tableVersion)
                .fieldLengths(fieldLengths)
                .columnPositionMap(columnPositionMap)
                .oneSplitPerRegion(!mergeStats);

        if (table != null) {
            result = result.storedAs(table.getStoredAs())
                    .location(table.getLocation())
                    .compression(table.getCompression())
                    .delimited(table.getDelimited())
                    .lines(table.getLines())
                    .escaped(table.getEscaped())
                    .partitionByColumns(table.getPartitionBy());
        }
        return result;
    }

    private static IteratorNoPutResultSet wrapResults(EmbedConnection conn, Iterable<ExecRow> rows, ResultColumnDescriptor[] columnDescriptors) throws
        StandardException {
        Activation lastActivation = conn.getLanguageConnection().getLastActivation();
        IteratorNoPutResultSet resultsToWrap = new IteratorNoPutResultSet(rows, columnDescriptors,
                                                                          lastActivation);
        resultsToWrap.openCore();
        return resultsToWrap;
    }

    private static ExecRow buildOutputTemplateRow() throws StandardException {
        ExecRow outputRow = new ValueRow(COLLECTED_STATS_OUTPUT_COLUMNS.length);
        DataValueDescriptor[] dvds = new DataValueDescriptor[COLLECTED_STATS_OUTPUT_COLUMNS.length];
        for (int i = 0; i < dvds.length; i++) {
            dvds[i] = COLLECTED_STATS_OUTPUT_COLUMNS[i].getType().getNull();
        }
        outputRow.setRowArray(dvds);
        return outputRow;
    }

    public static List<TableDescriptor> getAllTableDescriptors(SchemaDescriptor sd, EmbedConnection conn) throws
        SQLException {
        try (PreparedStatement statement = conn.prepareStatement(TABLEID_FROM_SCHEMA)) {
            statement.setString(1, sd.getUUID().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                DataDictionary dd = conn.getLanguageConnection().getDataDictionary();
                UUIDFactory uuidFactory = dd.getUUIDFactory();
                List<TableDescriptor> tds = new LinkedList<>();
                while (resultSet.next()) {
                    com.splicemachine.db.catalog.UUID tableId = uuidFactory.recreateUUID(resultSet.getString(1));
                    TableDescriptor tableDescriptor = dd.getTableDescriptor(tableId, null);
                    /*
                     * We need to filter out views from the TableDescriptor list. Views
                     * are special cases where the number of conglomerate descriptors is 0. We
                     * don't collect statistics for those views
                     */

                    if (tableDescriptor != null && !tableDescriptor.getConglomerateDescriptorList().isEmpty()) {
                        tds.add(tableDescriptor);
                    }
                }
                return tds;
            }
        } catch (StandardException e) {
            throw PublicAPI.wrapStandardException(e);
        }
    }

    private static final Comparator<ColumnDescriptor> order = new Comparator<ColumnDescriptor>() {
        @Override
        public int compare(ColumnDescriptor o1, ColumnDescriptor o2) {
            return o1.getPosition() - o2.getPosition();
        }
    };

    private static List<ColumnDescriptor> getCollectedColumns(EmbedConnection conn, TableDescriptor td) throws StandardException {
        ColumnDescriptorList columnDescriptorList = td.getColumnDescriptorList();
        List<ColumnDescriptor> toCollect = new ArrayList<>(columnDescriptorList.size());

        /* check the default collect stats behavior, whether to collect stats on all columns or just index columns */
        String collectStatsMode = PropertyUtil.getServiceProperty(conn.getLanguageConnection().getTransactionCompile(),
                Property.COLLECT_INDEX_STATS_ONLY);
        boolean collectIndexStatsOnly = Boolean.valueOf(collectStatsMode);

        boolean[] indexColumns = new boolean[columnDescriptorList.size()];

        IndexLister indexLister = td.getIndexLister();
        if (collectIndexStatsOnly) {
            // get all other index columns
            if (indexLister != null) {
                IndexRowGenerator[] indexRowGenerators = indexLister.getIndexRowGenerators();
                for (IndexRowGenerator irg : indexRowGenerators) {
                    int[] keyColumns = irg.getIndexDescriptor().baseColumnPositions();
                    for (int keyColumn : keyColumns) {
                        indexColumns[keyColumn - 1] = true;
                    }
                }
            }
        }


        /*
         * Get all the enabled statistics columns
         */
        for (ColumnDescriptor columnDescriptor : columnDescriptorList) {
            if (!collectIndexStatsOnly || indexColumns[columnDescriptor.getPosition()-1]) {
                if (columnDescriptor.collectStatistics())
                    toCollect.add(columnDescriptor);
            }
        }
        /*
         * Add in any disabled key columns.
         *
         * We want to collect for all key columns always, because they are very important when
         * comparing index columns. By default, we turn them on when possible, but even if they are disabled
         * for some reason, we should still collect them. Of course, we should also not be able to disable
         * keyed columns, but that's a to-do for now.
         */
        if (indexLister != null) {
            IndexRowGenerator[] distinctIndexRowGenerators = indexLister.getDistinctIndexRowGenerators();
            for (IndexRowGenerator irg : distinctIndexRowGenerators) {
                int[] keyColumns = irg.getIndexDescriptor().baseColumnPositions();
                for (int keyColumn : keyColumns) {
                    for (ColumnDescriptor cd : columnDescriptorList) {
                        if (cd.getPosition() == keyColumn) {
                            if (!toCollect.contains(cd)) {
                                toCollect.add(cd);
                            }
                            break;
                        }
                    }
                }
            }
        }

        // we should always include primary key if it exists
        ReferencedKeyConstraintDescriptor keyDescriptor = td.getPrimaryKey();
        if (keyDescriptor != null) {
            int[] pkColumns = keyDescriptor.getReferencedColumns();
            for (int keyColumn : pkColumns) {
                for (ColumnDescriptor cd : columnDescriptorList) {
                    if (cd.getPosition() == keyColumn) {
                        if (!toCollect.contains(cd)) {
                            toCollect.add(cd);
                        }
                        break;
                    }
                }
            }
        }

        Collections.sort(toCollect, order); //sort the columns into adjacent position order
        return toCollect;
    }

    private static void ensureNotKeyed(ColumnDescriptor descriptor, TableDescriptor td) throws StandardException {
        ConglomerateDescriptor heapConglom = td.getConglomerateDescriptor(td.getHeapConglomerateId());
        IndexRowGenerator pkDescriptor = heapConglom.getIndexDescriptor();
        if (pkDescriptor != null && pkDescriptor.getIndexDescriptor() != null) {
            for (int pkCol : pkDescriptor.baseColumnPositions()) {
                if (pkCol == descriptor.getPosition()) {
                    throw ErrorState.LANG_DISABLE_STATS_FOR_KEYED_COLUMN.newException(descriptor.getColumnName());
                }
            }
        }
        IndexLister indexLister = td.getIndexLister();
        if (indexLister != null) {
            for (IndexRowGenerator irg : indexLister.getIndexRowGenerators()) {
                if (irg.getIndexDescriptor() == null) continue;
                for (int col : irg.baseColumnPositions()) {
                    if (col == descriptor.getPosition())
                        throw ErrorState.LANG_DISABLE_STATS_FOR_KEYED_COLUMN.newException(descriptor.getColumnName());
                }
            }
        }
    }

    public static ExecRow generateRowFromStats(long conglomId,
                                               String partitionId,
                                               long rowCount,
                                               long partitionSize,
                                               int meanRowWidth,
                                               long numberOfPartitions,
                                               int statsType,
                                               double sampleFraction) throws StandardException {
        ExecRow row = new ValueRow(SYSTABLESTATISTICSRowFactory.SYSTABLESTATISTICS_COLUMN_COUNT);
        row.setColumn(SYSTABLESTATISTICSRowFactory.CONGLOMID,new SQLLongint(conglomId));
        row.setColumn(SYSTABLESTATISTICSRowFactory.PARTITIONID,new SQLVarchar(partitionId));
        row.setColumn(SYSTABLESTATISTICSRowFactory.TIMESTAMP,new SQLTimestamp(new Timestamp(System.currentTimeMillis())));
        row.setColumn(SYSTABLESTATISTICSRowFactory.STALENESS,new SQLBoolean(false));
        row.setColumn(SYSTABLESTATISTICSRowFactory.INPROGRESS,new SQLBoolean(false));
        row.setColumn(SYSTABLESTATISTICSRowFactory.ROWCOUNT,new SQLLongint(rowCount));
        row.setColumn(SYSTABLESTATISTICSRowFactory.PARTITION_SIZE,new SQLLongint(partitionSize));
        row.setColumn(SYSTABLESTATISTICSRowFactory.MEANROWWIDTH,new SQLInteger(meanRowWidth));
        row.setColumn(SYSTABLESTATISTICSRowFactory.NUMBEROFPARTITIONS,new SQLLongint(numberOfPartitions));
        row.setColumn(SYSTABLESTATISTICSRowFactory.STATSTYPE,new SQLInteger(statsType));
        row.setColumn(SYSTABLESTATISTICSRowFactory.SAMPLEFRACTION, new SQLDouble(sampleFraction));
        return row;
    }

    public static ExecRow generateRowFromStats(long conglomId,
                                               String partitionId,
                                               long timestamp,
                                               boolean isStale,
                                               boolean inProgress,
                                               long rowCount,
                                               long partitionSize,
                                               int meanRowWidth,
                                               long numberOfPartitions,
                                               int statsType,
                                               double sampleFraction) throws StandardException {
        ExecRow row = new ValueRow(SYSTABLESTATISTICSRowFactory.SYSTABLESTATISTICS_COLUMN_COUNT);
        row.setColumn(SYSTABLESTATISTICSRowFactory.CONGLOMID,new SQLLongint(conglomId));
        row.setColumn(SYSTABLESTATISTICSRowFactory.PARTITIONID,new SQLVarchar(partitionId));
        row.setColumn(SYSTABLESTATISTICSRowFactory.TIMESTAMP,new SQLTimestamp(new Timestamp(timestamp)));
        row.setColumn(SYSTABLESTATISTICSRowFactory.STALENESS,new SQLBoolean(isStale));
        row.setColumn(SYSTABLESTATISTICSRowFactory.INPROGRESS,new SQLBoolean(inProgress));
        row.setColumn(SYSTABLESTATISTICSRowFactory.ROWCOUNT,new SQLLongint(rowCount));
        row.setColumn(SYSTABLESTATISTICSRowFactory.PARTITION_SIZE,new SQLLongint(partitionSize));
        row.setColumn(SYSTABLESTATISTICSRowFactory.MEANROWWIDTH,new SQLInteger(meanRowWidth));
        row.setColumn(SYSTABLESTATISTICSRowFactory.NUMBEROFPARTITIONS,new SQLLongint(numberOfPartitions));
        row.setColumn(SYSTABLESTATISTICSRowFactory.STATSTYPE,new SQLInteger(statsType));
        row.setColumn(SYSTABLESTATISTICSRowFactory.SAMPLEFRACTION, new SQLDouble(sampleFraction));
        return row;
    }

    public static ExecRow generateRowFromStats(long conglomId, String regionId, int columnId, ItemStatistics columnStatistics) throws StandardException {
        ExecRow row = new ValueRow(SYSCOLUMNSTATISTICSRowFactory.SYSCOLUMNSTATISTICS_COLUMN_COUNT);
        row.setColumn(SYSCOLUMNSTATISTICSRowFactory.CONGLOMID,new SQLLongint(conglomId));
        row.setColumn(SYSCOLUMNSTATISTICSRowFactory.PARTITIONID,new SQLVarchar(regionId));
        row.setColumn(SYSCOLUMNSTATISTICSRowFactory.COLUMNID,new SQLInteger(columnId));
        row.setColumn(SYSCOLUMNSTATISTICSRowFactory.DATA, new UserType(columnStatistics));
        return row;
    }

    public static ExecRow generateOutputRowForColumnStats(String schemaName,
                                                          String tableName,
                                                          String columnName,
                                                          String partitionName,
                                                          long nullCount,
                                                          long totalCount,
                                                          long cardinality) throws StandardException {
        ExecRow row = new ValueRow(7);
        row.setColumn(1,new SQLVarchar(schemaName));
        row.setColumn(2,new SQLVarchar(tableName));
        row.setColumn(3,new SQLVarchar(columnName));
        row.setColumn(4,new SQLVarchar(partitionName));
        row.setColumn(5,new SQLLongint(nullCount));
        row.setColumn(6,new SQLLongint(totalCount));
        row.setColumn(7,new SQLLongint(cardinality));
        return row;
    }

    public static ExecRow generateOutputRow(String schemaName, String tableName, ExecRow partitionRow, HashSet<Integer> skippedColIds) throws StandardException {
        ExecRow row = new ValueRow(9);
        row.setColumn(1,new SQLVarchar(schemaName));
        row.setColumn(2,new SQLVarchar(tableName));
        row.setColumn(3,partitionRow.getColumn(SYSTABLESTATISTICSRowFactory.PARTITIONID));
        row.setColumn(4,partitionRow.getColumn(SYSTABLESTATISTICSRowFactory.ROWCOUNT));
        row.setColumn(5,partitionRow.getColumn(SYSTABLESTATISTICSRowFactory.PARTITION_SIZE));
        row.setColumn(6,partitionRow.getColumn(SYSTABLESTATISTICSRowFactory.NUMBEROFPARTITIONS));
        row.setColumn(7,partitionRow.getColumn(SYSTABLESTATISTICSRowFactory.STATSTYPE));
        row.setColumn(8,partitionRow.getColumn(SYSTABLESTATISTICSRowFactory.SAMPLEFRACTION));

        int cutOffLimit = 5;
        String skippedColIdsStr = skippedColIds.stream().limit(cutOffLimit).map(Object::toString).collect(Collectors.joining(", "));
        if (skippedColIds.size() > cutOffLimit)
            skippedColIdsStr += " ...";
        row.setColumn(9,new SQLVarchar(skippedColIdsStr));
        return row;
    }


    public static Iterable displayTableStatistics(ArrayList<StatisticsOperation> collectOps,
                                                  boolean mergeStats,
                                                  final DataDictionary dataDictionary,
                                                  final TransactionController tc,
                                                  final HashMap<Long, Pair<String, String>> displayPair) throws StandardException {

        List<CreateStatisticTask> tasks = new ArrayList<>();
        List statistics = new ArrayList();

        for (int i = 0; i < collectOps.size(); i++) {
            tasks.add(new CreateStatisticTask(collectOps.get(i), mergeStats, dataDictionary, tc, displayPair));
        }
        List<Future<Iterable>> results = null;
        try {
            if (mergeStats) {
                tc.setSavePoint("statistics", null);
                tc.elevate("statistics");
            }
            results = SIDriver.driver().getExecutorService().invokeAll(tasks);
            for (Future<Iterable> result : results) {
                List statisticResult = (List) StreamSupport.stream(result.get().spliterator(), false).collect(Collectors.toList());
                statistics.addAll(statisticResult);
            }
            if (mergeStats) {
                tc.releaseSavePoint("statistics", null);
            }
        } catch (Exception e) {
            if (results != null) {
                for (Future<Iterable> f : results) {
                    f.cancel(true);
                }
            }
            throw new RuntimeException(e);
        }
        return statistics;
    }

    private static long getNumOfPartitions(ConglomerateDescriptor cd) throws StandardException {
        String tableId = Long.toString(cd.getConglomerateNumber());
        byte[] table = Bytes.toBytes(tableId);
        try (Partition root = SIDriver.driver().getTableFactory().getTable(table)) {
            return root != null ? root.subPartitions(true).size() : 1;
        } catch (Exception ioe) {
            throw StandardException.plainWrapException(ioe);
        }
    }

    private static void dropTableStatistics(TableDescriptor td, DataDictionary dd, TransactionController tc) throws StandardException {
        for (ConglomerateDescriptor cd: td.getConglomerateDescriptorList()) {
            if (LOG.isDebugEnabled())
                SpliceLogUtils.debug(LOG,"Dropping conglomerate statistics [%d]",cd.getConglomerateNumber());
            dd.deletePartitionStatistics(cd.getConglomerateNumber(),tc);
        }
    }

    private static void dropTableStatistics(List<TableDescriptor> tds, DataDictionary dd, TransactionController tc) throws StandardException {

        for (TableDescriptor td: tds) {
            if (LOG.isDebugEnabled())
                SpliceLogUtils.debug(LOG,"Dropping Table statistics [%s]",td.getName());
            dropTableStatistics(td,dd,tc);
        }
    }

    private static long getRowCountFromTableStats(long conglomerateId,
                                                  DataDictionary dd,
                                                  TransactionController tc) throws StandardException {
        long totalCount = 0;
        List<PartitionStatisticsDescriptor> partitionStatsDescriptors = dd.getPartitionStatistics(conglomerateId, tc);

        if (partitionStatsDescriptors.isEmpty())
           return -1;

        double sampleFraction = 0.0d;
        int statsType = partitionStatsDescriptors.get(0).getStatsType();
        boolean isSampleStats = statsType == SYSTABLESTATISTICSRowFactory.SAMPLE_NONMERGED_STATS || statsType == SYSTABLESTATISTICSRowFactory.SAMPLE_MERGED_STATS;
        if (isSampleStats)
            sampleFraction = partitionStatsDescriptors.get(0).getSampleFraction();

        for (PartitionStatisticsDescriptor item: partitionStatsDescriptors) {
            totalCount += item.getRowCount();
        }

        if (isSampleStats)
            totalCount = (long)((double)totalCount/sampleFraction);

        return totalCount;
    }

    private static void dropColumnStatistics(long conglomerateId,
                                             int columnId,
                                             DataDictionary dd,
                                             TransactionController tc) throws StandardException {
        dd.deleteColumnStatisticsByColumnId(conglomerateId, columnId, tc);

    }

    private static class CreateStatisticTask implements Callable<Iterable> {

        private StatisticsOperation statisticsOperation;
        private boolean mergeStats;
        private final DataDictionary dataDictionary;
        private final TransactionController tc;
        private final HashMap<Long, Pair<String, String>> displayPair;
        private final ContextManager parent;

        private CreateStatisticTask(StatisticsOperation statisticsOperation, boolean mergeStats, final DataDictionary dataDictionary, final TransactionController tc, final HashMap<Long, Pair<String, String>> displayPair) {
            this.statisticsOperation = statisticsOperation;
            this.mergeStats = mergeStats;
            this.dataDictionary = dataDictionary;
            this.tc = tc;
            this.displayPair = displayPair;
            this.parent = ContextService.getService().getCurrentContextManager();
        }

        @Override
        public Iterable call() throws Exception {
            ContextManager cm = ContextService.getService().newContextManager(parent);
            ContextService.getService().setCurrentContextManager(cm);
            try {
                statisticsOperation.openCore();
                if (mergeStats) {
                    return getMergedStatistic(dataDictionary, tc, displayPair, statisticsOperation);
                } else {
                    return getStatistic(dataDictionary, tc, displayPair, statisticsOperation);
                }
            }
            finally {
                ContextService.getService().resetCurrentContextManager(cm);
                ContextService.getService().removeContextManager(cm);
            }
        }
    }

    private static Iterable getMergedStatistic(final DataDictionary dataDictionary, final TransactionController tc, final HashMap<Long, Pair<String, String>> displayPair, StatisticsOperation input) {
        final Iterator iterator = new Iterator<ExecRow>() {
            private ExecRow nextRow;
            private boolean fetched = false;
            // data structures to accumulate the partition stats
            private long conglomId = 0;
            private long rowCount = 0L;
            private long totalSize = 0;
            private int avgRowWidth = 0;
            private long numberOfPartitions = 0;
            private int statsType = SYSTABLESTATISTICSRowFactory.REGULAR_NONMERGED_STATS;
            private double sampleFraction = 0.0d;
            private final HashSet<Integer> skippedColIds = new HashSet<>();

            @Override
            @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "SpotBugs is confused, we rethrow the exception")
            public boolean hasNext() {
                try {
                    if (!fetched) {
                        nextRow = input.getNextRowCore();
                        while (nextRow != null) {
                            fetched = true;
                            if (nextRow.nColumns() == 2) {
                                int columnId = nextRow.getColumn(1).getInt();
                                ByteArrayInputStream bais = new ByteArrayInputStream(nextRow.getColumn(2).getBytes());
                                ObjectInputStream ois = new ObjectInputStream(bais);
                                // compose the entry for a given column
                                                ExecRow statsRow = StatisticsProcedures.generateRowFromStats(conglomId, "-All-", columnId, (ColumnStatisticsImpl) ois.readObject());
                                try {
                                    dataDictionary.addColumnStatistics(statsRow, tc);
                                } catch (StandardException e) {
                                    // DB-9890 Skip a column if its statistics object doesn't fit into HBase cell.
                                                    if (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("KeyValue size too large")) {
                                        SpliceLogUtils.warn(LOG, "Statistics object of [ConglomID=%d, ColumnID=%d] exceeds max KeyValue size. Try increase hbase.client.keyvalue.maxsize.",
                                                conglomId, columnId);
                                        skippedColIds.add(columnId);
                                    } else {
                                        throw e;
                                    }
                                } finally {
                                    bais.close();
                                }
                            } else {
                                // process tablestats row
                                conglomId = nextRow.getColumn(SYSCOLUMNSTATISTICSRowFactory.CONGLOMID).getLong();
                                long partitionRowCount = nextRow.getColumn(SYSTABLESTATISTICSRowFactory.ROWCOUNT).getLong();
                                rowCount = partitionRowCount;
                                totalSize = nextRow.getColumn(SYSTABLESTATISTICSRowFactory.PARTITION_SIZE).getLong();
                                avgRowWidth = nextRow.getColumn(SYSTABLESTATISTICSRowFactory.MEANROWWIDTH).getInt();
                                // while collecting merged stats, we may use more splits for one region/partition, so the numberOfPartitions here is really the number of splits, not ncessarily the number of regions
                                numberOfPartitions = nextRow.getColumn(SYSTABLESTATISTICSRowFactory.NUMBEROFPARTITIONS).getLong();
                                statsType = nextRow.getColumn(SYSTABLESTATISTICSRowFactory.STATSTYPE).getInt();
                                sampleFraction = nextRow.getColumn(SYSTABLESTATISTICSRowFactory.SAMPLEFRACTION).getDouble();
                            }
                            nextRow = input.getNextRowCore();
                        }
                    }
                    return fetched;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public ExecRow next() {
                try {
                    fetched = false;
                    // insert rows to dictionary tables, and return
                    ExecRow statsRow;
                    //change statsType to 2: merged full stats or 3: merged sample stats
                    if (statsType == SYSTABLESTATISTICSRowFactory.REGULAR_NONMERGED_STATS)
                        statsType = SYSTABLESTATISTICSRowFactory.REGULAR_MERGED_STATS;
                    else if (statsType == SYSTABLESTATISTICSRowFactory.SAMPLE_NONMERGED_STATS)
                        statsType = SYSTABLESTATISTICSRowFactory.SAMPLE_MERGED_STATS;
                    Pair<String, String> pair = displayPair.get(conglomId);
                                    SchemaDescriptor sd = dataDictionary.getSchemaDescriptor(null, pair.getFirst(), tc, true);
                    TableDescriptor td = dataDictionary.getTableDescriptor(pair.getSecond(), sd, tc);
                    ConglomerateDescriptor cd = null;
                    if (td == null) {
                        cd = dataDictionary.getConglomerateDescriptor(conglomId);
                        assert cd.getConglomerateName().equals(pair.getSecond());
                    }
                    // instead of using the numberOfPartitions which is really the number of splits for
                    // merged stats, directly fetch the number of regions
                    long numOfRegions = numberOfPartitions;
                    if (td != null) {
                        if (!td.isExternal()) {
                            numOfRegions = getNumOfPartitions(td.getBaseConglomerateDescriptor());
                        }
                    } else {
                        numOfRegions = getNumOfPartitions(cd);
                    }
                                    statsRow = StatisticsProcedures.generateRowFromStats(conglomId, "-All-", rowCount, totalSize, avgRowWidth, numOfRegions, statsType, sampleFraction);
                    dataDictionary.addTableStatistics(statsRow, tc);

                    return generateOutputRow(pair.getFirst(), pair.getSecond(), statsRow, skippedColIds);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Iterable<Integer> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());

    }

    private static Iterable getStatistic(final DataDictionary dataDictionary, final TransactionController tc, final HashMap<Long, Pair<String, String>> displayPair, StatisticsOperation input) {
        final Iterator iterator = new Iterator<ExecRow>() {
            private ExecRow nextRow;
            private boolean fetched = false;
            private final HashSet<Integer> skippedColIds = new HashSet<>();
            @Override
            public boolean hasNext() {
                try {
                    if (!fetched) {
                        nextRow = input.getNextRowCore();
                        while (nextRow != null && nextRow.nColumns() == SYSCOLUMNSTATISTICSRowFactory.SYSCOLUMNSTATISTICS_COLUMN_COUNT) {
                            try {
                                dataDictionary.addColumnStatistics(nextRow, tc);
                            } catch (StandardException e) {
                                // DB-9890 Skip a column if its statistics object doesn't fit into HBase cell.
                                if (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("KeyValue size too large")) {
                                    int columnId = nextRow.getColumn(SYSCOLUMNSTATISTICSRowFactory.COLUMNID).getInt();
                                    SpliceLogUtils.warn(LOG, "Statistics object of [ConglomID=%d, ColumnID=%d] exceeds max KeyValue size. Try increase hbase.client.keyvalue.maxsize.",
                                            nextRow.getColumn(SYSCOLUMNSTATISTICSRowFactory.CONGLOMID).getInt(),
                                            columnId);
                                    skippedColIds.add(columnId);
                                } else {
                                    throw e;
                                }
                            }
                            nextRow = input.getNextRowCore();
                        }
                        fetched = true;
                    }
                    return nextRow != null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public ExecRow next() {
                try {
                    fetched = false;
                    dataDictionary.addTableStatistics(nextRow, tc);
                    Pair<String,String> pair = displayPair.get(nextRow.getColumn(SYSTABLESTATISTICSRowFactory.CONGLOMID).getLong());
                    return generateOutputRow(pair.getFirst(),pair.getSecond(),nextRow,skippedColIds);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Iterable<Integer> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }
}
