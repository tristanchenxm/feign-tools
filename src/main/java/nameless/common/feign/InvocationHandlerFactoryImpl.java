package nameless.common.feign;

import feign.InvocationHandlerFactory;
import feign.Target;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import static feign.Util.checkNotNull;

/**
 * example:
 * <pre> {@code
 *     {@literal @}Bean
 *     {@literal @}Scope("prototype")
 *     public Feign.Builder feignBuilder(Retryer retryer, {@literal @}Value("environment") String environment) {
 *         return Feign.builder().invocationHandlerFactory(new InvocationHandlerFactoryImpl(environment)).retryer(retryer);
 *     }
 * }
 * </pre>
 */
public class InvocationHandlerFactoryImpl implements InvocationHandlerFactory {
    private final String environment;
    private final Class<? extends FeignInvocationHandler> handlerClass;

    public InvocationHandlerFactoryImpl(String environment) {
        this.environment = environment;
        this.handlerClass = FeignInvocationHandler.class;
    }

    public InvocationHandlerFactoryImpl(String environment, Class<? extends FeignInvocationHandler> handlerClass) {
        this.environment = environment;
        this.handlerClass = checkNotNull(handlerClass, "handlerClass");
    }

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
        try {
            return handlerClass.getConstructor(Target.class, Map.class, String.class)
                    .newInstance(target, dispatch, environment);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
