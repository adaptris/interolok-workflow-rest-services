package com.adaptris.rest.metrics.interlok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.monitor.agent.activity.ActivityMap;
import com.adaptris.monitor.agent.activity.AdapterActivity;
import com.adaptris.monitor.agent.activity.ChannelActivity;
import com.adaptris.monitor.agent.activity.ConsumerActivity;
import com.adaptris.monitor.agent.activity.ProducerActivity;
import com.adaptris.monitor.agent.activity.ServiceActivity;
import com.adaptris.monitor.agent.activity.WorkflowActivity;
import com.adaptris.monitor.agent.jmx.ProfilerEventClientMBean;
import com.adaptris.rest.util.JmxMBeanHelper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class InterlokProfilerMetricsGeneratorTest {
  
  private InterlokProfilerMetricsGenerator component;
    
  @Mock private ProfilerEventClientMBean mockMBean;
  
  @Mock private JmxMBeanHelper mockJmxHelper;

  private ActivityMap activityMap;
    
  private SimpleMeterRegistry meterRegistry;
  
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    
    meterRegistry = new SimpleMeterRegistry();
        
    component = new InterlokProfilerMetricsGenerator();
    component.setProfilerEventClient(mockMBean);
    component.setJmxMBeanHelper(mockJmxHelper);
    
    activityMap = buildActivityMap();
    
    when(mockMBean.getEventActivityMap())
        .thenReturn(activityMap)
        .thenReturn(null);
    
    component.init(null);
    component.start();
  }

  @After
  public void tearDown() throws Exception {
    component.stop();
    component.destroy();
  }

  @Test
  public void testNoMetrics() throws Exception {
    when(mockMBean.getEventActivityMap())
        .thenReturn(null);
    
    component.bindTo(meterRegistry);
    assertEquals(0, meterRegistry.getMeters().size());
  }
  
  @Test
  public void testNoProfilingMBeanNoMetrics() throws Exception {
    component.setProfilerEventClient(null);
    
    when(mockJmxHelper.proxyMBean(anyString(), any()))
        .thenReturn(null);
    
    component.bindTo(meterRegistry);
    assertEquals(0, meterRegistry.getMeters().size());
  }
  
  @Test
  public void testNoProfilingMBeanIsCreated() throws Exception {
    component.setProfilerEventClient(null);
    
    when(mockJmxHelper.proxyMBean(anyString(), any()))
        .thenReturn(mockMBean);
    
    component.bindTo(meterRegistry);
    assertTrue(meterRegistry.getMeters().size() > 0);
  }
  
  @Test
  public void testMultipleMetricsReturns() throws Exception {
    when(mockMBean.getEventActivityMap())
        .thenReturn(activityMap)
        .thenReturn(activityMap)
        .thenReturn(activityMap)
        .thenReturn(null);
    
    component.bindTo(meterRegistry);
    assertTrue(meterRegistry.getMeters().size() > 0);
  }
  
  private ActivityMap buildActivityMap() {
    ActivityMap map = new ActivityMap();
    
    AdapterActivity adapter = new AdapterActivity();
    adapter.setUniqueId("adapter");
    ChannelActivity channel = new ChannelActivity();
    channel.setUniqueId("channel");
    WorkflowActivity workflow = new WorkflowActivity();
    workflow.setUniqueId("workflow");
    ProducerActivity producer = new ProducerActivity();
    producer.setUniqueId("producer");
    ConsumerActivity consumer = new ConsumerActivity();
    consumer.setUniqueId("consumer");
    ServiceActivity service = new ServiceActivity();
    service.setUniqueId("service");
    ServiceActivity innerService = new ServiceActivity();
    innerService.setUniqueId("innerService");
    
    service.getServices().put(innerService.getUniqueId(), innerService);
    
    workflow.setConsumerActivity(consumer);
    workflow.setProducerActivity(producer);
    workflow.getServices().put(service.getUniqueId(), service);
    
    channel.getWorkflows().put(workflow.getUniqueId(), workflow);
    adapter.getChannels().put(channel.getUniqueId(), channel);
    
    map.getAdapters().put(adapter.getUniqueId(), adapter);
    
    return map;
  }
}