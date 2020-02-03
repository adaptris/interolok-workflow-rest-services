package com.adaptris.rest;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.awaitility.Durations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.InitialisedState;
import com.adaptris.core.ServiceException;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.StartedState;
import com.adaptris.core.StoppedState;
import com.adaptris.core.runtime.AdapterManager;

public class WorkflowHealthCheckComponentTest {
  
  private static final String ADAPTER_ID = "MyAdapterId";
  
  private static final String CHANNEL_ID = "MyChannelId";
  
  private static final String WORKFLOW_ID1 = "MyWorkflowId1";
  
  private static final String WORKFLOW_ID2 = "MyWorkflowId2";
  
  private static final String UNIQUE_ID = "UniqueId";

  private static final String CHILDREN_ATTRIBUTE = "Children";

  private static final String COMPONENT_STATE = "ComponentState";
  
  private static final String CHANNEL_OBJECT_NAME = "com.adaptris:type=Channel,adapter=" + ADAPTER_ID + ",id=" + CHANNEL_ID;
  
  private static final String WORKFLOW_OBJECT_NAME_1 = "com.adaptris:type=Workflow,adapter=" + ADAPTER_ID + ",channel=" + CHANNEL_ID + ",id=" + WORKFLOW_ID1;
  
  private static final String WORKFLOW_OBJECT_NAME_2 = "com.adaptris:type=Channel,adapter=" + ADAPTER_ID + ",channel=" + CHANNEL_ID + ",id=" + WORKFLOW_ID2;
  
  private static final String PATH_KEY = "jettyURI";
  
  private WorkflowHealthCheckComponent healthCheck;
  
  private Set<ObjectInstance> adapterInstances;
  
  private Set<ObjectName> channelObjectNames;
  
  private ObjectName channelObjectName;
  
  private Set<ObjectName> workflowObjectNames;
  
  private AdaptrisMessage message;
  
  private ObjectName workflowObjectName1, workflowObjectName2;
  
  private TestConsumer testConsumer;
  
  private InitialisedState initState;
  private StartedState startedState;
  private StoppedState stoppedState;
  
  @Mock private MBeanServer mockMBeanServer;
  
  @Mock private WorkflowServicesConsumer mockConsumer;
  
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
    
    
    ObjectInstance adapterInstance = new ObjectInstance("com.adaptris:type=Adapter,id=" + ADAPTER_ID, AdapterManager.class.getName());
    adapterInstances = new HashSet<>();
    adapterInstances.add(adapterInstance);
    
    channelObjectNames = new HashSet<>();
    channelObjectName = new ObjectName(CHANNEL_OBJECT_NAME);
    channelObjectNames.add(channelObjectName);
    
    workflowObjectNames = new HashSet<>();
    workflowObjectName1 = new ObjectName(WORKFLOW_OBJECT_NAME_1);
    workflowObjectName2 = new ObjectName(WORKFLOW_OBJECT_NAME_2);
    workflowObjectNames.add(workflowObjectName1);
    workflowObjectNames.add(workflowObjectName2);
    
    healthCheck = new WorkflowHealthCheckComponent();
    healthCheck.setConsumer(mockConsumer);
    
    when(mockMBeanServer.queryMBeans(any(), any()))
        .thenReturn(adapterInstances);
    when(mockMBeanServer.getAttribute(adapterInstance.getObjectName(), UNIQUE_ID))
        .thenReturn(ADAPTER_ID);
    when(mockMBeanServer.getAttribute(adapterInstance.getObjectName(), COMPONENT_STATE))
        .thenReturn(startedState);
    when(mockMBeanServer.getAttribute(adapterInstance.getObjectName(), CHILDREN_ATTRIBUTE))
        .thenReturn(channelObjectNames);
    
    when(mockMBeanServer.getAttribute(channelObjectName, UNIQUE_ID))
        .thenReturn(CHANNEL_ID);
    when(mockMBeanServer.getAttribute(channelObjectName, COMPONENT_STATE))
        .thenReturn(startedState);
    when(mockMBeanServer.getAttribute(channelObjectName, CHILDREN_ATTRIBUTE))
        .thenReturn(workflowObjectNames);
    
    when(mockMBeanServer.getAttribute(workflowObjectName1, UNIQUE_ID))
        .thenReturn(WORKFLOW_ID1);
    when(mockMBeanServer.getAttribute(workflowObjectName1, COMPONENT_STATE))
        .thenReturn(initState);
    
    when(mockMBeanServer.getAttribute(workflowObjectName2, UNIQUE_ID))
        .thenReturn(WORKFLOW_ID2);
    when(mockMBeanServer.getAttribute(workflowObjectName2, COMPONENT_STATE))
        .thenReturn(stoppedState);
    
    testConsumer = new TestConsumer();
    healthCheck.setConsumer(testConsumer);
    
    healthCheck.init(new Properties());
    healthCheck.start();
  }
  
  @After
  public void tearDown() throws Exception {
    healthCheck.stop();
    healthCheck.destroy();
  }
  
  @Test
  public void testNoMBeansNoError() throws Exception {
    healthCheck.setInterlokMBeanServer(mockMBeanServer);
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atLeast(Durations.ONE_HUNDRED_MILLISECONDS)
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
  }
  
  @Test
  public void testWrongAdapterIdEmptyPayload() throws Exception {
    healthCheck.setInterlokMBeanServer(mockMBeanServer);
    message.addMessageHeader(PATH_KEY, "/workflow-health-check/does_not_exist");
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atLeast(Durations.ONE_HUNDRED_MILLISECONDS)
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
    
    assertEquals("{\"java.util.Collection\":[\"\"]}", testConsumer.payload);
  }
  
  @Test
  public void testEverythingReturned() throws Exception {
    healthCheck.setInterlokMBeanServer(mockMBeanServer);
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atLeast(Durations.ONE_HUNDRED_MILLISECONDS)
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
    
    assertEquals("{\"java.util.Collection\":[\"\"]}", testConsumer.payload);
  }
  
  
  class TestConsumer extends WorkflowServicesConsumer {
    
    String payload;
    boolean isError;

    @Override
    protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath, String acceptedHttpMethods) {
      return null;
    }

    @Override
    protected void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage) throws ServiceException {
      payload = processedMessage.getContent();
    }

    @Override
    public void doErrorResponse(AdaptrisMessage message, Exception e) throws ServiceException {
      isError = true;
    }
    
    public boolean complete() {
      return isError == true || payload != null;
    }
    
  }
}
