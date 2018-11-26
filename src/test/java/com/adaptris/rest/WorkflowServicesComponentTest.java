package com.adaptris.rest;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.SerializableAdaptrisMessage;
import com.adaptris.interlok.client.jmx.InterlokJmxClient;
import com.adaptris.interlok.types.SerializableMessage;

import junit.framework.TestCase;

public class WorkflowServicesComponentTest extends TestCase {
  
  private static final String PATH_KEY = "jettyURI";
  
  private AdaptrisMessage message;
  
  private WorkflowServicesComponent workflowServicesComponent;
  
  @Mock WorkflowServicesConsumer mockConsumer;
  
  @Mock InterlokJmxClient mockJmxClient;

  private SerializableMessage mockSerMessage;
  
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
    mockSerMessage = new SerializableAdaptrisMessage();
    
    workflowServicesComponent = new WorkflowServicesComponent();
    workflowServicesComponent.setInitialJettyContextWaitMs(0l);
    workflowServicesComponent.setConsumer(mockConsumer);
    workflowServicesComponent.setJmxClient(mockJmxClient);
    
    startComponent();
  }
  
  public void tearDown() throws Exception {
    stopComponent();
  }
  
  public void testHappyPathMessageProcessed() throws Exception {
    message.addMessageHeader(PATH_KEY, "/workflow-services/myAdapter/myChannel/myWorkflow");
    
    when(mockJmxClient.process(any(), any())).thenReturn(mockSerMessage);
    
    workflowServicesComponent.onAdaptrisMessage(message);
    
    verify(mockJmxClient).process(any(), any());
    verify(mockConsumer).doResponse(any(), any());
  }
  
  public void testYamlDefRequest() throws Exception {
    message.addMessageHeader(PATH_KEY, "/workflow-services/");
    
    when(mockJmxClient.process(any(), any())).thenReturn(mockSerMessage);
    
    workflowServicesComponent.onAdaptrisMessage(message);
    
    verify(mockJmxClient, times(0)).process(any(), any());
  }
  
  public void testErrorResponse() throws Exception {
    message.addMessageHeader(PATH_KEY, "/workflow-services/1/2/3/4/5/6/7/8/9");
    workflowServicesComponent.onAdaptrisMessage(message);
    
    verify(mockJmxClient, times(0)).process(any(), any());
    verify(mockConsumer).doErrorResponse(any(), any());
  }
  
  private void startComponent() throws Exception {
    workflowServicesComponent.init(null);
    workflowServicesComponent.start();
  }
  
  private void stopComponent() throws Exception {
    workflowServicesComponent.stop();
    workflowServicesComponent.destroy();
  }

}
