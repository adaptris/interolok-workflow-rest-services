package com.adaptris.rest;

import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.StandaloneConsumer;

public class WorkflowServicesConsumerTest {
  
  private WorkflowServicesConsumer servicesConsumer;
  
  @Mock private StandaloneConsumer mockStandaloneConsumer;
  
  @Mock private AdaptrisMessageListener mockMessageListener;
  
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    
    servicesConsumer = new HttpRestWorkflowServicesConsumer();
    servicesConsumer.setMessageListener(mockMessageListener);
  }
  
  @Test
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
