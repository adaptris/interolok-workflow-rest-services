package com.adaptris.rest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.JettyResponseService;

public class HttpRestWorkflowServicesConsumerTest {
  
  private static final String PATH = "/workflow-services/*";
  
  private static final String ACCEPTED_FILTER = "POST,GET";
  
  private HttpRestWorkflowServicesConsumer servicesConsumer;
  
  private AdaptrisMessage originalMessage;
  
  private AdaptrisMessage processedMessage;
  
  @Mock private JettyResponseService mockResponseService;
  
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    
    servicesConsumer = new HttpRestWorkflowServicesConsumer();
    originalMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    processedMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
  }
  
  @After
  public void tearDown() throws Exception {
    
  }
  
  @Test
  public void testCreateStandardConsumer() throws Exception {
    StandaloneConsumer standaloneConsumer = servicesConsumer.configureConsumer(new AdaptrisMessageListener() {
      @Override
      public void onAdaptrisMessage(AdaptrisMessage msg) {}
      @Override
      public String friendlyName() {
        return null;
      }
    }, PATH, ACCEPTED_FILTER);
    
    assertEquals(PATH, standaloneConsumer.getConsumer().getDestination().getDestination());
    assertEquals(ACCEPTED_FILTER, standaloneConsumer.getConsumer().getDestination().getFilterExpression());
  }
  
  @Test
  public void testOkResponse() throws Exception {
    servicesConsumer.setResponseService(mockResponseService);
    servicesConsumer.doResponse(originalMessage, processedMessage);
    
    verify(mockResponseService).setHttpStatus("200");
    verify(mockResponseService).doService(processedMessage);
  }

  @Test
  public void testErrorResponse() throws Exception {
    servicesConsumer.setResponseService(mockResponseService);
    servicesConsumer.doErrorResponse(originalMessage, new Exception());
    
    verify(mockResponseService).setHttpStatus("400");
    verify(mockResponseService).doService(originalMessage);
  }
}
