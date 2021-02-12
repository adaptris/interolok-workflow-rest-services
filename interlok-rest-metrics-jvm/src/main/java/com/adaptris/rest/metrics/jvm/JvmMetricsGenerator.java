package com.adaptris.rest.metrics.jvm;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.function.ToLongFunction;

import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.rest.MetricBinder;
import com.adaptris.rest.MetricProviders;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;

public class JvmMetricsGenerator extends MgmtComponentImpl implements MetricBinder {

  private final Collection<Tag> tags;

  public JvmMetricsGenerator() {
    this(Collections.emptyList());
  }

  public JvmMetricsGenerator(Collection<Tag> tags) {
    this.tags = tags;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    for (BufferPoolMXBean bufferPoolBean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      Iterable<Tag> tagsWithId = Tags.concat(tags, "id", bufferPoolBean.getName());

      Gauge.builder("jvm.buffer.count", bufferPoolBean, BufferPoolMXBean::getCount).tags(tagsWithId)
          .description("An estimate of the number of buffers in the pool").baseUnit(BaseUnits.BUFFERS)
          .register(registry);

      Gauge.builder("jvm.buffer.memory.used", bufferPoolBean, BufferPoolMXBean::getMemoryUsed).tags(tagsWithId)
          .description("An estimate of the memory that the Java virtual machine is using for this buffer pool")
          .baseUnit(BaseUnits.BYTES).register(registry);

      Gauge.builder("jvm.buffer.total.capacity", bufferPoolBean, BufferPoolMXBean::getTotalCapacity).tags(tagsWithId)
          .description("An estimate of the total capacity of the buffers in this pool").baseUnit(BaseUnits.BYTES)
          .register(registry);
    }

    for (MemoryPoolMXBean memoryPoolBean : ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)) {
      String area = MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "nonheap";
      Iterable<Tag> tagsWithId = Tags.concat(tags, "id", memoryPoolBean.getName(), "area", area);

      Gauge.builder("jvm.memory.used", memoryPoolBean, (mem) -> getUsageValue(mem, MemoryUsage::getUsed))
          .tags(tagsWithId).description("The amount of used memory").baseUnit(BaseUnits.BYTES).register(registry);

      Gauge.builder("jvm.memory.committed", memoryPoolBean, (mem) -> getUsageValue(mem, MemoryUsage::getCommitted))
          .tags(tagsWithId)
          .description("The amount of memory in bytes that is committed for the Java virtual machine to use")
          .baseUnit(BaseUnits.BYTES).register(registry);

      Gauge.builder("jvm.memory.max", memoryPoolBean, (mem) -> getUsageValue(mem, MemoryUsage::getMax)).tags(tagsWithId)
          .description("The maximum amount of memory in bytes that can be used for memory management")
          .baseUnit(BaseUnits.BYTES).register(registry);
    }
  }

  static double getUsageValue(MemoryPoolMXBean memoryPoolMXBean, ToLongFunction<MemoryUsage> getter) {
    MemoryUsage usage = getUsage(memoryPoolMXBean);
    if (usage == null) {
      return Double.NaN;
    }
    return getter.applyAsLong(usage);
  }

  private static MemoryUsage getUsage(MemoryPoolMXBean memoryPoolMXBean) {
    try {
      return memoryPoolMXBean.getUsage();
    } catch (InternalError e) {
      return null;
    }
  }

  @Override
  public void init(Properties config) throws Exception {
    MetricProviders.PROVIDERS.add(this);
  }

  @Override
  public void start() throws Exception {
  }

  @Override
  public void stop() throws Exception {
  }

  @Override
  public void destroy() throws Exception {
  }
}
