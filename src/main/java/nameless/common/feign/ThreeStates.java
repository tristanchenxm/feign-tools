package nameless.common.feign;

import jakarta.annotation.Nonnull;

public class ThreeStates<T> {
    public static final ThreeStates<?> NONE = new ThreeStates<>();
    private final T value;
    private final boolean available;

    private ThreeStates(T value) {
        this.value = value;
        this.available = true;
    }

    private ThreeStates() {
        this.value = null;
        available = false;
    }

    private ThreeStates(@Nonnull T value, boolean available) {
        this.value = value;
        this.available = available;
    }

    @SuppressWarnings("unchecked")
    public static <T> ThreeStates<T> set(T value) {
        return value == null ? (ThreeStates<T>)NONE : new ThreeStates<>(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> ThreeStates<T> none() {
        return (ThreeStates<T>)NONE;
    }

    public static <T> ThreeStates<T> setButUnavailable(@Nonnull T value) {
        return new ThreeStates<>(value, false);
    }

    public T getValue() {
        return value;
    }

    public boolean isAvailable() {
        return available;
    }
}
