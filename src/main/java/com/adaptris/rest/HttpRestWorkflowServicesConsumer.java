package com.adaptris.rest;

import static com.adaptris.rest.AbstractRestfulEndpoint.MDC_KEY;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;
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
import com.adaptris.core.http.jetty.NoOpResponseHeaderProvider;
import lombok.Getter;
import lombok.Setter;

public class HttpRestWorkflowServicesConsumer extends WorkflowServicesConsumer {


  private static final String OK_200 = "200";
  
  private static final String ERROR_400 = "400";
  
  private static final String METADATA_STATUS = "httpReplyStatus";
  private static final String METADATA_CONTENT_TYPE = "httpReplyContentType";

  private static final String HEADER_PREFIX = "http.header.";
  
  private static final String PARAMETER_PREFIX = "http.param.";
  
  @Getter
  @Setter
  private transient JettyResponseService responseService;

  @Getter
  @Setter
  private transient String owner;

  public HttpRestWorkflowServicesConsumer(String ownerRef) {
    setOwner(ownerRef);
  }

  @Override
  protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath, String acceptedHttpMethods) {
    this.setResponseService(new JettyResponseService().withResponseHeaderProvider(new NoOpResponseHeaderProvider())
        .withHttpStatus("%message{httpReplyStatus}").withContentType("%message{httpReplyContentType}"));
    
    EmbeddedConnection jettyConnection = new EmbeddedConnection();
    JettyMessageConsumer messageConsumer = new JettyMessageConsumer() {
      @Override
      public AdaptrisMessage createMessage(HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
        MDC.put(MDC_KEY, owner);
        return super.createMessage(request, response);
      }
    };
    
    ConfiguredConsumeDestination configuredConsumeDestination = new ConfiguredConsumeDestination(consumedUrlPath);
    configuredConsumeDestination.setFilterExpression(acceptedHttpMethods);
    
    messageConsumer.setDestination(configuredConsumeDestination);
    messageConsumer.setHeaderHandler(new MetadataHeaderHandler(HEADER_PREFIX));
    messageConsumer.setParameterHandler(new MetadataParameterHandler(PARAMETER_PREFIX));
    messageConsumer.registerAdaptrisMessageListener(messageListener);
    
    return new StandaloneConsumer(jettyConnection, messageConsumer);
  }

  @Override
  protected void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage, String contentType)
      throws ServiceException {
    processedMessage.addObjectHeader(JettyConstants.JETTY_WRAPPER, originalMessage.getObjectHeaders().get(JettyConstants.JETTY_WRAPPER));
    processedMessage.addMetadata(METADATA_STATUS, OK_200);
    processedMessage.addMetadata(METADATA_CONTENT_TYPE, StringUtils.defaultIfBlank(contentType, CONTENT_TYPE_DEFAULT));
    this.getResponseService().doService(processedMessage);
  }

  @Override
  public void doErrorResponse(AdaptrisMessage message, Exception e, String contentType) throws ServiceException {
    message.setContent(ExceptionUtils.getStackTrace(e), message.getContentEncoding());
    message.addMetadata(METADATA_STATUS, ERROR_400);
    message.addMetadata(METADATA_CONTENT_TYPE, StringUtils.defaultIfBlank(contentType, CONTENT_TYPE_DEFAULT));
    this.getResponseService().doService(message);
  }
}
