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

import com.splicemachine.db.client.am.SqlState;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.SQLState;
import com.splicemachine.db.iapi.sql.compile.CompilerContext;
import com.splicemachine.db.iapi.types.SQLTimestamp;
import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceUnitTest;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.derby.test.framework.TestConnection;
import com.splicemachine.homeless.TestUtils;
import com.splicemachine.test.SerialTest;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;
import splice.com.google.common.collect.Lists;

import java.io.File;
import java.sql.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test different operations on extended-range timestamps.
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)
@Category(SerialTest.class) // maybe not run in parallel since it is changing global DB configs
public class TimestampIT extends SpliceUnitTest {
    private static final String SCHEMA = TimestampIT.class.getSimpleName().toUpperCase();
    private Boolean useSpark;
    private static boolean extendedTimestamps = true;
    protected static final SpliceWatcher spliceClassWatcher = new SpliceWatcher(SCHEMA);
    protected static final SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(SCHEMA);
    protected static final SpliceWatcher methodWatcher = new SpliceWatcher(SCHEMA);

    private static File BADDIR;

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
                                            .around(spliceSchemaWatcher)
                                            .around(methodWatcher);

    @Parameterized.Parameters(name = "useSpark = {0}")
    public static Collection<Object[]> data() {
        Collection<Object[]> params = Lists.newArrayListWithCapacity(1);
        params.add(new Object[]{true});
        params.add(new Object[]{false});
        return params;
    }

    public TimestampIT(Boolean useSpark) {
        this.useSpark = useSpark;
    }

    @BeforeClass
    public static void createDataSet() throws Exception {
        spliceClassWatcher.setAutoCommit(false);
        createSharedTables(spliceClassWatcher.getOrCreateConnection());
    }

    @After
    public void after() throws Exception {
        // reset autocommit to true if we failed within a non-autocommit test
        if(!methodWatcher.getOrCreateConnection().getAutoCommit()) {
            methodWatcher.rollback();
            methodWatcher.setAutoCommit(true);
        }
        methodWatcher.closeAll();
    }

    public static void createSharedTables(Connection conn) throws Exception {
        BADDIR = SpliceUnitTest.createBadLogDirectory(SCHEMA);
        assertNotNull(BADDIR);

        try (Statement s = conn.createStatement()) {
            s.execute("CALL SYSCS_UTIL.SYSCS_EMPTY_GLOBAL_STATEMENT_CACHE()");
            s.execute("CALL SYSCS_UTIL.INVALIDATE_GLOBAL_DICTIONARY_CACHE()");
            s.execute("call syscs_util.syscs_set_global_database_property('derby.database.convertOutOfRangeTimeStamps', 'true')");

            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t1"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t11"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t2"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t3"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t3b"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t4"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t5"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t6"));

            s.executeUpdate(String.format("create table %s ", SCHEMA + ".t1") + "(col1 timestamp, col2 int, primary key(col1,col2))");
            s.executeUpdate(String.format("create table %s ", SCHEMA + ".t11") + "(col1 timestamp, col2 int)");
            s.executeUpdate(String.format("create table %s ", SCHEMA + ".t2") + "(col1 timestamp, col2 int, primary key(col1, col2))");
            s.executeUpdate(String.format("create table %s ", SCHEMA + ".t3") + "(col1 timestamp, col2 int)");
            s.executeUpdate(String.format("create table %s ", SCHEMA + ".t3b") + "(col1 timestamp, col2 int)");
            s.executeUpdate(String.format("create index idx1 on %s ", SCHEMA + ".t3") + "(col1)");
            s.executeUpdate(String.format("create table %s ", SCHEMA + ".t4") + "(col1 timestamp, col2 timestamp)");
            s.executeUpdate(String.format("create table %s ", SCHEMA + ".t5") + "(col1 timestamp)");
            s.executeUpdate(String.format("create table %s ", SCHEMA + ".t6") + "(FIPS1 TIME, FIPS2 TIMESTAMP)");

            conn.commit();
            ResultSet rs = s.executeQuery("CALL SYSCS_UTIL.SYSCS_GET_GLOBAL_DATABASE_PROPERTY('derby.database.createTablesWithVersion2Serializer')");
            if (rs.next()) {
                String aStr = rs.getString(2);
                if (!rs.wasNull()) {
                    if (aStr.equals("true"))
                        extendedTimestamps = false;
                }
            }

            s.execute(String.format("call SYSCS_UTIL.IMPORT_DATA(" +
            "'%s'," +  // schema name
            "'%s'," +  // table name
            "'col1, col2'," +  // insert column list
            "'%s'," +  // file path
            "','," +   // column delimiter
            "null," +  // character delimiter
            "'yyyy-MM-dd HH:mm:ss.S'," +  // timestamp format
            "null," +  // date format
            "null," +  // time format
            "0," +    // max bad records
            "'%s'," +  // bad record dir
            "'true'," +  // has one line records
            "null)",   // char set
            SCHEMA, "t1", SpliceUnitTest.getResourceDirectory() + "ts2.csv",
            BADDIR.getCanonicalPath()));

            s.execute(String.format("call SYSCS_UTIL.IMPORT_DATA(" +
            "'%s'," +  // schema name
            "'%s'," +  // table name
            "'col1, col2'," +  // insert column list
            "'%s'," +  // file path
            "','," +   // column delimiter
            "null," +  // character delimiter
            "'yyyy-MM-dd HH:mm:ss.S'," +  // timestamp format
            "null," +  // date format
            "null," +  // time format
            "0," +    // max bad records
            "'%s'," +  // bad record dir
            "'true'," +  // has one line records
            "null)",   // char set
            SCHEMA, "t2", SpliceUnitTest.getResourceDirectory() + "ts3.csv",
            BADDIR.getCanonicalPath()));

            s.execute(String.format("call SYSCS_UTIL.IMPORT_DATA(" +
            "'%s'," +  // schema name
            "'%s'," +  // table name
            "'col1, col2'," +  // insert column list
            "'%s'," +  // file path
            "','," +   // column delimiter
            "null," +  // character delimiter
            "'yyyy-MM-dd HH:mm:ss.SSSSSS'," +  // timestamp format
            "null," +  // date format
            "null," +  // time format
            "0," +    // max bad records
            "'%s'," +  // bad record dir
            "'true'," +  // has one line records
            "null)",   // char set
            SCHEMA, "t3", SpliceUnitTest.getResourceDirectory() + "ts4.csv",
            BADDIR.getCanonicalPath()));

            s.execute(String.format("call SYSCS_UTIL.IMPORT_DATA(" +
            "'%s'," +  // schema name
            "'%s'," +  // table name
            "'col1, col2'," +  // insert column list
            "'%s'," +  // file path
            "','," +   // column delimiter
            "null," +  // character delimiter
            "'yyyy-MM-dd HH:mm:ss.SSSSSS'," +  // timestamp format
            "null," +  // date format
            "null," +  // time format
            "0," +    // max bad records
            "'%s'," +  // bad record dir
            "'true'," +  // has one line records
            "null)",   // char set
            SCHEMA, "t3b", SpliceUnitTest.getResourceDirectory() + "ts4b.csv",
            BADDIR.getCanonicalPath()));

            try {
                s.execute(String.format("call SYSCS_UTIL.IMPORT_DATA(" +
                "'%s'," +  // schema name
                "'%s'," +  // table name
                "'col1, col2'," +  // insert column list
                "'%s'," +  // file path
                "','," +   // column delimiter
                "null," +  // character delimiter
                "'yyyy-MM-dd HH:mm:ss.SSSSSS'," +  // timestamp format
                "null," +  // date format
                "null," +  // time format
                "1," +    // max bad records
                "'%s'," +  // bad record dir
                "'true'," +  // has one line records
                "null)",   // char set
                SCHEMA, "t3", SpliceUnitTest.getResourceDirectory() + "ts5.csv",
                BADDIR.getCanonicalPath()));

                // Assert that convertOutOfRangeTimestamps does not convert timestamps on version 3.0 tables.
                if (extendedTimestamps)
                     assertTrue("Import should have errored out.", false);
            } catch (Exception e) {

            }
            s.execute(String.format("call SYSCS_UTIL.IMPORT_DATA(" +
            "'%s'," +  // schema name
            "'%s'," +  // table name
            "'col1, col2'," +  // insert column list
            "'%s'," +  // file path
            "','," +   // column delimiter
            "null," +  // character delimiter
            "'yyyy-MM-dd HH:mm:ss.SSSSSS'," +  // timestamp format
            "null," +  // date format
            "null," +  // time format
            "0," +    // max bad records
            "'%s'," +  // bad record dir
            "'true'," +  // has one line records
            "null)",   // char set
            SCHEMA, "t4", SpliceUnitTest.getResourceDirectory() + "ts6.csv",
            BADDIR.getCanonicalPath()));


            s.executeUpdate(String.format("insert into %s select * from %s", SCHEMA + ".t11", SCHEMA + ".t1"));

            s.executeUpdate(String.format("insert into %s values({ts'1700-12-31 23:59:58.999999'})", SCHEMA + ".t5"));
            s.executeUpdate(String.format("insert into %s values({ts'1867-02-28 01:38:01.0426'})", SCHEMA + ".t5"));
            s.executeUpdate(String.format("insert into %s values({ts'1999-04-22 12:28:11.123456'})", SCHEMA + ".t5"));
            s.executeUpdate(String.format("insert into %s values({ts'2020-11-02 10:57:00.000006'})", SCHEMA + ".t5"));

            conn.commit();
            rs.close();
        }

    }

    @Test
    public void testMultiProbeTableScanWithProbeVariables() throws Exception {
        PreparedStatement ps = methodWatcher.prepareStatement(format("select col2 from t1 --SPLICE-PROPERTIES useSpark = %s  \n" +
                                          "where col1 in (?,?,?,?)", useSpark));
        ps.setTimestamp(1, new Timestamp(1 - 1900 /*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/));
        ps.setTimestamp(2, new Timestamp(2 - 1900/*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/));
        ps.setTimestamp(3, new Timestamp(3 - 1900/*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/));
        ps.setTimestamp(4, new Timestamp(4 - 1900/*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/));

        try (ResultSet rs = ps.executeQuery()) {
            int i = 0;
            while (rs.next()) {
                i++;
            }
            Assert.assertEquals("Incorrect count returned!", 4, i);
        }
        catch (SQLException e) {
            if (extendedTimestamps)
                assertTrue("Import shouldn't have errored out.", false);
        }
    }


    @Test
    public void testIntersect() throws Exception {
        ResultSet rs = methodWatcher.executeQuery(format("select count(*), max(col1), min(col1) " +
        " from  (select col1 from t1 --SPLICE-PROPERTIES useSpark = %s  \n" +
        "intersect select col1 from t2) argh", useSpark));

        assertTrue("intersect incorrect", rs.next());
        if (extendedTimestamps) {
            Assert.assertEquals("Wrong Count", 3, rs.getInt(1));
            Assert.assertEquals("Wrong Max", new Timestamp(4 - 1900 /*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/), rs.getTimestamp(2));
            Assert.assertEquals("Wrong Min", new Timestamp(1 - 1900 /*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/), rs.getTimestamp(3));
        } else {
            Assert.assertEquals("Wrong Count", 1, rs.getInt(1));
            Assert.assertEquals("Wrong Max", new Timestamp(1677 - 1900 /*year*/, 8 /*month-1*/, 20 /*day*/, 16 /*hour*/, 12/*minute*/, 43 /*second*/, 147000000 /*nano*/), rs.getTimestamp(2));
            Assert.assertEquals("Wrong Min", new Timestamp(1677 - 1900 /*year*/, 8 /*month-1*/, 20 /*day*/, 16 /*hour*/, 12/*minute*/, 43 /*second*/, 147000000 /*nano*/), rs.getTimestamp(3));

        }
        rs.close();

    }
    @Test
    public void testUnion() throws Exception {
        ResultSet rs = methodWatcher.executeQuery(format("select count(*), max(col1), min(col1) " +
        " from  (select col1 from t1 --SPLICE-PROPERTIES useSpark = %s  \n" +
        "union select col1 from t2) argh", useSpark));

        assertTrue("union incorrect", rs.next());
        if (extendedTimestamps) {
            Assert.assertEquals("Wrong Count", 5, rs.getInt(1));
            Assert.assertEquals("Wrong Max", new Timestamp(5 - 1900 /*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/), rs.getTimestamp(2));
            Assert.assertEquals("Wrong Min", new Timestamp(1 - 1900 /*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/), rs.getTimestamp(3));
        }
        else {
            Assert.assertEquals("Wrong Count", 1, rs.getInt(1));
            Assert.assertEquals("Wrong Max", new Timestamp(1677 - 1900 /*year*/, 8 /*month-1*/, 20 /*day*/, 16 /*hour*/, 12/*minute*/, 43 /*second*/, 147000000 /*nano*/), rs.getTimestamp(2));
            Assert.assertEquals("Wrong Min", new Timestamp(1677 - 1900 /*year*/, 8 /*month-1*/, 20 /*day*/, 16 /*hour*/, 12/*minute*/, 43 /*second*/, 147000000 /*nano*/), rs.getTimestamp(3));
        }
        rs.close();
    }


    @Test
    public void testExcept() throws Exception {

        ResultSet rs;

        rs = methodWatcher.executeQuery(
        format("select count(*), max(col1), min(col1) from (" +
        "select col1 from t1  --SPLICE-PROPERTIES useSpark = %s  \n except select col1 from t2) argh", useSpark));
        assertTrue("minus incorrect", rs.next());

        if (!extendedTimestamps) {
            Assert.assertEquals("Wrong Count", 0, rs.getInt(1));
            Assert.assertNull("Wrong Max,", rs.getTimestamp(2));
            Assert.assertNull("Wrong Min,", rs.getTimestamp(3));
        }
        else {
            Assert.assertEquals("Wrong Count", 2, rs.getInt(1));
            Assert.assertEquals("Wrong Max", new Timestamp(5 - 1900 /*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/), rs.getTimestamp(2));
            Assert.assertEquals("Wrong Min", new Timestamp(3 - 1900 /*year*/, 0 /*month-1*/, 1 /*day*/, 0 /*hour*/, 0/*minute*/, 0 /*second*/, 0 /*nano*/), rs.getTimestamp(3));
        }
        rs.close();
    }


    @Test
    public void testExceptWithOrderBy() throws Exception {
        ResultSet rs = methodWatcher.executeQuery(
        format("select col1 from t1 --SPLICE-PROPERTIES useSpark = %s\n except select col1 from t2 order by 1", useSpark));
        if (extendedTimestamps) {
            Assert.assertEquals(
            "COL1          |\n" +
            "-----------------------\n" +
            "0003-01-01 00:00:00.0 |\n" +
            "0005-01-01 00:00:00.0 |", TestUtils.FormattedResult.ResultFactory.toString(rs));
        }
        else {
            Assert.assertEquals(
            "", TestUtils.FormattedResult.ResultFactory.toString(rs));
        }
        rs.close();
    }


    /* positive test, top N is applied on the union all result */
    @Test
    public void testTopN1() throws Exception {
        String sqlText = "select top 2 * from t1 union all select * from t2 order by 1,2";
        String expected = extendedTimestamps ?
        "COL1          |COL2 |\n" +
        "-----------------------------\n" +
        "0001-01-01 00:00:00.0 |  1  |\n" +
        "0001-01-01 00:00:00.0 |  1  |" :
        "COL1           |COL2 |\n" +
        "-------------------------------\n" +
        "1677-09-20 16:12:43.147 |  1  |\n" +
        "1677-09-20 16:12:43.147 |  1  |";

        ResultSet rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    /* positive test, top N is applied on the union all result */
    @Test
    public void testTopN2() throws Exception {
        String sqlText = format("select count(*) from " +
        "(select top 2 * from t1 --SPLICE-PROPERTIES useSpark = %s  \n" +
        "union all select * from t2)dt", useSpark);
        String expected =
        "1 |\n" +
        "----\n" +
        " 2 |";

        ResultSet rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testGroupBy6Digits() throws Exception {
        String sqlText = "select count(*), col1 from t3 --SPLICE-PROPERTIES useSpark = %s  \n" +
                         "group by col1 order by 2";
        String expected;
        expected = extendedTimestamps ?
        "1 |           COL1            |\n" +
        "--------------------------------\n" +
        " 1 |0001-01-01 00:00:00.123456 |\n" +
        " 1 |0001-01-01 00:00:00.123457 |\n" +
        " 1 |0001-01-01 00:00:00.123458 |" :
        "1 |         COL1           |\n" +
        "-----------------------------\n" +
        " 5 |1677-09-20 16:12:43.147 |";

        ResultSet rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        sqlText = "select count(*), col1 from t3 --SPLICE-PROPERTIES useSpark = %s,index=idx1  \n" +
        "group by col1 order by 2";
        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        sqlText = "select count(*), col1 from t3b --SPLICE-PROPERTIES useSpark = %s  \n" +
        "group by col1 order by 2";

        expected = extendedTimestamps ?
        "1 |           COL1            |\n" +
        "--------------------------------\n" +
        " 1 |9999-12-31 23:59:59.999997 |\n" +
        " 1 |9999-12-31 23:59:59.999998 |\n" +
        " 1 |9999-12-31 23:59:59.999999 |" :
        "1 |         COL1           |\n" +
        "-----------------------------\n" +
        " 3 |2262-04-11 16:47:16.853 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));

        rs.close();
    }

    @Test
    public void testTimestampAdd() throws Exception {
        String sqlText = format("select TIMESTAMPADD(SQL_TSI_SECOND, -1, col1) from t3 order by 1 --SPLICE-PROPERTIES useSpark = %s", useSpark);
        String expected;

        TestConnection connection = methodWatcher.getOrCreateConnection();
        // Resulting timestamp should be out of range.
        if (extendedTimestamps)
            assertFailed(connection, sqlText, "42X01");

        sqlText = format("select TIMESTAMPADD(SQL_TSI_SECOND, 1, col1) from t3 --SPLICE-PROPERTIES useSpark = %s\n order by 1", useSpark);

        expected = extendedTimestamps ?
        "1             |\n" +
        "----------------------------\n" +
        "0001-01-01 00:00:01.123456 |\n" +
        "0001-01-01 00:00:01.123457 |\n" +
        "0001-01-01 00:00:01.123458 |" :
        "1            |\n" +
        "-------------------------\n" +
        "1677-09-20 16:12:44.147 |\n" +
        "1677-09-20 16:12:44.147 |\n" +
        "1677-09-20 16:12:44.147 |\n" +
        "1677-09-20 16:12:44.147 |\n" +
        "1677-09-20 16:12:44.147 |";

        ResultSet rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        sqlText = format("select TIMESTAMPADD(SQL_TSI_SECOND, 1, col1) from t3b --SPLICE-PROPERTIES useSpark = %s\n order by 1", useSpark);

        // Resulting timestamp should be out of range.
        if (extendedTimestamps)
            assertFailed(connection, sqlText, "22003");

        rs.close();
        sqlText = format("select TIMESTAMPADD(SQL_TSI_SECOND, -1, col1) from t3b --SPLICE-PROPERTIES useSpark = %s\n order by 1", useSpark);

        expected = extendedTimestamps ?
        "1             |\n" +
        "----------------------------\n" +
        "9999-12-31 23:59:58.999997 |\n" +
        "9999-12-31 23:59:58.999998 |\n" +
        "9999-12-31 23:59:58.999999 |" :
        "1            |\n" +
        "-------------------------\n" +
        "2262-04-11 16:47:15.853 |\n" +
        "2262-04-11 16:47:15.853 |\n" +
        "2262-04-11 16:47:15.853 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_FRAC_SECOND, -999999000, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |   1700-12-31 23:59:58.0   |\n" +
        " 1867-02-28 01:38:01.0426  |1867-02-28 01:38:00.042601 |\n" +
        "1999-04-22 12:28:11.123456 |1999-04-22 12:28:10.123457 |\n" +
        "2020-11-02 10:57:00.000006 |2020-11-02 10:56:59.000007 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_FRAC_SECOND, 111000, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 | 1700-12-31 23:59:59.00011 |\n" +
        " 1867-02-28 01:38:01.0426  |1867-02-28 01:38:01.042711 |\n" +
        "1999-04-22 12:28:11.123456 |1999-04-22 12:28:11.123567 |\n" +
        "2020-11-02 10:57:00.000006 |2020-11-02 10:57:00.000117 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_MINUTE, 59, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1701-01-01 00:58:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1867-02-28 02:37:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |1999-04-22 13:27:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2020-11-02 11:56:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_MINUTE, -30, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1700-12-31 23:29:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1867-02-28 01:08:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |1999-04-22 11:58:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2020-11-02 10:27:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_HOUR, 5, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1701-01-01 04:59:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1867-02-28 06:38:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |1999-04-22 17:28:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2020-11-02 15:57:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_HOUR, -3, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1700-12-31 20:59:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1867-02-27 22:38:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |1999-04-22 09:28:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2020-11-02 07:57:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_DAY, 30, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1701-01-30 23:59:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1867-03-30 01:38:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |1999-05-22 12:28:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2020-12-02 10:57:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_WEEK, -3, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1700-12-10 23:59:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1867-02-07 01:38:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |1999-04-01 12:28:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2020-10-12 10:57:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_MONTH, 99, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1709-03-31 23:59:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1875-05-28 01:38:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |2007-07-22 12:28:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2029-02-02 10:57:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_QUARTER, -4, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1699-12-31 23:59:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1866-02-28 01:38:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |1998-04-22 12:28:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2019-11-02 10:57:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
        // ----------------------------------------------------
        sqlText = format("select col1, TIMESTAMPADD(SQL_TSI_YEAR, 10, col1) from t5 order by 1", useSpark);

        expected =
        "COL1            |             2             |\n" +
        "--------------------------------------------------------\n" +
        "1700-12-31 23:59:58.999999 |1710-12-31 23:59:58.999999 |\n" +
        " 1867-02-28 01:38:01.0426  | 1877-02-28 01:38:01.0426  |\n" +
        "1999-04-22 12:28:11.123456 |2009-04-22 12:28:11.123456 |\n" +
        "2020-11-02 10:57:00.000006 |2030-11-02 10:57:00.000006 |";

        rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));

        rs.close();

    }

    @Test
    public void testTimestampDiff() throws Exception {
        String sqlText = format("select TIMESTAMPDIFF(SQL_TSI_FRAC_SECOND, col1, col2) from t4 --SPLICE-PROPERTIES useSpark = %s\n  order by 1 ", useSpark);
        String expected;

        expected = extendedTimestamps ?
        "1          |\n" +
        "---------------------\n" +
        "       1000         |\n" +
        "1911885146937623528 |" :
        "1    |\n" +
        "----------\n" +
        "    0    |\n" +
        "-3551616 |";

        ResultSet rs = methodWatcher.executeQuery(sqlText);
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

    }

    @Test
    public void testJoins() throws Exception {
        String sqlText;
        String expected;

        expected = extendedTimestamps ?
        "COL1          |\n" +
        "-----------------------\n" +
        "0001-01-01 00:00:00.0 |\n" +
        "0002-01-01 00:00:00.0 |\n" +
        "0004-01-01 00:00:00.0 |" :
        "COL1           |\n" +
        "-------------------------\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |\n" +
        "1677-09-20 16:12:43.147 |";

        ResultSet rs;
        List<String> jsList = Arrays.asList("NESTEDLOOP", "MERGE", "SORTMERGE", "BROADCAST");
        for (String js : jsList) {
            sqlText = format("select t1.col1 from t1,t2 --SPLICE-PROPERTIES useSpark = %s,joinStrategy=%s  \n" +
            "where t1.col1=t2.col1", useSpark, js);
            rs = methodWatcher.executeQuery(sqlText);
            assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toString(rs));
            rs.close();
        }
    }

    @Test
    public void updateTest() throws Exception {
        methodWatcher.setAutoCommit(false);
        int updated = methodWatcher.executeUpdate(format("update t1 set col1 = {ts '1999-01-01 00:00:00'}"));
        assertEquals("Incorrect number of records updated", 5, updated);

        ResultSet rs = methodWatcher.executeQuery("select col1,col2 from t1");
        assertEquals("COL1          |COL2 |\n" +
        "-----------------------------\n" +
        "1999-01-01 00:00:00.0 |  1  |\n" +
        "1999-01-01 00:00:00.0 |  2  |\n" +
        "1999-01-01 00:00:00.0 |  3  |\n" +
        "1999-01-01 00:00:00.0 |  4  |\n" +
        "1999-01-01 00:00:00.0 |  5  |",
        TestUtils.FormattedResult.ResultFactory.toString(rs));

        rs.close();

        // Test batch once update...
        /* Enable this test once SPLICE-2214 is fixed...
        updated = methodWatcher.executeUpdate(format("update t11 set col1 = (select col1 from t1 where t11.col2 = t1.col2)"));

        rs = methodWatcher.executeQuery("select col1,col2 from t11");
        assertEquals("COL1          |COL2 |\n" +
        "-----------------------------\n" +
        "1999-01-01 00:00:00.0 |  1  |\n" +
        "1999-01-01 00:00:00.0 |  2  |\n" +
        "1999-01-01 00:00:00.0 |  3  |\n" +
        "1999-01-01 00:00:00.0 |  4  |\n" +
        "1999-01-01 00:00:00.0 |  5  |",
        TestUtils.FormattedResult.ResultFactory.toString(rs));  */

        methodWatcher.rollback();
        methodWatcher.setAutoCommit(true);

        String match = extendedTimestamps ?
        "COL1          |COL2 |\n" +
        "-----------------------------\n" +
        "0001-01-01 00:00:00.0 |  1  |\n" +
        "0002-01-01 00:00:00.0 |  2  |\n" +
        "0003-01-01 00:00:00.0 |  3  |\n" +
        "0004-01-01 00:00:00.0 |  4  |\n" +
        "0005-01-01 00:00:00.0 |  5  |" :
        "COL1           |COL2 |\n" +
        "-------------------------------\n" +
        "1677-09-20 16:12:43.147 |  1  |\n" +
        "1677-09-20 16:12:43.147 |  2  |\n" +
        "1677-09-20 16:12:43.147 |  3  |\n" +
        "1677-09-20 16:12:43.147 |  4  |\n" +
        "1677-09-20 16:12:43.147 |  5  |";

        rs = methodWatcher.executeQuery("select col1,col2 from t1");
        assertEquals(match, TestUtils.FormattedResult.ResultFactory.toString(rs));

        rs.close();

        rs = methodWatcher.executeQuery("select col1,col2 from t11");
        assertEquals(match, TestUtils.FormattedResult.ResultFactory.toString(rs));

        rs.close();
    }

    @AfterClass
    public static void resetConvertOutOfRangeTimeStamps() throws Exception {
        Connection conn = spliceClassWatcher.getOrCreateConnection();

        try (Statement s = conn.createStatement()) {
            s.execute("CALL SYSCS_UTIL.SYSCS_EMPTY_GLOBAL_STATEMENT_CACHE()");
            s.execute("CALL SYSCS_UTIL.INVALIDATE_GLOBAL_DICTIONARY_CACHE()");
            s.execute("call syscs_util.syscs_set_global_database_property('derby.database.convertOutOfRangeTimeStamps', null)");
            s.execute("call syscs_util.syscs_set_global_database_property('splice.function.timestampFormat', null)");
            s.execute("call syscs_util.syscs_set_global_database_property('splice.function.currentTimestampPrecision', null)");

            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t1"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t11"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t2"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t3"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t3b"));
            s.executeUpdate(String.format("DROP TABLE %s IF EXISTS", SCHEMA + ".t4"));
            conn.commit();
        }
    }

    @Test
    public void testCurrentTimeStampOperations() throws Exception {
        String[] units = new String[] {"years", "year", "months", "month", "days", "day",
                "hours", "hour", "minutes", "minute", "seconds", "second"};
        String[] units2 = new String[] {"SQL_TSI_YEAR", "SQL_TSI_YEAR", "SQL_TSI_MONTH", "SQL_TSI_MONTH",
                "SQL_TSI_DAY", "SQL_TSI_DAY", "SQL_TSI_HOUR", "SQL_TSI_HOUR",
                "SQL_TSI_MINUTE", "SQL_TSI_MINUTE", "SQL_TSI_SECOND", "SQL_TSI_SECOND"};

        for (int i=0; i< units.length; i++) {
            String sqlText = format("select current_timestamp - 3 %s, timestampadd(%s, -3, current_timestamp) from sysibm.sysdummy1 --splice-properties useSpark=%s", units[i], units2[i], useSpark);

            try (ResultSet rs = methodWatcher.executeQuery(sqlText)) {
                assertTrue("Result does not match!", !rs.next() || rs.getTimestamp(1) != rs.getTimestamp(2));
            }

            sqlText = format("select current_timestamp + 3 %s, timestampadd(%s, 3, current_timestamp) from sysibm.sysdummy1 --splice-properties useSpark=%s", units[i], units2[i], useSpark);

            try (ResultSet rs = methodWatcher.executeQuery(sqlText)) {
                assertTrue("Result does not match!", !rs.next() || rs.getTimestamp(1) != rs.getTimestamp(2));
            }
        }
    }

    @Test
    public void testAddTimeSpanToTimestampValue() throws Exception {
        try (ResultSet rs = methodWatcher.executeQuery("select timestamp('2020-01-01 00:00:00') + 2 minutes")) {
            rs.next();
            assertEquals(new Timestamp(2020 - 1900, 0, 1, 0, 2, 0, 0), rs.getTimestamp(1));
        }
        try (ResultSet rs = methodWatcher.executeQuery("select 1 minute + timestamp('2020-01-01 00:00:00')")) {
            rs.next();
            assertEquals(new Timestamp(2020 - 1900, 0, 1, 0, 1, 0, 0), rs.getTimestamp(1));
        }
        try (ResultSet rs = methodWatcher.executeQuery("select timestamp('2020-01-01 00:00:00') + 2 minutes + 30 seconds")) {
            rs.next();
            assertEquals(new Timestamp(2020 - 1900, 0, 1, 0, 2, 30, 0), rs.getTimestamp(1));
        }
    }

    @Test
    public void testTimestampAutoConversionDB_10914() throws Exception {
        methodWatcher.executeUpdate("drop table DB_10914 if exists");
        methodWatcher.executeUpdate("create table DB_10914(t timestamp)");
        methodWatcher.executeUpdate("insert into DB_10914 values ('2020-01-01 10:00:00.123456'), ('2020-01-02 10:00:00.123456')");
        try (PreparedStatement ps = methodWatcher.prepareStatement("select * from DB_10914 where t = ? || ' 10:00:00.123456'")) {
            ps.setString(1, "2020-01-01");
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertEquals(
                        "T             |\n" +
                        "----------------------------\n" +
                        "2020-01-01 10:00:00.123456 |",
                        TestUtils.FormattedResult.ResultFactory.toString(rs));
            }
            ps.setString(1, "2020-01-05");
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertFalse(rs.next());
            }
        }
        try (PreparedStatement ps = methodWatcher.prepareStatement(
                "select * from DB_10914 where t between '2020-01-01' || '-00.00.00.000000' and ? || '-23.59.59.999999'")) {
            ps.setString(1, "2020-01-01");
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertEquals(
                        "T             |\n" +
                        "----------------------------\n" +
                        "2020-01-01 10:00:00.123456 |",
                        TestUtils.FormattedResult.ResultFactory.toString(rs));
            }
            ps.setString(1, "2020-01-02");
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertEquals(
                        "T             |\n" +
                        "----------------------------\n" +
                        "2020-01-01 10:00:00.123456 |\n" +
                        "2020-01-02 10:00:00.123456 |",
                        TestUtils.FormattedResult.ResultFactory.toString(rs));
            }
        }
    }
    @Test
    public void testTimestampFunctionFromLongVarchar_10926() throws Exception {
        try (PreparedStatement ps = methodWatcher.prepareStatement("select timestamp(? || '-23.59.59.999999')")) {
            ps.setString(1, "2020-01-01");
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertEquals(
                        "1             |\n" +
                                "----------------------------\n" +
                                "2020-01-01 23:59:59.999999 |",
                        TestUtils.FormattedResult.ResultFactory.toString(rs));
            }
        }
    }

    @Test
    public void testTimestampInsertOnSpark6DigitsPrecision() throws Exception {
        methodWatcher.executeUpdate("insert into t6 values ( TIME( '16:03:00'), TIMESTAMP( '1996-08-24 16:03:00.999999') )");
        String expected =
            "FIPS1  |           FIPS2           |\n" +
            "--------------------------------------\n" +
            "16:03:00 |1996-08-24 16:03:00.999999 |";
        String query = format("SELECT * FROM t6 --splice-properties useSpark=%s", useSpark);
        //testQuery(query, expected, methodWatcher);
        methodWatcher.executeUpdate("insert into t6 values ( TIME( '14:03:00'), TIMESTAMP( '0001-01-01 00:00:00.123456') )");
        methodWatcher.executeUpdate("insert into t6 values ( TIME( '13:03:00'), TIMESTAMP( '9999-12-31 23:59:59.123456') )");
        expected =
            "FIPS1  |           FIPS2           |\n" +
            "--------------------------------------\n" +
            "13:03:00 |9999-12-31 23:59:59.123456 |\n" +
            "14:03:00 |0001-01-01 00:00:00.123456 |\n" +
            "16:03:00 |1996-08-24 16:03:00.999999 |";
        testQuery(query, expected, methodWatcher);

        // Not allowed to specify more than 6 decimal digits in a timestamp.
        testFail("XJ207", "insert into t6 values ( TIME( '13:03:00'), TIMESTAMP( '9999-12-31 23:59:59.1234567') )", methodWatcher);

        methodWatcher.executeUpdate("delete from t6");
    }

    private void withFormat(String format) throws Exception {
        methodWatcher.execute(String.format("call SYSCS_UTIL.SYSCS_SET_GLOBAL_DATABASE_PROPERTY( 'splice.function.timestampFormat', '%s' )", format));
    }

    private void shouldEqual(String inputTimestamp, String expectedTimestamp) throws SQLException {
        try(ResultSet rs = methodWatcher.executeQuery(String.format(
                "select char(timestamp('%s')), length(char(timestamp('%s'))) from sysibm.sysdummy1 --SPLICE-PROPERTIES useSpark = %s", inputTimestamp, inputTimestamp, useSpark))) {
            Assert.assertTrue(rs.next());
            Assert.assertEquals(expectedTimestamp, rs.getString(1));
            Assert.assertEquals(expectedTimestamp.length(), rs.getInt(2));
            Assert.assertFalse(rs.next());
        }

        // make sure when upper/lower is used we are constructing the right cast node with the correct size
        try(ResultSet rs = methodWatcher.executeQuery(String.format(
                "select upper(timestamp('%s')), typeof(upper(timestamp('%s'))) from sysibm.sysdummy1 --SPLICE-PROPERTIES useSpark = %s", inputTimestamp, inputTimestamp, useSpark))) {
            Assert.assertTrue(rs.next());
            Assert.assertEquals(expectedTimestamp, rs.getString(1));
            Assert.assertEquals(String.format("VARCHAR(%d) NOT NULL", expectedTimestamp.length()), rs.getString(2));
            Assert.assertFalse(rs.next());
        }
    }

    @Test
    public void testConfigurableTimestampPrecision() throws Exception {
        withFormat("yyyy-MM-dd HH:mm:ss"); shouldEqual("2020-11-30 19:11:12", "2020-11-30 19:11:12");
        withFormat("yyyy-MM-dd HH:mm:ss"/*0*/); shouldEqual("2020-11-30 19:11:12.123456", "2020-11-30 19:11:12");
        withFormat("yyyy-MM-dd HH:mm:ss.SSS"/*3*/); shouldEqual("2020-11-30 19:11:12", "2020-11-30 19:11:12.000");
        withFormat("yyyy-MM-dd HH:mm:ss.SSS"/*3*/); shouldEqual("2020-11-30 19:11:12.123456", "2020-11-30 19:11:12.123");
        withFormat("yyyy-MM-dd HH:mm:ss.SSSSSS"/*6*/); shouldEqual("2020-11-30 19:11:12", "2020-11-30 19:11:12.000000");
        withFormat("yyyy-MM-dd HH:mm:ss.SSSSSS"/*6*/); shouldEqual("2020-11-30 19:11:12.123456", "2020-11-30 19:11:12.123456");

        withFormat("MM/dd/uuuu, hh:mm:ss.SS a"); shouldEqual("2020-11-30 19:11:12.123456", "11/30/2020, 07:11:12.12 PM");

        withFormat("yyyy-MM-dd-HH.mm.ss.SSSSSSSS"/*8*/);
        shouldEqual("2020-11-30 19:11:12.123456", "2020-11-30-19.11.12.12345600");

        // test code in UserTypeConstantNode
        Assert.assertEquals("1700-12-31-23.59.58.99999900", methodWatcher.executeGetString( "values( char({ts'1700-12-31 23:59:58.999999'}) )", 1));

        // reset to default
        withFormat(CompilerContext.DEFAULT_TIMESTAMP_FORMAT);
        Assert.assertEquals("1700-12-31 23:59:58.999999", methodWatcher.executeGetString( "values( char({ts'1700-12-31 23:59:58.999999'}) )", 1));
    }

    @Test
    public void testCurrentTimestampPrecision() throws Exception {

        try {
            // we might get very unlucky when timestamps end in 0s, e.g.
            // 2020-12-06 21:50:13.123456000 would have length of 2020-12-06 21:50:13.123456, even if precision is set to 9
            // to avoid sporadics, we try this 100 times
            for (int i = 0; i < 100; i++) {
                boolean bOK;
                methodWatcher.execute("call SYSCS_UTIL.SYSCS_SET_GLOBAL_DATABASE_PROPERTY( 'splice.function.currentTimestampPrecision', '1' )");
                bOK = "2020-12-06 21:50:13.1".length()
                        == methodWatcher.executeGetString("values current timestamp", 1).length();
                methodWatcher.execute("call SYSCS_UTIL.SYSCS_SET_GLOBAL_DATABASE_PROPERTY( 'splice.function.currentTimestampPrecision', '6' )");
                bOK = bOK && "2020-12-06 21:50:13.123456".length()
                        == methodWatcher.executeGetString("values current timestamp", 1).length();

                if (bOK) return;
            }
            Assert.fail("current timestamp precision didn't work");
        }
        finally {
            methodWatcher.execute("call SYSCS_UTIL.SYSCS_SET_GLOBAL_DATABASE_PROPERTY( 'splice.function.currentTimestampPrecision', null )");
        }
    }

    private static void verifyCurrentTimezoneInvalid(String query) throws Exception {
        try {
            methodWatcher.executeQuery(query);
        } catch(Exception e) {
            Assert.assertTrue(e instanceof SQLException);
            SQLException sqlException = (SQLException)e;
            Assert.assertEquals(SQLState.LANG_INVALID_TIMEZONE_OPERATION, sqlException.getSQLState());
        }
    }

    @Test
    public void testCurrentTimezoneInvalidExpressions() throws Exception {
        verifyCurrentTimezoneInvalid("select current_timezone - current_timestamp from sysibm.sysdummy1");
        verifyCurrentTimezoneInvalid("select date('2020-10-10') - current_timezone from sysibm.sysdummy1");
    }
}
