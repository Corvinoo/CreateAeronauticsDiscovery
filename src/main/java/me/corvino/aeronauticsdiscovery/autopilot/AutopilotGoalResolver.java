package me.corvino.aeronauticsdiscovery.autopilot;

import com.mojang.serialization.DataResult;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import me.corvino.aeronauticsdiscovery.util.SubLevelTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import javax.annotation.Nullable;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.AUTOPILOT;

/**
 * Reads the flight plan a mob should fly with. The plan is serialized onto the craft by
 * {@code PostAssemblyFinalizer} at assembly time (event plan, falling back to the template default).
 */
public final class AutopilotGoalResolver {

    private AutopilotGoalResolver() {
    }

    /** The plan of the sub-level the mob is riding, or {@code null} if none is defined. */
    @Nullable
    public static AutopilotPlan plan(AutopilotContext context) {
        if (!(context.subLevel() instanceof ServerSubLevel serverSubLevel)) return null;
        CompoundTag tag = serverSubLevel.getUserDataTag();
        if (tag == null) return null;
        if (!CreateAeronauticsDiscovery.MODID.equals(tag.getString("mod_id"))) return null;
        if (!tag.contains(SubLevelTags.PLAN_TAG)) return null;

        DataResult<AutopilotPlan> result = AutopilotPlan.CODEC.codec()
                .parse(NbtOps.INSTANCE, tag.getCompound(SubLevelTags.PLAN_TAG));
        return result.resultOrPartial(error -> ModLog.warn(AUTOPILOT, "Failed to read craft plan: {}", error))
                .orElse(null);
    }
}
