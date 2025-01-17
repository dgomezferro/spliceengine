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
 *
 */

package com.splicemachine.jdbc;

import com.splicemachine.db.shared.common.reference.SQLState;
import com.splicemachine.derby.test.framework.*;
import com.splicemachine.homeless.TestUtils;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;

import java.sql.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

public class JdbcApiIT {

    private static final String CLASS_NAME = JdbcApiIT.class.getSimpleName().toUpperCase();
    private static final SpliceWatcher spliceClassWatcher = new SpliceWatcher(CLASS_NAME);
    private static final SpliceSchemaWatcher schemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);
    protected static final SpliceTableWatcher A_TABLE = new SpliceTableWatcher("A",schemaWatcher.schemaName,
            "(a1 int)");
    protected static final SpliceTableWatcher DEC_TABLE = new SpliceTableWatcher("\"DEC\"",schemaWatcher.schemaName,
            "(dc1 decimal(4,2), dc2 decimal(2,0), dc3 decimal(2,2))");

    private static final String SCHEMA_A = CLASS_NAME + "_tom";
    private static final String SCHEMA_B = "\"" + CLASS_NAME + "_jerry\"";

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
            .around(schemaWatcher)
            .around(A_TABLE)
            .around(new SpliceDataWatcher() {
                @Override
                protected void starting(Description description) {
                    try {
                        spliceClassWatcher.execute("insert into a values 1,2");
                        try (PreparedStatement ps = spliceClassWatcher.prepareStatement("insert into a select a1 + (select count(*) from a) from a")) {
                            for (int i = 0; i < 10; i++) {
                                ps.execute();
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        spliceClassWatcher.closeAll();
                    }
                }
            })
            .around(DEC_TABLE)
            .around(new SpliceDataWatcher() {
                @Override
                protected void starting(Description description) {
                    try {
                        spliceClassWatcher.execute("insert into \"DEC\" values (11.11, -12, -0.13)");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        spliceClassWatcher.closeAll();
                    }
                }
            });

    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher(CLASS_NAME);

    private TestConnection conn;

    @Before
    public void setUp() throws Exception{
        conn = methodWatcher.getOrCreateConnection();
        conn.setAutoCommit(false);
    }

    @After
    public void tearDown() throws Exception{
        conn.rollback();
        conn.reset();
    }

    @Rule
    public Timeout globalTimeout= new Timeout(15, TimeUnit.SECONDS);


    @Test(expected = SQLTimeoutException.class)
    public void testTimeoutSpark() throws Exception {
        String sql = "select count(*) from a --splice-properties useSpark=true \n" +
                "natural join a a1 natural join a a2 natural join a a3";
        try(Statement s = conn.createStatement()){
            s.setQueryTimeout(2);
            ResultSet rs = s.executeQuery(sql);
        }
    }

    @Test(expected = SQLTimeoutException.class)
    public void testTimeoutControl() throws Exception {
        String sql = "select count(*) from a --splice-properties useSpark=false \n" +
                "natural join a a1 natural join a a2 natural join a a3";
        try(Statement s = conn.createStatement()){
            s.setQueryTimeout(2);
            ResultSet rs = s.executeQuery(sql);
        }
    }

    @Test
    public void testUrlWithSchema() throws Exception {
        try (Connection connection = SpliceNetConnection.newBuilder().schema(CLASS_NAME).build()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeQuery("select * from a");
            }
        }
    }

    @Test
    public void testUrlWithNonExistSchema() throws Exception {
         try (Connection connection = SpliceNetConnection.newBuilder().schema("nonexist").build()) {
             Assert.fail("Connect to non exist schema should fail");
         } catch (SQLException e) {
             Assert.assertEquals("Upexpected failure: "+ e.getMessage(), e.getSQLState(),
                     SQLState.LANG_SCHEMA_DOES_NOT_EXIST);
         }
    }

    @Test
    public void testUrlWithCurrentSchemaPropertyWithRegularSchemaName() throws Exception {
        spliceClassWatcher.execute(format("create schema %s", SCHEMA_A));
        try (Connection connection = SpliceNetConnection.newBuilder().currentSchema(SCHEMA_A).build()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("values current schema")) {
                    String expected = "1       |\n" +
                            "---------------\n" +
                            "JDBCAPIIT_TOM |";
                    assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
                }
            }
        }

        spliceClassWatcher.execute(format("drop schema %s restrict", SCHEMA_A));
    }

    @Test
    public void testUrlWithCurrentSchemaPropertyWithQuotedSchemaName() throws Exception {
        spliceClassWatcher.execute(format("create schema %s", SCHEMA_B));

        try (Connection connection = SpliceNetConnection.newBuilder().currentSchema(SCHEMA_B).build()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("values current schema")) {
                    String expected = "1        |\n" +
                            "-----------------\n" +
                            "JDBCAPIIT_jerry |";
                    assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
                }
            }
        }

        spliceClassWatcher.execute(format("drop schema %s restrict", SCHEMA_B));
    }

    @Test
    public void testCancelSpark() throws Exception {
        testCancel(true);
    }

    @Test
    public void testCancelControl() throws Exception {
        testCancel(false);
    }


    public void testCancel(boolean spark) throws Exception {
        String sql = "select count(*) from a --splice-properties useSpark=" +spark+" \n" +
                "natural join a a1 natural join a a2 natural join a a3";
        try(Statement s = conn.createStatement()){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                        s.cancel();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            ResultSet rs = s.executeQuery(sql);
        } catch (SQLTimeoutException se) {
            assertEquals("57014", se.getSQLState());
        }
    }

    @Test
    public void testSetClientInfo() throws Exception {
        conn.setClientInfo("ApplicationName", "SampleJDBCTest");
        conn.setClientInfo("ClientUser", "test");
        conn.setClientInfo("ClientHostname", "localhost");

        try {
            conn.setClientInfo("ClientID", "1234");
        } catch (SQLException e) {
            assertEquals("XCY02", e.getSQLState());
        }
    }

    @Test
    public void testJdbcDb2CompatibleMode() throws Exception {
        try (Connection connection = SpliceNetConnection.newBuilder().schema(CLASS_NAME).db2CompatibleMode(true).build()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("select * from \"DEC\"")) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int[] expectedDisplaySize = {6, 4, 4};
                    String[] expected = {"11.11", "-12.", "-.13"};
                    rs.next();
                    for (int i = 0; i < rsmd.getColumnCount(); i++) {
                        assertEquals(expectedDisplaySize[i], rsmd.getColumnDisplaySize(i + 1));
                        assertEquals(expected[i], rs.getString(i + 1));  // note: getString(), not getBigDecimal()
                    }
                }
            }
        }

        try (Connection connection = SpliceNetConnection.newBuilder().schema(CLASS_NAME).db2CompatibleMode(false).build()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("select * from \"DEC\"")) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int[] expectedDisplaySize = new int[]{6, 3, 5};
                    String[] expected = new String[]{"11.11", "-12", "-0.13"};
                    rs.next();
                    for (int i = 0; i < rsmd.getColumnCount(); i++) {
                        assertEquals(expectedDisplaySize[i], rsmd.getColumnDisplaySize(i + 1));
                        assertEquals(expected[i], rs.getString(i + 1));  // note: getString(), not getBigDecimal()
                    }
                }
            }
        }

        try (Connection connection = SpliceNetConnection.newBuilder().schema(CLASS_NAME).build()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("select * from \"DEC\"")) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int[] expectedDisplaySize = new int[]{6, 3, 5};
                    String[] expected = new String[]{"11.11", "-12", "-0.13"};
                    rs.next();
                    for (int i = 0; i < rsmd.getColumnCount(); i++) {
                        assertEquals(expectedDisplaySize[i], rsmd.getColumnDisplaySize(i + 1));
                        assertEquals(expected[i], rs.getString(i + 1));  // note: getString(), not getBigDecimal()
                    }
                }
            }
        }
    }
}
