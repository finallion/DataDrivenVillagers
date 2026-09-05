package com.lion.datadrivenvillagers.client;

import com.lion.datadrivenvillagers.ConfigFiles;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.PngHeader;
import com.lion.datadrivenvillagers.TexturedDefinition;
import com.lion.datadrivenvillagers.network.SyncedLooks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Registers textures that are not in a resource pack (a png next to the json, or bytes a server
/// sent) in the texture manager under the id vanilla derives from the registry id, which keeps our
/// namespace. Lazy, on the first lookup from the clothing renderer: a client initializer runs inside
/// the MinecraftClient constructor, before the texture manager exists.
public final class RuntimeTextures {

    /// Result per id, so an unreadable file is logged once and not retried every frame.
    private static final Map<Identifier, Boolean> HANDLED = new HashMap<>();

    /// Every id registered here, so leaving a world can hand the images back to the GPU.
    private static final Set<Identifier> REGISTERED = new HashSet<>();

    /// Generations the cache was built for. Compared on the render thread; the reload on the server
    /// thread never touches this map.
    private static int generation = -1;
    private static int syncedGeneration = -1;

    private RuntimeTextures() {}

    /// A png beside the json, for one entity type.
    ///
    /// @param folder the definition kind's folder; the file name in the json is relative to it
    /// @return the id for the renderer; empty when no file is named or it could not be read (caller
    ///         falls through to vanilla)
    public static Optional<Identifier> fromFile(TexturedDefinition definition, Path folder, String entityType) {
        Optional<String> file = definition.textureFileFor(entityType);

        if (file.isEmpty()) {
            return Optional.empty();
        }

        // The parser holds the name to a plain file name; this is the second lock on the same door.
        Optional<Path> png = ConfigFiles.resolveInside(folder, file.get());
        Identifier id = definition.vanillaTextureId(entityType);

        return ensure(id, () -> {
            if (png.isEmpty()) {
                throw new IOException("\"" + file.get() + "\" is not a file name inside " + folder);
            }
            if (!Files.isRegularFile(png.get())) {
                throw new IOException("no such file");
            }
            return Files.newInputStream(png.get());
        }, png.map(Path::toString).orElse(file.get()) + " for " + definition.id()) ? Optional.of(id) : Optional.empty();
    }

    /// Bytes a server sent, for one entity type.
    public static Optional<Identifier> fromBytes(SyncedLooks.Look look, String entityType) {
        Optional<byte[]> png = look.pngFor(entityType);

        if (png.isEmpty()) {
            return Optional.empty();
        }

        Identifier id = look.textureId(entityType);
        return ensure(id, () -> new ByteArrayInputStream(png.get()), "the image the server sent for " + look.definition()) ? Optional.of(id) : Optional.empty();
    }

    /// Gives every image registered here back to the GPU and forgets what was tried. Called when the
    /// client leaves a world, from whatever thread the loader fires that on; the work is queued to the
    /// render thread, where the texture manager lives.
    public static void destroyAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            TextureManager textures = client.getTextureManager();
            for (Identifier id : REGISTERED) {
                textures.destroyTexture(id);
            }
            REGISTERED.clear();
            HANDLED.clear();
        });
    }

    private interface Source {
        InputStream open() throws IOException;
    }

    private static boolean ensure(Identifier id, Source source, String what) {
        if (generation != DataDrivenVillagers.generation() || syncedGeneration != SyncedLooks.generation()) {
            generation = DataDrivenVillagers.generation();
            syncedGeneration = SyncedLooks.generation();
            HANDLED.clear();
        }

        Boolean known = HANDLED.get(id);
        if (known != null) {
            return known;
        }

        boolean registered = register(id, source, what);
        HANDLED.put(id, registered);
        return registered;
    }

    private static boolean register(Identifier id, Source source, String what) {
        try (InputStream in = new BufferedInputStream(source.open())) {
            // The header first: the decoder allocates width times height before anyone can look, and
            // a server is not trusted with the native heap. The same check refuses the client's own
            // file, so a png that works alone works on a server too.
            in.mark(PngHeader.LENGTH * 2);
            byte[] head = in.readNBytes(PngHeader.LENGTH);
            in.reset();
            Optional<String> rejected = PngHeader.rejection(head);
            if (rejected.isPresent()) {
                DataDrivenVillagers.LOGGER.error("Refusing texture {}: {}", what, rejected.get());
                return false;
            }

            NativeImage image = NativeImage.read(in);
            TextureManager textures = MinecraftClient.getInstance().getTextureManager();
            // Registering over an existing id would leak the old image on the GPU after every reload.
            textures.destroyTexture(id);
            textures.registerTexture(id, new NativeImageBackedTexture(image));
            REGISTERED.add(id);
            return true;
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not read texture {}: {}", what, e.getMessage());
            return false;
        }
    }
}
