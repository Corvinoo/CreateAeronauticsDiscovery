package me.corvino.aeronauticsdiscovery.assembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PrefabService {
    private PrefabService() {}

    public static StructureTemplate loadPrefab(ServerLevel level, ResourceLocation id) {
        return level.getServer()
                .getStructureManager()
                .get(id)
                .orElseThrow(() -> new IllegalStateException("Missing structure template: " + id));
    }

    public static StructureTemplate loadPrefab(MinecraftServer server, Path input) throws IOException {
        CompoundTag tag;
        try (InputStream in = Files.newInputStream(input)) {
            tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        return server.getStructureManager().readStructure(tag);
    }
}
