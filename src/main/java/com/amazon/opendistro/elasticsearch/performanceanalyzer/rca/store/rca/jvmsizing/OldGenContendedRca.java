package com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.store.rca.jvmsizing;

import com.amazon.opendistro.elasticsearch.performanceanalyzer.grpc.FlowUnitMessage;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.Rca;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.Resources.State;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.contexts.ResourceContext;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.flow_units.ResourceFlowUnit;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.summaries.HotNodeSummary;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.api.summaries.HotResourceSummary;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.framework.util.InstanceDetails;
import com.amazon.opendistro.elasticsearch.performanceanalyzer.rca.scheduler.FlowUnitOperationArgWrapper;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OldGenContendedRca extends Rca<ResourceFlowUnit<HotNodeSummary>> {

  private static final Logger LOG = LogManager.getLogger(OldGenContendedRca.class);
  private static final long EVAL_INTERVAL_IN_S = 5;
  private Rca<ResourceFlowUnit<HotResourceSummary>> highOldGenOccupancyRca;
  private Rca<ResourceFlowUnit<HotResourceSummary>> oldGenReclamationRca;

  public OldGenContendedRca(final Rca<ResourceFlowUnit<HotResourceSummary>> highOldGenOccupancyRca,
      final Rca<ResourceFlowUnit<HotResourceSummary>> oldGenReclamationRca) {
    super(EVAL_INTERVAL_IN_S);
    this.highOldGenOccupancyRca = highOldGenOccupancyRca;
    this.oldGenReclamationRca = oldGenReclamationRca;
  }

  @Override
  public void generateFlowUnitListFromWire(FlowUnitOperationArgWrapper args) {
    List<FlowUnitMessage> flowUnitMessages = args.getWireHopper().readFromWire(args.getNode());
    setFlowUnits(
        flowUnitMessages.stream()
                        .map((Function<FlowUnitMessage, ResourceFlowUnit<HotNodeSummary>>)
                            ResourceFlowUnit::buildFlowUnitFromWrapper)
                                 .collect(Collectors.toList()));
  }

  @Override
  public ResourceFlowUnit<HotNodeSummary> operate() {
    List<ResourceFlowUnit<HotResourceSummary>> oldGenOccupancyFlowUnits = highOldGenOccupancyRca
        .getFlowUnits();
    List<ResourceFlowUnit<HotResourceSummary>> oldGenReclamationFlowUnits = oldGenReclamationRca
        .getFlowUnits();
    long currTime = System.currentTimeMillis();

    // we expect only one flow unit to be present for both these RCAs as the nodes are scheduled
    // at the same frequency.

    if (oldGenOccupancyFlowUnits.size() != 1 || oldGenReclamationFlowUnits.size() != 1) {
      LOG.warn("Was expecting both oldGenOccupancy and oldGenReclamation RCAs to have exactly one"
          + " flowunit. Found: " + oldGenOccupancyFlowUnits.size() + ", and "
          + oldGenReclamationFlowUnits.size() + " respectively");
      return new ResourceFlowUnit<>(currTime);
    }

    ResourceFlowUnit<HotResourceSummary> oldGenOccupancyFlowUnit = oldGenOccupancyFlowUnits.get(0);
    ResourceFlowUnit<HotResourceSummary> oldGenReclamationFlowUnit =
        oldGenReclamationFlowUnits.get(0);

    if (!oldGenOccupancyFlowUnit.isEmpty()) {
      boolean isOccupancyUnhealthy = oldGenOccupancyFlowUnit.getResourceContext().isUnhealthy();
      boolean isFullGcIneffective = oldGenReclamationFlowUnit.getResourceContext().isUnhealthy();

      if (isOccupancyUnhealthy && isFullGcIneffective) {
        InstanceDetails instanceDetails = getAppContext().getMyInstanceDetails();
        HotNodeSummary summary =
            new HotNodeSummary(instanceDetails.getInstanceId(), instanceDetails.getInstanceIp());
        summary.appendNestedSummary(oldGenOccupancyFlowUnit.getSummary());
        summary.appendNestedSummary(oldGenReclamationFlowUnit.getSummary());

        ResourceContext context = new ResourceContext(State.CONTENDED);
        return new ResourceFlowUnit<>(currTime, context, summary);
      }
    }

    return new ResourceFlowUnit<>(currTime);
  }
}
