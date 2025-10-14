package nameless.common.feign;

import feign.InvocationHandlerFactory;
import feign.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static feign.Util.checkNotNull;

/**
 * loggable Feign InvocationHandler. Log input & output of feign interface
 */
public class FeignInvocationHandler implements InvocationHandler {
    private final static Logger logger = LoggerFactory.getLogger(FeignInvocationHandler.class);

    final Target target;
    final Map<Method, InvocationHandlerFactory.MethodHandler> dispatch;
    final String environment;

    /**
     * Cache for loggable methods.
     * Why not use a simple Map<Method, Boolean>?
     * because ConcurrentHashMap doesn't allow null values,
     * so we use Integer to represent the 3 loggable states: 0 for false, 1 for true, and -1 for null
     */
    private final ConcurrentHashMap<Method, ThreeStates<LogInvocation>> loggableCache;

    public FeignInvocationHandler(Target target, Map<Method, InvocationHandlerFactory.MethodHandler> dispatch, String environment) {
        this.target = checkNotNull(target, "target");
        this.dispatch = checkNotNull(dispatch, "dispatch for %s", target);
        this.environment = environment;
        this.loggableCache = new ConcurrentHashMap<>();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("equals".equals(method.getName())) {
            try {
                Object otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                return equals(otherHandler);
            } catch (IllegalArgumentException e) {
                return false;
            }
        } else if ("hashCode".equals(method.getName())) {
            return hashCode();
        } else if ("toString".equals(method.getName())) {
            return toString();
        }
        FeignContext.LoggableContext.setLogSettings(loggable(proxy, method));
        return dispatch.get(method).invoke(args);
    }


    // null, means not use logInvocation annotation
    protected ThreeStates<LogInvocation> loggable(Object proxy, Method method) {
        if (loggableCache.containsKey(method)) {
            return loggableCache.get(method);
        }
        LogInvocation logInvocation = method.getAnnotation(LogInvocation.class);
        if (logInvocation == null) {
            Class<?>[] interfaces = proxy.getClass().getInterfaces();
            for (Class<?> iter : interfaces) {
                logInvocation = iter.getAnnotation(LogInvocation.class);
                if (logInvocation != null) {
                    break;
                }

                for (Class<?> superClass = iter.getSuperclass();
                     superClass != null && logInvocation == null && superClass != method.getDeclaringClass();) {
                    logInvocation = superClass.getAnnotation(LogInvocation.class);
                    superClass = superClass.getSuperclass();
                }
            }
        }
        // if no LogInvocation annotation, then return null
        if (logInvocation == null) {
            loggableCache.putIfAbsent(method, ThreeStates.none());
            return loggableCache.get(method);
        }
        if (logInvocation.environments().length == 0) {
            loggableCache.putIfAbsent(method, ThreeStates.set(logInvocation));
            return loggableCache.get(method);
        }
        for (String supportedEnvironment : logInvocation.environments()) {
            if (supportedEnvironment.equalsIgnoreCase(environment)) {
                loggableCache.putIfAbsent(method, ThreeStates.set(logInvocation));
                return loggableCache.get(method);
            }
        }
        loggableCache.put(method, ThreeStates.setButUnavailable(logInvocation));
        return loggableCache.get(method);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FeignInvocationHandler) {
            FeignInvocationHandler other = (FeignInvocationHandler) obj;
            return target.equals(other.target);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @Override
    public String toString() {
        return target.toString();
    }
}
