/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package com.amazon.opendistro.elasticsearch.performanceanalyzer.decisionmaker.deciders;

import com.amazon.opendistro.elasticsearch.performanceanalyzer.PerformanceAnalyzerApp;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.decisionmaker.actions.Action;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.decisionmaker.actions.ActionListener;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.decisionmaker.actions.FlipFlopDetector;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.decisionmaker.actions.TimedFlipFlopDetector;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.core.NonLeafNode;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.metrics.ExceptionsAndErrors;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.metrics.RcaGraphMetrics;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.scheduler.FlowUnitOperationArgWrapper;
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Publisher extends NonLeafNode<EmptyFlowUnit> {

  private static final Logger LOG = LogManager.getLogger(Publisher.class);
  private final long initTime;

  private Collator collator;
  private FlipFlopDetector flipFlopDetector;
  private boolean isMuted = false;
  private Map<String, Long> actionToExecutionTime;
  private List<ActionListener> actionListeners;

  public Publisher(int evalIntervalSeconds, Collator collator) {
    super(0, evalIntervalSeconds);
    this.collator = collator;
    this.actionListeners = new ArrayList<>();
    this.actionToExecutionTime = new HashMap<>();
    // TODO please bring in guice so we can configure this with DI
    this.flipFlopDetector = new TimedFlipFlopDetector(1, TimeUnit.HOURS);
    initTime = Instant.now().toEpochMilli();
  }

  /**
   * Returns true if a given {@link Action}'s last execution time was >= {@link Action#coolOffPeriodInMillis()} ago
   *
   * <p>If this Publisher has never executed the action, the last execution time is defined as the time that the
   * publisher object was constructed.
   *
   * @param action The {@link Action} to test
   * @return true if a given {@link Action}'s last execution time was >= {@link Action#coolOffPeriodInMillis()} ago
   */
  public boolean isCooledOff(Action action) {
    long lastExecution = actionToExecutionTime.getOrDefault(action.name(), initTime);
    long elapsed = Instant.now().toEpochMilli() - lastExecution;
    if (elapsed >= action.coolOffPeriodInMillis()) {
      return true;
    } else {
      LOG.debug("Publisher: Action {} still has {} ms left in its cool off period",
          action.name(),
          action.coolOffPeriodInMillis() - elapsed);
      return false;
    }
  }

  @Override
  public EmptyFlowUnit operate() {
    // TODO: Need to add dampening, avoidance, state persistence etc.
    Decision decision = collator.getFlowUnits().get(0);
    for (Action action : decision.getActions()) {
      if (isCooledOff(action) && !flipFlopDetector.isFlipFlop(action)) {
        flipFlopDetector.recordAction(action);
        actionToExecutionTime.put(action.name(), Instant.now().toEpochMilli());
        for (ActionListener listener : actionListeners) {
          listener.actionPublished(action);
        }
      }
    }
    return new EmptyFlowUnit(Instant.now().toEpochMilli());
  }

  @Override
  public void generateFlowUnitListFromLocal(FlowUnitOperationArgWrapper args) {
    LOG.debug("Publisher: Executing fromLocal: {}", name());
    long startTime = System.currentTimeMillis();

    try {
      this.operate();
    } catch (Exception ex) {
      LOG.error("Publisher: Exception in operate", ex);
      PerformanceAnalyzerApp.ERRORS_AND_EXCEPTIONS_AGGREGATOR.updateStat(
          ExceptionsAndErrors.EXCEPTION_IN_OPERATE, name(), 1);
    }
    long duration = System.currentTimeMillis() - startTime;

    PerformanceAnalyzerApp.RCA_GRAPH_METRICS_AGGREGATOR.updateStat(
        RcaGraphMetrics.GRAPH_NODE_OPERATE_CALL, this.name(), duration);
  }

  /**
   * Register an action listener with Publisher
   * <p>
   * The listener is notified whenever an action is published
   */
  public void addActionListener(ActionListener listener) {
    actionListeners.add(listener);
  }

  /**
   * Publisher does not have downstream nodes and does not emit flow units
   */
  @Override
  public void persistFlowUnit(FlowUnitOperationArgWrapper args) {
    assert true;
  }

  @Override
  public void generateFlowUnitListFromWire(FlowUnitOperationArgWrapper args) {
    assert true;
  }

  @Override
  public void handleNodeMuted() {
    assert true;
  }

  public long getInitTime() {
    return this.initTime;
  }

  @VisibleForTesting
  protected FlipFlopDetector getFlipFlopDetector() {
    return this.flipFlopDetector;
  }
}
