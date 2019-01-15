package com.adaptris.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.DefaultSerializableMessageTranslator;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.interlok.client.MessageTarget;
import com.adaptris.interlok.client.jmx.InterlokJmxClient;
import com.adaptris.interlok.types.SerializableMessage;
import com.adaptris.util.stream.StreamUtil;

public class WorkflowServicesComponent extends MgmtComponentImpl implements AdaptrisMessageListener {
  
  private static final Long DEFAULT_INITIAL_JETTY_CONTEXT_WAIT = (30l*1000l);
  
  private static final String DEFINITION_FILE = "META-INF/workflow-services.yaml";
  
  private transient WorkflowServicesConsumer consumer;
  
  private transient DefaultSerializableMessageTranslator messageTranslator;
  
  private transient InterlokJmxClient jmxClient;
  
  private transient WorkflowTargetTranslator targetTranslator;
  
  private transient Long initialJettyContextWaitMs;
  
  public WorkflowServicesComponent() {
    this.setConsumer(new HttpRestWorkflowServicesConsumer());
    this.setTargetTranslator(new JettyConsumerWorkflowTargetTranslator());
    this.setJmxClient(new InterlokJmxClient());
    this.setMessageTranslator(new DefaultSerializableMessageTranslator());
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message) {
    log.debug("Processing incoming message {}", message.getUniqueId());
    
    try {
      MessageTarget translateTarget = this.getTargetTranslator().translateTarget(message);
      if(translateTarget != null) {
        SerializableMessage processedMessage = this.getJmxClient().process(translateTarget, this.getMessageTranslator().translate(message));
      
        AdaptrisMessage responseMessage = this.getMessageTranslator().translate(processedMessage);
        this.getConsumer().doResponse(message, responseMessage);
      } else { // we'll just return the definition.
        AdaptrisMessage responseMessage = generateDefinitionFile(); 
        this.getConsumer().doResponse(message, responseMessage);
      }
    } catch (Exception e) {
      log.error("Unable to inject REST message into the workflow.", e);
      try {
        this.getConsumer().doErrorResponse(message, e);
      } catch (Exception silent) {}
    }
  }

  private AdaptrisMessage generateDefinitionFile() throws IOException {
    InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(DEFINITION_FILE);
    AdaptrisMessage responseMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    StreamUtil.copyAndClose(resourceAsStream, responseMessage.getOutputStream());
    return responseMessage;
  }
  
  @Override
  public void init(Properties config) throws Exception {
    this.getConsumer().setMessageListener(this);
    this.getConsumer().prepare();
    LifecycleHelper.init(getConsumer());
  }

  @Override
  public void start() throws Exception {
    new Thread(new Runnable() {
      
      @Override
      public void run() {
        try {
          // Wait for the Jetty context to have been created.
          Thread.sleep(initialJettyContextWaitMs());
          LifecycleHelper.start(getConsumer());
          
          log.debug("Workflow REST services component started.");
        } catch (CoreException | InterruptedException e) {
          log.error("Could not start the Workflow REST services component.", e);
        }
      }
    }).start();
    
  }

  @Override
  public void stop() throws Exception {
    LifecycleHelper.stop(getConsumer());
  }

  @Override
  public void destroy() throws Exception {
    LifecycleHelper.close(getConsumer());
  }

  @Override
  public String friendlyName() {
    return this.getClass().getSimpleName();
  }

  public WorkflowServicesConsumer getConsumer() {
    return consumer;
  }

  public void setConsumer(WorkflowServicesConsumer consumer) {
    this.consumer = consumer;
  }

  public WorkflowTargetTranslator getTargetTranslator() {
    return targetTranslator;
  }

  public void setTargetTranslator(WorkflowTargetTranslator targetTranslator) {
    this.targetTranslator = targetTranslator;
  }

  public DefaultSerializableMessageTranslator getMessageTranslator() {
    return messageTranslator;
  }

  public void setMessageTranslator(DefaultSerializableMessageTranslator messageTranslator) {
    this.messageTranslator = messageTranslator;
  }

  public InterlokJmxClient getJmxClient() {
    return jmxClient;
  }

  public void setJmxClient(InterlokJmxClient jmxClient) {
    this.jmxClient = jmxClient;
  }

  long initialJettyContextWaitMs() {
    return this.getInitialJettyContextWaitMs() == null ? DEFAULT_INITIAL_JETTY_CONTEXT_WAIT : this.getInitialJettyContextWaitMs();
  }
  
  public Long getInitialJettyContextWaitMs() {
    return initialJettyContextWaitMs;
  }

  public void setInitialJettyContextWaitMs(Long initialJettyContextWaitMs) {
    this.initialJettyContextWaitMs = initialJettyContextWaitMs;
  }

}
