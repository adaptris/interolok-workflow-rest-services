package com.adaptris.rest;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
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
import com.adaptris.rest.util.JmxMBeanHelper;

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
  
  private ObjectName adapterObjectName;
  
  @Mock private JmxMBeanHelper mockJmxHelper;
  
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
    
    adapterObjectName = new ObjectName("com.adaptris:type=Adapter,id=" + ADAPTER_ID);
    ObjectInstance adapterInstance = new ObjectInstance(adapterObjectName, AdapterManager.class.getName());
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
    
    when(mockJmxHelper.getMBeans(any()))
        .thenReturn(adapterInstances);
    when(mockJmxHelper.getStringAttribute(adapterObjectName.toString(), UNIQUE_ID))
        .thenReturn(ADAPTER_ID);
    when(mockJmxHelper.getStringAttributeClassName(adapterObjectName.toString(), COMPONENT_STATE))
        .thenReturn(StartedState.class.getSimpleName());
    when(mockJmxHelper.getObjectSetAttribute(adapterObjectName.toString(), CHILDREN_ATTRIBUTE))
        .thenReturn(channelObjectNames);
    
    when(mockJmxHelper.getStringAttribute(channelObjectName.toString(), UNIQUE_ID))
        .thenReturn(CHANNEL_ID);
    when(mockJmxHelper.getStringAttributeClassName(channelObjectName.toString(), COMPONENT_STATE))
        .thenReturn(StartedState.class.getSimpleName());
    when(mockJmxHelper.getObjectSetAttribute(channelObjectName.toString(), CHILDREN_ATTRIBUTE))
        .thenReturn(workflowObjectNames);
    
    when(mockJmxHelper.getStringAttribute(workflowObjectName1.toString(), UNIQUE_ID))
        .thenReturn(WORKFLOW_ID1);
    when(mockJmxHelper.getStringAttributeClassName(workflowObjectName1.toString(), COMPONENT_STATE))
        .thenReturn(InitialisedState.class.getSimpleName());
    
    when(mockJmxHelper.getStringAttribute(workflowObjectName2.toString(), UNIQUE_ID))
        .thenReturn(WORKFLOW_ID2);
    when(mockJmxHelper.getStringAttributeClassName(workflowObjectName2.toString(), COMPONENT_STATE))
        .thenReturn(StoppedState.class.getSimpleName());
    
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
  public void testNoMBeans() throws Exception {    
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    
    doThrow(new Exception("Expected"))
        .when(mockJmxHelper).getMBeans(any());
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
    
    assertFalse(testConsumer.isError);
  }
  
  @Test
  public void testErrorFromMBean() throws Exception {
    healthCheck.setJmxMBeanHelper(mockJmxHelper);
    
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    
    doThrow(new Exception("Expected"))
        .when(mockJmxHelper).getMBeans(any());
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
    
    assertTrue(testConsumer.isError);
  }
  
  @Test
  public void testErrorFromMBeanAttribute() throws Exception {
    healthCheck.setJmxMBeanHelper(mockJmxHelper);
    
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    
    doThrow(new Exception("Expected"))
        .when(mockJmxHelper).getStringAttribute(any(), any());
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
    
    assertTrue(testConsumer.isError);
  }
  
  @Test
  public void testWrongAdapterIdEmptyPayload() throws Exception {
    healthCheck.setJmxMBeanHelper(mockJmxHelper);
    message.addMessageHeader(PATH_KEY, "/workflow-health-check/does_not_exist");
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
    assertEquals("{\"adapters\":[\"\"]}", testConsumer.payload);
  }
  
  @Test
  public void testEverythingReturned() throws Exception {
    healthCheck.setJmxMBeanHelper(mockJmxHelper);
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
    assertTrue(testConsumer.payload.contains(ADAPTER_ID));
    assertTrue(testConsumer.payload.contains(CHANNEL_ID));
    assertTrue(testConsumer.payload.contains(WORKFLOW_ID1));
    assertTrue(testConsumer.payload.contains(WORKFLOW_ID2));
  }
  
  @Test
  public void testSpecificWorkflowReturned() throws Exception {
    healthCheck.setJmxMBeanHelper(mockJmxHelper);
    message.addMessageHeader(PATH_KEY, "/workflow-health-check/" + ADAPTER_ID + "/" + CHANNEL_ID + "/" + WORKFLOW_ID1);
    
    healthCheck.onAdaptrisMessage(message);
    
 // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);
    
    assertTrue(testConsumer.payload.contains(ADAPTER_ID));
    assertTrue(testConsumer.payload.contains(CHANNEL_ID));
    assertTrue(testConsumer.payload.contains(WORKFLOW_ID1));
    
    assertFalse(testConsumer.payload.contains(WORKFLOW_ID2));
  }
  
  
  class TestConsumer extends WorkflowServicesConsumer {
    
    String payload;
    boolean isError;

    @Override
    protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath, String acceptedHttpMethods) {
      return null;
    }

    @Override
    protected void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage, String contentType)
        throws ServiceException {
      payload = processedMessage.getContent();
    }


    @Override
    public void doErrorResponse(AdaptrisMessage message, Exception e, String contentType) throws ServiceException {
      isError = true;
    }
    
    public boolean complete() {
      return isError == true || payload != null;
    }
    
    @Override
    public void prepare() {
      
    }
  }
}
