package com.adaptris.rest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.DefaultMessageFactory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

public class PrometheusEndpointComponentTest {

  private PrometheusEndpointComponent component;
  
  private AdaptrisMessage message;
    
  private MockWorkflowConsumer mockConsumer;
  
  @Before
  public void setUp() throws Exception {
    mockConsumer = new MockWorkflowConsumer();
    
    component = new PrometheusEndpointComponent();
    component.setConsumer(mockConsumer);
    
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
  }

  @After
  public void tearDown() throws Exception {
    
  }

  @Test
  public void testNoMetrics() throws Exception {
    component.onAdaptrisMessage(message);
    
    assertFalse(mockConsumer.isError);
    assertTrue(mockConsumer.payload.equals(""));
  }
  
  @Test
  public void testMetrics() throws Exception {
    MetricProviders.PROVIDERS.clear();
    MetricProviders.PROVIDERS.add(new MockMetricProvider());
    
    component.onAdaptrisMessage(message);
    
    assertFalse(mockConsumer.isError);
    assertTrue(mockConsumer.payload.contains("test_metric"));
  }
  
  @Test
  public void testErrorResponseOnMetricGenerationException() throws Exception {
    MetricProviders.PROVIDERS.clear();
    MetricProviders.PROVIDERS.add(new MockFailingMetricProvider());
    component.onAdaptrisMessage(message);
    
    assertFalse(mockConsumer.isError);
    assertTrue(mockConsumer.payload.isEmpty());
  }
  
  @Test
  public void testErrorResponse() throws Exception {
    component.onAdaptrisMessage(null);
    
    assertTrue(mockConsumer.isError);
    assertTrue(mockConsumer.httpStatus == 500);
  }
  
  class MockMetricProvider implements MetricBinder {
    int value = 100;
    
    @Override
    public void bindTo(MeterRegistry registry) throws Exception {
      Gauge.builder("test-metric", this, MockMetricProvider::getValue)
          .description("A test metric.")
          .register(registry);
    }
    
    public int getValue() {
      return value;
    }
  }
  
  class MockFailingMetricProvider implements MetricBinder {
    
    @Override
    public void bindTo(MeterRegistry registry) throws Exception {
      throw new Exception("Expected");
    }
  }
}
