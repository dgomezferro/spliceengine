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

package com.splicemachine.subquery;

import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.homeless.TestUtils;
import org.junit.*;

import java.sql.Connection;

import static com.splicemachine.subquery.SubqueryITUtil.*;

/**
 * Test NOT-EXIST subquery flattening for subqueries with unions.
 */
public class Subquery_Flattening_NotExists_Union_IT {

    private static final String SCHEMA = Subquery_Flattening_NotExists_Union_IT.class.getSimpleName();

    @ClassRule
    public static SpliceSchemaWatcher schemaWatcher = new SpliceSchemaWatcher(SCHEMA);

    @ClassRule
    public static SpliceWatcher classWatcher = new SpliceWatcher(SCHEMA);

    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher(SCHEMA);

    @BeforeClass
    public static void createSharedTables() throws Exception {
        TestUtils.executeSqlFile(classWatcher.getOrCreateConnection(), "subquery/SubqueryFlatteningTestTables.sql", "");
    }

    @Test
    public void union_unCorrelated() throws Exception {
        // union of empty tables
        String sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE union select 1 from EMPTY_TABLE)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, RESULT_ALL_OF_A);

        // union one non-empty first subquery
        sql = "select * from A where NOT exists(select 1 from C union select 1 from EMPTY_TABLE)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "");

        // union one non-empty second subquery
        sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE union select 1 from C)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "");

        // union, three unions
        sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE union select 1 from C union select 1 from D)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "");

        // non-empty tables, where subquery predicates eliminate all rows
        sql = "select * from A where NOT exists(select 1 from C where c1 > 999999 union select 1 from D where d1 < -999999)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, RESULT_ALL_OF_A);

        // non-empty tables, where subquery predicates eliminate all rows in ONE table
        sql = "select * from A where NOT exists(select 1 from C where c1 > 999999 union select 1 from D where d1 = 0)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "");

        // unions with column references
        sql = "select * from A where NOT exists(select c1,c2 from C union select d1,d2 from D)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "");

        // unions referencing all columns
        sql = "select * from A where NOT exists(select * from C union select * from D)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "");

        // union same table
        sql = "select * from A where NOT exists(select * from A union select * from A)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "");
    }

    @Test
    public void union_correlated() throws Exception {
        // union of empty tables
        String sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE where e1=a1 union select 1 from EMPTY_TABLE where e1=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, RESULT_ALL_OF_A);

        // union of empty tables - each subquery selects different columns
        sql = "select * from A where NOT exists(select e1 from EMPTY_TABLE where e1=a1 union select e2 from EMPTY_TABLE where e1=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, RESULT_ALL_OF_A);

        // union of empty tables - each subquery selects multiple different columns
        sql = "select * from A where NOT exists(select e1,e2 from EMPTY_TABLE where e1=a1 union select e3,e4 from EMPTY_TABLE where e1=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, RESULT_ALL_OF_A);

        // union one non-empty first subquery
        sql = "select * from A where NOT exists(select 1 from C where c1=a1 union select 1 from EMPTY_TABLE where e1=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 12  | 120 |\n" +
                " 12  | 120 |\n" +
                " 13  |  0  |\n" +
                " 13  |  1  |\n" +
                "  3  | 30  |\n" +
                "  4  | 40  |\n" +
                "  6  | 60  |\n" +
                "  7  | 70  |\n" +
                "NULL |NULL |");

        // union one non-empty second subquery
        sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE where e1=a1 union select 1 from C where c1=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 12  | 120 |\n" +
                " 12  | 120 |\n" +
                " 13  |  0  |\n" +
                " 13  |  1  |\n" +
                "  3  | 30  |\n" +
                "  4  | 40  |\n" +
                "  6  | 60  |\n" +
                "  7  | 70  |\n" +
                "NULL |NULL |");

        // union no non-empty
        sql = "select * from A where NOT exists(select 1 from D where d1=a1 union select 1 from C where c1=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 13  |  0  |\n" +
                " 13  |  1  |\n" +
                "  3  | 30  |\n" +
                "  4  | 40  |\n" +
                "NULL |NULL |");

    }

    @Test
    public void union_correlated_inequality() throws Exception {
        // union of empty tables
        String sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE where e1<=a1 union select 1 from EMPTY_TABLE where e1<=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, RESULT_ALL_OF_A);

        // union of empty tables - each subquery selects different columns
        sql = "select * from A where NOT exists(select e1 from EMPTY_TABLE where e1<=a1 union select e2 from EMPTY_TABLE where e1<=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, RESULT_ALL_OF_A);

        // union of empty tables - each subquery selects multiple different columns
        sql = "select * from A where NOT exists(select e1,e2 from EMPTY_TABLE where e1<=a1 union select e3,e4 from EMPTY_TABLE where e1<=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, RESULT_ALL_OF_A);

        // union one non-empty first subquery
        sql = "select * from A where NOT exists(select 1 from C where c1>=a1 union select 1 from EMPTY_TABLE where e1>=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 12  | 120 |\n" +
                " 12  | 120 |\n" +
                " 13  |  0  |\n" +
                " 13  |  1  |\n" +
                "NULL |NULL |");

        // union one non-empty second subquery
        sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE where e1>=a1 union select 1 from C where c1>=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 12  | 120 |\n" +
                " 12  | 120 |\n" +
                " 13  |  0  |\n" +
                " 13  |  1  |\n" +
                "NULL |NULL |");

        // union no non-empty
        sql = "select * from A where NOT exists(select 1 from D where d1>=a1 union select 1 from C where c1>=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 13  |  0  |\n" +
                " 13  |  1  |\n" +
                "NULL |NULL |");

    }

    @Test
    public void union_correlated_lotsOfSubqueryPredicates() throws Exception {
        // union no non-empty
        String sql = "select * from A where NOT exists(" + "" +
                "select 1 from D where a1=d1 and d2!=10 and d1!=0" +
                " union " +
                "select 1 from C where c1=a1 and c2!=10 and c1!=0" +
                ")";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                "  0  |  0  |\n" +
                "  1  | 10  |\n" +
                " 13  |  0  |\n" +
                " 13  |  1  |\n" +
                "  3  | 30  |\n" +
                "  4  | 40  |\n" +
                "NULL |NULL |");
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // UNION ALL
    //
    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Test
    public void unionAll_unCorrelated() throws Exception {
        String sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE union ALL select 1 from EMPTY_TABLE)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, RESULT_ALL_OF_A);

        // non-empty tables, where subquery predicates eliminate all rows in ONE table
        sql = "select * from A where NOT exists(select 1 from C where c1 > 999999 union ALL select 1 from D where d1 = 0)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "");
    }

    @Ignore
    @Test
    public void unionAll_correlated() throws Exception {
        String R = "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 12  | 120 |\n" +
                " 12  | 120 |\n" +
                " 13  |  0  |\n" +
                " 13  |  1  |\n" +
                "  3  | 30  |\n" +
                "  4  | 40  |\n" +
                "  6  | 60  |\n" +
                "  7  | 70  |\n" +
                "NULL |NULL |";

        // union one non-empty first subquery
        String sql = "select * from A where NOT exists(select 1 from C where c1=a1 union ALL select 1 from EMPTY_TABLE where e1=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, R);

        // union one non-empty second subquery
        sql = "select * from A where NOT exists(select 1 from EMPTY_TABLE where e1=a1 union ALL select 1 from C where c1=a1)";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, R);
    }


    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // misc
    //
    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Test
    public void union_unionsInFromListOfNotExistsSubquery() throws Exception {
        String sql = "select * from B where NOT exists(" +
                "select * from (select c1 r from C union select d1 r from D) foo where foo.r=b1" +
                ")";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "B1  | B2  |\n" +
                "------------\n" +
                "  3  | 30  |\n" +
                "  9  | 90  |\n" +
                "NULL |NULL |\n" +
                "NULL |NULL |");

        // Inequality
        sql = "select * from B where NOT exists(" +
                "select * from (select c1 r from C union select d1 r from D) foo where foo.r>=b1+3" +
                ")";
        assertUnorderedResult(conn(), sql, ZERO_SUBQUERY_NODES, "" +
                "B1  | B2  |\n" +
                "------------\n" +
                " 11  | 110 |\n" +
                " 11  | 110 |\n" +
                "NULL |NULL |\n" +
                "NULL |NULL |");
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // not flattened
    //
    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Test
    public void notFlattened_unionsReferenceMultipleOuterTableColumns() throws Exception {
        // same table in each union select
        String sql = "select * from A where NOT exists(select 1 from D where d1=a1 union select 1 from D where d2=a2)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 13  |  1  |\n" +
                "  2  | 20  |\n" +
                "  3  | 30  |\n" +
                "  4  | 40  |\n" +
                "NULL |NULL |");

        // different table in each union select
        sql = "select * from A where NOT exists(select 1 from C where c1=a1 union select 1 from D where d1=a2)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "" +
                "A1  | A2  |\n" +
                "------------\n" +
                " 12  | 120 |\n" +
                " 12  | 120 |\n" +
                "  3  | 30  |\n" +
                "  4  | 40  |\n" +
                "  6  | 60  |\n" +
                "  7  | 70  |\n" +
                "NULL |NULL |");
        // Inequality joins
        // same table in each union select
        sql = "select * from A where NOT exists(select 1 from D where d1>=a1+6 union select 1 from D where d2>=a2+4)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "" +
                "A1  | A2  |\n" +
                  "------------\n" +
                  " 12  | 120 |\n" +
                  " 12  | 120 |\n" +
                  "NULL |NULL |");

        // different table in each union select
        sql = "select * from A where NOT exists(select 1 from C where c1>=a1+6 union select 1 from D where d1>=a2+4)";
        assertUnorderedResult(conn(), sql, ONE_SUBQUERY_NODE, "" +
                "A1  | A2  |\n" +
                  "------------\n" +
                  " 11  | 110 |\n" +
                  " 12  | 120 |\n" +
                  " 12  | 120 |\n" +
                  "  6  | 60  |\n" +
                  "  7  | 70  |\n" +
                  "NULL |NULL |");
    }


    private Connection conn() {
        return methodWatcher.getOrCreateConnection();
    }


}
