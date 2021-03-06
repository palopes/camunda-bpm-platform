/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.engine.test.api.history.removaltime.batch;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.DecisionService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.batch.history.HistoricBatch;
import org.camunda.bpm.engine.batch.history.HistoricBatchQuery;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstanceQuery;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.history.SetRemovalTimeToHistoricBatchesBuilder;
import org.camunda.bpm.engine.history.SetRemovalTimeToHistoricDecisionInstancesBuilder;
import org.camunda.bpm.engine.history.SetRemovalTimeToHistoricProcessInstancesBuilder;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.api.history.removaltime.batch.helper.BatchSetRemovalTimeRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_FULL;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_REMOVAL_TIME_STRATEGY_END;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_REMOVAL_TIME_STRATEGY_NONE;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_REMOVAL_TIME_STRATEGY_START;
import static org.camunda.bpm.engine.test.api.history.removaltime.batch.helper.BatchSetRemovalTimeRule.addDays;

/**
 * @author Tassilo Weidner
 */

@RequiredHistoryLevel(HISTORY_FULL)
public class BatchSetRemovalTimeTest {

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule engineTestRule = new ProcessEngineTestRule(engineRule);
  protected BatchSetRemovalTimeRule testRule = new BatchSetRemovalTimeRule(engineRule, engineTestRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(engineTestRule).around(testRule);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  protected final Date CURRENT_DATE = testRule.CURRENT_DATE;
  protected final Date REMOVAL_TIME = testRule.REMOVAL_TIME;

  protected RuntimeService runtimeService;
  private DecisionService decisionService;
  protected HistoryService historyService;
  protected ManagementService managementService;

  @Before
  public void assignServices() {
    runtimeService = engineRule.getRuntimeService();
    decisionService = engineRule.getDecisionService();
    historyService = engineRule.getHistoryService();
    managementService = engineRule.getManagementService();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldNotSetRemovalTime_DmnDisabled() {
    // given
    testRule.getProcessEngineConfiguration()
      .setDmnEnabled(false);

    testRule.process().ruleTask("dish-decision").deploy().startWithVariables(
      Variables.createVariables()
        .putValue("temperature", 32)
        .putValue("dayType", "Weekend")
    );

    List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldNotSetRemovalTimeInHierarchy_DmnDisabled() {
    // given
    testRule.getProcessEngineConfiguration()
      .setDmnEnabled(false);

    testRule.process()
      .call()
        .passVars("temperature", "dayType")
      .ruleTask("dish-decision")
      .deploy()
      .startWithVariables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      );

    List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldNotSetRemovalTimeForStandaloneDecision_DmnDisabled() {
    // given
    testRule.getProcessEngineConfiguration()
      .setDmnEnabled(false);

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();
  }

  @Test
  public void shouldSetRemovalTime_MultipleInvocationsPerBatchJob() {
    // given
    testRule.getProcessEngineConfiguration()
      .setInvocationsPerBatchJob(2);

    testRule.process().userTask().deploy().start();
    testRule.process().userTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeForStandaloneDecision_MultipleInvocationsPerBatchJob() {
    // given
    testRule.getProcessEngineConfiguration()
      .setInvocationsPerBatchJob(2);

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // assume
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  public void shouldSetRemovalTimeForBatch_MultipleInvocationsPerBatchJob() {
    // given
    testRule.getProcessEngineConfiguration()
      .setInvocationsPerBatchJob(2);

    String processInstanceIdOne = testRule.process().userTask().deploy().start();
    Batch batchOne = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceIdOne), "");

    String processInstanceIdTwo = testRule.process().userTask().deploy().start();
    Batch batchTwo = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceIdTwo), "");

    List<HistoricBatch> historicBatches = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .list();

    // assume
    assertThat(historicBatches.get(0).getRemovalTime()).isNull();
    assertThat(historicBatches.get(1).getRemovalTime()).isNull();

    HistoricBatchQuery query = historyService.createHistoricBatchQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicBatches = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .list();

    // then
    assertThat(historicBatches.get(0).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicBatches.get(1).getRemovalTime()).isEqualTo(REMOVAL_TIME);

    // clear database
    managementService.deleteBatch(batchOne.getId(), true);
    managementService.deleteBatch(batchTwo.getId(), true);
  }

  @Test
  public void shouldSetRemovalTime_SingleInvocationPerBatchJob() {
    // given
    testRule.getProcessEngineConfiguration()
      .setInvocationsPerBatchJob(1);

    testRule.process().userTask().deploy().start();
    testRule.process().userTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeForStandaloneDecision_SingleInvocationPerBatchJob() {
    // given
    testRule.getProcessEngineConfiguration()
      .setInvocationsPerBatchJob(1);

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // assume
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  public void shouldSetRemovalTimeForBatch_SingleInvocationPerBatchJob() {
    // given
    testRule.getProcessEngineConfiguration()
      .setInvocationsPerBatchJob(1);

    String processInstanceIdOne = testRule.process().userTask().deploy().start();
    Batch batchOne = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceIdOne), "");

    String processInstanceIdTwo = testRule.process().userTask().deploy().start();
    Batch batchTwo = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceIdTwo), "");

    List<HistoricBatch> historicBatches = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .list();

    // assume
    assertThat(historicBatches.get(0).getRemovalTime()).isNull();
    assertThat(historicBatches.get(1).getRemovalTime()).isNull();

    HistoricBatchQuery query = historyService.createHistoricBatchQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicBatches = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .list();

    // then
    assertThat(historicBatches.get(0).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicBatches.get(1).getRemovalTime()).isEqualTo(REMOVAL_TIME);

    // clear database
    managementService.deleteBatch(batchOne.getId(), true);
    managementService.deleteBatch(batchTwo.getId(), true);
  }

  @Test
  public void shouldNotSetRemovalTime_BaseTimeNone() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE)
      .initHistoryRemovalTime();

    testRule.process().ttl(5).serviceTask().deploy().start();

    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // assume
    assertThat(historicProcessInstance.getRemovalTime()).isNull();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // then
    assertThat(historicProcessInstance.getRemovalTime()).isNull();
  }

  @Test
  public void shouldClearRemovalTime_BaseTimeNone() {
    // given
    testRule.process().ttl(5).serviceTask().deploy().start();

    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // assume
    assertThat(historicProcessInstance.getRemovalTime()).isNotNull();

    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE)
      .initHistoryRemovalTime();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // then
    assertThat(historicProcessInstance.getRemovalTime()).isNull();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldNotSetRemovalTimeForStandaloneDecision_BaseTimeNone() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE)
      .initHistoryRemovalTime();

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // assume
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldClearRemovalTimeForStandaloneDecision_BaseTimeNone() {
    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    // given
    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // assume
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNotNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNotNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNotNull();

    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE)
      .initHistoryRemovalTime();

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isNull();
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isNull();
  }

  @Test
  public void shouldNotSetRemovalTimeInHierarchy_BaseTimeNone() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE)
      .initHistoryRemovalTime();

    testRule.process().ttl(5).call().serviceTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();
  }

  @Test
  public void shouldClearRemovalTimeInHierarchy_BaseTimeNone() {
    // given
    testRule.process().ttl(5).call().serviceTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNotNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNotNull();

    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE)
      .initHistoryRemovalTime();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldNotSetRemovalTimeForStandaloneDecisionInHierarchy_BaseTimeNone() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE)
      .initHistoryRemovalTime();

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isNull();

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery().rootDecisionInstancesOnly();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isNull();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldClearRemovalTimeForStandaloneDecisionInHierarchy_BaseTimeNone() {
    // given
    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isNotNull();

    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE)
      .initHistoryRemovalTime();

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery().rootDecisionInstancesOnly();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isNull();
  }

  @Test
  public void shouldNotSetRemovalTimeForBatch_BaseTimeNone() {
    // given
    ProcessEngineConfigurationImpl configuration = testRule.getProcessEngineConfiguration();
    configuration.setHistoryCleanupStrategy("endTimeBased");
    configuration.setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE);

    configuration.setBatchOperationHistoryTimeToLive("P5D");
    configuration.initHistoryCleanup();

    String processInstanceIdOne = testRule.process().serviceTask().deploy().start();
    Batch batchOne = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceIdOne), "");
    testRule.syncExec(batchOne);

    String processInstanceIdTwo = testRule.process().serviceTask().deploy().start();
    Batch batchTwo = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceIdTwo), "");
    testRule.syncExec(batchTwo);

    List<HistoricBatch> historicBatches = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .list();

    // assume
    assertThat(historicBatches.get(0).getRemovalTime()).isNull();
    assertThat(historicBatches.get(1).getRemovalTime()).isNull();

    HistoricBatchQuery query = historyService.createHistoricBatchQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicBatches = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .list();

    // then
    assertThat(historicBatches.get(0).getRemovalTime()).isNull();
    assertThat(historicBatches.get(1).getRemovalTime()).isNull();
  }

  @Test
  public void shouldClearRemovalTimeForBatch_BaseTimeNone() {
    // given
    ProcessEngineConfigurationImpl configuration = testRule.getProcessEngineConfiguration();

    configuration.setBatchOperationHistoryTimeToLive("P5D");
    configuration.initHistoryCleanup();

    String processInstanceIdOne = testRule.process().serviceTask().deploy().start();
    Batch batchOne = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceIdOne), "");
    testRule.syncExec(batchOne);

    String processInstanceIdTwo = testRule.process().serviceTask().deploy().start();
    Batch batchTwo = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceIdTwo), "");
    testRule.syncExec(batchTwo);

    List<HistoricBatch> historicBatches = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .list();

    // assume
    assertThat(historicBatches.get(0).getRemovalTime()).isNotNull();
    assertThat(historicBatches.get(1).getRemovalTime()).isNotNull();

    configuration.setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_NONE);

    HistoricBatchQuery query = historyService.createHistoricBatchQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicBatches = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .list();

    // then
    assertThat(historicBatches.get(0).getRemovalTime()).isNull();
    assertThat(historicBatches.get(1).getRemovalTime()).isNull();
  }

  @Test
  public void shouldSetRemovalTime_BaseTimeStart() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_START)
      .initHistoryRemovalTime();

    testRule.process().userTask().deploy().start();

    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // assume
    assertThat(historicProcessInstance.getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLive("process", 5);

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // then
    assertThat(historicProcessInstance.getRemovalTime()).isEqualTo(testRule.addDays(CURRENT_DATE, 5));
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeForStandaloneDecision_BaseTimeStart() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_START)
      .initHistoryRemovalTime();

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .rootDecisionInstancesOnly()
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .rootDecisionInstancesOnly()
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
  }

  @Test
  public void shouldSetRemovalTimeForBatch_BaseTimeStart() {
    // given
    String processInstanceId = testRule.process().serviceTask().deploy().start();
    Batch batch = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceId), "");

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // assume
    assertThat(historicBatch.getRemovalTime()).isNull();

    ProcessEngineConfigurationImpl configuration = testRule.getProcessEngineConfiguration();
    configuration.setBatchOperationHistoryTimeToLive("P5D");
    configuration.initHistoryCleanup();

    HistoricBatchQuery query = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION);

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // then
    assertThat(historicBatch.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));

    // clear database
    managementService.deleteBatch(batch.getId(), true);
  }

  @Test
  public void shouldSetRemovalTimeInHierarchy_BaseTimeStart() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_START)
      .initHistoryRemovalTime();

    testRule.process().call().userTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLive("rootProcess", 5);

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeInHierarchyForStandaloneDecision_BaseTimeStart() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_START)
      .initHistoryRemovalTime();

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
  }

  @Test
  public void shouldNotSetRemovalTime_BaseTimeEnd() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END)
      .initHistoryRemovalTime();

    testRule.process().ttl(5).userTask().deploy().start();

    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // assume
    assertThat(historicProcessInstance.getRemovalTime()).isNull();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // then
    assertThat(historicProcessInstance.getRemovalTime()).isNull();
  }

  @Test
  public void shouldClearRemovalTime_BaseTimeEnd() {
    // given
    testRule.process().ttl(5).userTask().deploy().start();

    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // assume
    assertThat(historicProcessInstance.getRemovalTime()).isNotNull();

    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END)
      .initHistoryRemovalTime();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // then
    assertThat(historicProcessInstance.getRemovalTime()).isNull();
  }

  @Test
  public void shouldNotSetRemovalTimeForBatch_BaseTimeEnd() {
    // given
    ProcessEngineConfigurationImpl configuration = testRule.getProcessEngineConfiguration();

    configuration
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END);

    String processInstanceId = testRule.process().serviceTask().deploy().start();
    Batch batch = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceId), "");

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // assume
    assertThat(historicBatch.getRemovalTime()).isNull();

    configuration.setBatchOperationHistoryTimeToLive("P5D");
    configuration.initHistoryCleanup();

    HistoricBatchQuery query = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION);

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // then
    assertThat(historicBatch.getRemovalTime()).isNull();

    // clear database
    managementService.deleteBatch(batch.getId(), true);
  }

  @Test
  public void shouldClearRemovalTimeForBatch_BaseTimeEnd() {
    // given
    ProcessEngineConfigurationImpl configuration = testRule.getProcessEngineConfiguration();
    configuration.setBatchOperationHistoryTimeToLive("P5D");
    configuration.initHistoryCleanup();

    String processInstanceId = testRule.process().serviceTask().deploy().start();
    Batch batch = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceId), "");

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // assume
    assertThat(historicBatch.getRemovalTime()).isNotNull();

    configuration.setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END);

    HistoricBatchQuery query = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION);

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // then
    assertThat(historicBatch.getRemovalTime()).isNull();

    // clear database
    managementService.deleteBatch(batch.getId(), true);
  }

  @Test
  public void shouldNotSetRemovalTimeInHierarchy_BaseTimeEnd() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END)
      .initHistoryRemovalTime();

    testRule.process().call().ttl(5).userTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();
  }

  @Test
  public void shouldClearRemovalTimeInHierarchy_BaseTimeEnd() {
    // given
    testRule.process().call().ttl(5).userTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNotNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNotNull();

    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END)
      .initHistoryRemovalTime();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();
  }

  @Test
  public void shouldSetRemovalTime_BaseTimeEnd() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END)
      .initHistoryRemovalTime();

    testRule.process().serviceTask().deploy().start();

    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // assume
    assertThat(historicProcessInstance.getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLive("process", 5);

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // then
    assertThat(historicProcessInstance.getRemovalTime()).isEqualTo(testRule.addDays(CURRENT_DATE, 5));
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeForStandaloneDecision_BaseTimeEnd() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END)
      .initHistoryRemovalTime();

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .rootDecisionInstancesOnly()
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .rootDecisionInstancesOnly()
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
  }

  @Test
  public void shouldSetRemovalTimeForBatch_BaseTimeEnd() {
    // given
    ProcessEngineConfigurationImpl configuration = testRule.getProcessEngineConfiguration();

    configuration
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END);

    String processInstanceId = testRule.process().serviceTask().deploy().start();
    Batch batch = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceId), "");

    ClockUtil.setCurrentTime(addDays(CURRENT_DATE, 1));

    testRule.syncExec(batch);

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // assume
    assertThat(historicBatch.getRemovalTime()).isNull();

    configuration.setBatchOperationHistoryTimeToLive("P5D");
    configuration.initHistoryCleanup();

    HistoricBatchQuery query = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION);

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // then
    assertThat(historicBatch.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5+1));
  }

  @Test
  public void shouldSetRemovalTimeInHierarchy_BaseTimeEnd() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END)
      .initHistoryRemovalTime();

    testRule.process().call().serviceTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLive("rootProcess", 5);

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeInHierarchyForStandaloneDecision_BaseTimeEnd() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_END)
      .initHistoryRemovalTime();

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery().rootDecisionInstancesOnly();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
  }

  @Test
  public void shouldSetRemovalTime_Null() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_START)
      .initHistoryRemovalTime();

    testRule.process().ttl(5).userTask().deploy().start();

    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // assume
    assertThat(historicProcessInstance.getRemovalTime()).isEqualTo(testRule.addDays(CURRENT_DATE, 5));

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .absoluteRemovalTime(null)
        .executeAsync()
    );

    historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // then
    assertThat(historicProcessInstance.getRemovalTime()).isNull();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeForStandaloneDecision_Null() {
    // given
    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .rootDecisionInstancesOnly()
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .absoluteRemovalTime(null)
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .rootDecisionInstancesOnly()
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(null);
  }

  @Test
  public void shouldSetRemovalTimeForBatch_Null() {
    // given
    ProcessEngineConfigurationImpl configuration = testRule.getProcessEngineConfiguration();

    configuration.setBatchOperationHistoryTimeToLive("P5D");
    configuration.initHistoryCleanup();

    String processInstanceId = testRule.process().serviceTask().deploy().start();
    Batch batch = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceId), "");

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // assume
    assertThat(historicBatch.getRemovalTime()).isNotNull();

    HistoricBatchQuery query = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION);

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .absoluteRemovalTime(null)
        .executeAsync()
    );

    historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // then
    assertThat(historicBatch.getRemovalTime()).isNull();

    // clear database
    managementService.deleteBatch(batch.getId(), true);
  }

  @Test
  public void shouldSetRemovalTimeInHierarchy_Null() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_START)
      .initHistoryRemovalTime();

    testRule.process().call().ttl(5).userTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .absoluteRemovalTime(null)
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isNull();
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isNull();
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeInHierarchyForStandaloneDecision_Null() {
    // given
    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery().rootDecisionInstancesOnly();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .absoluteRemovalTime(null)
        .hierarchical()
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(null);
  }

  @Test
  public void shouldSetRemovalTime_Absolute() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_START)
      .initHistoryRemovalTime();

    testRule.process().ttl(5).userTask().deploy().start();

    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // assume
    assertThat(historicProcessInstance.getRemovalTime()).isEqualTo(testRule.addDays(CURRENT_DATE, 5));

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicProcessInstance = historyService.createHistoricProcessInstanceQuery().singleResult();

    // then
    assertThat(historicProcessInstance.getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeForStandaloneDecision_Absolute() {
    // given
    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .rootDecisionInstancesOnly()
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .rootDecisionInstancesOnly()
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  public void shouldSetRemovalTimeForBatch_Absolute() {
    // given
    String processInstanceId = testRule.process().serviceTask().deploy().start();
    Batch batch = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceId), "");

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // assume
    assertThat(historicBatch.getRemovalTime()).isNull();

    HistoricBatchQuery query = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION);

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .executeAsync()
    );

    historicBatch = historyService.createHistoricBatchQuery()
      .type(Batch.TYPE_HISTORIC_PROCESS_INSTANCE_DELETION)
      .singleResult();

    // then
    assertThat(historicBatch.getRemovalTime()).isEqualTo(REMOVAL_TIME);

    // clear database
    managementService.deleteBatch(batch.getId(), true);
  }

  @Test
  public void shouldSetRemovalTimeInHierarchy_Absolute() {
    // given
    testRule.getProcessEngineConfiguration()
      .setHistoryRemovalTimeStrategy(HISTORY_REMOVAL_TIME_STRATEGY_START)
      .initHistoryRemovalTime();

    testRule.process().call().ttl(5).userTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().rootProcessInstances();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeInHierarchyForStandaloneDecision_Absolute() {
    // given
    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery().rootDecisionInstancesOnly();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .hierarchical()
        .executeAsync()
    );

    historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // then
    assertThat(historicDecisionInstance.getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  public void shouldSetRemovalTimeInHierarchy_ByChildInstance() {
    // given
    String rootProcessInstance = testRule.process().call().ttl(5).userTask().deploy().start();

    List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // assume
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
      .superProcessInstanceId(rootProcessInstance);

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .absoluteRemovalTime(REMOVAL_TIME)
        .hierarchical()
        .executeAsync()
    );

    historicProcessInstances = historyService.createHistoricProcessInstanceQuery().list();

    // then
    assertThat(historicProcessInstances.get(0).getRemovalTime()).isEqualTo(REMOVAL_TIME);
    assertThat(historicProcessInstances.get(1).getRemovalTime()).isEqualTo(REMOVAL_TIME);
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldSetRemovalTimeInHierarchyForStandaloneDecision_ByChildInstance() {
    // given
    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstance historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
      .decisionDefinitionKey("season")
      .singleResult();

    // assume
    assertThat(historicDecisionInstance.getRemovalTime()).isNull();

    testRule.updateHistoryTimeToLiveDmn("dish-decision", 5);

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery()
      .decisionInstanceId(historicDecisionInstance.getId());

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .hierarchical()
        .executeAsync()
    );

    List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery().list();

    // then
    assertThat(historicDecisionInstances.get(0).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
    assertThat(historicDecisionInstances.get(1).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
    assertThat(historicDecisionInstances.get(2).getRemovalTime()).isEqualTo(addDays(CURRENT_DATE, 5));
  }

  @Test
  public void shouldThrowBadUserRequestException() {
    // given

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("historicProcessInstances is empty");

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    historyService.setRemovalTimeToHistoricProcessInstances()
      .byQuery(query)
      .absoluteRemovalTime(REMOVAL_TIME)
      .executeAsync();
  }

  @Test
  public void shouldThrowBadUserRequestExceptionForStandaloneDecision() {
    // given

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("historicDecisionInstances is empty");

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    historyService.setRemovalTimeToHistoricDecisionInstances()
      .byQuery(query)
      .absoluteRemovalTime(REMOVAL_TIME)
      .executeAsync();
  }

  @Test
  public void shouldThrowBadUserRequestExceptionForBatch() {
    // given

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("historicBatches is empty");

    HistoricBatchQuery query = historyService.createHistoricBatchQuery();

    // when
    historyService.setRemovalTimeToHistoricBatches()
      .byQuery(query)
      .absoluteRemovalTime(REMOVAL_TIME)
      .executeAsync();
  }

  @Test
  public void shouldProduceHistory() {
    // given
    testRule.process().serviceTask().deploy().start();

    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricProcessInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery().singleResult();

    // then
    assertThat(historicBatch.getType()).isEqualTo("process-set-removal-time");
    assertThat(historicBatch.getStartTime()).isEqualTo(CURRENT_DATE);
    assertThat(historicBatch.getEndTime()).isEqualTo(CURRENT_DATE);
  }

  @Test
  @Deployment(resources = {
    "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml"
  })
  public void shouldProduceHistoryForStandaloneDecision() {
    // given
    decisionService.evaluateDecisionByKey("dish-decision")
      .variables(
        Variables.createVariables()
          .putValue("temperature", 32)
          .putValue("dayType", "Weekend")
      ).evaluate();

    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricDecisionInstances()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery().singleResult();

    // then
    assertThat(historicBatch.getType()).isEqualTo("decision-set-removal-time");
    assertThat(historicBatch.getStartTime()).isEqualTo(CURRENT_DATE);
    assertThat(historicBatch.getEndTime()).isEqualTo(CURRENT_DATE);
  }

  @Test
  public void shouldProduceHistoryForBatch() {
    // given
    String processInstanceId = testRule.process().serviceTask().deploy().start();
    Batch batch = historyService.deleteHistoricProcessInstancesAsync(Collections.singletonList(processInstanceId), "");

    testRule.syncExec(batch);

    HistoricBatchQuery query = historyService.createHistoricBatchQuery();

    // when
    testRule.syncExec(
      historyService.setRemovalTimeToHistoricBatches()
        .byQuery(query)
        .calculatedRemovalTime()
        .executeAsync()
    );

    HistoricBatch historicBatch = historyService.createHistoricBatchQuery()
      .type("batch-set-removal-time")
      .singleResult();

    // then
    assertThat(historicBatch.getStartTime()).isEqualTo(CURRENT_DATE);
    assertThat(historicBatch.getEndTime()).isEqualTo(CURRENT_DATE);
  }

  @Test
  public void shouldThrowExceptionIfNoRemovalTimeSettingDefined()
  {
    // given
    HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

    SetRemovalTimeToHistoricProcessInstancesBuilder batchBuilder = historyService.setRemovalTimeToHistoricProcessInstances()
      .byQuery(query);

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("removalTime is null");

    // when
    batchBuilder.executeAsync();
  }

  @Test
  public void shouldThrowExceptionIfNoRemovalTimeSettingDefinedForStandaloneDecision() {
    // given
    HistoricDecisionInstanceQuery query = historyService.createHistoricDecisionInstanceQuery();

    SetRemovalTimeToHistoricDecisionInstancesBuilder batchBuilder = historyService.setRemovalTimeToHistoricDecisionInstances()
      .byQuery(query);

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("removalTime is null");

    // when
    batchBuilder.executeAsync();
  }

  @Test
  public void shouldThrowExceptionIfNoRemovalTimeSettingDefinedForBatch() {
    // given
    HistoricBatchQuery query = historyService.createHistoricBatchQuery();

    SetRemovalTimeToHistoricBatchesBuilder batchBuilder = historyService.setRemovalTimeToHistoricBatches()
      .byQuery(query);

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("removalTime is null");

    // when
    batchBuilder.executeAsync();
  }

  @Test
  public void shouldThrowExceptionIfNoQueryDefined()
  {
    // given
    SetRemovalTimeToHistoricProcessInstancesBuilder batchBuilder = historyService.setRemovalTimeToHistoricProcessInstances()
      .absoluteRemovalTime(new Date());

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("query is null");

    // when
    batchBuilder.executeAsync();
  }

  @Test
  public void shouldThrowExceptionIfNoQueryDefinedForStandaloneDecision()
  {
    // given
    SetRemovalTimeToHistoricDecisionInstancesBuilder batchBuilder = historyService.setRemovalTimeToHistoricDecisionInstances()
      .absoluteRemovalTime(new Date());

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("query is null");

    // when
    batchBuilder.executeAsync();
  }

  @Test
  public void shouldThrowExceptionIfNoQueryDefinedForBatch()
  {
    // given
    SetRemovalTimeToHistoricBatchesBuilder batchBuilder = historyService.setRemovalTimeToHistoricBatches()
      .absoluteRemovalTime(new Date());

    // then
    thrown.expect(BadUserRequestException.class);
    thrown.expectMessage("query is null");

    // when
    batchBuilder.executeAsync();
  }

}
