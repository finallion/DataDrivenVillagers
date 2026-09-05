package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.structure.PlotGenerator;
import com.lion.datadrivenvillagers.structure.StructureDefinition;
import com.lion.datadrivenvillagers.structure.StructureLoader;
import com.lion.datadrivenvillagers.structure.StructureRegistry;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/// Serves structure templates from the config folder (an author's nbt) or from {@link PlotGenerator}
/// (a generated plot) by answering `loadTemplate`. Hooking the loading method rather than the lookup
/// keeps the pool element a plain identifier and lets vanilla's template cache wrap the result.
@Mixin(StructureTemplateManager.class)
public abstract class StructureTemplateManagerMixin {

    /// Vanilla's private reader; runs the data fixers, so an nbt saved by an older Minecraft loads.
    @Shadow
    private StructureTemplate readTemplate(InputStream stream) throws IOException {
        throw new AssertionError();
    }

    /// Same reader for in-memory nbt, used for generated plots.
    @Shadow
    public abstract StructureTemplate createTemplate(NbtCompound nbt);

    @Inject(method = "loadTemplate(Lnet/minecraft/util/Identifier;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private void datadrivenvillagers$loadFromConfigFolder(Identifier id,
                                                          CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        Optional<StructureRegistry.GeneratedTemplate> generated = StructureRegistry.generated(id);
        if (generated.isPresent()) {
            Identifier workstation = generated.get().definition().workstation().orElseThrow();
            NbtCompound plot = PlotGenerator.plot(workstation, generated.get().village());
            cir.setReturnValue(Optional.of(createTemplate(plot)));
            return;
        }

        Optional<StructureDefinition> definition = StructureRegistry.get(id);
        if (definition.isEmpty() || definition.get().generated()) {
            return;
        }

        Path file = StructureLoader.fileOf(definition.get());
        if (!Files.isRegularFile(file)) {
            // The loader rejects a definition without its nbt, so the file vanished at runtime.
            DataDrivenVillagers.LOGGER.error("{} points at structure {}, which does not exist", id, file);
            return;
        }

        try (InputStream in = Files.newInputStream(file)) {
            cir.setReturnValue(Optional.of(readTemplate(in)));
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not read structure {} for {}", file, id, e);
        }
    }
}
