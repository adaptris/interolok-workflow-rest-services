package com.adaptris.rest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.monitor.agent.activity.ActivityMap;
import com.adaptris.monitor.agent.activity.AdapterActivity;
import com.adaptris.monitor.agent.activity.ChannelActivity;
import com.adaptris.monitor.agent.activity.ConsumerActivity;
import com.adaptris.monitor.agent.activity.ProducerActivity;
import com.adaptris.monitor.agent.activity.ServiceActivity;
import com.adaptris.monitor.agent.activity.WorkflowActivity;
import com.adaptris.monitor.agent.jmx.ProfilerEventClientMBean;
import com.adaptris.rest.util.JmxMBeanHelper;

public class PrometheusMetricsComponentTest {
  
  private PrometheusMetricsComponent component;
  
  private AdaptrisMessage message;
  
  @Mock private ProfilerEventClientMBean mockMBean;
  
  @Mock private JmxMBeanHelper mockJmxHelper;

  private ActivityMap activityMap;
  
  private MockWorkflowConsumer mockConsumer;
  
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    
    mockConsumer = new MockWorkflowConsumer();
    
    component = new PrometheusMetricsComponent();
    component.setConsumer(mockConsumer);
    component.setProfilerEventClient(mockMBean);
    component.setJmxMBeanHelper(mockJmxHelper);
    
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
    
    activityMap = buildActivityMap();
    
    when(mockMBean.getEventActivityMap())
        .thenReturn(activityMap)
        .thenReturn(null);
  }

  @After
  public void tearDown() throws Exception {
    
  }

  @Test
  public void testNoMetrics() throws Exception {
    when(mockMBean.getEventActivityMap())
        .thenReturn(null);
    
    component.onAdaptrisMessage(message);
    
    assertFalse(mockConsumer.isError);
    assertTrue(mockConsumer.payload.equals(""));
  }
  
  @Test
  public void testNoProfilingMBeanNoMetrics() throws Exception {
    component.setProfilerEventClient(null);
    
    when(mockJmxHelper.proxyMBean(anyString(), any()))
        .thenReturn(null);
    
    component.onAdaptrisMessage(message);
    
    assertFalse(mockConsumer.isError);
    assertTrue(mockConsumer.payload.equals(""));
  }
  
  @Test
  public void testNoProfilingMBeanIsCreated() throws Exception {
    component.setProfilerEventClient(null);
    
    when(mockJmxHelper.proxyMBean(anyString(), any()))
        .thenReturn(mockMBean);
    
    component.onAdaptrisMessage(message);
    
    assertFalse(mockConsumer.isError);
    assertTrue(mockConsumer.payload.contains("workflowMessageCount_workflow"));
    assertTrue(mockConsumer.payload.contains("workflowAvgNanos_workflow"));
    assertTrue(mockConsumer.payload.contains("serviceAvgNanos_workflow_service"));
    assertTrue(mockConsumer.payload.contains("producerAvgNanos_workflow_producer"));
  }
  
  @Test
  public void testErrorResponseOnException() throws Exception {
    activityMap.setAdapters(null);
    
    component.onAdaptrisMessage(message);
    
    assertTrue(mockConsumer.isError);
    assertTrue(mockConsumer.httpStatus == 500);
  }
  
  @Test
  public void testMetricsReturns() throws Exception {
    component.onAdaptrisMessage(message);
    
    assertTrue(mockConsumer.payload.contains("workflowMessageCount_workflow"));
    assertTrue(mockConsumer.payload.contains("workflowAvgNanos_workflow"));
    assertTrue(mockConsumer.payload.contains("serviceAvgNanos_workflow_service"));
    assertTrue(mockConsumer.payload.contains("producerAvgNanos_workflow_producer"));
  }
  
  @Test
  public void testMultipleMetricsReturns() throws Exception {
    when(mockMBean.getEventActivityMap())
        .thenReturn(activityMap)
        .thenReturn(activityMap)
        .thenReturn(activityMap)
        .thenReturn(null);
    
    component.onAdaptrisMessage(message);
    
    assertTrue(mockConsumer.payload.contains("workflowMessageCount_workflow"));
    assertTrue(mockConsumer.payload.contains("workflowAvgNanos_workflow"));
    assertTrue(mockConsumer.payload.contains("serviceAvgNanos_workflow_service"));
    assertTrue(mockConsumer.payload.contains("producerAvgNanos_workflow_producer"));
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