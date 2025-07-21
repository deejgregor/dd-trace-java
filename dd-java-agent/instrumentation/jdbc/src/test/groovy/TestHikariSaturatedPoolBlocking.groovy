import datadog.trace.agent.test.AgentTestRunner
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import test.TestDataSource

import java.sql.SQLTimeoutException
import java.sql.SQLTransientConnectionException

/**
 * Ideas taken from Hikari's com.zaxxer.hikari.pool.TestSaturatedPool830.
 */
class TestHikariSaturatedPoolBlocking extends AgentTestRunner {
  def "saturated pool test"(connectionTimeout, exhaustPoolForMillis, expectedBlockedSpans, expectedTimeout) {
    setup:
    TEST_WRITER.setFilter((trace) -> trace.get(0).getOperationName() == "test.when")

    final HikariConfig config = new HikariConfig()
    config.setPoolName("testPool")
    config.setMaximumPoolSize(1)
    config.setConnectionTimeout(connectionTimeout)
    config.setDataSourceClassName(TestDataSource.class.getName())
    final HikariDataSource ds = new HikariDataSource(config)

    when:
    if (exhaustPoolForMillis != null) {
      def saturatedConnection = ds.getConnection()
      new Thread(() -> {
        Thread.sleep(exhaustPoolForMillis)
        saturatedConnection.close()
      }, "saturated connection closer").start()
    }

    def timedOut = false
    def span = TEST_TRACER.startSpan("test", "test.when")
    try (def ignore = TEST_TRACER.activateSpan(span)) {
      ds.getConnection()
    } catch (SQLTransientConnectionException e) {
      if (e.getMessage().contains("request timed out after")) {
        timedOut = true
      } else {
        throw e
      }
    } catch (SQLTimeoutException ignored) {
      timedOut = true
    }
    span.finish()

    then:
    def blocked = TEST_WRITER.firstTrace().findAll {
      element -> element.getOperationName() == "hikari.blocked" }

    verifyAll {
      TEST_WRITER.size() == 1
      blocked.size() == expectedBlockedSpans
      timedOut == expectedTimeout
    }

    where:
    connectionTimeout | exhaustPoolForMillis | expectedBlockedSpans | expectedTimeout
    1000              | null                 | 0                    | false
    1000              | null                 | 0                    | false
    1000              | 500                  | 1                    | false
    1000              | 1500                 | 1                    | true
  }
}
