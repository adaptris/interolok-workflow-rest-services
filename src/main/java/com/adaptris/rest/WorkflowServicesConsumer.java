package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.ComponentLifecycle;
import com.adaptris.core.ComponentLifecycleExtension;
import com.adaptris.core.CoreException;
import com.adaptris.core.ServiceException;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.util.LifecycleHelper;

public abstract class WorkflowServicesConsumer implements ComponentLifecycle, ComponentLifecycleExtension {
    
  private StandaloneConsumer standaloneConsumer;
  
  private AdaptrisMessageListener messageListener;
    
  protected abstract StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener);
  
  protected abstract void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage) throws ServiceException;

  @Override
  public void prepare() throws CoreException {
    this.setStandaloneConsumer(configureConsumer(this.getMessageListener()));
    getStandaloneConsumer().prepare();
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

  public StandaloneConsumer getStandaloneConsumer() {
    return standaloneConsumer;
  }

  public void setStandaloneConsumer(StandaloneConsumer standaloneConsumer) {
    this.standaloneConsumer = standaloneConsumer;
  }
  
  public AdaptrisMessageListener getMessageListener() {
    return messageListener;
  }

  public void setMessageListener(AdaptrisMessageListener messageListener) {
    this.messageListener = messageListener;
  }

}
