package com.adaptris.rest.metrics.interlok;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.monitor.agent.activity.ActivityMap;
import com.adaptris.monitor.agent.activity.AdapterActivity;
import com.adaptris.monitor.agent.activity.BaseActivity;
import com.adaptris.monitor.agent.activity.ChannelActivity;
import com.adaptris.monitor.agent.activity.ProducerActivity;
import com.adaptris.monitor.agent.activity.ServiceActivity;
import com.adaptris.monitor.agent.activity.WorkflowActivity;
import com.adaptris.monitor.agent.jmx.ProfilerEventClientMBean;
import com.adaptris.rest.MetricBinder;
import com.adaptris.rest.MetricProviders;
import com.adaptris.rest.util.JmxMBeanHelper;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class InterlokProfilerMetricsGenerator extends MgmtComponentImpl implements MetricBinder {
  
  protected transient Logger log = LoggerFactory.getLogger(this.getClass().getName());

  private static final String PROFILER_OBJECT_NAME = "com.adaptris:type=Profiler";
  
  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  private transient JmxMBeanHelper jmxMBeanHelper;
  
  @Getter
  @Setter
  private ProfilerEventClientMBean profilerEventClient;
  
  public InterlokProfilerMetricsGenerator() {
    this.setJmxMBeanHelper(new JmxMBeanHelper());
  }
  
  private void addMetrics(WorkflowActivity workflowActivity, BaseActivity activity, Map<String, MetricHelpTypeAndValue> profilingEvents) {
    if(activity instanceof WorkflowActivity) {
      WorkflowActivity wActivity = (WorkflowActivity) activity;
      this.addMetrics(wActivity, wActivity.getProducerActivity(), profilingEvents);
      wActivity.getServices().forEach( ( serviceId, serviceActivity) -> {
        this.addMetrics(wActivity, serviceActivity, profilingEvents);
      });
      
      InterlokMetrics.WORKFLOW_MESSAGE_COUNT_METRIC.addMetric(wActivity.getUniqueId(), null, (double) wActivity.getMessageCount(), profilingEvents);
      InterlokMetrics.WORKFLOW_MESSAGE_FAIL_COUNT_METRIC.addMetric(wActivity.getUniqueId(), null, (double) wActivity.getFailedCount(), profilingEvents);
      InterlokMetrics.WORKFLOW_AVG_TIME_NANOS_METRIC.addMetric(wActivity.getUniqueId(), null, (double) wActivity.getAvgNsTaken(), profilingEvents);
      InterlokMetrics.WORKFLOW_AVG_TIME_MILLIS_METRIC.addMetric(wActivity.getUniqueId(), null, (double) wActivity.getAvgMsTaken(), profilingEvents);
      
    } else if (activity instanceof ProducerActivity) {
      ProducerActivity pActivity = (ProducerActivity) activity;
      
      InterlokMetrics.PRODUCER_AVG_TIME_NANOS_METRIC.addMetric(workflowActivity.getUniqueId(), pActivity.getUniqueId(), (double) pActivity.getAvgNsTaken(), profilingEvents);
      InterlokMetrics.PRODUCER_AVG_TIME_MILLIS_METRIC.addMetric(workflowActivity.getUniqueId(), pActivity.getUniqueId(), (double) pActivity.getAvgMsTaken(), profilingEvents);
      
    } else if (activity instanceof ServiceActivity) {
      ServiceActivity sActivity = (ServiceActivity) activity;
      
      InterlokMetrics.SERVICE_AVG_TIME_NANOS_METRIC.addMetric(workflowActivity.getUniqueId(), sActivity.getUniqueId(), (double) sActivity.getAvgNsTaken(), profilingEvents);
      InterlokMetrics.SERVICE_AVG_TIME_MILLIS_METRIC.addMetric(workflowActivity.getUniqueId(), sActivity.getUniqueId(), (double) sActivity.getAvgMsTaken(), profilingEvents);
      
      sActivity.getServices().forEach( (serviceId, serviceActivity) -> {
        this.addMetrics(workflowActivity, serviceActivity, profilingEvents);
      });
    }
  }
  @Override
  public void bindTo(MeterRegistry registry) throws Exception {
    this.loadProfilerEventClient();
    
    if(this.getProfilerEventClient() == null) {
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
    
    profilingEvents.forEach( (metricName, helpValueTags) -> {
      Gauge.builder(metricName, helpValueTags, MetricHelpTypeAndValue::getValue)
          .tags(helpValueTags.getTags())
          .description(helpValueTags.getHelp())
          .baseUnit(BaseUnits.BUFFERS)
          .register(registry);
    });
    
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
    
    @Getter
    @Setter
    private Tags tags;
    
    public MetricHelpTypeAndValue(String help, Double value, Tags tags) {
      this.setHelp(help);
      this.setValue(value);
      this.setTags(tags);
    }
  }

  @Override
  public void init(Properties config) throws Exception {
    MetricProviders.PROVIDERS.add(this);
  }

  @Override
  public void start() throws Exception {
  }

  @Override
  public void stop() throws Exception {
  }

  @Override
  public void destroy() throws Exception {
  }
}
