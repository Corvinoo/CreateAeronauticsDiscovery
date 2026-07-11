package me.corvino.aeronauticsdiscovery.child;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

public final class ChildSubLevelManager {

    public static final String PARENT_SUBLEVEL_ID_TAG = "parent_sublevel_id";
    public static final String CHILD_ROLE_TAG = "child_role";

    private ChildSubLevelManager() {}

    public static void tagAs(ServerSubLevel child, ChildRole role, UUID parentId) {
        CompoundTag tag = child.getUserDataTag();
        if (tag == null) {
            tag = new CompoundTag();
            child.setUserDataTag(tag);
        }
        tag.putUUID(PARENT_SUBLEVEL_ID_TAG, parentId);
        tag.putString(CHILD_ROLE_TAG, role.key());
    }

    public static ChildRole getRole(ServerSubLevel child) {
        CompoundTag tag = child.getUserDataTag();
        if (tag == null || !tag.contains(CHILD_ROLE_TAG)) return ChildRole.FRAGMENT;
        return ChildRole.fromKey(tag.getString(CHILD_ROLE_TAG));
    }

    public static List<ServerSubLevel> getChildSubLevels(SubLevelContainer container, UUID parentId) {
        return container.getAllSubLevels().stream()
                .filter(sl -> sl instanceof ServerSubLevel)
                .map(sl -> (ServerSubLevel) sl)
                .filter(sl -> {
                    CompoundTag tag = sl.getUserDataTag();
                    return tag != null && tag.hasUUID(PARENT_SUBLEVEL_ID_TAG)
                            && tag.getUUID(PARENT_SUBLEVEL_ID_TAG).equals(parentId);
                })
                .toList();
    }

    public static void cleanupChildren(ServerSubLevelContainer container, UUID parentId, ServerLevel level) {
        new ChildContext(container, level).cleanupChildrenOf(parentId);
    }
}
