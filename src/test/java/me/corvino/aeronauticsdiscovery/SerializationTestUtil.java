package me.corvino.aeronauticsdiscovery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reusable utilities for round-trip serialization tests.
 *
 * <p>Two categories of serialization are supported:
 * <ul>
 *   <li><b>Codec-based</b> - encode/decode through {@link DynamicOps} (JSON or NBT)</li>
 *   <li><b>Manual NBT</b> - arbitrary save/load {@link Function} pairs</li>
 * </ul>
 *
 * <p>Each method accepts a {@link BiConsumer} that asserts the decoded
 * value matches the original. Since many Minecraft types (e.g. {@code Vec3},
 * {@code AssemblyContext}) do not override {@code equals}, field-by-field
 * assertion is the safest approach.
 *
 * <p><b>Usage example:</b>
 * <pre>{@code
 * FlyoverData original = FlyoverData.fresh(id, template);
 * SerializationTestUtil.assertNbtRoundTrip(FlyoverData.CODEC, original,
 *     (a, b) -> {
 *         assertEquals(a.subLevelId(), b.subLevelId());
 *         assertEquals(a.lifeTicks(), b.lifeTicks());
 *     });
 * }</pre>
 */
public final class SerializationTestUtil {

    private SerializationTestUtil() {}

    /**
     * Encode {@code original} through {@link Codec} + {@link JsonOps},
     * decode the result, then pass both to {@code asserter}.
     */
    public static <T> void assertJsonRoundTrip(Codec<T> codec, T original, BiConsumer<T, T> asserter) {
        assertCodecRoundTrip(codec, JsonOps.INSTANCE, original, asserter);
    }

    /**
     * Encode {@code original} through {@link Codec} + {@link NbtOps},
     * decode the result, then pass both to {@code asserter}.
     */
    public static <T> void assertNbtRoundTrip(Codec<T> codec, T original, BiConsumer<T, T> asserter) {
        assertCodecRoundTrip(codec, NbtOps.INSTANCE, original, asserter);
    }

    private static <T, O> void assertCodecRoundTrip(Codec<T> codec, DynamicOps<O> ops, T original, BiConsumer<T, T> asserter) {
        var encoded = codec.encodeStart(ops, original)
                .getOrThrow(error -> new AssertionError("Encode failed: " + error));
        var decoded = codec.decode(ops, encoded)
                .getOrThrow(error -> new AssertionError("Decode failed: " + error))
                .getFirst();
        asserter.accept(original, decoded);
    }

    /**
     * Manual round-trip through a save {@link Function} and optional load
     * {@link Function}, then pass both to {@code asserter}.
     */
    public static <T> void assertManualNbtRoundTripOptional(
            Function<T, CompoundTag> saver,
            Function<CompoundTag, Optional<T>> loader,
            T original,
            BiConsumer<T, T> asserter
    ) {
        CompoundTag tag = saver.apply(original);
        T decoded = loader.apply(tag)
                .orElseThrow(() -> new AssertionError("Load returned empty"));
        asserter.accept(original, decoded);
    }

    /**
     * Convenience for loaders that never return empty (throws on failure).
     */
    public static <T> void assertManualNbtRoundTrip(
            Function<T, CompoundTag> saver,
            Function<CompoundTag, T> loader,
            T original,
            BiConsumer<T, T> asserter
    ) {
        CompoundTag tag = saver.apply(original);
        T decoded = loader.apply(tag);
        asserter.accept(original, decoded);
    }
}
