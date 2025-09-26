package datadog.trace.instrumentation.jdbc;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.InstrumenterConfig;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumenterModule.class)
public final class WebSphereJdbcDataSourceInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForKnownTypes, Instrumenter.HasMethodAdvice {

  public WebSphereJdbcDataSourceInstrumentation() {
    super("jdbc", "websphere-jdbc");
  }

  @Override
  protected boolean defaultEnabled() {
    return InstrumenterConfig.get().isJdbcPoolWaitingEnabled();
  }

  @Override
  public String[] knownMatchingTypes() {
    return new String[] {
      "com.ibm.ws.rsadapter.jdbc.WSJdbcDataSource"
    };
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".PoolWaitingDecorator"};
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        named("getConnection"),
        WebSphereJdbcDataSourceInstrumentation.class.getName() + "$GetConnectionAdvice");
  }

  public static class GetConnectionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      CallDepthThreadLocalMap.incrementCallDepth(PoolWaitingDecorator.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit() {
      CallDepthThreadLocalMap.decrementCallDepth(PoolWaitingDecorator.class);
    }
  }
}
