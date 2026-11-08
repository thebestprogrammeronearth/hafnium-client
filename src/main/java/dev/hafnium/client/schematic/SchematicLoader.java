package dev.hafnium.client.schematic;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads the three common schematic formats: vanilla structure files (.nbt, structure blocks / Create), Sponge
 * schematics (.schem v2 and v3, WorldEdit / FAWE) and Litematica (.litematic, every region). Air is dropped.
 */
public final class SchematicLoader {
    /** Refuse absurd files instead of freezing the game. */
    public static final int MAX_BLOCKS = 2_000_000;

    private SchematicLoader() {
    }

    public static Schematic load(Path path) throws IOException {
        if (Files.size(path) > 64L * 1024 * 1024) {
            throw new IOException("File is larger than 64 MB");
        }
        CompoundTag root;
        try (InputStream in = Files.newInputStream(path)) {
            root = NbtIo.readCompressed(in, NbtAccounter.create(512L * 1024 * 1024));
        }
        String file = path.getFileName().toString();
        String lower = file.toLowerCase(Locale.ROOT);
        String name = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
        if (lower.endsWith(".litematic") || root.contains("Regions")) {
            return litematic(name, root);
        }
        if (lower.endsWith(".schem") || lower.endsWith(".schematic") || root.contains("Schematic") || root.contains("BlockData")) {
            return sponge(name, root.getCompound("Schematic").orElse(root));
        }
        return structure(name, root);
    }

    private static final class Builder {
        final List<int[]> pos = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        void add(int x, int y, int z, BlockState state) throws IOException {
            if (state == null || state.isAir()) {
                return;
            }
            if (states.size() >= MAX_BLOCKS) {
                throw new IOException("More than " + MAX_BLOCKS + " blocks");
            }
            pos.add(new int[]{x, y, z});
            states.add(state);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        Schematic build(String name) throws IOException {
            if (states.isEmpty()) {
                throw new IOException("The schematic has no blocks");
            }
            int n = states.size();
            int[] xs = new int[n], ys = new int[n], zs = new int[n];
            for (int i = 0; i < n; i++) {
                int[] p = pos.get(i);
                xs[i] = p[0] - minX;
                ys[i] = p[1] - minY;
                zs[i] = p[2] - minZ;
            }
            return new Schematic(name, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, xs, ys, zs, states.toArray(new BlockState[0]));
        }
    }

    // ------------------------------------------------------------------ vanilla structure (.nbt)

    private static Schematic structure(String name, CompoundTag root) throws IOException {
        ListTag paletteTag = root.getListOrEmpty("palette");
        if (paletteTag.isEmpty()) {
            paletteTag = root.getListOrEmpty("palettes").getListOrEmpty(0);
        }
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, paletteTag.getCompoundOrEmpty(i));
        }
        ListTag blocks = root.getListOrEmpty("blocks");
        if (palette.length == 0 || blocks.isEmpty()) {
            throw new IOException("Not a structure file (no palette or blocks)");
        }
        Builder b = new Builder();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompoundOrEmpty(i);
            ListTag pos = block.getListOrEmpty("pos");
            int state = block.getIntOr("state", -1);
            if (pos.size() == 3 && state >= 0 && state < palette.length) {
                b.add(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0), palette[state]);
            }
        }
        return b.build(name);
    }

    // ------------------------------------------------------------------ Sponge (.schem v2 / v3)

    private static Schematic sponge(String name, CompoundTag root) throws IOException {
        int width = root.getShortOr("Width", (short) 0) & 0xFFFF;
        int height = root.getShortOr("Height", (short) 0) & 0xFFFF;
        int length = root.getShortOr("Length", (short) 0) & 0xFFFF;
        CompoundTag blocks = root.getCompound("Blocks").orElse(null); // v3
        CompoundTag paletteTag = blocks != null ? blocks.getCompoundOrEmpty("Palette") : root.getCompoundOrEmpty("Palette");
        byte[] data = (blocks != null ? blocks.getByteArray("Data") : root.getByteArray("BlockData")).orElse(new byte[0]);
        if (width == 0 || paletteTag.isEmpty() || data.length == 0) {
            throw new IOException("Not a Sponge schematic (missing size, palette or block data)");
        }
        BlockState[] palette = new BlockState[paletteTag.size() + 1];
        for (String key : paletteTag.keySet()) {
            int id = paletteTag.getIntOr(key, -1);
            if (id < 0) {
                continue;
            }
            if (id >= palette.length) {
                palette = java.util.Arrays.copyOf(palette, id + 1);
            }
            palette[id] = parseState(key);
        }
        Builder b = new Builder();
        int index = 0;
        int i = 0;
        while (i < data.length) {
            int value = 0, shift = 0;
            byte cur;
            do {
                cur = data[i++];
                value |= (cur & 0x7F) << shift;
                shift += 7;
            } while ((cur & 0x80) != 0 && i < data.length);
            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);
            if (y < height && value < palette.length) {
                b.add(x, y, z, palette[value]);
            }
            index++;
        }
        return b.build(name);
    }

    private static BlockState parseState(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false).blockState();
        } catch (CommandSyntaxException e) {
            return Blocks.AIR.defaultBlockState(); // unknown block (other version / mod): skipped
        }
    }

    // ------------------------------------------------------------------ Litematica (.litematic)

    private static Schematic litematic(String name, CompoundTag root) throws IOException {
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        if (regions.isEmpty()) {
            throw new IOException("Litematic has no regions");
        }
        Builder b = new Builder();
        for (String key : regions.keySet()) {
            CompoundTag region = regions.getCompoundOrEmpty(key);
            CompoundTag pos = region.getCompoundOrEmpty("Position");
            CompoundTag size = region.getCompoundOrEmpty("Size");
            int px = pos.getIntOr("x", 0), py = pos.getIntOr("y", 0), pz = pos.getIntOr("z", 0);
            int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
            // negative sizes extend towards negative coordinates from the position
            int ox = sx < 0 ? px + sx + 1 : px, oy = sy < 0 ? py + sy + 1 : py, oz = sz < 0 ? pz + sz + 1 : pz;
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            ListTag paletteTag = region.getListOrEmpty("BlockStatePalette");
            BlockState[] palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, paletteTag.getCompoundOrEmpty(i));
            }
            long[] packed = region.getLongArray("BlockStates").orElse(new long[0]);
            if (palette.length == 0 || packed.length == 0) {
                continue;
            }
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
            long mask = (1L << bits) - 1;
            long volume = (long) ax * ay * az;
            if (volume > MAX_BLOCKS * 4L) {
                throw new IOException("Region " + key + " is too large");
            }
            for (long index = 0; index < volume; index++) {
                long bit = index * bits;
                int word = (int) (bit >>> 6);
                int offset = (int) (bit & 63);
                if (word >= packed.length) {
                    break;
                }
                long value = packed[word] >>> offset;
                if (offset + bits > 64 && word + 1 < packed.length) {
                    value |= packed[word + 1] << (64 - offset);
                }
                int id = (int) (value & mask);
                if (id <= 0 || id >= palette.length) {
                    continue;
                }
                int x = (int) (index % ax);
                int z = (int) ((index / ax) % az);
                int y = (int) (index / ((long) ax * az));
                b.add(ox + x, oy + y, oz + z, palette[id]);
            }
        }
        return b.build(name);
    }
}
