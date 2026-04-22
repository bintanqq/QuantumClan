package me.bintanq.quantumclan.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static accessor for the {@link QuantumClanAPI} instance.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In your onEnable(), after QuantumClan has loaded:
 * if (QuantumClanProvider.isReady()) {
 *     QuantumClanAPI api = QuantumClanProvider.getAPI();
 *     // ...
 * }
 * }</pre>
 *
 * <p>QuantumClan sets this during its own {@code onEnable()} and clears it on
 * {@code onDisable()}, so always guard with {@link #isReady()} or a soft-depend
 * load-order check.</p>
 */
public final class QuantumClanProvider {

    private static QuantumClanAPI instance = null;

    private QuantumClanProvider() {}

    /**
     * Returns the API instance.
     *
     * @throws IllegalStateException if QuantumClan is not loaded yet
     */
    @NotNull
    public static QuantumClanAPI getAPI() {
        if (instance == null) {
            throw new IllegalStateException(
                    "QuantumClanAPI is not available. " +
                            "Make sure QuantumClan is loaded and add it as a (soft)depend.");
        }
        return instance;
    }

    /**
     * Returns the API instance, or {@code null} if QuantumClan is not loaded.
     * Prefer this over {@link #getAPI()} in soft-depend scenarios.
     */
    @Nullable
    public static QuantumClanAPI getAPIOrNull() {
        return instance;
    }

    /**
     * Returns {@code true} if the API has been registered by QuantumClan.
     */
    public static boolean isReady() {
        return instance != null;
    }

    /**
     * Called internally by QuantumClan during {@code onEnable()}.
     * Do NOT call this from external plugins.
     *
     * @param api The API implementation to register
     * @throws IllegalStateException if an API instance is already registered
     */
    public static void register(@NotNull QuantumClanAPI api) {
        if (instance != null) {
            throw new IllegalStateException("QuantumClanAPI is already registered.");
        }
        instance = api;
    }

    /**
     * Called internally by QuantumClan during {@code onDisable()}.
     * Do NOT call this from external plugins.
     */
    public static void unregister() {
        instance = null;
    }
}