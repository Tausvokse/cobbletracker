package com.cobbletracker.mod.spawning;

import com.cobblemon.mod.common.api.spawning.BestSpawner;
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.selection.SpawnSelectionData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CobblemonSpawnBridge {
    private static final Method GET_BUCKET = method(SpawnDetail.class, "getBucket");
    private static final Method CHOOSE = java.util.Arrays.stream(SpawnDetail.class.getMethods())
            .filter(m -> m.getName().equals("choose") && m.getParameterCount() == 3
                    && m.getParameterTypes()[0] == SpawnablePosition.class
                    && m.getParameterTypes()[2] == SpawnSelectionData.class)
            .findFirst().orElseThrow(() -> new IllegalStateException("Unsupported Cobblemon spawn action API"));

    private CobblemonSpawnBridge() {
    }

    public static String bucketName(SpawnDetail detail) {
        Object bucket = invoke(GET_BUCKET, detail);
        return bucket instanceof String name ? name : String.valueOf(invoke(method(bucket.getClass(), "getName"), bucket));
    }

    public static Map<String, Float> worldBucketWeights() {
        Object config = BestSpawner.INSTANCE.getConfig();
        Map<String, Float> weights = new LinkedHashMap<>();
        Method worldBuckets;
        try {
            worldBuckets = config.getClass().getMethod("getWorldBuckets");
        } catch (NoSuchMethodException oldApi) {
            Object buckets = invoke(method(config.getClass(), "getBuckets"), config);
            for (Object bucket : (Iterable<?>) buckets) {
                weights.put(String.valueOf(invoke(method(bucket.getClass(), "getName"), bucket)),
                        ((Number) invoke(method(bucket.getClass(), "getWeight"), bucket)).floatValue());
            }
            return weights;
        }
        Map<?, ?> buckets = (Map<?, ?>) invoke(worldBuckets, config);
        buckets.forEach((name, weight) -> weights.put(String.valueOf(name), ((Number) weight).floatValue()));
        return weights;
    }

    public static Object spawn(SpawnDetail detail, SpawnablePosition position) {
        SpawnAction<?> action = (SpawnAction<?>) invoke(CHOOSE, detail, position,
                invoke(GET_BUCKET, detail), SpawnSelectionData.Companion.getEMPTY());
        return action.complete();
    }

    private static Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Unsupported Cobblemon API: " + type.getName() + "." + name, e);
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access Cobblemon API: " + method, e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new IllegalStateException("Cobblemon API failed: " + method, e.getCause());
        }
    }
}
