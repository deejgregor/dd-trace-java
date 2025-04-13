package datadog.trace.instrumentation.inetaddress;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.concreteClass;
import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.extendsClass;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.InstrumenterConfig;
import datadog.trace.bootstrap.config.provider.ConfigProvider;
import datadog.trace.bootstrap.instrumentation.api.*;
import net.bytebuddy.agent.*;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.*;
import java.net.*;
import java.security.*;

@AutoService(InstrumenterModule.class)
public class InetAddressInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForTypeHierarchy, Instrumenter.HasMethodAdvice {

  public InetAddressInstrumentation() {
    super("inetaddress");
  }

  @Override
  protected boolean defaultEnabled() {
    return InstrumenterConfig.get().
        isConnectionDetailsTracingEnabled();
  }
  
  @Override
  public String hierarchyMarkerType() {
    return null;
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return extendsClass(named("java.net.InetAddress")); //.and(concreteClass());
  }

  @Override
  public boolean isEnabled() {
    return super.isEnabled()
        && InstrumenterConfig.get().isTraceEnabled();
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    // https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/net/InetAddress.java
    // https://github.com/jchanghong/jdk8/blob/master/src/share/classes/java/net/InetAddress.java
    transformer.applyAdvice(
        isMethod()
            //.and(named("getAllByName0"))
            .and(named("getAddressesFromNameService"))
            .and(takesArgument(0, String.class)),
            //.and(returns(InetAddress.class)),
//            .and(takesArgument(1, boolean.class)), // newer Java
//            .and(takesArgument(1, named("java.net.InetAddress")), // Java 8
//            .and(takesArgument(2, boolean.class)), // Java 8
        getClass().getName() + "$HostLookupAdvice");
  }

  public static final class HostLookupAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter(@Advice.Argument(0) String host) {
      AgentSpan span = startSpan("host.lookup");
      span.setResourceName(host); // TODO might want to optionally suppress this in case an app looks up many host names
      span.setTag("peer.hostname", host);
      return activateSpan(span);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Enter final AgentScope scope, @Advice.Return InetAddress[] addresses) {
      AgentSpan span = scope.span();
      if (addresses != null) {
        // TODO Think of whether populating peer.ipvX with the first is the right strategy if there are multiple
        if (addresses.length > 0) {
          // Copied from BaseDecorator.onPeerConnection -- we don't need the host name caching part
          // TODO refactor our that part? Might also be usable in the ElasticSearch TransportActionListener classes
          // which only seem to do IPv4 at the moment
          String ip = addresses[0].getHostAddress();
          if (addresses[0] instanceof Inet4Address) {
            span.setTag(Tags.PEER_HOST_IPV4, ip);
          } else if (addresses[0] instanceof Inet6Address) {
            span.setTag(Tags.PEER_HOST_IPV6, ip);
          }
        }
        if (addresses.length > 1) {
          // TODO clean this up
          StringBuffer list = new StringBuffer();
          for (int i = 1; i < addresses.length; i++) {
            if (i > 1) {
              list.append(", ");
            }
            list.append(addresses[i].getHostAddress());
          }
          span.setTag("additional.ips", list); // TODO probably not the best name
        }
      }
      scope.close();
      span.finish();
    }
  }

}
