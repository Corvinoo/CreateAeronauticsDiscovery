package me.corvino.aeronauticsdiscovery.child;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ChildSubLevelManager {

    public static final String PARENT_SUBLEVEL_ID_TAG = "parent_sublevel_id";
    public static final String CHILD_ROLE_TAG = "child_role";

    private static final int MAX_DEPTH = 64;

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

    public static void tagAs(ServerSubLevel child, ChildRole role) {
        CompoundTag tag = child.getUserDataTag();
        if (tag == null || !tag.hasUUID(PARENT_SUBLEVEL_ID_TAG)) return;
        tag.putString(CHILD_ROLE_TAG, role.key());
    }

    public static ChildRole getRole(ServerSubLevel child) {
        CompoundTag tag = child.getUserDataTag();
        if (tag == null || !tag.contains(CHILD_ROLE_TAG)) return ChildRole.TRANSIENT;
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

    public static boolean isDescendantOf(SubLevelContainer container, ServerSubLevel subLevel, UUID ancestorId) {
        ServerSubLevel cursor = subLevel;
        for (int depth = 0; depth < MAX_DEPTH && cursor != null; depth++) {
            CompoundTag tag = cursor.getUserDataTag();
            if (tag == null || !tag.hasUUID(PARENT_SUBLEVEL_ID_TAG)) return false;
            UUID parentId = tag.getUUID(PARENT_SUBLEVEL_ID_TAG);
            if (parentId.equals(ancestorId)) return true;
            cursor = container.getSubLevel(parentId) instanceof ServerSubLevel parent ? parent : null;
        }
        return false;
    }

    public static List<ServerSubLevel> getDescendants(SubLevelContainer container, UUID rootId) {
        List<ServerSubLevel> descendants = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(rootId);
        Deque<UUID> frontier = new ArrayDeque<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            for (ServerSubLevel child : getChildSubLevels(container, frontier.poll())) {
                if (!visited.add(child.getUniqueId())) continue;
                descendants.add(child);
                frontier.add(child.getUniqueId());
            }
        }
        return descendants;
    }

    public static void clearChildTags(ServerSubLevel child) {
        CompoundTag tag = child.getUserDataTag();
        if (tag == null) return;
        tag.remove(PARENT_SUBLEVEL_ID_TAG);
        tag.remove(CHILD_ROLE_TAG);
    }

    public static void cleanupChildren(ServerSubLevelContainer container, UUID parentId, ServerLevel level) {
        new ChildContext(container, level).cleanupChildrenOf(parentId);
    }
}
