package nameless.common.feign;

public class FeignContext {
    public static class LoggableContext {
        private static final ThreadLocal<ThreeStates<LogInvocation>> logSettings = ThreadLocal.withInitial(() -> null);

        public static void setLogSettings(ThreeStates<LogInvocation> value) {
            logSettings.set(value);
        }

        public static ThreeStates<LogInvocation> logSettings() {
            return logSettings.get();
        }

        public static void clear() {
            logSettings.remove();
        }
    }
}
