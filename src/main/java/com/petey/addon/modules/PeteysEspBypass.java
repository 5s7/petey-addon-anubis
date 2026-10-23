package com.petey.addon.modules;

import com.petey.addon.PeteyAddon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.anubis.client.esp.BlockLayer;
import net.anubis.client.esp.ChunkScanner;
import net.anubis.client.esp.EspStyle;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.MobSpawnerBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.registry.RegistryKey;

import java.util.function.LongPredicate;

/**
 * PeteysEspBypass — Storage ESP from chunk block states.
 *
 * Sees chests out to the full view distance, past the server's 11-chunk
 * block-entity cutoff. Air-exposed blocks only, so anti-xray fakes are
 * naturally skipped. Found blocks stay visible until the player moves far
 * away. Made by Petey.
 *
 * Intermediary → yarn class map used in this file:
 *   class_310          → MinecraftClient
 *   class_638          → ClientWorld
 *   class_2338         → BlockPos
 *   class_2338$class_2339 → BlockPos.Mutable
 *   class_1923         → ChunkPos
 *   class_2680         → BlockState
 *   class_2248         → Block
 *   class_2281         → ChestBlock
 *   class_2336         → EnderChestBlock
 *   class_2363         → FurnaceBlock
 *   class_2377         → HopperBlock
 *   class_2480         → ShulkerBoxBlock
 *   class_2496         → MobSpawnerBlock
 *   class_3708         → BarrelBlock
 *   class_2586         → BlockEntity
 *   class_4739         → ChestBlockEntity
 *   class_2818         → WorldChunk
 *   class_5321         → RegistryKey (dimension)
 *
 * Method map (selected):
 *   method_1551()                 → MinecraftClient.getInstance()
 *   field_1687                    → MinecraftClient.world
 *   field_1724                    → MinecraftClient.player
 *   method_27983()                → World.getRegistryKey()
 *   method_31600()                → World.getTopY()
 *   method_31607()                → World.getBottomY()
 *   method_2935()                 → World.getChunkManager()
 *   method_21730(cx,cz)          → ClientChunkManager.getWorldChunk(cx,cz)
 *   method_8320(pos)             → BlockView.getBlockState(pos)
 *   method_8321(pos)             → WorldChunk.getBlockEntity(pos)
 *   method_12201(pos,type)       → WorldChunk.getBlockEntityNbt(pos,type)
 *   method_26215()               → BlockState.isAir()
 *   method_26204()               → BlockState.getBlock()
 *   method_11010()               → BlockEntity.getCachedState()
 *   method_16363(packed)         → BlockPos.Mutable.set(long)
 *   method_10103(x,y,z)         → BlockPos.Mutable.set(int,int,int)
 *   method_10063()               → BlockPos.asLong()
 *   method_10062()               → BlockPos.Mutable (as BlockPos view)
 *   method_10263()               → BlockPos.getX()
 *   method_10264()               → BlockPos.getY()
 *   method_10260()               → BlockPos.getZ()
 *   method_10061(packed)         → BlockPos.unpackLongX(long)
 *   method_10071(packed)         → BlockPos.unpackLongY(long)
 *   method_10083(packed)         → BlockPos.unpackLongZ(long)
 *   method_8324()                → ChunkPos.toLong()
 *   method_8325(packed)         → ChunkPos.getPackedX(long)
 *   method_8332(packed)         → ChunkPos.getPackedZ(long)
 *   method_8331(x,z)            → ChunkPos.toLong(int,int)
 *   method_12004()               → Chunk.getPos()
 *   method_31477()               → Entity.getBlockX()
 *   method_31479()               → Entity.getBlockZ()
 *   field_1769                   → MinecraftClient.worldRenderer
 *   method_18146(...)            → WorldRenderer.updateBlock(...)
 *   class_2818.class_2819.field_12860 → WorldChunk.CreationType.IMMEDIATE
 */
public final class PeteysEspBypass extends ClientModule implements ChunkScanner.Consumer {

    // ── Constants ───────────────────────────────────────────────────────────
    private static final int MODELS_PER_TICK = 256;
    private static final int[][] DIRS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    // ── Singleton ────────────────────────────────────────────────────────────
    private static PeteysEspBypass instance;

    // ── State ────────────────────────────────────────────────────────────────
    /** packed BlockPos → storage type for every tracked block */
    private final Long2ObjectOpenHashMap<Type> found    = new Long2ObjectOpenHashMap<>();
    /** packed ChunkPos → set of packed BlockPos in that chunk */
    private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk  = new Long2ObjectOpenHashMap<>();
    /** blocks waiting for their neighbouring chunk to load before exposure check */
    private final Long2ObjectOpenHashMap<LongOpenHashSet> waiting  = new Long2ObjectOpenHashMap<>();
    /** queue of positions that need their block-entity model forcibly loaded */
    private final LongArrayFIFOQueue needModels = new LongArrayFIFOQueue();

    private final BlockPos.Mutable probe    = new BlockPos.Mutable();
    private Object                  lastDim = null;
    private int                     ticks   = 0;

    // ── Settings ─────────────────────────────────────────────────────────────
    private final EspStyle style = new EspStyle(
            (id, s) -> this.addSetting(id, s), "Outline", 0.25f, 512.0f);

    private final BooleanSetting tracers = (BooleanSetting) this.addSetting(
            "tracers", (Setting) new BooleanSetting("Tracers", false));

    private final NumberSetting forgetDistance = (NumberSetting) this.addSetting(
            "forget-distance", (Setting) new NumberSetting("Forget Distance", 30f, 1f, 128f).step(1f));

    // ── Storage types ────────────────────────────────────────────────────────
    private final Type chest      = new Type(this, "Chest",        -24576,    "chests",       "chest-color");
    private final Type barrel     = new Type(this, "Barrel",       -24576,    "barrels",      "barrel-color");
    private final Type enderChest = new Type(this, "Ender Chest",  -8912641,  "ender-chests", "ender-chest-color");
    private final Type shulker    = new Type(this, "Shulker Box",  -49976,    "shulker-boxes","shulker-box-color");
    private final Type spawner    = new Type(this, "Spawner",      -57826,    "spawners",     "spawner-color");
    private final Type furnace    = new Type(this, "Furnace",      -7566196,  "furnaces",     "furnace-color");
    private final Type hopper     = new Type(this, "Hopper",       -7566196,  "hoppers",      "hopper-color");

    // ── Rendering layer ──────────────────────────────────────────────────────
    private final BlockLayer layer = new BlockLayer(
            new BlockLayer.Palette() {
                @Override public int color(Object kind)       { return ((Type) kind).color.argb(); }
                @Override public int tracerColor(Object kind) {
                    return tracers.get() ? ((Type) kind).color.argb() : 0;
                }
            },
            () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                return mc.world == null ? null : mc.world.getRegistryKey();
            }
    );

    // ── Constructor ──────────────────────────────────────────────────────────
    public PeteysEspBypass() {
        super("peteys-esp-bypass", "Petey's Esp Bypass",
                "Storage ESP from chunk block states: sees chests out to your full view distance, " +
                "past the server's 11-chunk block-entity cut. Air-exposed only, so anti-xray fakes " +
                "are skipped; found blocks stay until you're far away. Made by Petey.",
                PeteyAddon.CATEGORY);
        instance = this;

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (this.isEnabled()) {
                this.chunkArrived(chunk.getPos().toLong());
            }
        });
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onEnable() {
        this.lastDim = null;
        this.ticks   = 0;
        ChunkScanner.register(this);
    }

    @Override
    protected void onDisable() {
        ChunkScanner.unregister(this);
        this.clearAll();
    }

    private void clearAll() {
        this.layer.clear();
        this.found.clear();
        this.byChunk.clear();
        this.waiting.clear();
        this.needModels.clear();
    }

    // ── ChunkScanner.Consumer ────────────────────────────────────────────────
    @Override
    public boolean wants(BlockState state) {
        Type t = this.typeOf(state.getBlock());
        return t != null && t.enabled.get();
    }

    @Override
    public void found(long pos, BlockState state) {
        if (this.found.containsKey(pos) || this.exposed(pos)) {
            this.add(pos, state.getBlock());
        }
    }

    @Override
    public void removed(long pos) {
        this.drop(pos);
    }

    @Override
    public void reset() {
        // intentionally empty — ChunkScanner calls this when it resets its own state
    }

    @Override
    public void chunkScanned(long chunkKey, LongPredicate stillThere) {
        LongOpenHashSet set = this.byChunk.get(chunkKey);
        ClientWorld level = MinecraftClient.getInstance().world;
        if (set == null || level == null) return;

        for (long pos : set.toLongArray()) {
            if (!level.getBlockState(this.probe.set(pos)).isAir()) continue;
            this.drop(pos);
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────
    /** @return true if any adjacent face of {@code pos} is exposed to air or missing chunk */
    private boolean exposed(long pos) {
        ClientWorld level = MinecraftClient.getInstance().world;
        if (level == null) return false;

        int x = BlockPos.unpackLongX(pos);
        int y = BlockPos.unpackLongY(pos);
        int z = BlockPos.unpackLongZ(pos);

        for (int[] d : DIRS) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];

            if (ny > level.getTopY()) return true;
            if (ny < level.getBottomY()) continue;

            WorldChunk c = level.getChunkManager().getWorldChunk(nx >> 4, nz >> 4);
            if (c == null) {
                // chunk not loaded — defer until it arrives
                this.waiting
                    .computeIfAbsent(ChunkPos.toLong(nx >> 4, nz >> 4), k -> new LongOpenHashSet())
                    .add(pos);
                continue;
            }
            if (!c.getBlockState(this.probe.set(nx, ny, nz)).isAir()) continue;
            return true;
        }
        return false;
    }

    /** Called when a chunk loads; re-evaluate positions that were waiting on it. */
    private void chunkArrived(long chunkKey) {
        LongOpenHashSet set = this.waiting.remove(chunkKey);
        ClientWorld level = MinecraftClient.getInstance().world;
        if (set == null || level == null) return;

        BlockPos.Mutable p = new BlockPos.Mutable();
        for (long pos : set.toLongArray()) {
            BlockState s = level.getBlockState(p.set(pos));
            if (!this.wants(s)) continue;
            this.found(pos, s);
        }
    }

    private void add(long pos, Block block) {
        Type t = this.typeOf(block);
        if (t == null) return;
        this.found.put(pos, t);
        this.byChunk.computeIfAbsent(chunkOf(pos), k -> new LongOpenHashSet()).add(pos);
        this.layer.put(pos, t);
        if (needsModel(block)) this.needModels.enqueue(pos);
    }

    private void drop(long pos) {
        if (this.found.remove(pos) == null) return;
        long key = chunkOf(pos);
        LongOpenHashSet set = this.byChunk.get(key);
        if (set != null && set.remove(pos) && set.isEmpty()) this.byChunk.remove(key);
        this.layer.remove(pos);
    }

    private static long chunkOf(long pos) {
        return ChunkPos.toLong(BlockPos.unpackLongX(pos) >> 4, BlockPos.unpackLongZ(pos) >> 4);
    }

    private static boolean far(long chunkKey, int pcx, int pcz, int r) {
        return Math.max(
            Math.abs(ChunkPos.getPackedX(chunkKey) - pcx),
            Math.abs(ChunkPos.getPackedZ(chunkKey) - pcz)
        ) > r;
    }

    private void forgetFar(int pcx, int pcz) {
        int r = this.forgetDistance.getInt();
        for (long key : this.byChunk.keySet().toLongArray()) {
            if (!far(key, pcx, pcz, r)) continue;
            for (long pos : this.byChunk.get(key).toLongArray()) this.drop(pos);
        }
        for (long key : this.waiting.keySet().toLongArray()) {
            if (!far(key, pcx, pcz, r)) continue;
            this.waiting.remove(key);
        }
    }

    private void typesChanged() {
        if (!this.isEnabled()) return;
        for (long pos : this.found.keySet().toLongArray()) {
            if (this.found.get(pos).enabled.get()) continue;
            this.drop(pos);
        }
        ChunkScanner.rescan();
    }

    // ── Event handlers ───────────────────────────────────────────────────────
    @EventHandler
    public void onTick(TickEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        RegistryKey<?> dim = mc.world.getRegistryKey();
        if (this.lastDim != null && !this.lastDim.equals(dim)) this.clearAll();
        this.lastDim = dim;

        if (++this.ticks % 20 == 0) {
            this.forgetFar(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4);
        }
        this.addModels(mc);
    }

    @EventHandler
    public void onBlockUpdate(BlockUpdateEvent event) {
        if (event.newState == null || !event.newState.isAir()) return;
        ClientWorld level = MinecraftClient.getInstance().world;
        if (level == null) return;

        BlockPos.Mutable n = new BlockPos.Mutable();
        for (int[] d : DIRS) {
            n.set(event.pos.getX() + d[0], event.pos.getY() + d[1], event.pos.getZ() + d[2]);
            BlockState s = level.getBlockState(n);
            if (!this.wants(s)) continue;
            this.add(n.asLong(), s.getBlock());
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        this.layer.render(event, this.style);
        if (this.tracers.get()) this.layer.renderTracers(this.style);
    }

    // ── Model loading ─────────────────────────────────────────────────────────
    private void addModels(MinecraftClient mc) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int i = 0; i < MODELS_PER_TICK && !this.needModels.isEmpty(); i++) {
            long pos = this.needModels.dequeueLong();
            WorldChunk c = mc.world.getChunkManager().getWorldChunk(
                    BlockPos.unpackLongX(pos) >> 4, BlockPos.unpackLongZ(pos) >> 4);
            if (c == null || !this.found.containsKey(pos)) continue;
            p.set(pos);
            // Only push a model update when: block still needs one, no entity yet, and nbt exists
            if (!needsModel(c.getBlockState(p).getBlock())
                    || c.getBlockEntity(p) != null
                    || c.getBlockEntityNbt(p.toImmutable(), WorldChunk.CreationType.IMMEDIATE) == null) {
                continue;
            }
            mc.worldRenderer.updateBlock(mc.world, p, null, null, 0);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    /** True for block types whose renderer requires a block-entity model. */
    private static boolean needsModel(Block b) {
        return b instanceof ChestBlockEntity || b instanceof ShulkerBoxBlock;
    }

    /**
     * Called from {@link com.petey.addon.mixin.BlockEntityRenderDispatcherMixin} to decide
     * whether a block entity that vanilla considers too far should still render.
     */
    public static boolean rendersFar(BlockEntity be) {
        PeteysEspBypass m = instance;
        return m != null && m.isEnabled()
                && needsModel(be.getCachedState().getBlock());
    }

    private Type typeOf(Block b) {
        if (b instanceof EnderChestBlock) return this.enderChest;
        if (b instanceof ChestBlock)      return this.chest;
        if (b instanceof BarrelBlock)     return this.barrel;
        if (b instanceof ShulkerBoxBlock) return this.shulker;
        if (b instanceof MobSpawnerBlock) return this.spawner;
        if (b instanceof FurnaceBlock)    return this.furnace;
        if (b instanceof HopperBlock)     return this.hopper;
        return null;
    }

    // ── Inner type record ────────────────────────────────────────────────────
    private final class Type {
        final BooleanSetting enabled;
        final ColorSetting   color;

        Type(PeteysEspBypass owner, String label, int argb, String idOn, String idColor) {
            this.enabled = (BooleanSetting) owner.addSetting(idOn,    (Setting) new BooleanSetting(label, true));
            this.color   = (ColorSetting)   owner.addSetting(idColor, (Setting) new ColorSetting(label + " Color", argb));
            this.color.visibleWhen(() -> this.enabled.get());
            this.enabled.onChange(owner::typesChanged);
        }
    }
}
