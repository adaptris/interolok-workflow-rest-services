package com.adaptris.rest.metrics.interlok;

import java.util.Map;
import java.util.Optional;

import com.adaptris.rest.metrics.interlok.InterlokProfilerMetricsGenerator.MetricHelpTypeAndValue;

import io.micrometer.core.instrument.Tags;

public enum InterlokMetrics {
  WORKFLOW_MESSAGE_COUNT_METRIC ("workflow.count") {
    @Override
    public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
      this.calculateAndAddTotalValue(getMetricName(), workflowId, activityId, metricMap, value, createMetricTags(workflowId, activityId));
    }

    @Override
    public String getHelp() { return "The number of messages processed by this workflow since the last time we checked."; }
  },
  
  WORKFLOW_MESSAGE_FAIL_COUNT_METRIC ("workflow.fail.count") {
    @Override
    public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
      this.calculateAndAddTotalValue(getMetricName(), workflowId, activityId, metricMap, value, createMetricTags(workflowId, activityId));
    }

    @Override
    public String getHelp() { return "The number of messages processed by this workflow that have failed since the last time we checked."; }
  },
  
  WORKFLOW_AVG_TIME_NANOS_METRIC ("workflow.avgnanos") {
    @Override
    public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
      this.calculateAndAddAvgValue(getMetricName(), workflowId, activityId, metricMap, value, createMetricTags(workflowId, activityId));
    }

    @Override
    public String getHelp() { return "The average amount of time in nanoseconds this workflow takes to process a single message."; }
  },
  
  WORKFLOW_AVG_TIME_MILLIS_METRIC ("workflow.avgmillis") {
    @Override
    public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
      this.calculateAndAddAvgValue(getMetricName(), workflowId, activityId, metricMap, value, createMetricTags(workflowId, activityId));
    }

    @Override
    public String getHelp() { return "The average amount of time in millisecods this workflow takes to process a single message."; }
  },
  
  SERVICE_AVG_TIME_NANOS_METRIC ("service.avgnanos") {
    @Override
    public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
      this.calculateAndAddAvgValue(getMetricName(), workflowId, activityId, metricMap, value, createMetricTags(workflowId, activityId));
    }

    @Override
    public String getHelp() { return "The average amount of time in nanoseconds this service takes to process a single message."; }
  },
  
  SERVICE_AVG_TIME_MILLIS_METRIC ("service.avgmillis") {
    @Override
    public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
      this.calculateAndAddAvgValue(getMetricName(), workflowId, activityId, metricMap, value, createMetricTags(workflowId, activityId));
    }

    @Override
    public String getHelp() { return "The average amount of time in millisecods this service takes to process a single message."; }
  },
  
  PRODUCER_AVG_TIME_NANOS_METRIC ("producer.avgnanos") {
    @Override
    public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
      this.calculateAndAddAvgValue(getMetricName(), workflowId, activityId, metricMap, value, createMetricTags(workflowId, activityId));
    }

    @Override
    public String getHelp() { return "The average amount of time in nanoseconds this producer takes to process a single message."; }
  },

  PRODUCER_AVG_TIME_MILLIS_METRIC ("producer.avgmillis") {
    @Override
    public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
      this.calculateAndAddAvgValue(getMetricName(), workflowId, activityId, metricMap, value, createMetricTags(workflowId, activityId));
    }

    @Override
    public String getHelp() { return "The average amount of time in milliseconds this producer takes to process a single message."; }
  };
  
  private String metricName;
  
  private InterlokMetrics(String name) {
    setMetricName(name);
  }

  public String getMetricName() {
    return metricName;
  }

  public void setMetricName(String metricName) {
    this.metricName = metricName;
  }
  
  private static String WORKFLOW_TAG = "workflow";
  
  private static String COMPONENT_TAG = "component";
  
  public abstract void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap);
  
  public abstract String getHelp();
  
  public Tags createMetricTags(String workflowId, String activityId) {
    Tags tags = Tags.of(WORKFLOW_TAG, workflowId);
    if(activityId != null)
      tags = tags.and(COMPONENT_TAG, activityId);
    
    return tags;
  }
  
  public void calculateAndAddAvgValue(String metricName, String workflowId, String activityId, Map<String, MetricHelpTypeAndValue> metricMap, Double newValue, Tags tags) {
    Optional<MetricHelpTypeAndValue> metricHelpTypeValue = Optional.ofNullable(metricMap.get(metricName + workflowId + activityId));
    metricHelpTypeValue.ifPresent( oldMetricHelpTypeValue -> {
      oldMetricHelpTypeValue.setValue((newValue + oldMetricHelpTypeValue.getValue()) / 2);
    });        
    metricMap.put(metricName + workflowId + activityId, metricHelpTypeValue.isPresent() ? metricHelpTypeValue.get() : metricHelpTypeValue.orElseGet(() -> new MetricHelpTypeAndValue(metricName, getHelp(), newValue, tags)));
  }
  
  public void calculateAndAddTotalValue(String metricName, String workflowId, String activityId, Map<String, MetricHelpTypeAndValue> metricMap, Double newValue, Tags tags) {
    Optional<MetricHelpTypeAndValue> metricHelpTypeValue = Optional.ofNullable(metricMap.get(metricName + workflowId + activityId));
    metricHelpTypeValue.ifPresent( oldMetricHelpTypeValue -> {
      oldMetricHelpTypeValue.setValue(newValue + oldMetricHelpTypeValue.getValue());
    });        
    metricMap.put(metricName + workflowId + activityId, metricHelpTypeValue.isPresent() ? metricHelpTypeValue.get() : metricHelpTypeValue.orElseGet(() -> new MetricHelpTypeAndValue(metricName, getHelp(), newValue, tags)));
  }
}
