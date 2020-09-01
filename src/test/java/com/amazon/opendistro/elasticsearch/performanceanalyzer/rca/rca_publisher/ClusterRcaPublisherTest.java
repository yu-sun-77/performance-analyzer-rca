/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.rca_publisher;

import com.amazon.opendistro.elasticsearch.performanceanalyzer.plugins.Plugin;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.Rca;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.flow_units.ResourceFlowUnit;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.summaries.HotClusterSummary;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;



public class ClusterRcaPublisherTest {
  private static final int EVAL_INTERVAL = 5;
  private ClusterRcaPublisher clusterRcaPublisher;

  @Mock
  private Rca<ResourceFlowUnit<HotClusterSummary>> rca;

  @Mock
  private ClusterSummaryListener clusterSummaryListener;

  @Mock
  private ResourceFlowUnit<HotClusterSummary> flowUnit;

  @Mock
  private HotClusterSummary summary;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    clusterRcaPublisher = new ClusterRcaPublisher(EVAL_INTERVAL, Collections.singletonList(rca));
    clusterRcaPublisher.addClusterSummaryListener(clusterSummaryListener);
  }

  @Test
  public void testListenerInvocations() {
    Mockito.when(rca.getFlowUnits()).thenReturn(Collections.singletonList(flowUnit));
    Mockito.when(flowUnit.getSummary()).thenReturn(summary);
    Assert.assertEquals(rca.getFlowUnits().get(0), flowUnit);
    Assert.assertNotNull(clusterRcaPublisher.getClusterSummaryListeners());
    ClusterSummaryListener testListener = Mockito.mock(TestClusterSummaryListener.class);
    clusterRcaPublisher.addClusterSummaryListener(testListener);
    clusterRcaPublisher.operate();
    Mockito.verify(clusterSummaryListener, Mockito.times(1)).summaryPublished(clusterRcaPublisher.getClusterSummary());
    Mockito.verify(testListener, Mockito.times(1)).summaryPublished(clusterRcaPublisher.getClusterSummary());
  }

  public static class TestClusterSummaryListener extends Plugin implements ClusterSummaryListener {
    @Override
    public String name() {
      return "Test_Plugin";
    }

    @Override
    public void summaryPublished(ClusterSummary clusterSummary) {
      assert true;
    }
  }
}