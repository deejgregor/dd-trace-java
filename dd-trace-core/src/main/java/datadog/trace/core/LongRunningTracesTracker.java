package datadog.trace.core;

import datadog.communication.ddagent.DDAgentFeaturesDiscovery;
import datadog.communication.ddagent.SharedCommunicationObjects;
import datadog.trace.api.Config;
import datadog.trace.common.writer.TraceDumpJsonExporter;
import datadog.trace.core.monitor.HealthMetrics;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LongRunningTracesTracker {
  private static final Logger log = LoggerFactory.getLogger(LongRunningTracesTracker.class);

  private final DDAgentFeaturesDiscovery features;
  private final HealthMetrics healthMetrics;
  private long lastFlushMilli = 0;
  private final boolean jmxEnabled;
  private ObjectName mbeanName;

  private final int maxTrackedTraces;
  private final int initialFlushPeriodMilli;
  private final int flushPeriodMilli;
  private final long maxTrackedDurationMilli = TimeUnit.HOURS.toMillis(12);
  private final List<PendingTrace> traceArray = new ArrayList<>(1 << 4);
  // Counters since last reset (for health metrics reporting)
  private int dropped = 0;
  private int write = 0;
  private int expired = 0;
  // Cumulative counters for JMX reporting (never reset)
  private long totalDropped = 0;
  private long totalWrite = 0;
  private long totalExpired = 0;

  public static final int NOT_TRACKED = -1;
  public static final int UNDEFINED = 0;
  public static final int TO_TRACK = 1;
  public static final int TRACKED = 2;
  public static final int WRITE_RUNNING_SPANS = 3;
  public static final int EXPIRED = 4;

  public LongRunningTracesTracker(
      Config config,
      int maxTrackedTraces,
      SharedCommunicationObjects sharedCommunicationObjects,
      HealthMetrics healthMetrics) {
    this.maxTrackedTraces = maxTrackedTraces;
    this.initialFlushPeriodMilli =
        (int) TimeUnit.SECONDS.toMillis(config.getLongRunningTraceInitialFlushInterval());
    this.flushPeriodMilli =
        (int) TimeUnit.SECONDS.toMillis(config.getLongRunningTraceFlushInterval());
    this.features = sharedCommunicationObjects.featuresDiscovery(config);
    this.healthMetrics = healthMetrics;
    this.jmxEnabled = config.isTelemetryJmxEnabled();

    if (jmxEnabled) {
      registerMBean();
    }
  }

  /**
   * Unregisters the MBean. Should be called when the tracker is being shut down.
   */
  public void close() {
    if (jmxEnabled) {
      unregisterMBean();
    }
  }

  private void registerMBean() {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

    try {
      mbeanName = new ObjectName("datadog.trace.core:type=LongRunningTraces");
      LongRunningTracesManager manager = new LongRunningTracesManager(this);
      mbs.registerMBean(manager, mbeanName);
      log.info("Registered LongRunningTraces MBean at {}", mbeanName);
    } catch (MalformedObjectNameException
        | InstanceAlreadyExistsException
        | MBeanRegistrationException
        | NotCompliantMBeanException e) {
      log.warn("Failed to register LongRunningTraces MBean", e);
      mbeanName = null;
    }
  }

  private void unregisterMBean() {
    if (mbeanName != null) {
      MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
      try {
        mbs.unregisterMBean(mbeanName);
        log.debug("Unregistered LongRunningTraces MBean");
      } catch (Exception e) {
        log.debug("Failed to unregister LongRunningTraces MBean", e);
      }
    }
  }

  public boolean add(PendingTraceBuffer.Element element) {
    if (!(element instanceof PendingTrace)) {
      return false;
    }
    PendingTrace trace = (PendingTrace) element;
    // PendingTraces are added only once
    if (!trace.compareAndSetLongRunningState(TO_TRACK, TRACKED)) {
      return false;
    }
    this.addTrace(trace);
    return true;
  }

  private void addTrace(PendingTrace trace) {
    if (trace.empty()) {
      return;
    }
    if (traceArray.size() == maxTrackedTraces) {
      dropped++;
      totalDropped++;
      return;
    }
    traceArray.add(trace);
  }

  public void flushAndCompact(long nowMilli) {
    if (nowMilli < lastFlushMilli + TimeUnit.SECONDS.toMillis(1)) {
      return;
    }
    int i = 0;
    while (i < traceArray.size()) {
      PendingTrace trace = traceArray.get(i);
      if (trace == null) {
        cleanSlot(i);
        continue;
      }
      if (trace.empty()) {
        trace.compareAndSetLongRunningState(WRITE_RUNNING_SPANS, NOT_TRACKED);
        cleanSlot(i);
        continue;
      }
      // NOTE: We don't check features.supportsLongRunning() here because:
      // 1. If the tracker was created, local config already enabled long-running traces
      // 2. Remote agent feature discovery may not be available (e.g., when using LoggingWriter)
      // 3. Traces added to tracker should stay tracked regardless of agent capabilities
      if (hasExpired(nowMilli, trace)) {
        trace.compareAndSetLongRunningState(WRITE_RUNNING_SPANS, EXPIRED);
        expired++;
        totalExpired++;
        cleanSlot(i);
        continue;
      }
      if (shouldFlush(nowMilli, trace)) {
        if (negativeOrNullPriority(trace)) {
          trace.compareAndSetLongRunningState(TRACKED, NOT_TRACKED);
          cleanSlot(i);
          continue;
        }
        trace.compareAndSetLongRunningState(TRACKED, WRITE_RUNNING_SPANS);
        write++;
        totalWrite++;
        trace.write();
      }
      i++;
    }
    lastFlushMilli = nowMilli;
    flushStats();
  }

  private boolean hasExpired(long nowMilli, PendingTrace trace) {
    return (nowMilli - TimeUnit.NANOSECONDS.toMillis(trace.getRunningTraceStartTime()))
        > maxTrackedDurationMilli;
  }

  private boolean shouldFlush(long nowMilli, PendingTrace trace) {
    long traceStartTimeNano = trace.getRunningTraceStartTime();
    long lastWriteTimeNano = trace.getLastWriteTime();

    // Initial flush
    if (lastWriteTimeNano <= traceStartTimeNano) {
      return nowMilli - TimeUnit.NANOSECONDS.toMillis(traceStartTimeNano) > initialFlushPeriodMilli;
    }

    return nowMilli - TimeUnit.NANOSECONDS.toMillis(lastWriteTimeNano) > flushPeriodMilli;
  }

  private void cleanSlot(int index) {
    int lastElementIndex = traceArray.size() - 1;
    traceArray.set(index, traceArray.get(lastElementIndex));
    traceArray.remove(lastElementIndex);
  }

  private boolean negativeOrNullPriority(PendingTrace trace) {
    Integer prio = trace.evaluateSamplingPriority();
    return prio == null || prio <= 0;
  }

  private void flushStats() {
    healthMetrics.onLongRunningUpdate(dropped, write, expired);
    dropped = 0;
    write = 0;
    expired = 0;
  }

  /**
   * Returns detailed information about all currently tracked long-running traces as JSON.
   *
   * @return JSON string containing trace details
   * @throws IOException if an error occurs while generating JSON
   */
  public String getTracesAsJson() throws IOException {
    try (TraceDumpJsonExporter writer = new TraceDumpJsonExporter()) {
      for (PendingTrace  trace : traceArray) {
        writer.write(trace.getSpans());
      }
      return writer.getDumpJson();
    }
  }

  /**
   * Returns the number of currently tracked traces.
   *
   * @return the count of tracked traces
   */
  public int getTrackedTraceCount() {
    return traceArray.size();
  }

  /**
   * Returns the total number of dropped traces (cumulative).
   *
   * @return the count of dropped traces
   */
  public long getDroppedTraceCount() {
    return totalDropped;
  }

  /**
   * Returns the total number of written traces (cumulative).
   *
   * @return the count of written traces
   */
  public long getWrittenTraceCount() {
    return totalWrite;
  }

  /**
   * Returns the total number of expired traces (cumulative).
   *
   * @return the count of expired traces
   */
  public long getExpiredTraceCount() {
    return totalExpired;
  }
}
