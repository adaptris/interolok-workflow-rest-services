package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.interlok.client.MessageTarget;

public class JettyConsumerWorkflowTargetTranslator implements WorkflowTargetTranslator {
  
  /**
   * The metadata item that contains the full rest path given by the user
   * 
   * For example it could be "/workflow-services/myAdapter/myChannel/myWorkflow"
   */
  private static final String PATH_KEY = "jettyURI";

  @Override
  public MessageTarget translateTarget(AdaptrisMessage message) {
    String metadataValue = message.getMetadataValue(PATH_KEY);
    if(metadataValue ==  null)
      return null;
    else {
      String[] pathItems = metadataValue.split("/");
      if(pathItems.length == 5) {  // We expect 5 items like the example above.
        MessageTarget result = new MessageTarget()
            .withAdapter(pathItems[2])
            .withChannel(pathItems[3])
            .withWorkflow(pathItems[4]);
        
        return result;
      } else
        return null;
    }
  }

}
