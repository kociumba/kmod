package org.kociumba.kmod.mixin.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import java.util.function.Supplier;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import static org.kociumba.kmod.client.KmodClientKt.getC;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin extends World {

    @Unique
    private boolean shouldChangeTime = getC().getShouldChangeTime();

    protected ClientWorldMixin(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, Supplier<Profiler> profiler, boolean isClient, boolean debugWorld, long biomeAccess, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, profiler, isClient, debugWorld, biomeAccess, maxChainedNeighborUpdates);
    }
    @Override
    public float getRainGradient(float delta) {
        if (shouldChangeTime) {
            return 0f;
        }
        return super.getRainGradient(delta);
    }

    @Override
    public float getThunderGradient(float delta) {
        if (shouldChangeTime) {
            return 0f;
        }
        return super.getRainGradient(delta);
    }
}