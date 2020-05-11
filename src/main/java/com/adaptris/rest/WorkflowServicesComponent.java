package com.adaptris.rest;

import static com.adaptris.rest.WorkflowServicesConsumer.sendErrorResponseQuietly;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.MDC;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.DefaultSerializableMessageTranslator;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.interlok.client.MessageTarget;
import com.adaptris.interlok.client.jmx.InterlokJmxClient;
import com.adaptris.interlok.types.SerializableMessage;
import lombok.Getter;
import lombok.Setter;

public class WorkflowServicesComponent extends AbstractRestfulEndpoint {

  private static final String WORKFLOW_OBJ_NAME ="*com.adaptris:type=Workflow,*";

  private static final String BOOTSTRAP_PATH_KEY = "rest.workflow-services.path";

  private static final String DEFAULT_PATH = "/workflow-services/*";

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

  @Getter
  @Setter
  private transient DefaultSerializableMessageTranslator messageTranslator;

  @Getter
  @Setter
  private transient InterlokJmxClient jmxClient;

  @Getter
  @Setter
  private transient WorkflowTargetTranslator targetTranslator;

  @Getter
  @Setter
  private transient MBeanServer interlokMBeanServer;

  @Getter
  @Setter
  private transient AdaptrisMessageFactory messageFactory;

  @Setter
  private String configuredUrlPath;

  public WorkflowServicesComponent() {
    this.setTargetTranslator(new JettyConsumerWorkflowTargetTranslator());
    this.setJmxClient(new InterlokJmxClient());
    this.setMessageTranslator(new DefaultSerializableMessageTranslator());
    this.setMessageFactory(DefaultMessageFactory.getDefaultInstance());
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message, java.util.function.Consumer<AdaptrisMessage> onSuccess) {
    MDC.put(MDC_KEY, friendlyName());
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
      sendErrorResponseQuietly(getConsumer(), message, e);
    } finally {
      MDC.remove(MDC_KEY);
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
    super.init(config);
    this.setConfiguredUrlPath(config.getProperty(BOOTSTRAP_PATH_KEY));
  }

  @Override
  public String getConfiguredUrlPath() {
    return ObjectUtils.defaultIfNull(configuredUrlPath, DEFAULT_PATH);
  }

  @Override
  protected String getAcceptedFilter() {
    return ACCEPTED_FILTER;
  }

}
