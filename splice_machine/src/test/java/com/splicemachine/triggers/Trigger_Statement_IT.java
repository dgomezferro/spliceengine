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

package com.splicemachine.triggers;

import com.splicemachine.db.client.am.ResultSet;
import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceUnitTest;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.derby.test.framework.TestConnection;
import com.splicemachine.test.SerialTest;
import com.splicemachine.test_dao.TriggerBuilder;
import com.splicemachine.test_dao.TriggerDAO;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import splice.com.google.common.collect.Lists;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collection;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test STATEMENT triggers.
 */
@Category(value = {SerialTest.class})
@RunWith(Parameterized.class)
public class Trigger_Statement_IT extends SpliceUnitTest {

    private static final String SCHEMA = Trigger_Statement_IT.class.getSimpleName();

    @ClassRule
    public static SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(SCHEMA);

    @ClassRule
    public static SpliceWatcher classWatcher = new SpliceWatcher(SCHEMA);

    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher(SCHEMA);

    private TriggerBuilder tb = new TriggerBuilder();
    private TriggerDAO triggerDAO = new TriggerDAO(methodWatcher.getOrCreateConnection());

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        Collection<Object[]> params = Lists.newArrayListWithCapacity(2);
        params.add(new Object[]{"jdbc:splice://localhost:1527/splicedb;user=splice;password=admin;useSpark=true"});
        params.add(new Object[]{"jdbc:splice://localhost:1527/splicedb;user=splice;password=admin"});
        return params;
    }

    private String connectionString;

    public Trigger_Statement_IT(String connecitonString) {
        this.connectionString = connecitonString;
    }

    private static void repopulateFeatureTables() throws Exception {
        classWatcher.executeUpdate("delete from FeatureTable");
        classWatcher.executeUpdate("delete from FeatureTable_History");
        classWatcher.executeUpdate("insert into FeatureTable values (1, timestamp('2020-3-14 12:00:00'), timestamp('2020-3-14 12:00:00'), timestamp('2020-3-14 12:00:00'), 1, 2)");
        classWatcher.executeUpdate("insert into FeatureTable values (1, timestamp('2020-3-14 10:00:00'), timestamp('2020-3-14 12:00:01'), timestamp('2020-3-14 12:00:01'), 3, 4)");
        classWatcher.executeUpdate("insert into FeatureTable_History values (1, timestamp('2020-3-14 12:00:01'), timestamp('2020-3-14 12:00:01'), 1, 2)");

    }
    @BeforeClass
    public static void createSharedTables() throws Exception {
        classWatcher.executeUpdate("drop table if exists T");
        classWatcher.executeUpdate("drop table if exists RECORD");
        classWatcher.executeUpdate("drop table if exists t1");
        classWatcher.executeUpdate("drop table if exists t3");
        classWatcher.executeUpdate("drop table if exists FeatureTable");
        classWatcher.executeUpdate("drop table if exists FeatureTable_History");
        classWatcher.executeUpdate("create table T (a int, b int, c int)");
        classWatcher.executeUpdate("create table RECORD (txt varchar(99))");
        classWatcher.executeUpdate("create table t1 (a1 int, b1 varchar(30), c1 varchar(30), primary key (a1))");
        classWatcher.executeUpdate("create table t3 (a3 int, b3 varchar(30), c3 varchar(30))");
        classWatcher.executeUpdate("create table FeatureTable (ENTITY_KEY int, LAST_UPDATE_TS timestamp, ASOF_TS timestamp, UNTIL_TS timestamp, feature1 int, feature2 int)");
        classWatcher.executeUpdate("create table FeatureTable_History (ENTITY_KEY int, ASOF_TS timestamp, UNTIL_TS timestamp, feature1 int, feature2 int)");
        classWatcher.
            executeUpdate("CREATE TRIGGER FeatureTable_History_UPDATE_PREHISTORIC\n" +
                        "AFTER UPDATE ON FeatureTable\n" +
                        "REFERENCING OLD_TABLE AS OLDW NEW_TABLE AS NEWW\n" +
                        "FOR EACH STATEMENT\n" +
                        "WHEN (\n" +
                        "     (NOT EXISTS (SELECT 1 FROM FeatureTable_History H,NEWW N WHERE H.ENTITY_KEY=N.ENTITY_KEY AND N.LAST_UPDATE_TS >= H.ASOF_TS AND N.LAST_UPDATE_TS < H.UNTIL_TS))\n" +
                        "     AND\n" +
                        "     (EXISTS (SELECT 1 FROM OLDW,NEWW WHERE OLDW.ENTITY_KEY=NEWW.ENTITY_KEY AND OLDW.LAST_UPDATE_TS < NEWW.LAST_UPDATE_TS))\n" +
                        ")\n" +
                        "\n" +
                        "    -- creates new history rows, only if the new update occurs after the last one\n" +
                        "INSERT INTO FeatureTable_History (ENTITY_KEY, ASOF_TS, UNTIL_TS, feature1, feature2)\n" +
                        "        SELECT N.ENTITY_KEY, N.LAST_UPDATE_TS, MIN(H.ASOF_TS) MIN_ASOF, N.feature1, N.feature2\n" +
                        "        FROM OLDW O\n" +
                        "        INNER JOIN\n" +
                        "             NEWW N ON O.ENTITY_KEY=N.ENTITY_KEY AND O.LAST_UPDATE_TS > N.LAST_UPDATE_TS\n" +
                        "        INNER JOIN\n" +
                        "             FeatureTable_History H ON H.ENTITY_KEY = N.ENTITY_KEY\n" +
                        "        GROUP BY 1,2,4,5");
    }

    @Before
    public void resetTables() throws Exception {
        triggerDAO.dropAllTriggers(SCHEMA, "T");
        methodWatcher.executeUpdate("delete from T");
        methodWatcher.executeUpdate("delete from RECORD");
        methodWatcher.executeUpdate("delete from t1");
        methodWatcher.executeUpdate("delete from t3");
        methodWatcher.executeUpdate("insert into T values(1,1,1),(2,2,2),(3,3,3),(4,4,4),(5,5,5),(6,6,6)");
        repopulateFeatureTables();
        Connection conn = new TestConnection(DriverManager.getConnection(connectionString, new Properties()));
        conn.setSchema(SCHEMA.toUpperCase());
        methodWatcher.setConnection(conn);
    }
    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // AFTER statement triggers
    //
    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Test
    public void afterUpdate() throws Exception {
        methodWatcher.executeUpdate(tb.after().update().on("T").statement().then("INSERT INTO RECORD VALUES('update')").build());

        // when - update
        methodWatcher.executeUpdate("update T set b = b * 2 where a <= 4");
        // then - verify trigger has fired
        Assert.assertEquals(1L,(long)methodWatcher.query("select count(*) from RECORD where txt = 'update'"));

        // when -- update twice more
        methodWatcher.executeUpdate("update T set b = b * 2 where a <= 2");
        methodWatcher.executeUpdate("update T set b = b * 2 where a <= 2");

        // then - verify trigger has fired twice more
        Assert.assertEquals(3L,(long)methodWatcher.query("select count(*) from RECORD where txt = 'update'"));
    }

    /* When an update succeeds but the trigger action fails then the changes from the triggering statement should be rolled back. */
    @Test
    public void afterUpdateTriggerFailureRollsBackTriggeringStatement() throws Exception {
        methodWatcher.executeUpdate(tb.after().update().on("T").statement().then("select 1/0 from sys.systables").build());

        // when - update causes trigger action that fails
        try {
            methodWatcher.executeUpdate("update T set b=0,c=0");
            fail("expected trigger to blow up");
        } catch (Exception e) {
            assertEquals("Attempt to divide by zero.", e.getMessage());
        }

        // most important original update changes should not be visible
        Assert.assertEquals(0L,(long)methodWatcher.query("select count(*) from T where b=0 or c=0"));
    }

    /* Need a test that fires the same trigger more than 16 times to verify it doesn't blow up because of failure
     * to reset recursion depth counter. */
    @Test
    public void afterUpdateRepeat() throws Exception {
        methodWatcher.executeUpdate(tb.after().update().on("T").statement().then("INSERT INTO RECORD VALUES('update')").build());

        /* Update triggers should be fired even if the value is updated to the same number. */
        for (int i = 0; i < 32; i++) {
            methodWatcher.executeUpdate("update T set a = 1 where a = 1");
        }

        // then - verify trigger has fired
        Assert.assertEquals(32L,(long)methodWatcher.query("select count(*) from RECORD where txt = 'update'"));
    }

    /* Trigger on subset of columns */
    @Test
    public void afterUpdateOfColumns() throws Exception {
        methodWatcher.executeUpdate(tb.after().update().of("b,c").on("T").statement().then("INSERT INTO RECORD VALUES('update')").build());

        // when - update
        methodWatcher.executeUpdate("update T set a = a * 2");
        // then - verify trigger has fired
        Assert.assertEquals(0L,(long)methodWatcher.query("select count(*) from RECORD where txt = 'update'"));

        // when -- update twice more
        methodWatcher.executeUpdate("update T set b = b * 2");
        Assert.assertEquals(1L,(long)methodWatcher.query("select count(*) from RECORD where txt = 'update'"));
        methodWatcher.executeUpdate("update T set c = c * 2");
        Assert.assertEquals(2L,(long)methodWatcher.query("select count(*) from RECORD where txt = 'update'"));
    }

    @Test
    public void afterInsert() throws Exception {
        methodWatcher.executeUpdate(tb.after().insert().on("T").statement().then("INSERT INTO RECORD VALUES('insert')").build());

        // one insert
        methodWatcher.executeUpdate("insert into T select * from T");
        Assert.assertEquals(1L,(long)methodWatcher.query("select count(*) from RECORD where txt='insert'"));

        // two more inserts
        methodWatcher.executeUpdate("insert into T select * from T");
        methodWatcher.executeUpdate("insert into T select * from T");
        Assert.assertEquals(3L,(long)methodWatcher.query("select count(*) from RECORD where txt='insert'"));

        // Insert VALUES - a special case in splice at the time of writing, different code is executed.
        methodWatcher.executeUpdate("insert into T values (1,1,1),(2,2,2),(3,3,3)");
        Assert.assertEquals(4L,(long)methodWatcher.query("select count(*) from RECORD where txt='insert'"));
    }

    @Test
    public void afterInsertMultipleTriggersForSameEvent() throws Exception {
        methodWatcher.executeUpdate(tb.named("afterInsertTrig01").after().insert().on("T").statement()
                .then("INSERT INTO RECORD VALUES('insert01')").build());
        methodWatcher.executeUpdate(tb.named("afterInsertTrig02").after().insert().on("T").statement()
                .then("INSERT INTO RECORD VALUES('insert02')").build());

        // one insert
        methodWatcher.executeUpdate("insert into T select * from T");
        Assert.assertEquals(1L,(long)methodWatcher.query("select count(*) from RECORD where txt='insert01'"));
        Assert.assertEquals(1L,(long)methodWatcher.query("select count(*) from RECORD where txt='insert02'"));
    }

    @Test
    public void afterDelete() throws Exception {
        methodWatcher.executeUpdate(tb.after().delete().on("T").statement().then("INSERT INTO RECORD VALUES('delete')").build());

        // trigger fires on single delete
        methodWatcher.executeUpdate("delete from T where a = 4");
        Assert.assertEquals(1L,(long)methodWatcher.query("select count(*) from RECORD where txt = 'delete'"));

        // delete two rows, trigger still fires once
        methodWatcher.executeUpdate("delete from T where a = 5 or a = 6");
        Assert.assertEquals(2L,(long)methodWatcher.query("select count(*) from RECORD where txt = 'delete'"));
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // BEFORE statement triggers -- before triggers are currently not that useful.  You cannot have actions that
    // insert/update/delete or call stored procedures.  For now I test that before triggers are actually invoked
    // by having the trigger action throw an exception.
    //
    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Test
    public void beforeUpdate() throws Exception {
        methodWatcher.executeUpdate(tb.before().update().on("T").statement().then("select 1/0 from sys.systables").build());

        assertQueryFails("update T set b = 99", "Attempt to divide by zero.");

        // triggering statement should have had no affect.
        Assert.assertEquals(0L,(long)methodWatcher.query("select count(*) from T where b = 99"));
    }

    @Test
    public void beforeInsert() throws Exception {
        methodWatcher.executeUpdate(tb.before().insert().on("T").statement().then("select 1/0 from sys.systables").build());

        assertQueryFails("insert into T select * from T", "Attempt to divide by zero.");
        assertQueryFails("insert into T values(99,99,99)", "Attempt to divide by zero.");

        // triggering statement should have had no affect.
        Assert.assertEquals(0L,(long)methodWatcher.query("select count(*) from T where a = 99"));
    }

    @Test
    public void beforeDelete() throws Exception {
        methodWatcher.executeUpdate(tb.before().delete().on("T").statement().then("select 1/0 from sys.systables").build());

        Long count = methodWatcher.query("select count(*) from T where a = 1");
        assertQueryFails("delete from T where a = 1", "Attempt to divide by zero.");

        // triggering statement should have had no affect.

        Assert.assertEquals(count,methodWatcher.query("select count(*) from T where a = 1"));
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // Recursive triggers.  Currently recursive *statement* triggers will always fail.  This won't be the case when
    //                      we implement restrictions.  For now assert the failure semantics: triggering statement
    //                      is rolled back.
    //
    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Test
    public void afterRecursiveUpdateDelete() throws Exception {
        // given
        methodWatcher.executeUpdate(tb.named("deleteTrigRecursive").after().delete().on("T").statement()
                .then("delete from T").build());
        methodWatcher.executeUpdate(tb.named("updateTrigRecursive").after().update().on("T").statement()
                .then("update T set c = c +1").build());

        // when
        assertQueryFails("delete from T", "Maximum depth of nested triggers was exceeded.");
        assertQueryFails("update T set b = b + 1", "Maximum depth of nested triggers was exceeded.");

        // then
        Assert.assertEquals("expected unchanged row count",6L,(long)methodWatcher.query("select count(*) from T"));
    }

    @Test
    public void afterRecursiveInsertOverValues() throws Exception {
        // given
        methodWatcher.executeUpdate(tb.after().insert().on("T").statement().then("insert into T values(1,1,1)").build());

        Long count = methodWatcher.query("select count(*) from T");
        // when
        assertQueryFails("insert into T select * from T", "Maximum depth of nested triggers was exceeded.");
        assertQueryFails("insert into T values(1,1,1)", "Maximum depth of nested triggers was exceeded.");

        // then
        Assert.assertEquals("expected unchanged row count",count,methodWatcher.query("select count(*) from T"));
    }

    @Test
    @Ignore("DB-5474")
    public void afterRecursiveInsertOverSelect() throws Exception {
        // given
        methodWatcher.executeUpdate(tb.after().insert().on("T").statement().then("insert into T select * from T").build());

        // when
        assertQueryFails("insert into T select * from T", "Maximum depth of nested triggers was exceeded.");
        assertQueryFails("insert into T values(1,1,1)", "Maximum depth of nested triggers was exceeded.");

        // then
        Assert.assertEquals("expected unchanged row count",6L,(long)methodWatcher.query("select count(*) from T"));
    }

    /* DB-3351 */
    @Test
    public void recursiveTriggerNotRecursiveAfterDropped() throws Exception {
        // Takes too long to run on Spark.
        if (connectionString.contains("useSpark"))
            return;
        methodWatcher.executeUpdate("create table a (b int,c int)");
        methodWatcher.executeUpdate("insert into a values (1,2)");

        methodWatcher.executeUpdate(tb.named("trig1").after().delete().on("a").statement()
                .then("insert into a values (1,2)").build());

        methodWatcher.executeUpdate("delete from a");

        Assert.assertEquals(1L,(long)methodWatcher.query("select count(*) from a"));

        // when - add recursive trigger
        methodWatcher.executeUpdate(tb.named("trig2").after().delete().on("a").statement()
                .then("delete from a").build());

        assertQueryFails("delete from a", "Maximum depth of nested triggers was exceeded.");

        methodWatcher.executeUpdate("drop trigger trig2");

        methodWatcher.executeUpdate("drop table a");
    }

    @Test
    public void DB_10266() throws Exception {
        methodWatcher.executeUpdate("create trigger trig22\n" +
                                    "after insert on t1\n" +
                                    "referencing new_table as NT\n" +
                                    "for each statement\n" +
                                    "insert into t3\n" +
                                    "select * from NT\n");

        methodWatcher.executeUpdate("insert into t1 values (1, 'abcdefg', 'dummy')");
        Assert.assertEquals(1, (int)methodWatcher.query("select a3 from t3"));
        methodWatcher.executeUpdate("drop trigger trig22");
        methodWatcher.executeUpdate("delete from t3");

        methodWatcher.executeUpdate("create trigger trig22\n" +
                                    "after update on t1\n" +
                                    "referencing new_table as NT\n" +
                                    "for each statement\n" +
                                    "insert into t3 select * from NT\n");
        methodWatcher.executeUpdate("update t1 set a1 = 2");
        Assert.assertEquals(2, (int)methodWatcher.query("select a3 from t3"));
        methodWatcher.executeUpdate("drop trigger trig22");
        methodWatcher.executeUpdate("create trigger trig22\n" +
                                    "after delete on t1\n" +
                                    "referencing old_table as OT\n" +
                                    "for each statement\n" +
                                    "delete from t3 where a3 in (select a1 from OT)\n");
        methodWatcher.executeUpdate("delete from t1");
        Assert.assertEquals(0, (long)methodWatcher.query("select count(*) from t3"));
        methodWatcher.executeUpdate("drop trigger trig22");

    }

    @Test
    public void DB_11252() throws Exception {
        methodWatcher.executeUpdate("update FeatureTable set LAST_UPDATE_TS = timestamp('2020-3-14 11:00:00') where ENTITY_KEY = 1");
        String sqlText = "select * from FeatureTable_History";
        String expected =
            "ENTITY_KEY |       ASOF_TS        |      UNTIL_TS        |FEATURE1 |FEATURE2 |\n" +
            "------------------------------------------------------------------------------\n" +
            "     1     |2020-03-14 11:00:00.0 |2020-03-14 12:00:01.0 |    1    |    2    |\n" +
            "     1     |2020-03-14 11:00:00.0 |2020-03-14 12:00:01.0 |    3    |    4    |\n" +
            "     1     |2020-03-14 12:00:01.0 |2020-03-14 12:00:01.0 |    1    |    2    |";
        testQuery(sqlText, expected, methodWatcher);
    }

@Test
    public void sparkHint() throws Exception {
        methodWatcher.executeUpdate("create trigger trig22\n" +
                                    "after insert on t1\n" +
                                    "referencing new_table as NT\n" +
                                    "for each statement\n" +
                                    "insert into t3 --splice-properties useSpark=true\n" +
                                    "select * from NT\n");

        // The following should not throw a ClassCastException.
        methodWatcher.executeUpdate("insert into t1 values (1, 'abcdefg', 'dummy')");
        Assert.assertEquals(1, (int)methodWatcher.query("select a3 from t3"));
        methodWatcher.executeUpdate("drop trigger trig22");
        methodWatcher.executeUpdate("delete from t3");
    }

    private void assertQueryFails(String query, String expectedError) {
        try {
            methodWatcher.executeUpdate(query);
            fail("expected to fail with message = " + expectedError);
        } catch (Exception e) {
            assertEquals(expectedError, e.getMessage());
        }
    }

}
