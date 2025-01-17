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

import com.splicemachine.derby.test.framework.*;
import com.splicemachine.homeless.TestUtils;
import com.splicemachine.test.SerialTest;
import com.splicemachine.test_tools.TableCreator;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static com.splicemachine.test_tools.Rows.row;
import static com.splicemachine.test_tools.Rows.rows;

/**
 * Created by jyuan on 10/7/14.
 */
@Category(SerialTest.class) // Not run in parallel since it is changing global log level
public class ExplainPlanIT extends SpliceUnitTest  {

    public static final String CLASS_NAME = ExplainPlanIT.class.getSimpleName().toUpperCase();
    protected static final SpliceWatcher spliceClassWatcher = new SpliceWatcher(CLASS_NAME);
    public static final String TABLE_NAME = "A";
    protected static final SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);

    private static final String tableDef = "(I INT)";
    protected static final SpliceTableWatcher spliceTableWatcher = new SpliceTableWatcher(TABLE_NAME,CLASS_NAME, tableDef);
    private static boolean isMemPlatform = false;

    /// a class to make sure Connection, Statement and ResultSet are correctly closed (spotbugs)
    /// todo(martinrupp) there's a lot of other spotbugs in ITs similar to this, check if we can have sth like a
    /// MultiAutoClose class to do this.
    static class ConnectionStatementWrapper implements AutoCloseable
    {
        public ConnectionStatementWrapper(Connection connection, String schema) throws SQLException {
            this.connection = connection;
            this.connection.setSchema(schema);
            s = connection.createStatement();
        }

        public ResultSet executeQuery(String query) throws SQLException
        {
            if(rs != null && !rs.isClosed() ) rs.close();
            rs = s.executeQuery(query);
            return rs;
        }

        @Override
        public void close() throws Exception {
            if(rs != null && !rs.isClosed() ) rs.close();
            s.close();
            connection.close();
        }

        private Connection connection;
        private Statement s;
        private ResultSet rs;
    }

    public static boolean
    isMemPlatform() throws Exception{
        try (ResultSet rs = spliceClassWatcher.executeQuery("CALL SYSCS_UTIL.SYSCS_IS_MEM_PLATFORM()")) {
            rs.next();
            return ((Boolean)rs.getObject(1));
        }
    }

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
            .around(spliceSchemaWatcher)
            .around(spliceTableWatcher)
            .around(new SpliceDataWatcher(){
                @Override
                protected void starting(Description description){
                    try{
                        PreparedStatement ps=spliceClassWatcher.prepareStatement(format("insert into %s.%s values (?)",CLASS_NAME,TABLE_NAME));
                        for(int i=0;i<10;i++){
                            ps.setInt(1, i);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        ps = spliceClassWatcher.prepareStatement(
                                format("insert into %s.%s select * from %s.%s", CLASS_NAME,TABLE_NAME, CLASS_NAME,TABLE_NAME));
                        for (int i = 0; i < 11; ++i) {
                            ps.execute();
                        }
                        ps = spliceClassWatcher.prepareStatement(format("analyze schema %s", CLASS_NAME));
                        ps.execute();
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }finally{
                        spliceClassWatcher.closeAll();
                    }
                }

            });
    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher(CLASS_NAME);

    @BeforeClass
    public static void createTables() throws Exception {
        isMemPlatform = isMemPlatform();
        Connection conn = spliceClassWatcher.getOrCreateConnection();

        new TableCreator(conn)
                .withCreate("create table t1 (c1 int, c2 int)")
                .create();

        new TableCreator(conn)
                .withCreate("create table t2 (c1 int, c2 int)")
                .create();

        new TableCreator(conn)
                .withCreate("create table t3 (c1 int, c2 int)")
                .create();

        new TableCreator(conn)
                .withCreate("create table t4 (a4 int, b4 int, c4 int, primary key(a4))")
                .withInsert("insert into t4 values(?,?,?)")
                .withRows(rows(
                        row(1,1,1),
                        row(2,2,2),
                        row(3,3,3),
                        row(4,4,4),
                        row(5,5,5),
                        row(6,6,6),
                        row(7,7,7),
                        row(8,8,8),
                        row(9,9,9),
                        row(10,10,10)))
                .create();
        int factor = 10;
        for (int i = 1; i <= 12; i++) {
            spliceClassWatcher.executeUpdate(format("insert into t4 select a4+%d, b4,c4 from t4", factor));
            factor = factor * 2;
        }

        new TableCreator(conn)
                .withCreate("create table t5 (a5 int, b5 int, c5 int, d5 int, e5 int, primary key(a5, b5, c5))")
                .withIndex("create index ix_t5 on t5(b5, c5, d5)")
                .withInsert("insert into t5 values(?,?,?,?,?)")
                .withRows(rows(
                        row(1,1,1,1,1),
                        row(2,2,2,2,2),
                        row(3,3,3,3,3),
                        row(4,4,4,4,4),
                        row(5,5,5,5,5),
                        row(6,6,6,6,6),
                        row(7,7,7,7,7),
                        row(8,8,8,8,8),
                        row(9,9,9,9,9),
                        row(10,10,10,10,10)))
                .create();

        for (int i = 1; i <= 12; i++) {
            spliceClassWatcher.executeUpdate(format("insert into t5 select a5+%d, b5,c5, d5, e5 from t5", factor));
            factor = factor * 2;
        }

        new TableCreator(conn)
                .withCreate("create table t6 (a6 int, b6 int, c6 int)")
                .create();

        new TableCreator(conn)
                .withCreate("create table t7 (a7 int, b7 int, c7 int)")
                .create();
    }

    @Test
    public void testExplainSelect() throws Exception {
        int count = 0;
        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("explain select * from %s", this.getTableReference(TABLE_NAME)))) {

            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }

        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("sparkexplain select * from %s", this.getTableReference(TABLE_NAME)))) {

            count = 0;
            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }

        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("sparkexplain_analyzed select * from %s", this.getTableReference(TABLE_NAME)))) {

            count = 0;
            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }

        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("sparkexplain_logical select * from %s", this.getTableReference(TABLE_NAME)))) {

            count = 0;
            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }

        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("sparkexplain_optimized select * from %s", this.getTableReference(TABLE_NAME)))) {

            count = 0;
            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }
    }

    @Test
    public void testExplainUpdate() throws Exception {
        int count = 0;
        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("explain update %s set i = 0 where i = 1", this.getTableReference(TABLE_NAME)))) {

            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }

        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("sparkexplain update %s set i = 0 where i = 1", this.getTableReference(TABLE_NAME)))) {

            count = 0;
            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }

    }

    @Test
    public void testExplainDelete() throws Exception {
        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("explain delete from %s where i = 1", this.getTableReference(TABLE_NAME)))) {

            int count = 0;
            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }

        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("sparkexplain delete from %s where i = 1", this.getTableReference(TABLE_NAME)))) {

            int count = 0;
            while (rs.next()) {
                ++count;
            }
            Assert.assertTrue(count > 0);
        }
    }

    @Test
    public void testExplainTwice() throws Exception {
        int count1 = 0;
        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("-- some comments %n explain%nupdate %s set i = 0 where i = 1", this.getTableReference(TABLE_NAME)))) {
            while (rs.next()) {
                ++count1;
            }
        }
        int count2 = 0;
        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("-- some comments %n explain%nupdate %s set i = 0 where i = 1", this.getTableReference(TABLE_NAME)))) {
            while (rs.next()) {
                ++count2;
            }
            Assert.assertTrue(count1 == count2);
        }

        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("-- some comments %n sparkexplain%nupdate %s set i = 0 where i = 1", this.getTableReference(TABLE_NAME)))) {
            count1 = 0;
            while (rs.next()) {
                ++count1;
            }
        }
        try (ResultSet rs  = methodWatcher.executeQuery(
                String.format("-- some comments %n sparkexplain%nupdate %s set i = 0 where i = 1", this.getTableReference(TABLE_NAME)))) {
            count2 = 0;
            while (rs.next()) {
                ++count2;
            }
            Assert.assertTrue(count1 == count2);
        }
    }

    @Test
    public void testUseSpark() throws Exception {
        String sql = format("explain select * from %s.%s --SPLICE-PROPERTIES useOLAP=false", CLASS_NAME, TABLE_NAME);
        try (ResultSet rs  = methodWatcher.executeQuery(sql)) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("expect explain plan contains engine=OLTP", rs.getString(1).contains("engine=OLTP"));
        }

        sql = format("explain select * from %s.%s", CLASS_NAME, TABLE_NAME);
        try (ResultSet rs  = methodWatcher.executeQuery(sql)) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("expect explain plan contains engine=OLAP", rs.getString(1).contains("engine=OLAP"));
        }
    }

    @Test
    public void testSparkConnection() throws Exception {
        String url = "jdbc:splice://localhost:1527/splicedb;create=true;user=splice;password=admin;useOLAP=true";
        try( ConnectionStatementWrapper s = new ConnectionStatementWrapper(DriverManager.getConnection(url, new Properties()), CLASS_NAME.toUpperCase()) )
        {
            try (ResultSet rs = s.executeQuery("explain select * from A")) {
                Assert.assertTrue(rs.next());
                Assert.assertTrue("expect explain plan contains useSpark=false", rs.getString(1).contains("engine=OLAP"));
            }
        }
    }

    @Test
    public void testControlConnection() throws Exception {
        String types[] = { "useSpark", "useOLAP" };
        for( String type : types ) {
            String url = "jdbc:splice://localhost:1527/splicedb;create=true;user=splice;password=admin;" + type + "=false";
            try( ConnectionStatementWrapper s = new ConnectionStatementWrapper(
                 DriverManager.getConnection(url, new Properties()), CLASS_NAME.toUpperCase()) )
            {
                try (ResultSet rs = s.executeQuery("explain select * from A")) {
                    Assert.assertTrue(rs.next());
                    Assert.assertTrue("expect explain plan contains useSpark=false", rs.getString(1).contains("engine=OLTP"));
                }
            }
        }
    }

    @Test
    public void testControlQuery() throws Exception {
        String url = "jdbc:splice://localhost:1527/splicedb;user=splice;password=admin";
        try( ConnectionStatementWrapper s = new ConnectionStatementWrapper(DriverManager.getConnection(url, new Properties()), CLASS_NAME.toUpperCase()) )
        {
            try (ResultSet rs = s.executeQuery("explain select * from A --SPLICE-PROPERTIES useSpark=false")) {
                Assert.assertTrue(rs.next());
                Assert.assertTrue("expect explain plan contains useSpark=false", rs.getString(1).contains("engine=OLTP"));
            }
        }
    }

    //DB-5743
    @Test
    public void testPredicatePushDownAfterOJ2IJ() throws Exception {

        String query =
                "explain select count(*) from t1 a\n" +
                "left join t2 b on a.c1=b.c1\n" +
                "left join t2 c on b.c2=c.c2\n" +
                "where a.c2 not in (1, 2, 3) and c.c1 > 0";

        // Make sure predicate on a.c2 is pushed down to the base table scan
        String predicate = "preds=[(A.C2[0:2] <> 1),(A.C2[0:2] <> 2),(A.C2[0:2] <> 3)]";
        try (ResultSet rs  = methodWatcher.executeQuery(query)) {
            while (rs.next()) {
                String s = rs.getString(1);
                if (s.contains(predicate)) {
                    Assert.assertTrue(s, s.contains("TableScan"));
                }
            }
        }
    }

    @Test
    public void testChoiceOfDatasetProcessorType() throws Exception {
        // collect stats
        methodWatcher.executeQuery(format("analyze table %s.t4", CLASS_NAME));
        //test select with single table
        // PK access path, we should pick control path
        try (ResultSet rs = methodWatcher.executeQuery("explain select * from t4 where a4=10000")) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("expect explain plan to pick control path", rs.getString(1).contains("engine=OLTP"));
        }

        // full table scan, we should go for spark path as all rows need to be accessed, even though the output row count
        // is small after applying the predicate
        try (ResultSet rs = methodWatcher.executeQuery("explain select * from t4 where b4=10000")) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("expect explain plan to pick spark path", rs.getString(1).contains("engine=OLAP"));
        }

        // test join case, base table scan may not exceeds the rowcount limit of 20000, if the join result rowcount exceeds this
        // limit, we still need to go for Spark path
        try (ResultSet rs = methodWatcher.executeQuery("explain select * from t4 as X, t4 as Y where X.a4>30000 and Y.a4 >30000 and X.b4=Y.b4")) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("expect explain plan to pick spark path", rs.getString(1).contains("engine=OLAP"));
        }

        try (ResultSet rs = methodWatcher.executeQuery("explain select * from t4 as X, t4 as Y where X.a4=30000 and Y.a4 =30000 and X.b4=Y.b4")) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("expect explain plan to pick control path", rs.getString(1).contains("engine=OLTP"));
        }
    }

    @Test
    public void testDefaultSelectivityFactorHint() throws Exception {
        // collect stats
        methodWatcher.executeQuery(format("analyze table %s.t5", CLASS_NAME));
        methodWatcher.executeQuery(format("analyze table %s.t4", CLASS_NAME));

        double selectivity[] = {1, 1e-1, 1e-2, 1e-3, 1e-4, 1e-5, 1e-6, 1e-7};
        String engine[] = {"OLAP", "OLAP", "OLTP", "OLTP", "OLTP", "OLTP", "OLTP", "OLTP"};
        int rowCount[] = {900000, 90000, 9000, 900, 90, 9, 1,1};

        /* Q1: test single table case on PK */
        for (int i=0; i < selectivity.length; i++) {
            try (ResultSet rs = methodWatcher.executeQuery(format("explain select * from t5 --splice-properties useDefaultRowCount=1000000, defaultSelectivityFactor=%.8f\n where a5=100001", selectivity[i]))) {
                Assert.assertTrue(rs.next());
                Assert.assertTrue(format("Iteration [%d]:expect explain plan to pick %s path", i, engine[i]), rs.getString(1).contains(format("engine=%s", engine[i])));
                //skip the next step to get to the TableScan step
                Assert.assertTrue(rs.next());

                Assert.assertTrue(rs.next());
                Assert.assertTrue(format("Iteration [%d]:Expected TableScan", i), rs.getString(1).contains("TableScan"));
                Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount[i]), rs.getString(1).contains(format("outputRows=%d", rowCount[i])));
            }
        }

        /* test with stats */
        try (ResultSet rs = methodWatcher.executeQuery("explain select * from t5 where a5=100001")) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("With stats, expect explain plan to pick control path", rs.getString(1).contains("engine=OLTP"));
            //skip the next step to get to the TableScan step
            Assert.assertTrue(rs.next());

            Assert.assertTrue(rs.next());
            Assert.assertTrue("Expected TableScan", rs.getString(1).contains("TableScan"));
            Assert.assertTrue("With stats, outputRows is expected to be 1", rs.getString(1).contains("outputRows=1"));
        }

        /* Q2: test the switch from table scan to index scan */
        String engine2[] = {"OLAP", "OLAP", "OLAP", "OLTP", "OLTP", "OLTP", "OLTP", "OLTP"};
        int rowCount2[] = {1000000, 1000000, 1000000, 27, 1, 1, 1, 1};
        for (int i=0; i < selectivity.length; i++) {
            try (ResultSet rs = methodWatcher.executeQuery(format("explain select * from t5 --splice-properties useDefaultRowCount=1000000, defaultSelectivityFactor=%.8f\n where b5=100001 and c5=3", selectivity[i]))) {
                Assert.assertTrue(rs.next());
                Assert.assertTrue(format("Iteration [%d]:expect explain plan to pick %s path", i, engine[i]), rs.getString(1).contains(format("engine=%s", engine2[i])));
                if (i < 3) {
                    //selectivity is not small enough to make index lookup plan win, so we expect TableScan plan
                    //skip the next step to get to the TableScan step
                    Assert.assertTrue(rs.next());

                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:Expected TableScan", i), rs.getString(1).contains("TableScan"));
                    Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount2[i]), rs.getString(1).contains(format("scannedRows=%d", rowCount2[i])));
                } else {
                    // index lookup plan should win
                    //skip the next two steps to get to the IndexScan step
                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(rs.next());

                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:Expected IndexScan", i), rs.getString(1).contains("IndexScan"));
                    Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount2[i]), rs.getString(1).contains(format("scannedRows=%d", rowCount2[i])));
                }
            }
        }

        /* test with stats */
        try (ResultSet rs = methodWatcher.executeQuery("explain select * from t5 where b5=100001 and c5=3")) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("With stats, expect explain plan to pick control path", rs.getString(1).contains("engine=OLTP"));
            //skip the next step(s) to get to the IndexScan or IndexPrefixIteratorMode step
            Assert.assertTrue(rs.next());
            if (isMemPlatform)
                Assert.assertTrue(rs.next());

            String indexString = isMemPlatform ? "IndexScan" : "IndexPrefixIteratorMode";

            Assert.assertTrue(rs.next());
            Assert.assertTrue("Expected " + indexString, rs.getString(1).contains(indexString));
            Assert.assertTrue("With stats, outputRows is expected to be 1", rs.getString(1).contains("scannedRows=1"));
        }

        /* Q3: test join case */
        String engine3[] = {"OLAP", "OLAP", "OLAP", "OLTP", "OLTP", "OLTP", "OLTP", "OLTP"};
        int rowCount3[] = {1000000, 1000000, 1000000, 27, 1, 1, 1, 1};
        String join3[] = {"BroadcastJoin", "BroadcastJoin", "BroadcastJoin",
                "NestedLoopJoin", "NestedLoopJoin", "NestedLoopJoin", "NestedLoopJoin", "NestedLoopJoin"};
        for (int i=0; i < selectivity.length; i++) {
            try (ResultSet rs = methodWatcher.executeQuery(format("explain select * from --splice-properties joinOrder=fixed\n" +
                    "t5 --splice-properties useDefaultRowCount=1000000, defaultSelectivityFactor=%.8f\n " +
                    ", t4 where b5=100001 and c5=3 and d5=a4", selectivity[i]))) {
                Assert.assertTrue(rs.next());
                Assert.assertTrue(format("Iteration [%d]:expect explain plan to pick %s path", i, engine3[i]), rs.getString(1).contains(format("engine=%s", engine3[i])));
                // skip ScrollInsensitive step
                Assert.assertTrue(rs.next());
                if (i < 3) {
                    //selectivity is not small enough to make index lookup plan win, so we expect TableScan plan
                    // with large input table rows, broadcast join should win
                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:Expected %s", i, join3[i]), rs.getString(1).contains(join3[i]));
                    //skip the next step to get to the TableScan step for T5
                    Assert.assertTrue(rs.next());

                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:Expected TableScan", i), rs.getString(1).contains("TableScan"));
                    Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount2[i]), rs.getString(1).contains(format("scannedRows=%d", rowCount3[i])));
                } else {
                    // index lookup plan should win
                    // with small input table rows, nested loop join should win
                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:Expected %s", i, join3[i]), rs.getString(1).contains(join3[i]));

                    //skip the next two steps to get to the IndexScan step
                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(rs.next());

                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:Expected IndexScan", i), rs.getString(1).contains("IndexScan"));
                    Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount2[i]), rs.getString(1).contains(format("scannedRows=%d", rowCount3[i])));
                }
            }
        }

        /* test with stats */
        try (ResultSet rs = methodWatcher.executeQuery("explain select * from --splice-properties joinOrder=fixed\n t5, t4 where b5=100001 and c5=3 and d5=a4")) {
            Assert.assertTrue(rs.next());
            Assert.assertTrue("With stats, expect explain plan to pick control path", rs.getString(1).contains("engine=OLTP"));
            // skip ScrollInsensitive step
            Assert.assertTrue(rs.next());

            Assert.assertTrue(rs.next());
            Assert.assertTrue("Expected NestedLoopJoin", rs.getString(1).contains("NestedLoopJoin"));
            //skip the next step(s) to get to the IndexScan or IndexPrefixIteratorMode step
            Assert.assertTrue(rs.next());
            Assert.assertTrue(rs.next());
            if (isMemPlatform)
                Assert.assertTrue(rs.next());

            String indexString = isMemPlatform ? "IndexScan" : "IndexPrefixIteratorMode";

            Assert.assertTrue("Expected " + indexString, rs.getString(1).contains(indexString));
            Assert.assertTrue("With stats, outputRows is expected to be 1", rs.getString(1).contains("scannedRows=1"));
        }

    }

    @Test
    @Ignore("DB-7058")
    public void testDefaultSelectivityFactorHintAtSessionLevel() throws Exception {
        // collect stats
        methodWatcher.executeQuery(format("analyze table %s.t5", CLASS_NAME));
        methodWatcher.executeQuery(format("analyze table %s.t4", CLASS_NAME));

        double selectivity[] = {1, 1e-1, 1e-2, 1e-3, 1e-4, 1e-5, 1e-6, 1e-7};
        String engine[] = {"OLAP", "OLAP", "OLTP", "OLTP", "OLTP", "OLTP", "OLTP", "OLTP"};
        int rowCount[] = {900000, 90000, 9000, 900, 90, 9, 1,1};

        String engine2[] = {"OLAP", "OLAP", "OLAP", "OLTP", "OLTP", "OLTP", "OLTP", "OLTP"};
        int rowCount2[] = {1000000, 1000000, 1000000, 27, 1, 1, 1, 1};

        String engine3[] = {"OLAP", "OLAP", "OLAP", "OLTP", "OLTP", "OLTP", "OLTP", "OLTP"};
        int rowCount3[] = {1000000, 1000000, 1000000, 27, 1, 1, 1, 1};
        String join3[] = {"BroadcastJoin", "BroadcastJoin", "BroadcastJoin",
                "BroadcastJoin", "NestedLoopJoin", "NestedLoopJoin", "NestedLoopJoin", "NestedLoopJoin"};

        for (int i=0; i<selectivity.length; i++) {
            String url = format("jdbc:splice://localhost:1527/splicedb;create=true;user=splice;password=admin;defaultSelectivityFactor=%.8f", selectivity[i]);
            try (ConnectionStatementWrapper s = new ConnectionStatementWrapper(DriverManager.getConnection(url, new Properties()), CLASS_NAME.toUpperCase())) {
                /* Q1: test single table case on PK */
                try (ResultSet rs = s.executeQuery("explain select * from t5 --splice-properties useDefaultRowCount=1000000\n where a5=100001")) {
                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:expect explain plan to pick %s path", i, engine[i]), rs.getString(1).contains(format("engine=%s", engine[i])));
                    //skip the next step to get to the TableScan step
                    Assert.assertTrue(rs.next());

                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:Expected TableScan", i), rs.getString(1).contains("TableScan"));
                    Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount[i]), rs.getString(1).contains(format("outputRows=%d", rowCount[i])));
                }

                /* Q2: test the switch from table scan to index scan */
                try (ResultSet rs = s.executeQuery("explain select * from t5 --splice-properties useDefaultRowCount=1000000\n where b5=100001 and c5=3")) {
                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:expect explain plan to pick %s path", i, engine[i]), rs.getString(1).contains(format("engine=%s", engine2[i])));
                    if (i < 3) {
                        //selectivity is not small enough to make index lookup plan win, so we expect TableScan plan
                        //skip the next step to get to the TableScan step
                        Assert.assertTrue(rs.next());

                        Assert.assertTrue(rs.next());
                        Assert.assertTrue(format("Iteration [%d]:Expected TableScan", i), rs.getString(1).contains("TableScan"));
                        Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount2[i]), rs.getString(1).contains(format("scannedRows=%d", rowCount2[i])));
                    } else {
                        // index lookup plan should win
                        //skip the next two steps to get to the IndexScan step
                        Assert.assertTrue(rs.next());
                        Assert.assertTrue(rs.next());

                        Assert.assertTrue(rs.next());
                        Assert.assertTrue(format("Iteration [%d]:Expected IndexScan", i), rs.getString(1).contains("IndexScan"));
                        Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount2[i]), rs.getString(1).contains(format("scannedRows=%d", rowCount2[i])));
                    }
                }


                /* Q3: test join case */
                try (ResultSet rs = s.executeQuery("explain select * from --splice-properties joinOrder=fixed\n" +
                        "t5 --splice-properties useDefaultRowCount=1000000\n" +
                        ", t4 where b5=100001 and c5=3 and d5=a4")) {
                    Assert.assertTrue(rs.next());
                    Assert.assertTrue(format("Iteration [%d]:expect explain plan to pick %s path", i, engine3[i]), rs.getString(1).contains(format("engine=%s", engine3[i])));
                    // skip ScrollInsensitive step
                    Assert.assertTrue(rs.next());
                    if (i < 3) {
                        //selectivity is not small enough to make index lookup plan win, so we expect TableScan plan
                        // with large input table rows, broadcast join should win
                        Assert.assertTrue(rs.next());
                        Assert.assertTrue(format("Iteration [%d]:Expected %s", i, join3[i]), rs.getString(1).contains(join3[i]));
                        //skip the next step to get to the TableScan step for T5
                        Assert.assertTrue(rs.next());

                        Assert.assertTrue(rs.next());
                        Assert.assertTrue(format("Iteration [%d]:Expected TableScan", i), rs.getString(1).contains("TableScan"));
                        Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount2[i]), rs.getString(1).contains(format("scannedRows=%d", rowCount3[i])));
                    } else {
                        // index lookup plan should win
                        // with small input table rows, nested loop join should win
                        Assert.assertTrue(rs.next());
                        Assert.assertTrue(format("Iteration [%d]:Expected %s", i, join3[i]), rs.getString(1).contains(join3[i]));

                        //skip the next two steps to get to the IndexScan step
                        Assert.assertTrue(rs.next());
                        Assert.assertTrue(rs.next());

                        Assert.assertTrue(rs.next());
                        Assert.assertTrue(format("Iteration [%d]:Expected IndexScan", i), rs.getString(1).contains("IndexScan"));
                        Assert.assertTrue(format("Iteration [%d]: outputRows is expected to be: %d", i, rowCount2[i]), rs.getString(1).contains(format("scannedRows=%d", rowCount3[i])));
                    }
                }
            }
        }
    }

    @Ignore("DB-11278")
    @Test
    public void testJoinStrategyHintOnTargetTable() throws Exception {
        String query ="explain update t4 --splice-properties joinStrategy=nestedloop\n" +
                             "set (a4,b4) = (select 1,1 from t1 where c1 = a4)";

        rowContainsQuery(3, query, "NestedLoopJoin", methodWatcher);
    }

    @Test
    public void testTriggerWithManyJoinsUsesMoreThan10msInQueryPlanner() throws Exception {
        methodWatcher.execute("CREATE TRIGGER MyTrig\n" +
            "BEFORE INSERT ON T4\n" +
            "FOR EACH STATEMENT\n" +
            "WHEN (EXISTS (SELECT 1 FROM t4 a, t4 b, t4 c, t4 d, t4 e, t4 f, t4 g, t4 h, t4 i, t4 j " +
            "WHERE a.a4 = b.a4 and b.a4=c.a4 and c.a4 = d.a4 and d.a4 = e.a4 and e.a4 = f.a4 and" +
                  " f.a4 = g.a4 and g.a4 = h.a4 and h.a4 = i.a4 and i.a4 = j.a4))\n" +
            "SIGNAL SQLSTATE '12345' ('No inserts allowed')\n");

        long startTime = System.currentTimeMillis();
        String query ="insert into t4 values (20,20,20)";

        List<String> expectedErrors =
           Arrays.asList("Application raised error or warning with diagnostic text: \"No inserts allowed\"");
        methodWatcher.execute("CALL SYSCS_UTIL.SYSCS_INVALIDATE_STORED_STATEMENTS()");
        methodWatcher.execute("CALL SYSCS_UTIL.SYSCS_EMPTY_GLOBAL_STATEMENT_CACHE()");
        testUpdateFail(query, expectedErrors, methodWatcher);
        long endTime = System.currentTimeMillis();
        long runTime = (endTime - startTime);
        Assert.assertTrue("Expected query planning to take more than 10 ms.  Actual time: " + runTime + " milliseconds", runTime > 10);
        methodWatcher.execute("DROP TRIGGER MyTrig");
    }

    @Test
    public void testReportMissingTableStatistics() throws Exception {
        String query ="explain select * from t3";
        String[] expected = {
                "Table statistics are missing or skipped for the following tables",
                "\"" + CLASS_NAME + "\".\"T3\""
        };
        rowContainsQuery(new int[]{4, 5}, query, spliceClassWatcher, expected);

        methodWatcher.executeQuery(format("analyze table %s.t3", CLASS_NAME));
        try (ResultSet rs  = methodWatcher.executeQuery(query)) {
            String explainStr = TestUtils.FormattedResult.ResultFactory.toString(rs);
            Assert.assertFalse(explainStr.contains(expected[0]) || explainStr.contains(expected[1]));
        }
    }

    @Test
    public void testTableSkipStatistics() throws Exception {
        String query ="explain select * from t5 --splice-properties skipStats=true";
        methodWatcher.executeQuery(format("analyze table %s.t5", CLASS_NAME));

        String[] expected = {
                "Table statistics are missing or skipped for the following tables",
                "\"" + CLASS_NAME + "\".\"T5\""
        };
        rowContainsQuery(new int[]{4, 5}, query, spliceClassWatcher, expected);
    }

    @Test
    public void testReportMissingColumnStatisticsUsedOnly() throws Exception {
        String query ="explain select * from t5";
        methodWatcher.execute(format("CALL SYSCS_UTIL.DISABLE_COLUMN_STATISTICS('%s', 'T5', 'E5')", CLASS_NAME));
        methodWatcher.executeQuery(format("analyze table %s.t5", CLASS_NAME));

        String[] expected = {
                "Column statistics are missing or skipped for the following columns",
                "\"" + CLASS_NAME + "\".\"T5\".E5"
        };

        // only columns used for estimating selectivity/cost but missing statistics are reported
        // for the query above, T5.E5 is not used
        try (ResultSet rs  = methodWatcher.executeQuery(query)) {
            String explainStr = TestUtils.FormattedResult.ResultFactory.toString(rs);
            Assert.assertFalse(explainStr.contains(expected[0]) || explainStr.contains(expected[1]));
        }

        // use T5.E5 for estimating cost
        rowContainsQuery(new int[]{4, 5}, query + " where e5 < 3", spliceClassWatcher, expected);
    }

    @Test
    public void testReportMissingColumnStatisticsInListAndRange() throws Exception {
        String query =
                "explain select * from t1 --splice-properties joinStrategy=NESTEDLOOP\n" +
                " , t2 --splice-properties joinStrategy=NESTEDLOOP\n" +
                " where t1.c1 = t2.c1 and t1.c2 in (3, 5, 7) and t2.c1 between 1 and 10";
        methodWatcher.execute(format("CALL SYSCS_UTIL.DISABLE_COLUMN_STATISTICS('%s', 'T1', 'c2')", CLASS_NAME));
        methodWatcher.execute(format("CALL SYSCS_UTIL.DISABLE_COLUMN_STATISTICS('%s', 'T2', 'c1')", CLASS_NAME));
        methodWatcher.executeQuery(format("analyze table %s.t1", CLASS_NAME));
        methodWatcher.executeQuery(format("analyze table %s.t2", CLASS_NAME));

        String[] expected = {
                "Column statistics are missing or skipped for the following columns",
                "\"" + CLASS_NAME + "\".\"T1\".C2",
                "\"" + CLASS_NAME + "\".\"T2\".C1"
        };

        try (ResultSet rs  = methodWatcher.executeQuery(query)) {
            String explainStr = TestUtils.FormattedResult.ResultFactory.toString(rs);
            Assert.assertTrue(explainStr.contains(expected[0]));
            Assert.assertTrue(explainStr.contains(expected[1]));
            Assert.assertTrue(explainStr.contains(expected[2]));
        }
    }

    @Test
    public void testReportMissingColumnStatisticsJoinSelectivity() throws Exception {
        String query =
                "explain select * from t1\n" +
                " , t2 --splice-properties joinStrategy=BROADCAST\n" +
                " where t1.c1 = t2.c1";
        methodWatcher.execute(format("CALL SYSCS_UTIL.DISABLE_COLUMN_STATISTICS('%s', 'T1', 'c2')", CLASS_NAME));
        methodWatcher.execute(format("CALL SYSCS_UTIL.DISABLE_COLUMN_STATISTICS('%s', 'T2', 'c1')", CLASS_NAME));
        methodWatcher.executeQuery(format("analyze table %s.t1", CLASS_NAME));
        methodWatcher.executeQuery(format("analyze table %s.t2", CLASS_NAME));

        String[] expected = {
                "Column statistics are missing or skipped for the following columns",
                CLASS_NAME + ".T2.C1"
        };

        try (ResultSet rs  = methodWatcher.executeQuery(query)) {
            String explainStr = TestUtils.FormattedResult.ResultFactory.toString(rs);
            Assert.assertTrue(explainStr.contains(expected[0]));
            Assert.assertTrue(explainStr.contains(expected[1]));
            Assert.assertFalse(explainStr.contains(CLASS_NAME + ".T1.C1"));
        }
    }

    @Test
    public void testReportMissingColumnStatisticsGroupByAndAggregates() throws Exception {
        String query = "explain select b6, avg(a6), sum(c6) from t6 group by b6";
        methodWatcher.execute(format("CALL SYSCS_UTIL.DISABLE_COLUMN_STATISTICS('%s', 'T6', 'b6')", CLASS_NAME));
        methodWatcher.execute(format("CALL SYSCS_UTIL.DISABLE_COLUMN_STATISTICS('%s', 'T6', 'c6')", CLASS_NAME));
        methodWatcher.executeQuery(format("analyze table %s.t6", CLASS_NAME));

        String[] expected = {
                "Column statistics are missing or skipped for the following columns",
                CLASS_NAME + ".T6.B6",
                CLASS_NAME + ".T6.C6"
        };

        try (ResultSet rs  = methodWatcher.executeQuery(query)) {
            String explainStr = TestUtils.FormattedResult.ResultFactory.toString(rs);
            Assert.assertTrue(explainStr.contains(expected[0]));
            Assert.assertTrue(explainStr.contains(expected[1]));
            Assert.assertFalse(explainStr.contains(expected[2]));
            Assert.assertFalse(explainStr.contains(CLASS_NAME + ".T6.A6"));
        }
    }

    @Test
    public void testReportMissingColumnStatisticsNoAliasNoDuplicates() throws Exception {
        methodWatcher.execute("create table if not exists t11 (a int, b int, c int)");
        methodWatcher.execute("create table if not exists t21 (a int, b int, c int)");
        methodWatcher.execute("create table if not exists t31 (a int, b int, c int)");

        String query = "explain select * from t11" +
                "         inner join t21 as aliast2 on (aliast2.c = t11.b)\n" +
                "where t11.b is null\n" +
                "  and aliast2.c = 0";

        // no ALIAST2 in missing stats report
        String[] expected = {
                "Table statistics are missing or skipped for the following tables",
                "\"" + CLASS_NAME + "\".\"T11\"",
                "\"" + CLASS_NAME + "\".\"T21\"",
                "Column statistics are missing or skipped for the following columns",
                CLASS_NAME + ".T21.C",
                CLASS_NAME + ".T11.B"
        };

        rowContainsQuery(new int[]{6,7,7,8,9,9}, query, methodWatcher, expected);
    }

    @Test
    public void testPlanPrinterBugDB11341() throws Exception {
        try (ResultSet rs = methodWatcher.executeQuery("call syscs_util.syscs_get_logger_level('com.splicemachine.db.impl.ast.PlanPrinter')")) {
            rs.next();
            String planPrinterLevel = rs.getString(1);
            try {
                methodWatcher.execute("call syscs_util.syscs_set_logger_level('com.splicemachine.db.impl.ast.PlanPrinter','DEBUG')");
                methodWatcher.executeQuery("SELECT 1 FROM ( SELECT 1 FROM sysibm.sysdummy1 ORDER BY 1 )"); // Failed before DB-11341
            } finally {
                methodWatcher.execute(format("call syscs_util.syscs_set_logger_level('com.splicemachine.db.impl.ast.PlanPrinter', '%s')", planPrinterLevel));
            }
        }
    }

    @Test
    public void testExplainSelectForUpdateDB11371() throws Exception {
        try (ResultSet rs = methodWatcher.executeQuery("explain select * from t1 for update")) {
            String explainStr = TestUtils.FormattedResult.ResultFactory.toString(rs);
            Assert.assertTrue(explainStr.contains("ScrollInsensitive"));
        }
    }

    private long cacheTime(String query) throws SQLException {
        try(ResultSet rs = methodWatcher.executeQuery("CALL SYSCS_UTIL.SYSCS_GET_CACHED_STATEMENTS()")) {
            while(rs.next()) {
                if(rs.getString(2).equals(query)) {
                    return rs.getTimestamp(3).getTime();
                }
            }
        }
        return -1;
    }

    private void testCachingExplainStatementInternal(String statement, String explainPrefix) throws Exception {
        Assume.assumeFalse(isMemPlatform(methodWatcher));
        methodWatcher.execute("call syscs_util.SYSCS_EMPTY_GLOBAL_STATEMENT_CACHE()");
        String explainQuery = explainPrefix + statement;
        methodWatcher.execute(explainQuery);
        long cacheTime = cacheTime(statement);
        Assert.assertTrue(cacheTime > 0);
        methodWatcher.execute(statement);
        long cacheTimeAfterExecution = cacheTime(statement);
        Assert.assertEquals("statement cached by explain was recompiled!", cacheTime, cacheTimeAfterExecution);
    }

    @Test
    public void testCachingExplainStatementDifferentStatements() throws Exception {
        Assume.assumeFalse("test is ignored in mem-platform until DB-11456 is fixed", isMemPlatform(methodWatcher));
        testCachingExplainStatementInternal("select * from t7", "explain ");
        testCachingExplainStatementInternal("select * from t7 where a7 > 1", "explain ");
        testCachingExplainStatementInternal("update t7 set a7 = 40 where a7 = 42", "explain ");
        testCachingExplainStatementInternal("select * from t7 --splice-properties useOlap=false", "explain ");
        testCachingExplainStatementInternal("select * from t7 --splice-properties useOlap=true", "explain ");
    }

    @Test
    public void testCachingExplainStatementDifferentExplainPrefix() throws Exception {
        Assume.assumeFalse("test is ignored in mem-platform until DB-11456 is fixed", isMemPlatform(methodWatcher));
        testCachingExplainStatementInternal("select * from t7", "explain \n\n\t ");
        testCachingExplainStatementInternal("select * from t7", "ExpLain \t   \n");
        testCachingExplainStatementInternal("select * from t7", "--comment1\n--comment2\n   explain \n\n\n\t");
        testCachingExplainStatementInternal("select * from t7", "--comment1\n--comment2\n   explain   \n\n\n\nexclude  no \t\t\t   statistics   \n\t\n\t");
        testCachingExplainStatementInternal("select * from t7", "--comment1\n--comment2\n   explain exclude no statistics\t\t\t\t\t");
    }
}
