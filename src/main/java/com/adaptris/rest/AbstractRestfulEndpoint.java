package com.adaptris.rest;

import java.util.Properties;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.LifecycleHelper;

public abstract class AbstractRestfulEndpoint extends MgmtComponentImpl implements AdaptrisMessageListener {

  public AbstractRestfulEndpoint() {
    this.setConsumer(new HttpRestWorkflowServicesConsumer());
  }
  
  private transient WorkflowServicesConsumer consumer;
  
  @Override
  public void init(Properties config) throws Exception {
  }

  @Override
  public void start() throws Exception {
    AbstractRestfulEndpoint instance = this;
    new Thread(() -> {
      try {
        getConsumer().setAcceptedHttpMethods(getAcceptedFilter());
        getConsumer().setConsumedUrlPath(getConfiguredUrlPath());
        getConsumer().setMessageListener(instance);
        LifecycleHelper.initAndStart(getConsumer());

        log.debug(friendlyName() + " component started.");
      } catch (CoreException e) {
        log.error("Could not start [{}]", friendlyName(), e);
      }
    }).start();
  }
  
  protected abstract String getAcceptedFilter();
  
  protected abstract String getConfiguredUrlPath();
  
  @Override
  public String friendlyName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void stop() throws Exception {
    LifecycleHelper.stop(getConsumer());
    
    log.debug(friendlyName() + " component stopped.");
  }

  @Override
  public void destroy() throws Exception {
    LifecycleHelper.close(getConsumer());
  }
  
  public WorkflowServicesConsumer getConsumer() {
    return consumer;
  }

  public void setConsumer(WorkflowServicesConsumer consumer) {
    this.consumer = consumer;
  }
}
