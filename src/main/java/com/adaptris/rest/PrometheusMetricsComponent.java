package com.adaptris.rest;

import static com.adaptris.rest.WorkflowServicesConsumer.ERROR_DEFAULT;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.management.ObjectName;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.monitor.agent.activity.ActivityMap;
import com.adaptris.monitor.agent.activity.AdapterActivity;
import com.adaptris.monitor.agent.activity.BaseActivity;
import com.adaptris.monitor.agent.activity.ChannelActivity;
import com.adaptris.monitor.agent.activity.ProducerActivity;
import com.adaptris.monitor.agent.activity.ServiceActivity;
import com.adaptris.monitor.agent.activity.WorkflowActivity;
import com.adaptris.monitor.agent.jmx.ProfilerEventClientMBean;
import com.adaptris.rest.util.JmxMBeanHelper;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class PrometheusMetricsComponent extends AbstractRestfulEndpoint {
  
  private static final String ACCEPTED_FILTER = "GET";

  private static final String DEFAULT_PATH = "/prometheus/metrics/*";

  private static final String PROFILER_OBJECT_NAME = "com.adaptris:type=Profiler";
    
  private enum Metrics {
    WORKFLOW_MESSAGE_COUNT_METRIC ("workflowMessageCount") {
      @Override
      public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
        String metricName = this.createMetricName(workflowId, activityId);
        this.calculateAndAddTotalValue(metricName, metricMap, value);
      }

      @Override
      public String getHelp() { return "The number of messages processed by this workflow since the last time we checked."; }
    },
    
    WORKFLOW_MESSAGE_FAIL_COUNT_METRIC ("workflowMessageFailCount") {
      @Override
      public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
        String metricName = this.createMetricName(workflowId, activityId);        
        this.calculateAndAddTotalValue(metricName, metricMap, value);
      }

      @Override
      public String getHelp() { return "The number of messages processed by this workflow that have failed since the last time we checked."; }
    },
    
    WORKFLOW_AVG_TIME_NANOS_METRIC ("workflowAvgNanos") {
      @Override
      public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
        String metricName = this.createMetricName(workflowId, activityId);
        this.calculateAndAddAvgValue(metricName, metricMap, value);
      }

      @Override
      public String getHelp() { return "The average amount of time in nanoseconds this workflow takes to process a single message."; }
    },
    
    WORKFLOW_AVG_TIME_MILLIS_METRIC ("workflowAvgMillis") {
      @Override
      public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
        String metricName = this.createMetricName(workflowId, activityId);
        this.calculateAndAddAvgValue(metricName, metricMap, value);
      }

      @Override
      public String getHelp() { return "The average amount of time in millisecods this workflow takes to process a single message."; }
    },
    
    SERVICE_AVG_TIME_NANOS_METRIC ("serviceAvgNanos") {
      @Override
      public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
        String metricName = this.createMetricName(workflowId, activityId);
        this.calculateAndAddAvgValue(metricName, metricMap, value);
      }

      @Override
      public String getHelp() { return "The average amount of time in nanoseconds this service takes to process a single message."; }
    },
    
    SERVICE_AVG_TIME_MILLIS_METRIC ("serviceAvgMillis") {
      @Override
      public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
        String metricName = this.createMetricName(workflowId, activityId);
        this.calculateAndAddAvgValue(metricName, metricMap, value);
      }

      @Override
      public String getHelp() { return "The average amount of time in millisecods this service takes to process a single message."; }
    },
    
    PRODUCER_AVG_TIME_NANOS_METRIC ("producerAvgNanos") {
      @Override
      public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
        String metricName = this.createMetricName(workflowId, activityId);
        this.calculateAndAddAvgValue(metricName, metricMap, value);
      }

      @Override
      public String getHelp() { return "The average amount of time in nanoseconds this producer takes to process a single message."; }
    },
  
    PRODUCER_AVG_TIME_MILLIS_METRIC ("producerAvgMillis") {
      @Override
      public void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap) {
        String metricName = this.createMetricName(workflowId, activityId);
        this.calculateAndAddAvgValue(metricName, metricMap, value);
      }

      @Override
      public String getHelp() { return "The average amount of time in milliseconds this producer takes to process a single message."; }
    };
    
    private String metricName;
    
    private Metrics(String name) {
      setMetricName(name);
    }

    public String getMetricName() {
      return metricName;
    }

    public void setMetricName(String metricName) {
      this.metricName = metricName;
    }
    
    public abstract void addMetric(String workflowId, String activityId, Double value, Map<String, MetricHelpTypeAndValue> metricMap);
    
    public abstract String getHelp();
    
    public String createMetricName(String workflowId, String activityId) {
      StringBuffer returnValue = new StringBuffer();
      returnValue.append(getMetricName());
      returnValue.append("_");
      returnValue.append(workflowId.replace(".", "").replace("-", "").replace("_", ""));
      if(activityId != null) {
        returnValue.append("_");
        returnValue.append(activityId.replace(".", "").replace("-", "").replace("_", ""));  
      }
      
      return returnValue.toString();
    }
    
    public void calculateAndAddAvgValue(String metricName, Map<String, MetricHelpTypeAndValue> metricMap, Double newValue) {
      Optional<MetricHelpTypeAndValue> metricHelpTypeValue = Optional.ofNullable(metricMap.get(metricName));
      metricHelpTypeValue.ifPresent( oldMetricHelpTypeValue -> {
        oldMetricHelpTypeValue.setValue((newValue + oldMetricHelpTypeValue.getValue()) / 2);
      });        
      metricMap.put(metricName, metricHelpTypeValue.isPresent() ? metricHelpTypeValue.get() : metricHelpTypeValue.orElseGet(() -> new MetricHelpTypeAndValue(getHelp(), newValue)));
    }
    
    public void calculateAndAddTotalValue(String metricName, Map<String, MetricHelpTypeAndValue> metricMap, Double newValue) {
      Optional<MetricHelpTypeAndValue> metricHelpTypeValue = Optional.ofNullable(metricMap.get(metricName));
      metricHelpTypeValue.ifPresent( oldMetricHelpTypeValue -> {
        oldMetricHelpTypeValue.setValue(newValue + oldMetricHelpTypeValue.getValue());
      });        
      metricMap.put(metricName, metricHelpTypeValue.isPresent() ? metricHelpTypeValue.get() : metricHelpTypeValue.orElseGet(() -> new MetricHelpTypeAndValue(getHelp(), newValue)));
    }
  }
  
  @Getter(AccessLevel.PROTECTED)
  private transient final String defaultUrlPath = DEFAULT_PATH;

  @Getter(AccessLevel.PROTECTED)
  private transient final String acceptedFilter = ACCEPTED_FILTER;
  
  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  private transient JmxMBeanHelper jmxMBeanHelper;
  
  @Getter
  @Setter
  private ProfilerEventClientMBean profilerEventClient;

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message, Consumer<AdaptrisMessage> success, Consumer<AdaptrisMessage> failure) {
    try {
      this.loadProfilerEventClient();
      
      if(this.getProfilerEventClient() == null) {
        log.warn("No profiling events found.  Continuing...");
        message.setContent("", message.getContentEncoding());
        getConsumer().doResponse(message, message, HttpRestWorkflowServicesConsumer.CONTENT_TYPE_DEFAULT);
        success.accept(message);
        return;
      }
        
      Map<String, MetricHelpTypeAndValue> profilingEvents = new HashMap<>();
      ActivityMap eventActivityMap = this.getProfilerEventClient().getEventActivityMap();
      while(eventActivityMap != null) {
        eventActivityMap.getAdapters().forEach( (adapterId, adapterActivity) -> {
          ((AdapterActivity) adapterActivity).getChannels().forEach( (channelId, channelActivity) -> {
            ((ChannelActivity) channelActivity).getWorkflows().forEach( ( workflowId, workflowActivity) -> {
              addMetrics(null, workflowActivity, profilingEvents);
            });
          });
        });
        
        eventActivityMap = this.getProfilerEventClient().getEventActivityMap();
      }
      
      message.setContent(this.stringifyMap(profilingEvents), message.getContentEncoding());
      
      getConsumer().doResponse(message, message);
      success.accept(message);
      
    } catch (Exception ex) {
      getConsumer().doErrorResponse(message, ex, ERROR_DEFAULT);
      failure.accept(message);
    }
  }
  
  private String stringifyMap(Map<String, MetricHelpTypeAndValue> profilingEvents) {
    StringBuffer buffer = new StringBuffer();
    
    profilingEvents.forEach( (metricName, metric) -> {
      buffer.append("# HELP ");
      buffer.append(metric.getHelp());
      buffer.append("\n");
      buffer.append(metricName);
      buffer.append(" ");
      buffer.append(metric.getValue());
      buffer.append("\n");
    });
    
    return buffer.toString();
  }

  private void addMetrics(WorkflowActivity workflowActivity, BaseActivity activity, Map<String, MetricHelpTypeAndValue> profilingEvents) {
    if(activity instanceof WorkflowActivity) {
      WorkflowActivity wActivity = (WorkflowActivity) activity;
      this.addMetrics(wActivity, wActivity.getProducerActivity(), profilingEvents);
      wActivity.getServices().forEach( ( serviceId, serviceActivity) -> {
        this.addMetrics(wActivity, serviceActivity, profilingEvents);
      });
      
      Metrics.WORKFLOW_MESSAGE_COUNT_METRIC.addMetric(wActivity.getUniqueId(), null, (double) wActivity.getMessageCount(), profilingEvents);
      Metrics.WORKFLOW_MESSAGE_FAIL_COUNT_METRIC.addMetric(wActivity.getUniqueId(), null, (double) wActivity.getFailedCount(), profilingEvents);
      Metrics.WORKFLOW_AVG_TIME_NANOS_METRIC.addMetric(wActivity.getUniqueId(), null, (double) wActivity.getAvgNsTaken(), profilingEvents);
      Metrics.WORKFLOW_AVG_TIME_MILLIS_METRIC.addMetric(wActivity.getUniqueId(), null, (double) wActivity.getAvgMsTaken(), profilingEvents);
      
    } else if (activity instanceof ProducerActivity) {
      ProducerActivity pActivity = (ProducerActivity) activity;
      
      Metrics.PRODUCER_AVG_TIME_NANOS_METRIC.addMetric(workflowActivity.getUniqueId(), pActivity.getUniqueId(), (double) pActivity.getAvgNsTaken(), profilingEvents);
      Metrics.PRODUCER_AVG_TIME_MILLIS_METRIC.addMetric(workflowActivity.getUniqueId(), pActivity.getUniqueId(), (double) pActivity.getAvgMsTaken(), profilingEvents);
      
    } else if (activity instanceof ServiceActivity) {
      ServiceActivity sActivity = (ServiceActivity) activity;
      
      Metrics.SERVICE_AVG_TIME_NANOS_METRIC.addMetric(workflowActivity.getUniqueId(), sActivity.getUniqueId(), (double) sActivity.getAvgNsTaken(), profilingEvents);
      Metrics.SERVICE_AVG_TIME_MILLIS_METRIC.addMetric(workflowActivity.getUniqueId(), sActivity.getUniqueId(), (double) sActivity.getAvgMsTaken(), profilingEvents);
      
      sActivity.getServices().forEach( (serviceId, serviceActivity) -> {
        this.addMetrics(workflowActivity, serviceActivity, profilingEvents);
      });
    }
    
  }

  private void loadProfilerEventClient() throws Exception {    
    if(this.getProfilerEventClient() == null) {
      ProfilerEventClientMBean profilerEventClientMBean = getJmxMBeanHelper().proxyMBean(new ObjectName(PROFILER_OBJECT_NAME).toString(), ProfilerEventClientMBean.class);
      if(profilerEventClientMBean != null)
        this.setProfilerEventClient(profilerEventClientMBean);
    }
  }
  
  static class MetricHelpTypeAndValue {
    @Getter
    @Setter
    private String help;
    
    @Getter
    @Setter
    private Double value;
    
    public MetricHelpTypeAndValue(String help, Double value) {
      this.setHelp(help);
      this.setValue(value);
    }
  }

}
