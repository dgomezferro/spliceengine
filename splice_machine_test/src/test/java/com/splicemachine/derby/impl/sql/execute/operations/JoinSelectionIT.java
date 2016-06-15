package com.splicemachine.derby.impl.sql.execute.operations;

import com.google.common.base.Joiner;
import com.splicemachine.derby.test.framework.*;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.IsEqual.equalTo;

// Note - we are using the format of the EXPLAIN output to pass these tests.  They will need to be updated if the EXPLAIN
// output changes

public class JoinSelectionIT extends SpliceUnitTest  {

    public static final String CLASS_NAME = JoinSelectionIT.class.getSimpleName().toUpperCase();
    public static final SpliceWatcher spliceClassWatcher = new SpliceWatcher(CLASS_NAME);
    public static final SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);
    public static final SpliceTableWatcher spliceTableWatcher = new SpliceTableWatcher("PERSON",CLASS_NAME,"(pid int NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), i int)");
    public static final SpliceTableWatcher spliceTableWatcher2 = new SpliceTableWatcher("RP_BC_14_1",CLASS_NAME,"(pid int NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), i int)");
    public static final SpliceTableWatcher spliceTableWatcher3 = new SpliceTableWatcher("T",CLASS_NAME,"(i int)");
    public static final SpliceTableWatcher spliceTableWatcher4 = new SpliceTableWatcher("A",CLASS_NAME,"(v1 varchar(10), v2 varchar(10))");
    public static final SpliceTableWatcher spliceTableWatcher5 = new SpliceTableWatcher("B",CLASS_NAME,"(v1 varchar(10), v2 varchar(10))");
    public static final SpliceTableWatcher spliceTableWatcher6 = new SpliceTableWatcher("T1",CLASS_NAME,"(i int)");
    public static final SpliceTableWatcher spliceTableWatcher7 = new SpliceTableWatcher("T2",CLASS_NAME,"(i int, j int)");
    public static final SpliceTableWatcher spliceTableWatcher8 = new SpliceTableWatcher("T3",CLASS_NAME,"(i int, j int)");

    public static final SpliceTableWatcher spliceTableRegion = new SpliceTableWatcher("REGION2",CLASS_NAME,
    	"(R_REGIONKEY INTEGER NOT NULL PRIMARY KEY, R_NAME VARCHAR(25))");
    public static final SpliceTableWatcher spliceTableNation = new SpliceTableWatcher("NATION2",CLASS_NAME,
		"(N_NATIONKEY INTEGER NOT NULL PRIMARY KEY, N_NAME VARCHAR(25), N_REGIONKEY INTEGER NOT NULL)");

    /*
     * Tables for the regression of DB-4122
     */
    private static final SpliceTableWatcher card = new SpliceTableWatcher("CARD",CLASS_NAME,"(BASE_ACCT_ID varchar(11),BASIC_SUPP_NO varchar(2),PROD_ID varchar(6))");
    private static final SpliceTableWatcher product = new SpliceTableWatcher("PRODUCT",CLASS_NAME,"(PROD_ID varchar(6),LOB_CD varchar(13))");
    private static final SpliceIndexWatcher card_idx = new SpliceIndexWatcher("CARD",CLASS_NAME,"CARD_IDX",CLASS_NAME,"(BASE_ACCT_ID ,BASIC_SUPP_NO, PROD_ID)");
    private static final SpliceIndexWatcher product_idx = new SpliceIndexWatcher("PRODUCT",CLASS_NAME,"PROD_IDX",CLASS_NAME,"(PROD_ID,LOB_CD)");
    private static final SpliceIndexWatcher a_idx = new SpliceIndexWatcher("A",CLASS_NAME,"A_IDX",CLASS_NAME,"(V1)");
    private static final SpliceIndexWatcher b_idx = new SpliceIndexWatcher("B",CLASS_NAME,"B_IDX",CLASS_NAME,"(V1, V2)");
    private static final SpliceIndexWatcher t2_idx = new SpliceIndexWatcher("T3",CLASS_NAME,"T3_IDX",CLASS_NAME,"(j)");

    private static final String PLAN_LINE_LEADER = "->  ";
    private static final String JOIN_STRATEGY_TERMINATOR = "(";
    private static final String NESTED_LOOP_JOIN = "NestedLoopJoin";
    private static final String MERGE_SORT_JOIN = "MergeSortJoin";
    private static final String MERGE_JOIN = "MergeJoin";
    private static final String LO_MERGE_SORT_JOIN = "MergeSortLeftOuterJoin";
    private static final String BROADCAST_JOIN = "BroadcastJoin";
    private static final String LO_BROADCAST_JOIN = "BroadcastLeftOuterJoin";

    @ClassRule
    public static TestRule rule = RuleChain.outerRule(spliceSchemaWatcher)
            .around(spliceTableWatcher)
            .around(spliceTableWatcher2)
            .around(spliceTableWatcher3)
            .around(spliceTableRegion)
            .around(spliceTableNation)
            .around(card)
            .around(card_idx)
            .around(product)
            .around(product_idx)
            .around(spliceTableWatcher4)
            .around(a_idx)
            .around(spliceTableWatcher5)
            .around(b_idx)
            .around(spliceTableWatcher6)
            .around(spliceTableWatcher7)
            .around(spliceTableWatcher8)
            .around(t2_idx)
            .around(new SpliceDataWatcher() {
                @Override
                protected void starting(Description description) {
                    try {
                        spliceClassWatcher.setAutoCommit(true);
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) values 1,2,3,4,5,6,7,8,9,10",
                                spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher, spliceTableWatcher));

                        spliceClassWatcher.executeUpdate(format("insert into %s (i) values 1,2,3,4,5,6,7,8,9,10",
                                spliceTableWatcher2));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher2, spliceTableWatcher2));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher2, spliceTableWatcher2));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher2, spliceTableWatcher2));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher2, spliceTableWatcher2));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher2, spliceTableWatcher2));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher2, spliceTableWatcher2));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i) select i from %s", spliceTableWatcher2, spliceTableWatcher2));

                        spliceClassWatcher.executeUpdate(format("insert into %s (i) values 1,2,3,4,5,6,7,8,9,10",
                                spliceTableWatcher3));

                        spliceClassWatcher.executeUpdate(format("insert into %s (i) values 1,2,3,4",
                                spliceTableWatcher6));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i,j) values (1,1),(2,2),(3,3),(4,4)",
                                spliceTableWatcher7));
                        spliceClassWatcher.executeUpdate(format("insert into %s (i,j) values (1,1),(2,2),(3,3),(4,4)",
                                spliceTableWatcher8));

                        spliceClassWatcher.executeUpdate(format(
                                "insert into %s (r_regionkey, r_name) values " +
                                        "(0, 'AFRICA'), (1, 'AMERICA'), (2, 'ASIA'), (3, 'EUROPE'), (4, 'MIDDLE EAST'), " +
                                        "(5, 'AMERICA'), (6, 'AMERICA'), (7, 'AMERICA'), (8, 'AMERICA'), (9, 'AMERICA')",
                                spliceTableRegion));

                        spliceClassWatcher.executeUpdate(format(
                                "insert into %s (n_nationkey, n_name, n_regionkey) values " +
                                        "(0, 'ALGERIA', 0), " +
                                        "(1, 'ARGENTINA', 1), " +
                                        "(2, 'BRAZIL', 1), " +
                                        "(4, 'EGYPT', 4), " +
                                        "(5, 'ETHIOPIA', 0), " +
                                        "(6, 'FRANCE', 3)",
                                spliceTableNation));

                        spliceClassWatcher.execute(format("call syscs_util.COLLECT_SCHEMA_STATISTICS('%s',false)", CLASS_NAME));

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        spliceClassWatcher.closeAll();
                    }
                }
            });

    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher(CLASS_NAME);

    // should be NestedLoopJoin
    @Test
    public void testInnerJoinWithSubqueryFilterExactCriteria() throws Exception {
        fourthRowContainsQuery(
            format("explain select a2.pid from %s a2 join " +
            		  "(select person.pid from %s) as a3 " +
            		  " on a2.pid = a3.pid " + 
            		  " where a2.pid = 100", spliceTableWatcher2, spliceTableWatcher),
            MERGE_SORT_JOIN, methodWatcher);
    }
    
    @Test
    public void testInnerJoinWithSubquery() throws Exception {
        ResultSet rs = methodWatcher.executeQuery(
              format("explain select a2.pid from %s a2 join " +
            		  "(select person.pid from %s) as a3 " +
            		  " on a2.pid = a3.pid ", spliceTableWatcher2, spliceTableWatcher));
        int count = 0;
        while (rs.next()) {
            count++;
            if (count == 4) {
    			String row = rs.getString(1);
    			String joinStrategy = row.substring(row.indexOf(PLAN_LINE_LEADER) + PLAN_LINE_LEADER.length(),
                        row.indexOf(JOIN_STRATEGY_TERMINATOR));
                Assert.assertThat("Join strategy must be either MERGE_SORT_JOIN or BROADCAST_JOIN", joinStrategy,
                        anyOf(equalTo(MERGE_SORT_JOIN), equalTo(BROADCAST_JOIN)));
            	break;
            }
        }   
    }


    // should be NLJ?  Comes back with MergeSortJoin
    @Test
    public void testLeftOuterJoinWithSubqueryFilterExactCriteria() throws Exception {
        fourthRowContainsQuery(
            format("explain select a2.pid from %s a2 left outer join " +
            		  "(select person.pid from %s) as a3 " +
            		  " on a2.pid = a3.pid " + 
            		  " where a2.pid = 100", spliceTableWatcher2, spliceTableWatcher),
		    LO_MERGE_SORT_JOIN, methodWatcher);
    }
    
    // should be Broadcast but comes back with MergeSort?
    @Test
    public void testLeftOuterJoinWithSubquery() throws Exception {
        fourthRowContainsQuery(
        	format("explain select a2.pid from %s a2 left outer join " +
            		  "(select person.pid from %s) as a3 " +
            		  " on a2.pid = a3.pid ", spliceTableWatcher2, spliceTableWatcher),
    		LO_MERGE_SORT_JOIN, methodWatcher);
    }

    @Test
    public void testRPLeftOuterJoinWithNestedSubqueries() throws Exception {
		explainQueryNoNestedLoops(
                format("explain SELECT a2.pid FROM %s a2 " +
                        "LEFT OUTER JOIN " +
                        "(SELECT a4.PID FROM %s a4 WHERE EXISTS " +
                        "(SELECT a5.PID FROM %s a5 WHERE a4.PID = a5.PID)) AS a3 " +
                        "ON a2.PID = a3.PID", spliceTableWatcher2, spliceTableWatcher2, spliceTableWatcher));
    }
    
    @Test
    public void testRPLeftOuterJoinWithNestedSubqueriesFilterExactCriteria() throws Exception {
        fourthRowContainsQuery(
                format("explain SELECT a2.pid FROM %s a2 " +
                        "LEFT OUTER JOIN " +
                        "(SELECT a4.PID FROM %s a4 WHERE EXISTS " +
                        "(SELECT a5.PID FROM %s a5 WHERE a4.PID = a5.PID)) AS a3 " +
                        "ON a2.PID = a3.PID " +
                        "WHERE a2.PID = 100", spliceTableWatcher2, spliceTableWatcher2, spliceTableWatcher),
                LO_MERGE_SORT_JOIN, methodWatcher);
    }

    @Test
    public void testInnerJoinWithNestedSubqueries() throws Exception {
    	fourthRowContainsQuery(
            format("explain SELECT a2.pid FROM %s a2 " +
            		  "INNER JOIN " +
            		  "(SELECT a4.PID FROM %s a4 WHERE EXISTS " +
            				  "(SELECT a5.PID FROM %s a5 WHERE a4.PID = a5.PID)) AS a3 " +
            				  "ON a2.PID = a3.PID", spliceTableWatcher2, spliceTableWatcher2, spliceTableWatcher),
            MERGE_SORT_JOIN, methodWatcher);
    }
    
    @Test
    public void testInnerJoinWithNestedSubqueriesFilterExactCriteria() throws Exception {
    	fourthRowContainsQuery(
            format("explain SELECT a2.pid FROM %s a2 " + 
            		  "INNER JOIN " +
            		  "(SELECT a4.PID FROM %s a4 WHERE EXISTS " +
            				  "(SELECT a5.PID FROM %s a5 WHERE a4.PID = a5.PID)) AS a3 " +
            				  "ON a2.PID = a3.PID" +
            				  " where a2.pid = 100", spliceTableWatcher2, spliceTableWatcher2, spliceTableWatcher),
            BROADCAST_JOIN, methodWatcher);
    }

    @Test
    public void testInnerJoinWithSubqueryLHSFilterExactCriteria() throws Exception {
    	fourthRowContainsQuery(
            format("explain select a2.pid from (select person.pid from %s) as a3 " +
            		  " join %s a2 " +
            		  " on a2.pid = a3.pid " + 
            		  " where a2.pid = 100", spliceTableWatcher, spliceTableWatcher2),
            MERGE_SORT_JOIN, methodWatcher);
    }
    
    @Test
    public void testInnerJoinWithSubqueryLHS() throws Exception {

        ResultSet rs = methodWatcher.executeQuery(
              format("explain select a2.pid from (select person.pid from %s) as a3 " +
            		  " join %s a2 " +
            		  " on a2.pid = a3.pid ", spliceTableWatcher, spliceTableWatcher2));
        int count = 0;
        while (rs.next()) {
            count++;
            if (count == 4) {
    			String row = rs.getString(1);
    			String joinStrategy = row.substring(row.indexOf(PLAN_LINE_LEADER)+PLAN_LINE_LEADER.length(), row.indexOf(JOIN_STRATEGY_TERMINATOR));
                Assert.assertTrue(MERGE_SORT_JOIN.equals(joinStrategy) || BROADCAST_JOIN.equals(joinStrategy));
            	break;
            }
        }   
    }


    // should be NLJ
    @Test
    public void testLeftOuterJoinWithSubqueryLHSFilterExactCriteria() throws Exception {
    	fourthRowContainsQuery(
            format("explain select a2.pid from (select person.pid from %s) as a3 " +
            		  " left outer join %s a2 " +
            		  " on a2.pid = a3.pid " + 
            		  " where a2.pid = 100", spliceTableWatcher, spliceTableWatcher2),
            MERGE_SORT_JOIN, methodWatcher);
    }

    @Test
    public void testLeftOuterJoinWithSubqueryLHS() throws Exception {
    	fourthRowContainsQuery(
            format("explain select a2.pid from (select person.pid from %s) as a3 " +
            		  " left outer join %s a2 " +
            		  " on a2.pid = a3.pid ", spliceTableWatcher, spliceTableWatcher2),
            LO_BROADCAST_JOIN, methodWatcher);
    }

    @Test
    public void testLeftOuterJoinWithNestedSubqueryLHSFilterExactCriteria() throws Exception {
    	fourthRowContainsQuery(
            format("explain select a2.pid from " +
            		  "(SELECT a4.PID FROM %s a4 WHERE EXISTS " +
            		  "   (SELECT a5.PID FROM %s a5 WHERE a4.PID = a5.PID)) AS a3 " +
            		  " left outer join %s a2 " +
            		  " on a2.pid = a3.pid " + 
            		  " where a2.pid = 100", spliceTableWatcher2, spliceTableWatcher, spliceTableWatcher2),
            BROADCAST_JOIN, methodWatcher);
    }
    
    @Test
    public void testInnerJoinWithNestedSubqueryLHSFilterExactCriteria() throws Exception {
    	fourthRowContainsQuery(
            format("explain select a2.pid from " +
            		  "(SELECT a4.PID FROM %s a4 WHERE EXISTS " +
            		  "   (SELECT a5.PID FROM %s a5 WHERE a4.PID = a5.PID)) AS a3 " +
            		  " join %s a2 " +
            		  " on a2.pid = a3.pid " + 
            		  " where a2.pid = 100", spliceTableWatcher2, spliceTableWatcher, spliceTableWatcher2),
            BROADCAST_JOIN, methodWatcher);
    }

    @Test
    public void testInnerJoinWithNestedSubqueryLHS() throws Exception {
    	fourthRowContainsQuery(
            format("explain select a2.pid from " +
            		  "(SELECT a4.PID FROM %s a4 WHERE EXISTS " +
            		  "   (SELECT a5.PID FROM %s a5 WHERE a4.PID = a5.PID)) AS a3 " +
            		  " join %s a2 " +
            		  " on a2.pid = a3.pid ", spliceTableWatcher2, spliceTableWatcher, spliceTableWatcher2),
            BROADCAST_JOIN, methodWatcher);
    }

    @Test
    public void testLeftOuterJoinWithNestedSubqueryLHS() throws Exception {
    	fourthRowContainsQuery(
            format("explain select a2.pid from " +
            		  "(SELECT a4.PID FROM %s a4 WHERE EXISTS " +
            		  "   (SELECT a5.PID FROM %s a5 WHERE a4.PID = a5.PID)) AS a3 " +
            		  " left outer join %s a2 " +
            		  " on a2.pid = a3.pid ", spliceTableWatcher2, spliceTableWatcher, spliceTableWatcher2),
    	    LO_BROADCAST_JOIN, methodWatcher);
    }

    @Test
    public void testNoNestedLoops1() throws Exception {
    	// This tests DB-3608 (wrong row estimate for frequent element match of type varchar).
    	// If it fails, do not ignore it or comment it out. Let it fail until it is fixed.
    	explainQueryNoNestedLoops(
                "explain select * from region2, nation2 where n_regionkey = r_regionkey and r_name = 'AMERICA'");
    }
    
    private void explainQueryNoNestedLoops(String query) throws Exception {
        queryDoesNotContainString(query, NESTED_LOOP_JOIN, methodWatcher);
    }
    
    /* Regression test for DB-3614 */
    @Test
    public void testTenTableJoinExplainDuration() throws Exception {
    	int size = 10;
    	List<String> tables = new ArrayList<String>(size);
    	List<String> joins = new ArrayList<String>(size - 1);
    	for (int i = 0; i < size; i++) {
	        methodWatcher.executeUpdate(format("create table tentab%s (c1 int primary key)", i));
	        methodWatcher.executeUpdate(format("insert into tentab%s values (1)", i));
	        tables.add(format("tentab%s", i));
	        if (i > 0) {
	        	joins.add(format("tentab%s.c1 = tentab%s.c1", i, i - 1));
	        }
		}
        String fromClause = Joiner.on(", ").join(tables); 
        String joinCriteria = Joiner.on(" AND ").join(joins); 

        long start = System.currentTimeMillis();
        String query = format("EXPLAIN SELECT * FROM %s WHERE %s ", fromClause, joinCriteria);
		ResultSet rs = methodWatcher.executeQuery(query);
        long duration = System.currentTimeMillis() - start;

        // Loose check that explain statement took a few seconds or less,
        // because we're just making sure the short circuit logic in
        // OptimizerImpl.checkTimeout() blocks this from taking several minutes.
		Assert.assertTrue("Explain did not return result!", rs.next());
        Assert.assertTrue(format("Explain statement took %d millis which is too long", duration),
        	duration < 5000L /* 5 seconds */);
    }
    
    /*
     * Regression test for DB-3812
     * 
     * Make sure the explain plan for hinted and unhinted versions of a certain query are the same.
     * Previously, unhinted plan had wrong join order due to costing problem.
     */
    @Test
    @Ignore
    public void testNestedLoopJoinWithAndWithoutJoinOrderHint() throws Exception {
        methodWatcher.executeUpdate("CREATE TABLE NLJ3812A (i bigint NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), j bigint)");
        methodWatcher.executeUpdate("CREATE TABLE NLJ3812B (i bigint NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), j bigint)");
        methodWatcher.executeUpdate("create table NLJ3812C1 (i bigint, j bigint, k bigint, l bigint)");
        methodWatcher.executeUpdate("create table NLJ3812C2 (i bigint, j bigint, k bigint, l bigint)");

        methodWatcher.executeUpdate("insert into NLJ3812A (j) values 1,2,3,4,5,6,7,8,9,10");
        methodWatcher.executeUpdate("insert into NLJ3812A (j) select j from NLJ3812A");
    	for (int i = 0; i < 16; i++) {
            methodWatcher.executeUpdate("insert into NLJ3812A (j) select j from NLJ3812A");
		}
        methodWatcher.executeUpdate("insert into NLJ3812B (j) values 1");
        
		String query = "%s insert into %s (i, j, k, l) " +
            "select a.i, a.j, b.i, b.j " +
            "from %s " +
            "NLJ3812B b, NLJ3812A a " +
            "where b.i = a.j and b.i = 1";

        String explainHinted = format(query, "explain", "NLJ3812C1", "--SPLICE-PROPERTIES joinOrder=FIXED\n");
        String explainUnhinted = format(query, "explain", "NLJ3812C2", "");

        rowContainsQuery(
                new int[]{3, 4, 5},
                explainHinted,
                methodWatcher,
                BROADCAST_JOIN, "TableScan[NLJ3812A", "TableScan[NLJ3812B");

        rowContainsQuery(
                new int[]{2, 3, 4},
                explainUnhinted,
                methodWatcher,
                BROADCAST_JOIN, "TableScan[NLJ3812B", "TableScan[NLJ3812A");
    }

    //DB-3865
    @Test
    public void testLeftJoin() throws Exception {
        thirdRowContainsQuery(
                format("explain select * from %s t1 left join %s t2 on t1.i=t2.i",
                        spliceTableWatcher3, spliceTableWatcher3),
                LO_BROADCAST_JOIN, methodWatcher);
    }

    @Test
    public void mergeSortJoinRequiresOrderBy() throws Exception{
    /*
     * Regression test for DB-4122. Whenever there is a MergeSortJoin under an OrderBy clause, then you can't
     * lose the OrderBy (Sort Elision is not allowed)
     */
        try(ResultSet rs = methodWatcher.executeQuery("explain select CARD.BASE_ACCT_ID, CARD.BASIC_SUPP_NO," +
                "PRODUCT.LOB_CD from --SPLICE-PROPERTIES joinOrder=FIXED\n" +
                "CARD INNER JOIN PRODUCT --SPLICE-PROPERTIES index=NULL, joinStrategy=SORTMERGE\n" +
                "ON CARD.PROD_ID = PRODUCT.PROD_ID\n" +
                "ORDER BY CARD.BASE_ACCT_ID, CARD.BASIC_SUPP_NO")){
            String explainPlan = "";
            while(rs.next()){
                explainPlan += rs.getString(1)+"\n";
            }
            Assert.assertTrue("Does not contain A MergeSortJoin!",explainPlan.contains("MergeSortJoin"));
            Assert.assertTrue("Does not contain an Order BY!",explainPlan.contains("OrderBy"));
        }
    }

    @Test
    public void testHintDisableTransitiveClosure() throws Exception {
        String query = "explain\n" +
                "select * from a %s\n" +
                "join b  %s\n" +
                "on a.v1 = b.v1\n" +
                "where a.v1 = '5'";

        String broadcast = String.format(query, "", "--SPLICE-PROPERTIES joinStrategy=BROADCAST");
        String mergeSort = String.format(query, "", "--SPLICE-PROPERTIES joinStrategy=SORTMERGE");
        String merge = String.format(query, "--SPLICE-PROPERTIES index = a_idx",
                "--SPLICE-PROPERTIES index = b_idx,joinStrategy=MERGE");

        thirdRowContainsQuery(broadcast, BROADCAST_JOIN, methodWatcher);
        thirdRowContainsQuery(mergeSort, MERGE_SORT_JOIN, methodWatcher);
        thirdRowContainsQuery(merge, MERGE_JOIN, methodWatcher);
    }


    @Test
    //DB-4473
    public void testMergeSortJoinWithUnion() throws Exception {
        String query = "SELECT count(*)\n" +
                "FROM --splice-properties joinOrder=FIXED\n" +
                "t1 a\n" +
                "JOIN \n" +
                "  (select i from t2\n" +
                "   union\n" +
                "   select i from t3) b --splice-properties joinStrategy=SORTMERGE\n" +
                "ON a.i=b.i";

        String explain = "explain " + query;
        rowContainsQuery(new int[]{6}, explain, methodWatcher, "MergeSortJoin");

        ResultSet rs = methodWatcher.executeQuery(query);
        Assert.assertTrue(rs.next());
        Assert.assertTrue("wrong row counts", 4 == rs.getInt(1));
    }
    //DB-4189
    @Test
    public void testMergeSortJoinCost() throws Exception {

        String query = "explain\n" +
                "select t1.i, t2.j, t3.i, t3.j\n" +
                "from t1\n" +
                ", t2 --SPLICE-PROPERTIES joinStrategy=SORTMERGE\n" +
                ", t3 --SPLICE-PROPERTIES %s joinStrategy=SORTMERGE\n" +
                "where t1.i=t2.i and t2.j=t3.j";

        String withIndex = String.format(query, "index=t3_idx,");
        ResultSet rs = methodWatcher.executeQuery(withIndex);
        String costString = null;
        while(rs.next()) {
            String s = rs.getString(1);
            if (s.contains("ScrollInsensitive")) {
                costString = s;
            }
        }
        double costWithIndex = getTotalCost(costString);

        String withoutIndex = String.format(query, "");
        rs = methodWatcher.executeQuery(withoutIndex);

        while(rs.next()) {
            String s = rs.getString(1);
            if (s.contains("ScrollInsensitive")) {
                costString = s;
            }
            // Should not use an index
            Assert.assertFalse(s.contains("IndexLookup"));
        }
        double costWithoutIndex = getTotalCost(costString);

        // The plan without an index lookup should be less expensive
        Assert.assertTrue(costWithoutIndex < costWithIndex);
    }

    private double getTotalCost(String s) {
        double cost = 0.0d;
        String[] strings = s.split(",");
        for(String string : strings) {
            String[] s1 = string.split("=");
            if (s1[0].compareTo("totalCost") == 0) {
                cost = Double.parseDouble(s1[1]);
                break;
            }
        }
        return cost;
    }
}