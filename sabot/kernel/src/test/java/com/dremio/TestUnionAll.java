/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio;

import static org.junit.Assume.assumeFalse;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import com.dremio.common.exceptions.UserException;
import com.dremio.common.expression.SchemaPath;
import com.dremio.common.types.TypeProtos.MajorType;
import com.dremio.common.types.TypeProtos.MinorType;
import com.dremio.common.types.Types;
import com.dremio.common.util.FileUtils;
import com.dremio.config.DremioConfig;
import com.dremio.exec.planner.physical.PlannerSettings;
import com.dremio.test.TemporarySystemProperties;
import com.google.common.collect.Lists;

public class TestUnionAll extends BaseTestQuery{
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TestUnionAll.class);

  private static final String sliceTargetSmall = "alter session set \"planner.slice_target\" = 1";
  private static final String sliceTargetDefault = "alter session reset \"planner.slice_target\"";
  private static final String enableDistribute = "alter session set \"planner.enable_unionall_distribute\" = true";
  private static final String defaultDistribute = "alter session reset \"planner.enable_unionall_distribute\"";

  @Rule
  public TemporarySystemProperties properties = new TemporarySystemProperties();

  @BeforeClass
  public static void ignoreIfUnlimitedSplits() {
    assumeFalse(getSabotContext().getOptionManager().getOption(PlannerSettings.UNLIMITED_SPLITS_SUPPORT));
  }

  @Test  // Simple Union-All over two scans
  public void testUnionAll1() throws Exception {
    String query = "(select n_regionkey from cp.\"tpch/nation.parquet\") union all (select r_regionkey from cp.\"tpch/region.parquet\")";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q1.tsv")
        .baselineTypes(MinorType.INT)
        .baselineColumns("n_regionkey")
        .build().run();
  }

  @Test  // Union-All over inner joins
  public void testUnionAll2() throws Exception {
    String query =
         "select n1.n_nationkey from cp.\"tpch/nation.parquet\" n1 inner join cp.\"tpch/region.parquet\" r1 on n1.n_regionkey = r1.r_regionkey where n1.n_nationkey in (1, 2) " +
         "union all " +
         "select n2.n_nationkey from cp.\"tpch/nation.parquet\" n2 inner join cp.\"tpch/region.parquet\" r2 on n2.n_regionkey = r2.r_regionkey where n2.n_nationkey in (3, 4)";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q2.tsv")
        .baselineTypes(MinorType.INT)
        .baselineColumns("n_nationkey")
        .build().run();
  }

  @Test  // Union-All over grouped aggregates
  public void testUnionAll3() throws Exception {
    String query = "select n1.n_nationkey from cp.\"tpch/nation.parquet\" n1 where n1.n_nationkey in (1, 2) group by n1.n_nationkey union all select r1.r_regionkey from cp.\"tpch/region.parquet\" r1 group by r1.r_regionkey";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q3.tsv")
        .baselineTypes(MinorType.INT)
        .baselineColumns("n_nationkey")
        .build().run();
  }

  @Test    // Chain of Union-Alls
  public void testUnionAll4() throws Exception {
    String query = "select n_regionkey from cp.\"tpch/nation.parquet\" union all select r_regionkey from cp.\"tpch/region.parquet\" union all select n_nationkey from cp.\"tpch/nation.parquet\" union all select c_custkey from cp.\"tpch/customer.parquet\" where c_custkey < 5";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q4.tsv")
        .baselineTypes(MinorType.INT)
        .baselineColumns("n_regionkey")
        .build().run();
  }

  @Test  // Union-All of all columns in the table
  public void testUnionAll5() throws Exception {
    String query = "select r_name, r_comment, r_regionkey from cp.\"tpch/region.parquet\" r1 " +
                     "union all " +
                     "select r_name, r_comment, r_regionkey from cp.\"tpch/region.parquet\" r2";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q5.tsv")
        .baselineTypes(MinorType.VARCHAR, MinorType.VARCHAR, MinorType.INT)
        .baselineColumns("r_name", "r_comment", "r_regionkey")
        .build().run();
  }

  @Test // Union-All where same column is projected twice in right child
  public void testUnionAll6() throws Exception {
    String query = "select n_nationkey, n_regionkey from cp.\"tpch/nation.parquet\" where n_regionkey = 1 union all select r_regionkey, r_regionkey from cp.\"tpch/region.parquet\" where r_regionkey = 2";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q6.tsv")
        .baselineTypes(MinorType.INT, MinorType.INT)
        .baselineColumns("n_nationkey", "n_regionkey")
        .build().run();
  }

  @Test // Union-All where same column is projected twice in left and right child
  public void testUnionAll6_1() throws Exception {
    String query = "select n_nationkey, n_nationkey from cp.\"tpch/nation.parquet\" union all select r_regionkey, r_regionkey from cp.\"tpch/region.parquet\"";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q6_1.tsv")
        .baselineTypes(MinorType.INT, MinorType.INT)
        .baselineColumns("n_nationkey", "n_nationkey1")
        .build().run();
  }

  @Test  // Union-all of two string literals of different lengths
  public void testUnionAll7() throws Exception {
    String query = "select 'abc' from cp.\"tpch/region.parquet\" union all select 'abcdefgh' from cp.\"tpch/region.parquet\"";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q7.tsv")
        .baselineTypes(MinorType.VARCHAR)
        .baselineColumns("EXPR$0")
        .build().run();
  }

  @Test  // Union-all of two character columns of different lengths
  public void testUnionAll8() throws Exception {
    String query = "select n_name, n_nationkey from cp.\"tpch/nation.parquet\" union all select r_comment, r_regionkey  from cp.\"tpch/region.parquet\"";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q8.tsv")
        .baselineTypes(MinorType.VARCHAR, MinorType.INT)
        .baselineColumns("n_name", "n_nationkey")
        .build().run();
  }

  @Test // DRILL-1905: Union-all of * column from JSON files in different directories
  public void testUnionAll9() throws Exception {
    String file0 = FileUtils.getResourceAsFile("/multilevel/json/1994/Q1/orders_94_q1.json").toURI().toString();
    String file1 = FileUtils.getResourceAsFile("/multilevel/json/1995/Q1/orders_95_q1.json").toURI().toString();
    String query = String.format("select o_custkey, o_orderstatus, o_totalprice, o_orderdate, o_orderpriority, o_clerk, o_shippriority, o_comment, o_orderkey from dfs_test.\"%s\" union all " +
                                 "select o_custkey, o_orderstatus, o_totalprice, o_orderdate, o_orderpriority, o_clerk, o_shippriority, o_comment, o_orderkey from dfs_test.\"%s\"", file0, file1);

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q9.tsv")
        .baselineTypes(MinorType.BIGINT, MinorType.VARCHAR, MinorType.FLOAT8, MinorType.VARCHAR,
                       MinorType.VARCHAR, MinorType.VARCHAR, MinorType.BIGINT,MinorType.VARCHAR, MinorType.BIGINT)
        .baselineColumns("o_custkey", "o_orderstatus", "o_totalprice", "o_orderdate",
                         "o_orderpriority", "o_clerk", "o_shippriority", "o_comment", "o_orderkey")
        .build().run();
  }

  @Test // Union All constant literals
  public void testUnionAll10() throws Exception {
    String query = "(select n_name, 'LEFT' as LiteralConstant, n_nationkey, 1 as NumberConstant from cp.\"tpch/nation.parquet\") " +
              "union all " +
              "(select 'RIGHT', r_name, 2, r_regionkey from cp.\"tpch/region.parquet\")";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q10.tsv")
        .baselineTypes(MinorType.VARCHAR, MinorType.VARCHAR, MinorType.INT, MinorType.INT)
        .baselineColumns("n_name", "LiteralConstant", "n_nationkey", "NumberConstant")
        .build().run();
  }

  @Test
  public void testUnionAllViewExpandableStar() throws Exception {
    try {
      properties.set(DremioConfig.LEGACY_STORE_VIEWS_ENABLED, "true");
      test("use dfs_test");
      test("create view nation_view_testunionall as select n_name, n_nationkey from cp.\"tpch/nation.parquet\";");
      test("create view region_view_testunionall as select r_name, r_regionkey from cp.\"tpch/region.parquet\";");

      String query1 = "(select * from dfs_test.\"nation_view_testunionall\") " +
        "union all " +
        "(select * from dfs_test.\"region_view_testunionall\") ";

      String query2 = "(select r_name, r_regionkey from cp.\"tpch/region.parquet\") " +
        "union all " +
        "(select * from dfs_test.\"nation_view_testunionall\")";

      try {
        testBuilder()
          .sqlQuery(query1)
          .unOrdered()
          .csvBaselineFile("testframework/testUnionAllQueries/q11.tsv")
          .baselineTypes(MinorType.VARCHAR, MinorType.INT)
          .baselineColumns("n_name", "n_nationkey")
          .build().run();

        testBuilder()
          .sqlQuery(query2)
          .unOrdered()
          .csvBaselineFile("testframework/testUnionAllQueries/q12.tsv")
          .baselineTypes(MinorType.VARCHAR, MinorType.INT)
          .baselineColumns("r_name", "r_regionkey")
          .build().run();
      } finally {
        test("drop view nation_view_testunionall");
        test("drop view region_view_testunionall");
      }
    } finally {
      properties.clear(DremioConfig.LEGACY_STORE_VIEWS_ENABLED);
    }
  }

  @Test // see DRILL-2203
  public void testDistinctOverUnionAllwithFullyQualifiedColumnNames() throws Exception {
    String query = "select distinct sq.x1, sq.x2 " +
        "from " +
        "((select n_regionkey as a1, n_name as b1 from cp.\"tpch/nation.parquet\") " +
        "union all " +
        "(select r_regionkey as a2, r_name as b2 from cp.\"tpch/region.parquet\")) as sq(x1,x2)";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q14.tsv")
        .baselineTypes(MinorType.INT, MinorType.VARCHAR)
        .baselineColumns("x1", "x2")
        .build().run();
  }

  @Test // see DRILL-1923
  public void testUnionAllContainsColumnANumericConstant() throws Exception {
    String query = "(select n_nationkey, n_regionkey, n_name from cp.\"tpch/nation.parquet\"  limit 5) " +
        "union all " +
        "(select 1, n_regionkey, 'abc' from cp.\"tpch/nation.parquet\" limit 5)";

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q15.tsv")
        .baselineTypes(MinorType.INT, MinorType.INT, MinorType.VARCHAR)
        .baselineColumns("n_nationkey", "n_regionkey", "n_name")
        .build().run();
  }

  @Test // see DRILL-2207
  public void testUnionAllEmptySides() throws Exception {
    String query1 = "(select n_nationkey, n_regionkey, n_name from cp.\"tpch/nation.parquet\"  limit 0) " +
        "union all " +
        "(select 1, n_regionkey, 'abc' from cp.\"tpch/nation.parquet\" limit 5)";

    String query2 = "(select n_nationkey, n_regionkey, n_name from cp.\"tpch/nation.parquet\"  limit 5) " +
        "union all " +
        "(select 1, n_regionkey, 'abc' from cp.\"tpch/nation.parquet\" limit 0)";

    testBuilder()
        .sqlQuery(query1)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q16.tsv")
        .baselineTypes(MinorType.INT, MinorType.INT, MinorType.VARCHAR)
        .baselineColumns("n_nationkey", "n_regionkey", "n_name")
        .build().run();


    testBuilder()
        .sqlQuery(query2)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q17.tsv")
        .baselineTypes(MinorType.INT, MinorType.INT, MinorType.VARCHAR)
        .baselineColumns("n_nationkey", "n_regionkey", "n_name")
        .build().run();
    }

  @Test // see DRILL-1977, DRILL-2376, DRILL-2377, DRILL-2378, DRILL-2379
  public void testAggregationOnUnionAllOperator() throws Exception {
    String root = FileUtils.getResourceAsFile("/store/text/data/t.json").toURI().toString();
    String query1 = String.format(
            "(select calc1, max(b1) as \"max\", min(b1) as \"min\", count(c1) as \"count\" " +
                    "from (select a1 + 10 as calc1, b1, c1 from dfs_test.\"%s\" " +
                    "union all " +
                    "select a1 + 100 as diff1, b1 as diff2, c1 as diff3 from dfs_test.\"%s\") " +
                    "group by calc1 order by calc1)", root, root);

    String query2 = String.format(
        "(select calc1, min(b1) as \"min\", max(b1) as \"max\", count(c1) as \"count\" " +
        "from (select a1 + 10 as calc1, b1, c1 from dfs_test.\"%s\" " +
        "union all " +
        "select a1 + 100 as diff1, b1 as diff2, c1 as diff3 from dfs_test.\"%s\") " +
        "group by calc1 order by calc1)", root, root);

    testBuilder()
        .sqlQuery(query1)
        .ordered()
        .csvBaselineFile("testframework/testExampleQueries/testAggregationOnUnionAllOperator/q1.tsv")
        .baselineTypes(MinorType.BIGINT, MinorType.BIGINT, MinorType.BIGINT, MinorType.BIGINT)
        .baselineColumns("calc1", "max", "min", "count")
        .build().run();

    testBuilder()
        .sqlQuery(query2)
        .ordered()
        .csvBaselineFile("testframework/testExampleQueries/testAggregationOnUnionAllOperator/q2.tsv")
        .baselineTypes(MinorType.BIGINT, MinorType.BIGINT, MinorType.BIGINT, MinorType.BIGINT)
        .baselineColumns("calc1", "min", "max", "count")
        .build().run();
  }

  @Test(expected = UserException.class) // see DRILL-2590
  public void testUnionAllImplicitCastingFailure() throws Exception {
    String rootInt = FileUtils.getResourceAsFile("/store/json/intData.json").toURI().toString();
    String rootBoolean = FileUtils.getResourceAsFile("/store/json/booleanData.json").toURI().toString();

    String query = String.format(
        "(select key from dfs_test.\"%s\" " +
        "union all " +
        "select key from dfs_test.\"%s\" )", rootInt, rootBoolean);

    test(query);
  }

  @Test // see DRILL-2591
  public void testDateAndTimestampJson() throws Exception {
    String rootDate = FileUtils.getResourceAsFile("/store/json/dateData.json").toURI().toString();
    String rootTimpStmp = FileUtils.getResourceAsFile("/store/json/timeStmpData.json").toURI().toString();

    String query1 = String.format(
        "(select max(key) as key from dfs_test.\"%s\" " +
        "union all " +
        "select key from dfs_test.\"%s\")", rootDate, rootTimpStmp);

    String query2 = String.format(
        "select key from dfs_test.\"%s\" " +
        "union all " +
        "select max(key) as key from dfs_test.\"%s\"", rootDate, rootTimpStmp);

    String query3 = String.format(
        "select key from dfs_test.\"%s\" " +
        "union all " +
        "select max(key) as key from dfs_test.\"%s\"", rootDate, rootTimpStmp);

    testBuilder()
        .sqlQuery(query1)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q18_1.tsv")
        .baselineTypes(MinorType.VARCHAR)
        .baselineColumns("key")
        .build().run();

    testBuilder()
        .sqlQuery(query2)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q18_2.tsv")
        .baselineTypes(MinorType.VARCHAR)
        .baselineColumns("key")
        .build().run();

    testBuilder()
        .sqlQuery(query3)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/q18_3.tsv")
        .baselineTypes(MinorType.VARCHAR)
        .baselineColumns("key")
        .build().run();
  }

  @Ignore("DX-4180")
  @Test // see DRILL-2637
  public void testUnionAllOneInputContainsAggFunction() throws Exception {
    String root = FileUtils.getResourceAsFile("/multilevel/csv/1994/Q1/orders_94_q1.csv").toURI().toString();
    String query1 = String.format("select * from ((select count(c1) as ct from (select columns[0] c1 from dfs.\"%s\")) \n" +
        "union all \n" +
        "(select columns[0] c2 from dfs.\"%s\")) order by ct limit 3", root, root);

    String query2 = String.format("select * from ((select columns[0] ct from dfs.\"%s\")\n" +
        "union all \n" +
        "(select count(c1) as c2 from (select columns[0] c1 from dfs.\"%s\"))) order by ct limit 3", root, root);

    String query3 = String.format("select * from ((select count(c1) as ct from (select columns[0] c1 from dfs.\"%s\"))\n" +
        "union all \n" +
        "(select count(c1) as c2 from (select columns[0] c1 from dfs.\"%s\"))) order by ct", root, root);

    testBuilder()
        .sqlQuery(query1)
        .ordered()
        .baselineColumns("ct")
        .baselineValues((long) 10)
        .baselineValues((long) 66)
        .baselineValues((long) 99)
        .build().run();

    testBuilder()
        .sqlQuery(query2)
        .ordered()
        .baselineColumns("ct")
        .baselineValues((long) 10)
        .baselineValues((long) 66)
        .baselineValues((long) 99)
        .build().run();

    testBuilder()
        .sqlQuery(query3)
         .ordered()
         .baselineColumns("ct")
         .baselineValues((long) 10)
         .baselineValues((long) 10)
         .build().run();
  }

  @Test // see DRILL-2717
  public void testUnionInputsGroupByOnCSV() throws Exception {
    String root = FileUtils.getResourceAsFile("/multilevel/csv/1994/Q1/orders_94_q1.csv").toURI().toString();
    String query = String.format("select * from \n" +
            "((select columns[0] as col0 from dfs.\"%s\" t1 \n" +
            "where t1.columns[0] = 66) \n" +
            "union all \n" +
            "(select columns[0] c2 from dfs.\"%s\" t2 \n" +
            "where t2.columns[0] is not null \n" +
            "group by columns[0])) \n" +
        "group by col0"
        , root, root);

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .baselineColumns("col0")
        .baselineValues("290")
        .baselineValues("291")
        .baselineValues("323")
        .baselineValues("352")
        .baselineValues("389")
        .baselineValues("417")
        .baselineValues("66")
        .baselineValues("673")
        .baselineValues("833")
        .baselineValues("99")
        .build().run();
  }

  @Test
  public void testUnionAllRightEmptyBatch() throws Exception {
    String rootSimple = FileUtils.getResourceAsFile("/store/json/booleanData.json").toURI().toString();

    String queryRightEmptyBatch = String.format(
        "select key from dfs_test.\"%s\" " +
            "union all " +
            "select key from dfs_test.\"%s\" where 1 = 0",
        rootSimple,
        rootSimple);

    testBuilder()
        .sqlQuery(queryRightEmptyBatch)
        .unOrdered()
        .baselineColumns("key")
        .baselineValues(true)
        .baselineValues(false)
        .build().run();
  }

  @Test
  public void testUnionAllLeftEmptyBatch() throws Exception {
    String rootSimple = FileUtils.getResourceAsFile("/store/json/booleanData.json").toURI().toString();

    final String queryLeftBatch = String.format(
        "select key from dfs_test.\"%s\" where 1 = 0 " +
            "union all " +
            "select key from dfs_test.\"%s\"",
        rootSimple,
        rootSimple);

    testBuilder()
        .sqlQuery(queryLeftBatch)
        .unOrdered()
        .baselineColumns("key")
        .baselineValues(true)
        .baselineValues(false)
        .build()
        .run();
  }

  @Ignore("DX-4180")
  @Test
  public void testUnionAllBothEmptyBatch() throws Exception {
    String rootSimple = FileUtils.getResourceAsFile("/store/json/booleanData.json").toURI().toString();
    final String query = String.format(
        "select key from dfs_test.\"%s\" where 1 = 0 " +
            "union all " +
            "select key from dfs_test.\"%s\" where 1 = 0",
        rootSimple,
        rootSimple);

    final List<Pair<SchemaPath, MajorType>> expectedSchema = Lists.newArrayList();
    final MajorType majorType = Types.optional(MinorType.INT);
    expectedSchema.add(Pair.of(SchemaPath.getSimplePath("key"), majorType));

    testBuilder()
        .sqlQuery(query)
        .schemaBaseLine(expectedSchema)
        .build()
        .run();
  }

  @Test // see DRILL-2746
  public void testFilterPushDownOverUnionAll() throws Exception {
    String query = "select n_regionkey from \n"
        + "(select n_regionkey from cp.\"tpch/nation.parquet\" union all select r_regionkey from cp.\"tpch/region.parquet\") \n"
        + "where n_regionkey > 0 and n_regionkey < 2 \n"
        + "order by n_regionkey";

    // Validate the plan
    final String[] expectedPlan = {"Scan.*columns=\\[`n_regionkey`\\]", "Scan.*columns=\\[`r_regionkey`\\]"};
    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    // Validate the result
    testBuilder()
        .sqlQuery(query)
        .ordered()
        .baselineColumns("n_regionkey")
        .baselineValues(1)
        .baselineValues(1)
        .baselineValues(1)
        .baselineValues(1)
        .baselineValues(1)
        .baselineValues(1)
        .build()
        .run();
  }

  @Test // see DRILL-2746
  public void testInListOnUnionAll() throws Exception {
    String query = "select n_nationkey \n" +
        "from (select n1.n_nationkey from cp.\"tpch/nation.parquet\" n1 inner join cp.\"tpch/region.parquet\" r1 on n1.n_regionkey = r1.r_regionkey \n" +
        "union all \n" +
        "select n2.n_nationkey from cp.\"tpch/nation.parquet\" n2 inner join cp.\"tpch/region.parquet\" r2 on n2.n_regionkey = r2.r_regionkey) \n" +
        "where n_nationkey in (1, 2)";

    // Validate the plan
    final String[] expectedPlan = {
                ".*Filter.*\n" +
                ".*Scan.*columns=\\[`n_nationkey`, `n_regionkey`\\].*\n" +
                ".*Scan.*columns=\\[`r_regionkey`\\].*\n" +
                ".*Project.*\n" +
                ".*HashJoin.*\n" +
                ".*SelectionVectorRemover.*\n" +
                ".*Filter.*\n" +
                ".*Scan.*columns=\\[`n_nationkey`, `n_regionkey`\\].*\n" +
                ".*Scan.*columns=\\[`r_regionkey`\\].*"};
    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    // Validate the result
    testBuilder()
        .sqlQuery(query)
        .ordered()
        .baselineColumns("n_nationkey")
        .baselineValues(1)
        .baselineValues(2)
        .baselineValues(1)
        .baselineValues(2)
        .build()
        .run();
  }

  @Ignore("DX-4180")
  @Test // see DRILL-2746
  public void testFilterPushDownOverUnionAllCSV() throws Exception {
    String root = FileUtils.getResourceAsFile("/multilevel/csv/1994/Q1/orders_94_q1.csv").toURI().toString();
    String query = String.format("select ct \n" +
        "from ((select count(c1) as ct from (select columns[0] c1 from dfs.\"%s\")) \n" +
        "union all \n" +
        "(select columns[0] c2 from dfs.\"%s\")) \n" +
        "where ct < 100", root, root);

    // Validate the plan
    final String[] expectedPlan = {"Filter.*\n" +
        ".*UnionAll.*\n" +
            ".*StreamAgg.*\n" +
                ".*Project.*\n" +
                    ".*Scan.*columns=\\[`columns`\\[0\\]\\].*\n" +
            ".*Project.*\n" +
                ".*Scan.*columns=\\[`columns`\\[0\\]\\].*"};

    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    // Validate the result
    testBuilder()
        .sqlQuery(query)
        .ordered()
        .baselineColumns("ct")
        .baselineValues((long) 10)
        .baselineValues((long) 66)
        .baselineValues((long) 99)
        .build().run();
  }

  @Test // see DRILL-3130
  public void testProjectPushDownOverUnionAllWithProject() throws Exception {
    String query = "select n_nationkey, n_name from \n" +
        "(select n_nationkey, n_name from cp.\"tpch/nation.parquet\" \n" +
        "union all select r_regionkey, r_name  from cp.\"tpch/region.parquet\")";

    // Validate the plan
    final String[] expectedPlan = {"Project\\(n_nationkey=\\[\\$0\\], n_name=\\[\\$1\\]\\).*\n" +
        ".*UnionAll.*\n" +
            ".*Project.*\n" +
                ".*Scan.*columns=\\[`n_nationkey`, `n_name`\\].*\n" +
            ".*Project.*\n" +
                ".*Scan.*columns=\\[`r_regionkey`, `r_name`\\].*"
    };
    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/testProjectPushDownOverUnionAllWithProject.tsv")
        .baselineTypes(MinorType.INT, MinorType.VARCHAR)
        .baselineColumns("n_nationkey", "n_name")
        .build()
        .run();
  }

  @Test // see DRILL-3130
  public void testProjectPushDownOverUnionAllWithoutProject() throws Exception {
    String query = "select n_nationkey from \n" +
        "(select n_nationkey, n_comment from cp.\"tpch/nation.parquet\" \n" +
        "union all select r_regionkey, r_comment  from cp.\"tpch/region.parquet\")";

    // Validate the plan
    final String[] expectedPlan = {"Project\\(n_nationkey=\\[\\$0\\]\\).*\n" +
        ".*UnionAll.*\n" +
        ".*Scan.*columns=\\[`n_nationkey`\\].*\n" +
        ".*Scan.*columns=\\[`r_regionkey`\\].*"};
    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    // Validate the result
    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/testProjectPushDownOverUnionAllWithoutProject.tsv")
        .baselineTypes(MinorType.INT)
        .baselineColumns("n_nationkey")
        .build()
        .run();
  }

  @Test // see DRILL-3130
  public void testProjectWithExpressionPushDownOverUnionAll() throws Exception {
    String query = "select 2 * n_nationkey as col from \n" +
        "(select n_nationkey, n_name, n_comment from cp.\"tpch/nation.parquet\" \n" +
        "union all select r_regionkey, r_name, r_comment  from cp.\"tpch/region.parquet\")";

    // Validate the plan
    final String[] expectedPlan = {
        "Scan.*columns=\\[`n_nationkey`\\]",
        "Scan.*columns=\\[`r_regionkey`\\]"
        };
    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    // Validate the result
    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/testProjectWithExpressionPushDownOverUnionAll.tsv")
        .baselineTypes(MinorType.INT)
        .baselineColumns("col")
        .build()
        .run();
  }

  @Ignore("DX-4180")
  @Test // see DRILL-3130
  public void testProjectDownOverUnionAllImplicitCasting() throws Exception {
    String root = FileUtils.getResourceAsFile("/store/text/data/nations.csv").toURI().toString();
    String query = String.format("select 2 * n_nationkey as col from \n" +
        "(select n_nationkey, n_name, n_comment from cp.\"tpch/nation.parquet\" \n" +
        "union all select columns[0], columns[1], columns[2] from dfs.\"%s\") \n" +
        "order by col limit 10", root);

    // Validate the plan
    final String[] expectedPlan = {"UnionAll.*\n." +
        ".*Project.*\n" +
            ".*Scan.*columns=\\[`n_nationkey`, `n_name`, `n_comment`\\].*\n" +
        ".*Project.*\n" +
            ".*Scan.*columns=\\[`columns`\\[0\\], `columns`\\[1\\], `columns`\\[2\\]\\]"};
    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    // Validate the result
    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/testProjectDownOverUnionAllImplicitCasting.tsv")
        .baselineTypes(MinorType.INT)
        .baselineColumns("col")
        .build()
        .run();
  }

  @Test // see DRILL-3130
  public void testProjectPushDownProjectColumnReorderingAndAlias() throws Exception {
    String query = "select n_comment as col1, n_nationkey as col2, n_name as col3 from \n" +
        "(select n_nationkey, n_name, n_comment from cp.\"tpch/nation.parquet\" \n" +
        "union all select r_regionkey, r_name, r_comment  from cp.\"tpch/region.parquet\")";

    // Validate the plan
    final String[] expectedPlan = {
        "Scan.*columns=\\[`n_nationkey`, `n_name`, `n_comment`\\]",
        "Scan.*columns=\\[`r_regionkey`, `r_name`, `r_comment`\\]"};
    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    // Validate the result
    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/testProjectPushDownProjectColumnReorderingAndAlias.tsv")
        .baselineTypes(MinorType.VARCHAR, MinorType.INT, MinorType.VARCHAR)
        .baselineColumns("col1", "col2", "col3")
        .build()
        .run();
  }

  @Test // see DRILL-2746, DRILL-3130
  public void testProjectFiltertPushDownOverUnionAll() throws Exception {
    String query = "select n_nationkey from \n" +
        "(select n_nationkey, n_name, n_comment from cp.\"tpch/nation.parquet\" \n" +
        "union all select r_regionkey, r_name, r_comment  from cp.\"tpch/region.parquet\") \n" +
        "where n_nationkey > 0 and n_nationkey < 4";

    /**
     * 00-07              Filter(condition=[AND(>($0, 0), <($0, 2))]) : rowType = RecordType(INTEGER n_regionkey): rowcount = 6.25, cumulative cost = {50.0 rows, 450.0 cpu, 250.0 io, 250.0 network, 0.0 memory}, id = 27288
00-09                ParquetScan(table=[cp.`tpch/nation.parquet`], columns=[`n_regionkey`], splits=[1]) : rowType = RecordType(INTEGER n_regionkey): rowcount = 25.0, cumulative cost = {25.0 rows, 250.0 cpu, 250.0 io, 250.0 network, 0.0 memory}, id = 27287
00-04            SelectionVectorRemover : rowType = RecordType(INTEGER r_regionkey): rowcount = 1.25, cumulative cost = {11.25 rows, 91.25 cpu, 50.0 io, 50.0 network, 0.0 memory}, id = 27292
00-06              Filter(condition=[AND(>($0, 0), <($0, 2))]) : rowType = RecordType(INTEGER r_regionkey): rowcount = 1.25, cumulative cost = {10.0 rows, 90.0 cpu, 50.0 io, 50.0 network, 0.0 memory}, id = 27291
00-08                ParquetScan(table=[cp.`tpch/region.parquet`], columns=[`r_regionkey`], splits=[1]) : rowType = RecordType(INTEGER r_regionkey): rowcount = 5.0, cumulative cost = {5.0 rows, 50.0 cpu, 50.0 io, 50.0 network, 0.0 memory}, id = 27290
     */
    // Validate the plan
    final String[] expectedPlan = {
        "Filter.*\n.*Scan.*columns=\\[`n_nationkey`\\]",
        "Filter.*\n.*Scan.*columns=\\[`r_regionkey`\\]"
        };
    final String[] excludedPlan = {};
    PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

    // Validate the result
    testBuilder()
        .sqlQuery(query)
        .unOrdered()
        .csvBaselineFile("testframework/testUnionAllQueries/testProjectFiltertPushDownOverUnionAll.tsv")
        .baselineTypes(MinorType.INT)
        .baselineColumns("n_nationkey")
        .build()
        .run();
  }

  @Test // DRILL-3257 (Simplified Query from TPC-DS query 74)
  public void testUnionAllInWith() throws Exception {
    final String query1 = "WITH year_total \n" +
        "     AS (SELECT c.r_regionkey    customer_id,\n" +
        "                1 year_total\n" +
        "         FROM   cp.\"tpch/region.parquet\" c\n" +
        "         UNION ALL \n" +
        "         SELECT c.r_regionkey    customer_id, \n" +
        "                1 year_total\n" +
        "         FROM   cp.\"tpch/region.parquet\" c) \n" +
        "SELECT count(t_s_secyear.customer_id) as ct \n" +
        "FROM   year_total t_s_firstyear, \n" +
        "       year_total t_s_secyear, \n" +
        "       year_total t_w_firstyear, \n" +
        "       year_total t_w_secyear \n" +
        "WHERE  t_s_secyear.customer_id = t_s_firstyear.customer_id \n" +
        "       AND t_s_firstyear.customer_id = t_w_secyear.customer_id \n" +
        "       AND t_s_firstyear.customer_id = t_w_firstyear.customer_id \n" +
        "       AND CASE \n" +
        "             WHEN t_w_firstyear.year_total > 0 THEN t_w_secyear.year_total \n" +
        "             ELSE NULL \n" +
        "           END > -1";

    final String query2 = "WITH year_total \n" +
        "     AS (SELECT c.r_regionkey    customer_id,\n" +
        "                1 year_total\n" +
        "         FROM   cp.\"tpch/region.parquet\" c\n" +
        "         UNION ALL \n" +
        "         SELECT c.r_regionkey    customer_id, \n" +
        "                1 year_total\n" +
        "         FROM   cp.\"tpch/region.parquet\" c) \n" +
        "SELECT count(t_w_firstyear.customer_id) as ct \n" +
        "FROM   year_total t_w_firstyear, \n" +
        "       year_total t_w_secyear \n" +
        "WHERE  t_w_firstyear.year_total = t_w_secyear.year_total \n" +
        " AND t_w_firstyear.year_total > 0 and t_w_secyear.year_total > 0";

    final String query3 = "WITH year_total_1\n" +
        "             AS (SELECT c.r_regionkey    customer_id,\n" +
        "                        1 year_total\n" +
        "                 FROM   cp.\"tpch/region.parquet\" c\n" +
        "                 UNION ALL \n" +
        "                 SELECT c.r_regionkey    customer_id, \n" +
        "                        1 year_total\n" +
        "                 FROM   cp.\"tpch/region.parquet\" c) \n" +
        "             , year_total_2\n" +
        "             AS (SELECT c.n_nationkey    customer_id,\n" +
        "                        1 year_total\n" +
        "                 FROM   cp.\"tpch/nation.parquet\" c\n" +
        "                 UNION ALL \n" +
        "                 SELECT c.n_nationkey    customer_id, \n" +
        "                        1 year_total\n" +
        "                 FROM   cp.\"tpch/nation.parquet\" c) \n" +
        "        SELECT count(t_w_firstyear.customer_id) as ct\n" +
        "        FROM   year_total_1 t_w_firstyear,\n" +
        "               year_total_2 t_w_secyear\n" +
        "        WHERE  t_w_firstyear.year_total = t_w_secyear.year_total\n" +
        "           AND t_w_firstyear.year_total > 0 and t_w_secyear.year_total > 0";

    final String query4 = "WITH year_total_1\n" +
        "             AS (SELECT c.r_regionkey    customer_id,\n" +
        "                        1 year_total\n" +
        "                 FROM   cp.\"tpch/region.parquet\" c\n" +
        "                 UNION ALL \n" +
        "                 SELECT c.n_nationkey    customer_id, \n" +
        "                        1 year_total\n" +
        "                 FROM   cp.\"tpch/nation.parquet\" c), \n" +
        "             year_total_2\n" +
        "             AS (SELECT c.r_regionkey    customer_id,\n" +
        "                        1 year_total\n" +
        "                 FROM   cp.\"tpch/region.parquet\" c\n" +
        "                 UNION ALL \n" +
        "                 SELECT c.n_nationkey    customer_id, \n" +
        "                        1 year_total\n" +
        "                 FROM   cp.\"tpch/nation.parquet\" c) \n" +
        "        SELECT count(t_w_firstyear.customer_id) as ct \n" +
        "        FROM   year_total_1 t_w_firstyear,\n" +
        "               year_total_2 t_w_secyear\n" +
        "        WHERE  t_w_firstyear.year_total = t_w_secyear.year_total\n" +
        "         AND t_w_firstyear.year_total > 0 and t_w_secyear.year_total > 0";

    testBuilder()
        .sqlQuery(query1)
        .ordered()
        .baselineColumns("ct")
        .baselineValues((long) 80)
        .build()
        .run();

    // DX-3642  Should not need to disable the rule to make these queries work.
    test("alter session set planner.enable_reduce_filter=false");
    testBuilder()
        .sqlQuery(query2)
        .ordered()
        .baselineColumns("ct")
        .baselineValues((long) 100)
        .build()
        .run();

    testBuilder()
        .sqlQuery(query3)
        .ordered()
        .baselineColumns("ct")
        .baselineValues((long) 500)
        .build()
        .run();

    testBuilder()
        .sqlQuery(query4)
        .ordered()
        .baselineColumns("ct")
        .baselineValues((long) 900)
        .build()
        .run();
    test("alter session set planner.enable_reduce_filter=true");
  }

  @Test // DRILL-4147 // base case
  public void testDrill4147_1() throws Exception {
    final String l = FileUtils.getResourceAsFile("/multilevel/parquet/1994").toURI().toString();
    final String r = FileUtils.getResourceAsFile("/multilevel/parquet/1995").toURI().toString();

    final String query = String.format("SELECT o_custkey FROM dfs_test.\"%s\" \n" +
        "Union All SELECT o_custkey FROM dfs_test.\"%s\"", l, r);

    // Validate the plan
    final String[] expectedPlan = {"UnionExchange.*\n",
        ".*Project.*\n" +
        ".*UnionAll"};
    final String[] excludedPlan = {};

    try {
      test(sliceTargetSmall);
      PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

      testBuilder()
        .optionSettingQueriesForTestQuery(sliceTargetSmall)
        .optionSettingQueriesForBaseline(sliceTargetDefault)
        .unOrdered()
        .sqlQuery(query)
        .sqlBaselineQuery(query)
        .build()
        .run();
    } finally {
      test(sliceTargetDefault);
    }
  }

  @Test // DRILL-4147  // group-by on top of union-all
  public void testDrill4147_2() throws Exception {
    final String l = FileUtils.getResourceAsFile("/multilevel/parquet/1994").toURI().toString();
    final String r = FileUtils.getResourceAsFile("/multilevel/parquet/1995").toURI().toString();

    final String query = String.format("Select o_custkey, count(*) as cnt from \n" +
        " (SELECT o_custkey FROM dfs_test.\"%s\" \n" +
        "Union All SELECT o_custkey FROM dfs_test.\"%s\") \n" +
        "group by o_custkey", l, r);

    // Validate the plan
    final String[] expectedPlan = {"(?s)UnionExchange.*HashAgg.*HashToRandomExchange.*UnionAll.*"};
    final String[] excludedPlan = {};

    try {
      test(sliceTargetSmall);
      PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

      testBuilder()
        .optionSettingQueriesForTestQuery(sliceTargetSmall)
        .optionSettingQueriesForBaseline(sliceTargetDefault)
        .unOrdered()
        .sqlQuery(query)
        .sqlBaselineQuery(query)
        .build()
        .run();
    } finally {
      test(sliceTargetDefault);
    }
  }

  @Test // DRILL-4147 // union-all above a hash join
  public void testDrill4147_3() throws Exception {
    final String l = FileUtils.getResourceAsFile("/multilevel/parquet/1994").toURI().toString();
    final String r = FileUtils.getResourceAsFile("/multilevel/parquet/1995").toURI().toString();

    final String query = String.format("SELECT o_custkey FROM \n" +
        " (select o1.o_custkey from dfs_test.\"%s\" o1 inner join dfs_test.\"%s\" o2 on o1.o_orderkey = o2.o_custkey) \n" +
        " Union All SELECT o_custkey FROM dfs_test.\"%s\" where o_custkey < 10", l, r, l);

    // Validate the plan
    final String[] expectedPlan = {"(?s)UnionExchange.*UnionAll.*HashJoin.*"};
    final String[] excludedPlan = {};

    try {
      test(sliceTargetSmall);
      PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

      testBuilder()
        .optionSettingQueriesForTestQuery(sliceTargetSmall)
        .optionSettingQueriesForBaseline(sliceTargetDefault)
        .unOrdered()
        .sqlQuery(query)
        .sqlBaselineQuery(query)
        .build()
        .run();
    } finally {
      test(sliceTargetDefault);
    }
  }

  @Test // DRILL-4833  // limit 1 is on RHS of union-all
  public void testDrill4833_1() throws Exception {
    final String l = FileUtils.getResourceAsFile("/multilevel/parquet/1994").toURI().toString();
    final String r = FileUtils.getResourceAsFile("/multilevel/parquet/1995").toURI().toString();

    final String query = String.format("SELECT o_custkey FROM \n" +
        " ((select o1.o_custkey from dfs_test.\"%s\" o1 inner join dfs_test.\"%s\" o2 on o1.o_orderkey = o2.o_custkey) \n" +
        " Union All (SELECT o_custkey FROM dfs_test.\"%s\" order by dir0 limit 1))", l, r, l);

    // Validate the plan
    final String[] expectedPlan = {"(?s)UnionExchange.*UnionAll.*HashJoin.*"};
    final String[] excludedPlan = {};

    try {
      test(sliceTargetSmall);
      test(enableDistribute);

      PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

      testBuilder()
        .optionSettingQueriesForTestQuery(sliceTargetSmall)
        .optionSettingQueriesForBaseline(sliceTargetDefault)
        .unOrdered()
        .sqlQuery(query)
        .sqlBaselineQuery(query)
        .go();

      test(sliceTargetSmall);

      testBuilder()
        .unOrdered()
        .sqlQuery(query)
        .sqlBaselineQuery(query)
        .go();

    } finally {
      test(sliceTargetDefault);
      test(defaultDistribute);
    }
  }

  @Test // DRILL-4833  // limit 1 is on LHS of union-all
  public void testDrill4833_2() throws Exception {
    final String l = FileUtils.getResourceAsFile("/multilevel/parquet/1994").toURI().toString();
    final String r = FileUtils.getResourceAsFile("/multilevel/parquet/1995").toURI().toString();

    final String query = String.format("SELECT o_custkey FROM \n" +
        " ((SELECT o_custkey FROM dfs_test.\"%s\" order by dir0 limit 1) \n" +
        " union all \n" +
        " (select o1.o_custkey from dfs_test.\"%s\" o1 inner join dfs_test.\"%s\" o2 on o1.o_orderkey = o2.o_custkey))", l, r, l);

    // Validate the plan
    final String[] expectedPlan = {"(?s)UnionExchange.*UnionAll.*HashJoin.*"};
    final String[] excludedPlan = {};

    try {
      test(sliceTargetSmall);
      test(enableDistribute);

      PlanTestBase.testPlanMatchingPatterns(query, expectedPlan, excludedPlan);

      testBuilder()
        .optionSettingQueriesForTestQuery(sliceTargetSmall)
        .optionSettingQueriesForBaseline(sliceTargetDefault)
        .unOrdered()
        .sqlQuery(query)
        .sqlBaselineQuery(query)
        .go();

      test(sliceTargetSmall);

      testBuilder()
        .unOrdered()
        .sqlQuery(query)
        .sqlBaselineQuery(query)
        .go();

    } finally {
      test(sliceTargetDefault);
      test(defaultDistribute);
    }
  }

}
