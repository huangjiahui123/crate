/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.analyze;

import io.crate.action.sql.SessionContext;
import io.crate.data.Row;
import io.crate.metadata.CoordinatorTxnCtx;
import io.crate.planner.ExecutionPlan;
import io.crate.planner.Plan;
import io.crate.planner.PlannerContext;
import io.crate.planner.operators.LogicalPlan;
import io.crate.planner.operators.SubQueryResults;
import io.crate.sql.parser.SqlParser;
import io.crate.sql.tree.Statement;
import io.crate.testing.SQLExecutor;
import io.crate.es.cluster.ClusterState;
import io.crate.es.cluster.node.DiscoveryNode;
import io.crate.es.cluster.service.ClusterService;
import io.crate.es.threadpool.TestThreadPool;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static io.crate.testing.DiscoveryNodes.newNode;
import static io.crate.es.test.ClusterServiceUtils.createClusterService;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(value = Scope.Benchmark)
public class PreExecutionBenchmark {

    private TestThreadPool threadPool;
    private SQLExecutor e;
    private Statement selectStatement;
    private Analysis selectAnalysis;
    private PlannerContext plannerContext;

    @Setup
    public void setup() throws Exception {
        threadPool = new TestThreadPool("testing");
        DiscoveryNode localNode = newNode("benchmarkNode", "n1");
        ClusterService clusterService = createClusterService(threadPool, localNode);
        long dummySeed = 10;
        e = SQLExecutor.builder(clusterService, 1, new Random(dummySeed))
            .addTable("create table users (id int primary key, name string, date timestamp, text string index using fulltext)")
            .build();
        selectStatement = SqlParser.createStatement("select name from users");
        selectAnalysis =
            e.analyzer.boundAnalyze(selectStatement, new CoordinatorTxnCtx(SessionContext.systemSessionContext()), ParameterContext.EMPTY);
        plannerContext = e.getPlannerContext(clusterService.state(), new Random(dummySeed));
    }

    @TearDown
    public void cleanup() throws InterruptedException {
        threadPool.shutdown();
        threadPool.awaitTermination(20, TimeUnit.SECONDS);
    }

    @Benchmark
    public Statement measureParseSimpleSelect() throws Exception {
        return SqlParser.createStatement("select name from users");
    }

    @Benchmark
    public AnalyzedStatement measureParseAndAnalyzeSimpleSelect() {
        return e.analyze("select name from users");
    }

    @Benchmark
    public ExecutionPlan measureParseAnalyzeAndPlanSimpleSelect() {
        return e.plan("select name from users");
    }

    @Benchmark
    public Analysis measureAnalyzeSimpleSelect() {
        return e.analyzer.boundAnalyze(selectStatement, new CoordinatorTxnCtx(SessionContext.systemSessionContext()), ParameterContext.EMPTY);
    }

    @Benchmark
    public ExecutionPlan measurePlanSimpleSelect() {
        return ((LogicalPlan) e.planner.plan(selectAnalysis.analyzedStatement(), e.getPlannerContext(ClusterState.EMPTY_STATE)))
            .build(plannerContext, null, -1, 0, null, null, Row.EMPTY, SubQueryResults.EMPTY);
    }

    @Benchmark
    public ExecutionPlan measureParseAnalyzeAndPlanSelectWithMultiPrimaryKeyLookup() {
        return e.plan("select * from users where id = 1 or id = 2 or id = 3 or id = 4 order by id asc");
    }

    @Benchmark
    public Plan measureParseAnalyzeAndPlanInsertFromValues() {
        return e.plan("insert into users (id, name, text, date) values (1, 'Arthur', 'So long and thanks for all the fish', '2017-03-13')");
    }


    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(PreExecutionBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build();
        new Runner(opt).run();
    }
}
