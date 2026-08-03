package me.corvino.aeronauticsdiscovery.autopilot;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Actuates an {@link AutopilotBias} on a Create contraption through redstone-link transmitters.
 * Compares the craft's measured pitch/roll against the autopilot's desired offsets (with
 * hysteresis) and activates the matching stabilizer channels.
 * <p>
 * Channel assignments: {@code pitchUpWool} = nose up, {@code pitchDownWool} = nose down,
 * {@code rollRightWool} = bank right, {@code rollLeftWool} = bank left.
 */
public final class RedstoneStabilizer {

    private static final double PITCH_THRESHOLD_ON = Math.toRadians(9);
    private static final double PITCH_THRESHOLD_OFF = Math.toRadians(7);
    private static final double ROLL_THRESHOLD_ON = Math.toRadians(1);
    private static final double ROLL_THRESHOLD_OFF = Math.toRadians(0);

    private final StabilizerTransmitter pitchUpSignal;
    private final StabilizerTransmitter pitchDownSignal;
    private final StabilizerTransmitter rollRightSignal;
    private final StabilizerTransmitter rollLeftSignal;
    private boolean linksRegistered = false;

    public RedstoneStabilizer(Mob owner, Item pitchUpWool, Item pitchDownWool, Item rollRightWool, Item rollLeftWool) {
        this.pitchUpSignal = new StabilizerTransmitter(owner, pitchUpWool);
        this.pitchDownSignal = new StabilizerTransmitter(owner, pitchDownWool);
        this.rollRightSignal = new StabilizerTransmitter(owner, rollRightWool);
        this.rollLeftSignal = new StabilizerTransmitter(owner, rollLeftWool);
    }

    private StabilizerTransmitter[] allTransmitters() {
        return new StabilizerTransmitter[]{pitchUpSignal, pitchDownSignal, rollRightSignal, rollLeftSignal};
    }

    private void registerLinksIfNeeded(ServerLevel serverLevel) {
        if (linksRegistered) return;
        for (StabilizerTransmitter t : allTransmitters())
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, t);
        linksRegistered = true;
    }

    public void unregisterLinks(ServerLevel serverLevel) {
        if (!linksRegistered) return;
        for (StabilizerTransmitter t : allTransmitters())
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(serverLevel, t);
        linksRegistered = false;
    }

    private void setActive(ServerLevel serverLevel, StabilizerTransmitter t, boolean shouldBeActive) {
        if (t.isActive() == shouldBeActive) return;
        t.setActive(shouldBeActive);
        Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(serverLevel, t);
    }

    public void setAllInactive(ServerLevel serverLevel) {
        for (StabilizerTransmitter t : allTransmitters())
            setActive(serverLevel, t, false);
    }

    private static boolean hysteresis(boolean currentlyActive, double value, double onThreshold, double offThreshold) {
        return currentlyActive ? value > offThreshold : value > onThreshold;
    }

    /**
     * Drive the stabilizer channels for one tick given the measured attitude and the autopilot's
     * desired offsets. {@link AutopilotContext#of} guarantees the craft is inside a sub-level.
     */
    public void tick(ServerLevel serverLevel, AutopilotContext context, AutopilotBias bias) {
        registerLinksIfNeeded(serverLevel);

        double adjustedPitch = context.pitch() - bias.pitch();
        double adjustedRoll = context.roll() - bias.roll();

        boolean pitchTooLow = hysteresis(pitchUpSignal.isActive(), -adjustedPitch, PITCH_THRESHOLD_ON, PITCH_THRESHOLD_OFF);
        boolean pitchTooHigh = hysteresis(pitchDownSignal.isActive(), adjustedPitch, PITCH_THRESHOLD_ON, PITCH_THRESHOLD_OFF);
        boolean rollTooLeft = hysteresis(rollRightSignal.isActive(), -adjustedRoll, ROLL_THRESHOLD_ON, ROLL_THRESHOLD_OFF);
        boolean rollTooRight = hysteresis(rollLeftSignal.isActive(), adjustedRoll, ROLL_THRESHOLD_ON, ROLL_THRESHOLD_OFF);

        setActive(serverLevel, pitchUpSignal, pitchTooLow);
        setActive(serverLevel, pitchDownSignal, pitchTooHigh);
        setActive(serverLevel, rollRightSignal, rollTooLeft);
        setActive(serverLevel, rollLeftSignal, rollTooRight);
    }

    private static final class StabilizerTransmitter implements IRedstoneLinkable {
        private final Mob owner;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private boolean active = false;

        StabilizerTransmitter(Mob owner, Item woolItem) {
            this.owner = owner;
            RedstoneLinkNetworkHandler.Frequency freq = RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(woolItem));
            this.key = Couple.create(freq, freq);
        }

        boolean isActive() {
            return active;
        }

        void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public int getTransmittedStrength() {
            return active ? 15 : 0;
        }

        @Override
        public void setReceivedStrength(int networkPower) {
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public boolean isAlive() {
            return owner.isAlive() && !owner.isRemoved() && owner.level() != null;
        }

        @Override
        public BlockPos getLocation() {
            return owner.blockPosition();
        }
    }
}
