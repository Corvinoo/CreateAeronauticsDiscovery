package me.corvino.aeronauticsdiscovery;

import com.mojang.logging.LogUtils;
import me.corvino.aeronauticsdiscovery.benchmark.BenchmarkCommand;
import me.corvino.aeronauticsdiscovery.commands.DebugCommands;
import me.corvino.aeronauticsdiscovery.commands.PipelineDebugCommand;
import me.corvino.aeronauticsdiscovery.commands.PrefabCommands;
import me.corvino.aeronauticsdiscovery.entities.EntityRegistry;
import me.corvino.aeronauticsdiscovery.event.FlyoverCommands;
import me.corvino.aeronauticsdiscovery.event.FlyoverEventRegistry;
import me.corvino.aeronauticsdiscovery.event.FlyoverEventScheduler;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import me.corvino.aeronauticsdiscovery.client.renderer.SoaringTraderRenderer;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsRegistry;
import me.corvino.aeronauticsdiscovery.worldgen.ModWorldgen;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyQueue;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import static me.corvino.aeronauticsdiscovery.entities.EntityRegistry.ENTITIES;
import static me.corvino.aeronauticsdiscovery.items.ItemRegistry.ITEMS;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreateAeronauticsDiscovery.MODID)
public class CreateAeronauticsDiscovery {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "aeronauticsdiscovery";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "aeronauticsdiscovery" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "aeronauticsdiscovery" namespace
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "aeronauticsdiscovery" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    
    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CreateAeronauticsDiscovery(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITIES.register(modEventBus);
        ModWorldgen.STRUCTURE_TYPES.register(modEventBus);
        ModWorldgen.STRUCTURE_PIECE_TYPES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(AssemblyQueue::onLevelTick);
        NeoForge.EVENT_BUS.addListener(FlyoverEventScheduler::onLevelTick);
        NeoForge.EVENT_BUS.addListener(FlyoverManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(FlyoverCommands::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PrefabPhysicsRegistry::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(FlyoverEventRegistry::onAddReloadListeners);

        modEventBus.addListener(this::onTicketControllerRegister);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register entity attributes
        modEventBus.addListener(EntityRegistry::RegisterEntityAttributes);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    void onTicketControllerRegister(RegisterTicketControllersEvent event) {
        event.register(FlyoverManager.ticketController);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }
    

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PrefabCommands.register(event.getDispatcher());
        BenchmarkCommand.register(event.getDispatcher());
        FlyoverCommands.register(event.getDispatcher());
        DebugCommands.register(event.getDispatcher());
        PipelineDebugCommand.register(event.getDispatcher());
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegistry.SOARING_TRADER.get(), SoaringTraderRenderer::new);
        }
    }
}
