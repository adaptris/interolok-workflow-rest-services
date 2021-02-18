package com.adaptris.rest.metrics;

import io.micrometer.core.instrument.MeterRegistry;

public interface MetricBinder {

  public void bindTo(MeterRegistry registry) throws Exception;
  
}
