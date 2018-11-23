package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.ConfiguredConsumeDestination;
import com.adaptris.core.ServiceException;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.EmbeddedConnection;
import com.adaptris.core.http.jetty.JettyConstants;
import com.adaptris.core.http.jetty.JettyMessageConsumer;
import com.adaptris.core.http.jetty.JettyResponseService;
import com.adaptris.core.http.jetty.MetadataHeaderHandler;
import com.adaptris.core.http.jetty.MetadataParameterHandler;

public class HttpRestWorkflowServicesConsumer extends WorkflowServicesConsumer {
  
  private static final String PATH = "/workflow-services/*";
  
  private static final String HEADER_PREFIX = "http.header.";
  
  private static final String PARAMETER_PREFIX = "http.param.";
  
  private transient JettyResponseService responseService;
  
  public HttpRestWorkflowServicesConsumer() {
    responseService = new JettyResponseService();
    responseService.setHttpStatus("200");
  }
  
  @Override
  protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener) {
    EmbeddedConnection jettyConnection = new EmbeddedConnection();
    JettyMessageConsumer messageConsumer = new JettyMessageConsumer();
    
    ConfiguredConsumeDestination configuredConsumeDestination = new ConfiguredConsumeDestination(PATH);
    configuredConsumeDestination.setFilterExpression("POST");
    
    messageConsumer.setDestination(configuredConsumeDestination);
    messageConsumer.setHeaderHandler(new MetadataHeaderHandler(HEADER_PREFIX));
    messageConsumer.setParameterHandler(new MetadataParameterHandler(PARAMETER_PREFIX));
    messageConsumer.registerAdaptrisMessageListener(messageListener);
    
    return new StandaloneConsumer(jettyConnection, messageConsumer);
  }

  @Override
  protected void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage) throws ServiceException {
    processedMessage.addObjectHeader(JettyConstants.JETTY_WRAPPER, originalMessage.getObjectHeaders().get(JettyConstants.JETTY_WRAPPER));
    responseService.doService(processedMessage);
  }

}
