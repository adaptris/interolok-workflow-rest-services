package com.adaptris.rest.metrics.jvm;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.function.ToLongFunction;

import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.rest.metrics.MetricBinder;
import com.adaptris.rest.metrics.MetricProviders;

import io.micrometer.core.instrument.FunctionCounter;
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
    
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    Gauge.builder("jvm.threads.peak", threadBean, ThreadMXBean::getPeakThreadCount)
            .tags(tags)
            .description("The peak live thread count since the Java virtual machine started or peak was reset")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

    Gauge.builder("jvm.threads.daemon", threadBean, ThreadMXBean::getDaemonThreadCount)
            .tags(tags)
            .description("The current number of live daemon threads")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

    Gauge.builder("jvm.threads.live", threadBean, ThreadMXBean::getThreadCount)
            .tags(tags)
            .description("The current number of live threads including both daemon and non-daemon threads")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

    try {
        threadBean.getAllThreadIds();
        for (Thread.State state : Thread.State.values()) {
            Gauge.builder("jvm.threads.states", threadBean, (bean) -> getThreadStateCount(bean, state))
                    .tags(Tags.concat(tags, "state", getStateTagValue(state)))
                    .description("The current number of threads having " + state + " state")
                    .baseUnit(BaseUnits.THREADS)
                    .register(registry);
        }
    } catch (Error error) {
        // An error will be thrown for unsupported operations
        // e.g. SubstrateVM does not support getAllThreadIds
    }
    
    ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();

    Gauge.builder("jvm.classes.loaded", classLoadingBean, ClassLoadingMXBean::getLoadedClassCount)
            .tags(tags)
            .description("The number of classes that are currently loaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);

    FunctionCounter.builder("jvm.classes.unloaded", classLoadingBean, ClassLoadingMXBean::getUnloadedClassCount)
            .tags(tags)
            .description("The total number of classes unloaded since the Java virtual machine has started execution")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);
    
    CompilationMXBean compilationBean = ManagementFactory.getCompilationMXBean();
    if (compilationBean != null && compilationBean.isCompilationTimeMonitoringSupported()) {
        FunctionCounter.builder("jvm.compilation.time", compilationBean, CompilationMXBean::getTotalCompilationTime)
                .tags(Tags.concat(tags, "compiler", compilationBean.getName()))
                .description("The approximate accumulated elapsed time spent in compilation")
                .baseUnit(BaseUnits.MILLISECONDS)
                .register(registry);
    }
  }
  
  static long getThreadStateCount(ThreadMXBean threadBean, Thread.State state) {
    return Arrays.stream(threadBean.getThreadInfo(threadBean.getAllThreadIds()))
            .filter(threadInfo -> threadInfo != null && threadInfo.getThreadState() == state)
            .count();
  }

  private static String getStateTagValue(Thread.State state) {
    return state.name().toLowerCase().replace("_", "-");
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
    MetricProviders.addProvider(this);
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
