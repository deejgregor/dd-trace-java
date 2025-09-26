package datadog.trace.instrumentation.jdbc;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.bootstrap.instrumentation.api.Tags.DB_POOL_NAME;
import static datadog.trace.instrumentation.jdbc.PoolWaitingDecorator.DECORATE;
import static datadog.trace.instrumentation.jdbc.PoolWaitingDecorator.POOL_WAITING;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.zaxxer.hikari.util.ConcurrentBag;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.InstrumenterConfig;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumenterModule.class)
public final class WebSphereFreePoolInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForKnownTypes, Instrumenter.HasMethodAdvice {

  public WebSphereFreePoolInstrumentation() {
    super("jdbc", "websphere-jdbc");
  }

  @Override
  protected boolean defaultEnabled() {
    return InstrumenterConfig.get().isJdbcPoolWaitingEnabled();
  }

  @Override
  public String[] knownMatchingTypes() {
    return new String[] {
      "com.ibm.ejs.j2c.FreePool"
    };
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
            packageName + ".PoolWaitingDecorator",
            packageName + ".HikariBlockedTracker"
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
            named("queueRequest"),
            WebSphereFreePoolInstrumentation.class.getName() + "$QueueRequestAdvice");
    transformer.applyAdvice(
            named("createOrWaitForResource"),
            WebSphereFreePoolInstrumentation.class.getName() + "$CreateOrWaitForConnectionAdvice");
  }

  public static class CreateOrWaitForConnectionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      if (CallDepthThreadLocalMap.getCallDepth(PoolWaitingDecorator.class) > 0) {
        HikariBlockedTracker.setBlocked();
      }
    }
  }

  public static class QueueRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Long onEnter() {
      if (CallDepthThreadLocalMap.getCallDepth(PoolWaitingDecorator.class) > 0) {
        HikariBlockedTracker.clearBlocked();
        return System.currentTimeMillis();
      } else {
        return null;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Enter final Long startTimeMillis, @Advice.Thrown final Throwable throwable) {
      if (startTimeMillis != null && HikariBlockedTracker.wasBlocked()) {
        final AgentSpan span =
                startSpan(POOL_WAITING, TimeUnit.MILLISECONDS.toMicros(startTimeMillis));
        DECORATE.afterStart(span);
        DECORATE.onError(span, throwable);
        span.setResourceName("websphere.waiting");
        span.finish();
      }
      HikariBlockedTracker.clearBlocked();
    }
  }
}
