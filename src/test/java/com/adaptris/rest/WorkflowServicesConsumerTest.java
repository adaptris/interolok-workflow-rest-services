package com.adaptris.rest;

import static org.mockito.Mockito.verify;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.StandaloneConsumer;

import junit.framework.TestCase;

public class WorkflowServicesConsumerTest extends TestCase {
  
  private WorkflowServicesConsumer servicesConsumer;
  
  @Mock private StandaloneConsumer mockStandaloneConsumer;
  
  @Mock private AdaptrisMessageListener mockMessageListener;
  
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    
    servicesConsumer = new HttpRestWorkflowServicesConsumer();
    servicesConsumer.setMessageListener(mockMessageListener);
  }
  
  public void testLifecycle() throws Exception {
    servicesConsumer.prepare();
    
    servicesConsumer.setStandaloneConsumer(mockStandaloneConsumer);
    servicesConsumer.init();
    servicesConsumer.start();
    servicesConsumer.stop();
    servicesConsumer.close();
    
    verify(mockStandaloneConsumer).requestInit();
    verify(mockStandaloneConsumer).requestStart();
    verify(mockStandaloneConsumer).requestStop();
    verify(mockStandaloneConsumer).requestClose();
  }

}
