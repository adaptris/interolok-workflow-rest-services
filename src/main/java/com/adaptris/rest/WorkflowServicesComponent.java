package com.adaptris.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.DefaultSerializableMessageTranslator;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.interlok.client.MessageTarget;
import com.adaptris.interlok.client.jmx.InterlokJmxClient;
import com.adaptris.interlok.types.SerializableMessage;

import javax.management.*;

import org.apache.commons.io.IOUtils;

public class WorkflowServicesComponent extends MgmtComponentImpl implements AdaptrisMessageListener {
  
  private static final Long DEFAULT_INITIAL_JETTY_CONTEXT_WAIT = (30l*1000l);
    
  private static final String WORKFLOW_OBJ_NAME ="*com.adaptris:type=Workflow,*";
  
  private static final String PATH = "/workflow-services/*";
  
  private static final String ACCEPTED_FILTER = "POST,GET";
  
  private static final String DEF_HEADER = "META-INF/definition-header.yaml";
  
  private static final String DEF_WORKFLOW = "META-INF/definition-workflow.yaml";
  
  private static final String WORKFLOW_MANAGER = "com.adaptris.core.runtime.WorkflowManager";
  
  private static final String HOST_PLACEHOLDER = "{host}";
  
  private static final String ADAPTER_PLACEHOLDER = "{adapter}";
  
  private static final String CHANNEL_PLACEHOLDER = "{channel}";
  
  private static final String WORKFLOW_PLACEHOLDER = "{id}";
  
  private static final String OBJECT_PROPERTY_ADAPTER = "adapter";
  
  private static final String OBJECT_PROPERTY_CHANNEL = "channel";
  
  private static final String OBJECT_PROPERTY_WORKFLOW = "id";
  
  private static final String HTTP_HEADER_HOST = "http.header.Host";
  
  private transient WorkflowServicesConsumer consumer;
  
  private transient DefaultSerializableMessageTranslator messageTranslator;
  
  private transient InterlokJmxClient jmxClient;
  
  private transient WorkflowTargetTranslator targetTranslator;
  
  private transient Long initialJettyContextWaitMs;
  
  private transient MBeanServer interlokMBeanServer;
  
  private transient AdaptrisMessageFactory messageFactory;
  
  public WorkflowServicesComponent() {
    this.setConsumer(new HttpRestWorkflowServicesConsumer());
    this.setTargetTranslator(new JettyConsumerWorkflowTargetTranslator());
    this.setJmxClient(new InterlokJmxClient());
    this.setMessageTranslator(new DefaultSerializableMessageTranslator());
    this.setMessageFactory(DefaultMessageFactory.getDefaultInstance());
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
        AdaptrisMessage responseMessage = generateDefinitionFile(message.getMetadataValue(HTTP_HEADER_HOST));
        this.getConsumer().doResponse(message, responseMessage);
      }
    } catch (Exception e) {
      log.error("Unable to inject REST message into the workflow.", e);
      try {
        this.getConsumer().doErrorResponse(message, e);
      } catch (Exception silent) {}
    }
  }
  private AdaptrisMessage generateDefinitionFile(String host) throws IOException, MalformedObjectNameException {
    StringBuilder definition = new StringBuilder();

    AdaptrisMessage responseMessage = this.getMessageFactory().newMessage();
    
    if(this.getInterlokMBeanServer() == null)
      this.setInterlokMBeanServer(JmxHelper.findMBeanServer());

    Set<ObjectInstance> objectInstanceSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(WORKFLOW_OBJ_NAME), null);
    Iterator<ObjectInstance> iterator = objectInstanceSet.iterator();
    definition.append(readResourceAsString(DEF_HEADER, responseMessage.getContentEncoding()));
    while (iterator.hasNext()) {
      ObjectInstance instance = iterator.next();
      if (instance.getClassName().equals(WORKFLOW_MANAGER)) {

        definition.append("\n");
        definition.append(personalizedWorkflowDef(readResourceAsString(DEF_WORKFLOW, responseMessage.getContentEncoding()), instance.getObjectName()));
      }
    }
    
    responseMessage.setContent(definition.toString().replace(HOST_PLACEHOLDER, host), responseMessage.getContentEncoding());
    return responseMessage;
  }
  
  private String readResourceAsString(String resourceName, String contentEncoding) throws IOException {
    InputStream resourceBody = this.getClass().getClassLoader().getResourceAsStream(resourceName);
    return IOUtils.toString(resourceBody, contentEncoding);
  }
  
  private String personalizedWorkflowDef(String rawDef, ObjectName objectName) {
    return rawDef.replace(ADAPTER_PLACEHOLDER, objectName.getKeyProperty(OBJECT_PROPERTY_ADAPTER))
        .replace(CHANNEL_PLACEHOLDER, objectName.getKeyProperty(OBJECT_PROPERTY_CHANNEL))
        .replace(WORKFLOW_PLACEHOLDER, objectName.getKeyProperty(OBJECT_PROPERTY_WORKFLOW));
  }
  
  @Override
  public void init(Properties config) throws Exception {
    
  }

  @Override
  public void start() throws Exception {
    WorkflowServicesComponent instance = this;
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          getConsumer().setAcceptedHttpMethods(ACCEPTED_FILTER);
          getConsumer().setConsumedUrlPath(PATH);
          getConsumer().setMessageListener(instance);
          getConsumer().prepare();
          LifecycleHelper.init(getConsumer());
          LifecycleHelper.start(getConsumer());
          
          log.debug("Workflow REST services component started.");
        } catch (CoreException e) {
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

  public MBeanServer getInterlokMBeanServer() {
    return interlokMBeanServer;
  }

  public void setInterlokMBeanServer(MBeanServer interlokMBeanServer) {
    this.interlokMBeanServer = interlokMBeanServer;
  }

  public AdaptrisMessageFactory getMessageFactory() {
    return messageFactory;
  }

  public void setMessageFactory(AdaptrisMessageFactory messageFactory) {
    this.messageFactory = messageFactory;
  }

}
