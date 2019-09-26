package com.adaptris.rest;

import java.util.Properties;

import javax.management.MBeanServer;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.DefaultSerializableMessageTranslator;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.core.util.LifecycleHelper;

public class WorkflowHealthCheckComponent extends MgmtComponentImpl implements AdaptrisMessageListener {
  
  private static final Long DEFAULT_INITIAL_JETTY_CONTEXT_WAIT = (30l*1000l);
    
  private static final String PATH = "/workflow-health-check/*";
  
  private static final String ACCEPTED_FILTER = "GET";
  
  private transient WorkflowServicesConsumer consumer;
  
  private transient DefaultSerializableMessageTranslator messageTranslator;
      
  private transient Long initialJettyContextWaitMs;
  
  private transient MBeanServer interlokMBeanServer;
  
  private transient AdaptrisMessageFactory messageFactory;

  
  public WorkflowHealthCheckComponent() {
    this.setConsumer(new HttpRestWorkflowServicesConsumer());
    this.setMessageTranslator(new DefaultSerializableMessageTranslator());
    this.setMessageFactory(DefaultMessageFactory.getDefaultInstance());
  }
  
  @Override
  public void onAdaptrisMessage(AdaptrisMessage message) {
    
    // DIN - YOUR CODE GOES HERE.
    
    /**
     * This method will look a bit like the one in WorkflowServicesComponent.
     * 
     * You'll notice I modified the PATH and ACCEPTED_FILTER above so that this method will get triggered when a user
     * fires a GET request to "http://host:port/workflow-health-check/..."
     * 
     * I would say if the user only gives you an adapter, so basically a url like this;
     * "http://host:port/workflow-health-check/myAdapter"
     * 
     * Then you could prepare a response that shows the status (started, stopped etc) for all workflows in all channels.
     * 
     * If you get an adapter with a channel, so something like this;
     * "http://host:port/workflow-health-check/myAdapter"/myChannel"
     * 
     * Then you woulw prepare a response that shows that status all of the workflows in that channel.
     * 
     * If you get an adapter with a channel and a workflow, so something like this;
     * "http://host:port/workflow-health-check/myAdapter"/myChannel/myWorkflow"
     * 
     * Then prepare a response that shows the status for that single workflow.
     * 
     * Where do you get the statuses for each of the adapter, channels and workflows?
     * JMX of course.  If you start an instance of Interlok that has at least one configured workflow, then open up
     * JConsole, connect to the instance and you'll be able to see all of the MBeans available.  Shout if you need a quick guide around this bit.
     * 
     * You can use this.getInterlokMBeanServer(), to get access to the MBeans.
     * 
     * How do you send your response back to the user?
     * Simply write your response to the message payload and then call this method;
     * this.getConsumer().doResponse(message, message);
     * 
     * What happens if you want to send an error response back to the user; for example they have given a channel that doesn't exist?
     * Simply write your response to the message payload and then call this method;
     * this.getConsumer().doErrorResponse(message, new Exception("Your channel doesn't exist"));
     * 
     */
  }
  
  @Override
  public void init(Properties config) throws Exception {
    if(this.getInterlokMBeanServer() == null)
      this.setInterlokMBeanServer(JmxHelper.findMBeanServer());
    
    this.getConsumer().setAcceptedHttpMethods(ACCEPTED_FILTER);
    this.getConsumer().setConsumedUrlPath(PATH);
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

  public DefaultSerializableMessageTranslator getMessageTranslator() {
    return messageTranslator;
  }

  public void setMessageTranslator(DefaultSerializableMessageTranslator messageTranslator) {
    this.messageTranslator = messageTranslator;
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
