package com.adaptris.rest;

import org.apache.commons.lang3.exception.ExceptionUtils;

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
import com.adaptris.core.http.jetty.MetadataResponseHeaderProvider;
import com.adaptris.core.metadata.NoOpMetadataFilter;

public class HttpRestWorkflowServicesConsumer extends WorkflowServicesConsumer {
  
  private static final String PATH = "/workflow-services/*";
  
  private static final String ACCEPTED_FILTER = "POST,GET";
  
  private static final String OK_200 = "200";
  
  private static final String ERROR_400 = "400";
  
  private static final String HEADER_PREFIX = "http.header.";
  
  private static final String PARAMETER_PREFIX = "http.param.";
  
  private transient JettyResponseService responseService;
  
  public HttpRestWorkflowServicesConsumer() {
  }
  
  @Override
  protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener) {
    MetadataResponseHeaderProvider metadataResponseHeaderProvider = new MetadataResponseHeaderProvider();
    metadataResponseHeaderProvider.setFilter(new NoOpMetadataFilter());
    
    this.setResponseService(new JettyResponseService());
    this.getResponseService().setResponseHeaderProvider(metadataResponseHeaderProvider);
    
    EmbeddedConnection jettyConnection = new EmbeddedConnection();
    JettyMessageConsumer messageConsumer = new JettyMessageConsumer();
    
    ConfiguredConsumeDestination configuredConsumeDestination = new ConfiguredConsumeDestination(PATH);
    configuredConsumeDestination.setFilterExpression(ACCEPTED_FILTER);
    
    messageConsumer.setDestination(configuredConsumeDestination);
    messageConsumer.setHeaderHandler(new MetadataHeaderHandler(HEADER_PREFIX));
    messageConsumer.setParameterHandler(new MetadataParameterHandler(PARAMETER_PREFIX));
    messageConsumer.registerAdaptrisMessageListener(messageListener);
    
    return new StandaloneConsumer(jettyConnection, messageConsumer);
  }

  @Override
  protected void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage) throws ServiceException {
    processedMessage.addObjectHeader(JettyConstants.JETTY_WRAPPER, originalMessage.getObjectHeaders().get(JettyConstants.JETTY_WRAPPER));
    this.getResponseService().setHttpStatus(OK_200);
    this.getResponseService().doService(processedMessage);
  }

  @Override
  public void doErrorResponse(AdaptrisMessage message, Exception e) throws ServiceException {
    message.setContent(ExceptionUtils.getStackTrace(e), message.getContentEncoding());
    this.getResponseService().setHttpStatus(ERROR_400); // bad request.
    this.getResponseService().doService(message);
  }

  public JettyResponseService getResponseService() {
    return responseService;
  }

  public void setResponseService(JettyResponseService responseService) {
    this.responseService = responseService;
  }

}
