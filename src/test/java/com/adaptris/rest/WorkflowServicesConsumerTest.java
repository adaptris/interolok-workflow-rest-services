package com.adaptris.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import java.util.EnumSet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.JettyMessageConsumer;
import com.adaptris.core.http.jetty.JettyResponseService;
import com.adaptris.core.http.jetty.NoOpResponseHeaderProvider;
import com.adaptris.core.stubs.DefectiveMessageFactory;
import com.adaptris.core.stubs.DefectiveMessageFactory.WhenToBreak;
import com.adaptris.core.stubs.MockMessageListener;

public class WorkflowServicesConsumerTest {
  
  private WorkflowServicesConsumer servicesConsumer;
  
  @Mock private StandaloneConsumer mockStandaloneConsumer;
  
  @Mock private AdaptrisMessageListener mockMessageListener;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    
    servicesConsumer = new HttpRestWorkflowServicesConsumer("WorkflowServicesConsumerTest");
    servicesConsumer.setMessageListener(mockMessageListener);
    servicesConsumer.setAcceptedHttpMethods("POST,GET");
    servicesConsumer.setConsumedUrlPath("/myPath/");
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

  @Test
  public void testJettyConsumer() throws Exception {
    WorkflowServicesConsumer consumer = new HttpRestWorkflowServicesConsumer("WorkflowServicesConsumerTest");
    StandaloneConsumer sc = consumer.configureConsumer(new MockMessageListener(), "/path/to/url", "GET");
    assertTrue(sc.getConsumer() instanceof JettyMessageConsumer);
    // Bit of a dodgy test.
    try {
      ((JettyMessageConsumer) sc.getConsumer()).createMessage(Mockito.mock(HttpServletRequest.class),
          Mockito.mock(HttpServletResponse.class));
      fail();
    } catch (Exception expected) {
      
    }
    assertEquals("WorkflowServicesConsumerTest", MDC.get(AbstractRestfulEndpoint.MDC_KEY));
  }

  @Test
  public void testSendErrorQuietly() throws Exception {
    HttpRestWorkflowServicesConsumer consumer = new HttpRestWorkflowServicesConsumer("WorkflowServicesConsumerTest");
    consumer.setResponseService(new JettyResponseService().withResponseHeaderProvider(new NoOpResponseHeaderProvider()));
    AdaptrisMessage m1 = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    WorkflowServicesConsumer.sendErrorResponseQuietly(consumer, m1, new Exception());
    AdaptrisMessage m2 = new DefectiveMessageFactory(EnumSet.allOf(WhenToBreak.class)).newMessage();
    WorkflowServicesConsumer.sendErrorResponseQuietly(consumer, m2, new Exception());
  }
}
