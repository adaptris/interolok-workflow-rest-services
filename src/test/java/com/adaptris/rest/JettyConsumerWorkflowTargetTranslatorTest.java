package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.interlok.client.MessageTarget;

import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

public class JettyConsumerWorkflowTargetTranslatorTest extends TestCase {
  
  private static final String PATH_KEY = "jettyURI";
  
  private AdaptrisMessage message;
  
  private JettyConsumerWorkflowTargetTranslator targetTranslator;

  @Before
  public void setUp() throws Exception {
    targetTranslator = new JettyConsumerWorkflowTargetTranslator();
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
  }

  @Test
  public void testFullPathToWorkflow() throws Exception{
    message.addMessageHeader(PATH_KEY, "/workflow-services/myAdapter/myChannel/myWorkflow");
    MessageTarget target = targetTranslator.translateTarget(message);
    
    assertEquals("myAdapter", target.getAdapter());
    assertEquals("myChannel", target.getChannel());
    assertEquals("myWorkflow", target.getWorkflow());
  }

  @Test
  public void testRootReturnsNull() throws Exception{
    message.addMessageHeader(PATH_KEY, "/workflow-services/");
    MessageTarget target = targetTranslator.translateTarget(message);
    
    assertNull(target);
  }

  @Test
  public void testNoPathMetadataReturnsNull() throws Exception{
    MessageTarget target = targetTranslator.translateTarget(message);
    
    assertNull(target);
  }

  @Test
  public void testWrongNumberOfParams() throws Exception{
    message.addMessageHeader(PATH_KEY, "/workflow-services/1/2/3/4/5/6/7/");
    try {
      targetTranslator.translateTarget(message);
      fail("Message target should be null, because one was not supplied.");
    } catch (CoreException ex) {
      // expected
    }
  }
  
}
