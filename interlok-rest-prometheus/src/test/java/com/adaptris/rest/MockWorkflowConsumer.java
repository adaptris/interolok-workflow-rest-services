package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.StandaloneConsumer;

public class MockWorkflowConsumer extends WorkflowServicesConsumer {

  String payload;
  boolean isError;
  int httpStatus = -1;

  @Override
  protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath, String acceptedHttpMethods) {
    return null;
  }

  @Override
  protected void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage,
      String contentType, int status) {
    payload = processedMessage.getContent();
    httpStatus = status;
  }


  @Override
  public void doErrorResponse(AdaptrisMessage message, Exception e, int status) {
    isError = true;
    httpStatus = status;
  }

  public boolean complete() {
    return isError == true || payload != null;
  }

  @Override
  public void prepare() {

  }
}