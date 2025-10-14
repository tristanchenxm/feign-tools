package nameless.common.feign;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Log input & output of feign interface. Handled by {@link FeignInvocationHandler}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LogInvocation {
    String[] environments() default {};

    DesensitizationMode desensitizationMode() default DesensitizationMode.NONE;

    /**
     * whether to log the request headers.
     */
    boolean logRequestHeaders() default false;

    /**
     * sensitive headers that should be masked in the logs.
     */
    String[] sensitiveHeaders() default {};

    String[] sensitiveParams() default {};

    String[] sensitiveRequestBodyFields() default {};

    String[] sensitiveResponseFields() default {};
}
