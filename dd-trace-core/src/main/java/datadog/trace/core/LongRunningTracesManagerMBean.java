package datadog.trace.core;

import java.io.IOException;

/**
 * MBean interface for managing and monitoring long-running traces.
 *
 * <p>This interface provides JMX operations to inspect currently tracked long-running traces.
 */
public interface LongRunningTracesManagerMBean {

  /**
   * Returns detailed information about all currently tracked long-running traces as JSON.
   *
   * @return JSON string containing an array of long-running trace details
   * @throws IOException if an error occurs while generating JSON
   */
  String getLongRunningTracesJson() throws IOException;

  /**
   * Returns the number of currently tracked long-running traces.
   *
   * @return the count of tracked traces
   */
  int getTrackedTraceCount();

  /**
   * Returns the total number of dropped traces (cumulative).
   *
   * @return the count of dropped traces
   */
  long getDroppedTraceCount();

  /**
   * Returns the total number of written traces (cumulative).
   *
   * @return the count of written traces
   */
  long getWrittenTraceCount();

  /**
   * Returns the total number of expired traces (cumulative).
   *
   * @return the count of expired traces
   */
  long getExpiredTraceCount();
}
