package nameless.common.feign;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.function.Function;

public enum DesensitizationMode {
    NONE(s -> s),
    /**
     * Encryption to be implemented
     */
    ENCRYPT(s -> s),
    /**
     * Hash to be implemented
     */
    HASH(s -> s),
    MASK(s -> StringUtils.repeat("*", s.length()));

    private final Function<String, String> desensitizer;

    DesensitizationMode(Function <String, String> desensitizer) {
        this.desensitizer = desensitizer;
    }

    @SuppressWarnings("unchecked")
    public Object desensitize(Object obj) {
        if (obj == null) {
            return null;
        } else if (obj instanceof String str) {
            return desensitizer.apply(str);
        } else if (obj.getClass().isArray()) {
            Object[] desensitizedArray = new Object[Array.getLength(obj)];
            for (int i = 0; i < Array.getLength(obj); i++) {
                Object element = Array.get(obj, i);
                desensitizedArray[i] = desensitize(element.toString());
            }
            return desensitizedArray;
        } else if (obj instanceof Collection) {
            Collection<Object> desensitizedCollection = new java.util.ArrayList<>(((Collection<Object>)obj).size());
            for (Object element : (Collection<?>) obj) {
                desensitizedCollection.add(desensitize(element.toString()));
            }
            return desensitizedCollection;
        } else {
            return desensitize(obj.toString());
        }
    }

    public static boolean needDesensitize(@Nullable ThreeStates<LogInvocation> threeStates) {
        if (threeStates == null || !threeStates.isAvailable()) {
            return false;
        }
        return threeStates.getValue().desensitizationMode() != DesensitizationMode.NONE;
    }
}