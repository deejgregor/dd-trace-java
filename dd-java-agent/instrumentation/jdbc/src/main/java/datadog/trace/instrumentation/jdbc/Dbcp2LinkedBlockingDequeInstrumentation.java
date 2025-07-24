package datadog.trace.instrumentation.jdbc;

import com.google.auto.service.*;
import datadog.trace.agent.tooling.*;
import datadog.trace.bootstrap.instrumentation.api.*;
import net.bytebuddy.asm.*;
import org.slf4j.*;

import java.util.concurrent.*;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.*;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@AutoService(InstrumenterModule.class)
public final class Dbcp2LinkedBlockingDequeInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForKnownTypes, Instrumenter.HasMethodAdvice {

  private static final Logger log = LoggerFactory.getLogger(Dbcp2LinkedBlockingDequeInstrumentation.class);

  public Dbcp2LinkedBlockingDequeInstrumentation() {
    super("jdbc-datasource");
  }

  @Override
  public String[] knownMatchingTypes() {
    return new String[] {
        "org.apache.commons.pool2.impl.LinkedBlockingDeque", // standalone
        "org.apache.tomcat.dbcp.pool2.impl.LinkedBlockingDeque" // bundled with Tomcat
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        named("pollFirst")
            .and(takesArguments(1)),
        Dbcp2LinkedBlockingDequeInstrumentation.class.getName() + "$PollFirstAdvice");
  }

  public static class PollFirstAdvice {
    private static final String POOL_WAITING = "pool.waiting";

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentSpan onEnter() {
      return startSpan("dbcp2", POOL_WAITING);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Enter final AgentSpan span) {
      span.finish();
    }
  }
}
