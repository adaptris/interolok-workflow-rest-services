package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.interlok.client.MessageTarget;

public interface WorkflowTargetTranslator {

  public MessageTarget translateTarget(AdaptrisMessage message);
  
}
