package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.ComponentLifecycle;
import com.adaptris.core.ComponentLifecycleExtension;
import com.adaptris.core.CoreException;
import com.adaptris.core.ServiceException;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.interlok.util.Args;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public abstract class WorkflowServicesConsumer implements ComponentLifecycle, ComponentLifecycleExtension {


  public static final String CONTENT_TYPE_DEFAULT = "text/plain";
  public static final String CONTENT_TYPE_JSON = "application/json";

  @Getter
  @Setter
  private StandaloneConsumer standaloneConsumer;
  
  @Getter
  @Setter
  private AdaptrisMessageListener messageListener;
  
  /**
   * This is the url that this consumer will listen for requests.
   * For example "/workflow-services/*"; will trigger this consumer for any requests on "http://host:port/workflow-services/...".
   */
  @Getter
  @Setter
  private String consumedUrlPath;
  
  /**
   * A comma separated list of accepted http request methods; GET, POST, PATCH etc etc
   * And example might be "GET,POST".
   */
  @Getter
  @Setter
  private String acceptedHttpMethods;
    
  protected abstract StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath, String acceptedHttpMethods);
  
  protected void doResponse(AdaptrisMessage original, AdaptrisMessage processed) throws ServiceException {
    doResponse(original, processed, CONTENT_TYPE_DEFAULT);
  }

  protected abstract void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage, String contentType)
      throws ServiceException;
  
  public abstract void doErrorResponse(AdaptrisMessage message, Exception e, String contentType) throws ServiceException;

  @Override
  public void prepare() throws CoreException {
    this.setStandaloneConsumer(configureConsumer(this.getMessageListener(), this.getConsumedUrlPath(), this.getAcceptedHttpMethods()));
    LifecycleHelper.prepare(getStandaloneConsumer());
  }
  
  @Override
  public void init() throws CoreException {
    LifecycleHelper.init(getStandaloneConsumer());
  }

  @Override
  public void start() throws CoreException {
    LifecycleHelper.start(getStandaloneConsumer());
  }

  @Override
  public void stop() {
    LifecycleHelper.stop(getStandaloneConsumer());
  }

  @Override
  public void close() {
    LifecycleHelper.close(getStandaloneConsumer());
  }
  
  public static void sendErrorResponseQuietly(WorkflowServicesConsumer c, AdaptrisMessage msg, Exception e, String contentType) {
    try {
      Args.notNull(c, "workflow-services-consumer").doErrorResponse(msg, e, contentType);
    } catch (Exception exc) {
      log.trace("Ignored exception during error response {}", exc.getMessage());
    }
  }

  public static void sendErrorResponseQuietly(WorkflowServicesConsumer c, AdaptrisMessage msg, Exception e) {
    sendErrorResponseQuietly(c, msg, e, CONTENT_TYPE_DEFAULT);
  }
}
