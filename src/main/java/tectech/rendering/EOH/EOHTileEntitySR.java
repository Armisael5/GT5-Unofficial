package tectech.rendering.EOH;

import static tectech.rendering.EOH.EOHRenderingUtils.renderOuterSpaceShell;

import java.awt.Color;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import gregtech.common.render.shader.RenderState;
import tectech.Reference;
import tectech.client.USSSparkFX;
import tectech.thing.block.TileEntityEyeOfHarmony;
import tectech.voidcraft.uss.USSConstants;
import tectech.voidcraft.uss.USSInfraBuild;
import tectech.voidcraft.uss.USSNovaExplosion;
import tectech.voidcraft.uss.USSStarRenderType;
import tectech.voidcraft.uss.USSStarType;
import tectech.voidcraft.uss.USSSupernovaExplosion;

public class EOHTileEntitySR extends TileEntitySpecialRenderer {

    public static final ResourceLocation STAR_LAYER_0 = new ResourceLocation(Reference.MODID, "models/StarLayer0.png");
    public static final ResourceLocation STAR_LAYER_1 = new ResourceLocation(Reference.MODID, "models/StarLayer1.png");
    public static final ResourceLocation STAR_LAYER_2 = new ResourceLocation(Reference.MODID, "models/StarLayer2.png");

    private static final float STAR_RESCALE = 0.2f;
    private static final float SPEED_SCALE = 0.1f; // keep your old tuning

    private static final Matrix4f eyeModel = new Matrix4f();

    /** The detonation burst's spark count (one radial spray of class-colored sparks when the shell launches). */
    public static final int PARTICLE_BURST_COUNT = 240;

    /**
     * The detonation burst's initial speed, × the star's radius: the sparks' 0.9-per-tick friction makes them
     * travel ~10× their initial speed, so this lands the spray a few star radii out.
     */
    public static final float PARTICLE_BURST_SPEED_FACTOR = 0.25f;

    /** The continuous sparks' per-tick emission while the shell is expanding. */
    public static final int PARTICLE_SPARKS_PER_TICK = 3;

    /** The continuous sparks' initial speed, × the star's radius. */
    public static final float PARTICLE_SPARK_SPEED_FACTOR = 0.1f;

    /** How far the spark colors mix toward white (the hot-core look over the class color). */
    public static final float PARTICLE_WHITE_MIX = 0.5f;

    private static final Random PARTICLE_RANDOM = new Random();

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {

        if (!(tile instanceof TileEntityEyeOfHarmony te)) return;

        World world = te.getWorldObj();
        if (world == null) return; // Just in-case

        // Smooth global animation clock: the world tick (partialTicks for sub-tick smoothness), EXCEPT when a
        // Voidcraft USS has synced its virtual orbit clock — then advance from the last sync at the normal rate
        // so the star/planet phases keep the server's clock (it only ever runs FASTER than the world during a
        // stellar-acceleration second, which the machine re-syncs every tick of).
        double time;
        if (te.getUssOrbitTime() > 0L) {
            long sinceSync = world.getTotalWorldTime() - te.getUssSyncedWorldTime();
            time = (double) (te.getUssOrbitTime() + Math.max(0L, sinceSync)) + partialTicks;
        } else {
            time = (double) world.getTotalWorldTime() + partialTicks;
        }
        // The star's own spin rides an independent rate on top of the clock above (see currentStarSpinTime).
        final double starSpinTime = te.currentStarSpinTime(time);

        eyeModel.translation((float) x + 0.5f, (float) y + 0.5f, (float) z + 0.5f);

        final boolean blendWas = GL11.glGetBoolean(GL11.GL_BLEND);

        GL11.glDisable(GL11.GL_BLEND);

        // Pass 12: the space-shell radius is a per-machine property (legacy EoH 12.95, Voidcraft USS 27.1 since pass
        // 15).
        renderOuterSpaceShell(eyeModel, te.getDomeRadius());
        renderOrbitObjects(te, time);
        // USS machines (an explicit planet system) render the star over the neutral-gray USS base layers with the
        // star class's registered color; legacy EoH machines keep the orange legacy layers + default orange tint.
        if (te.hasExplicitPlanets()) {
            final USSStarRenderType starRenderType = te.getStarRenderType();
            if (starRenderType == USSStarRenderType.NOVA) {
                renderNovaStar(te, world, time, starSpinTime, x, y, z);
            } else {
                final boolean hypernova = starRenderType == USSStarRenderType.HYPERNOVA;
                final boolean exploding = starRenderType == USSStarRenderType.SUPERNOVA || hypernova;
                final float progress = exploding ? USSSupernovaExplosion.progress(
                    USSConstants.lifespanForType(hypernova ? USSStarType.HYPERNOVA : USSStarType.SUPERNOVA),
                    te.getStarLifespanRemaining()) : 0f;
                final double starRadius = te.getStarSize()
                    * (exploding ? USSSupernovaExplosion.bodyScale(progress, hypernova) : 1.0d);
                if (!exploding) {
                    // −1 = "not mid-show": the detonation burst fires only on a crossing the client saw (see
                    // explosionLastProgress).
                    te.setExplosionLastProgress(-1f);
                }
                EOHRenderingUtils.renderUSSStar(
                    eyeModel,
                    IItemRenderer.ItemRenderType.INVENTORY,
                    new Color(te.getStarColor()),
                    te.getStarShellColor(),
                    starSpinTime,
                    starRadius,
                    te.isStarHalo(),
                    exploding ? USSSupernovaExplosion.bodyGain(progress, hypernova, time) : 1f);
                // The explosion's surface churn — drawn right after the star body so the swarm/infrastructure
                // shells pass over it (the star's roiling layer, spinning and pulsing on the virtual clock).
                if (exploding) {
                    EOHRenderingUtils.renderSupernovaChurn(eyeModel, time, (float) starRadius, te.getStarColor());
                }
            }
            // Custom render treatments (the star's registered render type): the magnetar's magnetic dipole loops
            // pass through the core — drawn AFTER the star body so the body's written depth occludes the far arcs
            // (that is what makes the field lines read as entering and exiting the core).
            if (starRenderType == USSStarRenderType.MAGNETAR) {
                EOHRenderingUtils.renderMagnetarFieldLoops(
                    eyeModel,
                    time,
                    te.getStarSize(),
                    te.getStarColor() != 0 ? te.getStarColor() : te.getStarShellColor());
            }
            // Dyson Swarm (the infrastructure pass): the star's satellite swarm renders as a near-opaque gray
            // triangle shell with a dark blue core in each panel, filling with the satellite count.
            if (te.getSwarmCount() > 0L) {
                EOHRenderingUtils.renderUSSDysonSwarm(
                    eyeModel,
                    time,
                    (float) te.getStarSize(),
                    te.getSwarmCount(),
                    te.getSwarmCapacity());
            }
            // The star's constructor-built infrastructure shell (the infrastructure-builder pass): the Stellar
            // Injector (light gray panels, orange cores) or the Stellar Gravitational Lens (dark gray panels,
            // light green cores) — the star's shell slot is exclusive, so at most one of Dyson Swarm / Injector /
            // Lens draws.
            int shellType = te.getInfraShellType();
            if (shellType >= 0 && te.getInfraShellCount() > 0L) {
                if (shellType == USSInfraBuild.INJECTOR) {
                    EOHRenderingUtils.renderUSSInfraShell(
                        eyeModel,
                        time,
                        (float) te.getStarSize() + USSConstants.INJECTOR_SHELL_RADIUS_MARGIN,
                        USSConstants.INJECTOR_TRIANGLE_EDGE,
                        te.getInfraShellCount(),
                        te.getInfraShellCapacity(),
                        EOHRenderingUtils.INJECTOR_SHELL_TINT,
                        EOHRenderingUtils.INJECTOR_ACCENT_TINT);
                } else if (shellType == USSInfraBuild.LENS) {
                    EOHRenderingUtils.renderUSSInfraShell(
                        eyeModel,
                        time,
                        (float) te.getStarSize() + USSConstants.LENS_SHELL_RADIUS_MARGIN,
                        USSConstants.LENS_TRIANGLE_EDGE,
                        te.getInfraShellCount(),
                        te.getInfraShellCapacity(),
                        EOHRenderingUtils.LENS_SHELL_TINT,
                        EOHRenderingUtils.LENS_ACCENT_TINT);
                }
            }
            // Nova overlay, drawn LAST like the classic one below. Safe unconditionally: every value is 0
            // pre-detonation.
            if (starRenderType == USSStarRenderType.NOVA) {
                renderNovaExplosionOverlay(te, world, x, y, z);
            }
            // The supernova/hypernova explosion overlay (classic render types only) — drawn LAST: the additive
            // light washes over the infrastructure shells too (the detonation illuminates the whole system view).
            if (starRenderType == USSStarRenderType.SUPERNOVA || starRenderType == USSStarRenderType.HYPERNOVA) {
                final boolean hypernova = starRenderType == USSStarRenderType.HYPERNOVA;
                final float progress = USSSupernovaExplosion.progress(
                    USSConstants.lifespanForType(hypernova ? USSStarType.HYPERNOVA : USSStarType.SUPERNOVA),
                    te.getStarLifespanRemaining());
                EOHRenderingUtils.renderSupernovaExplosion(
                    eyeModel,
                    te.getStarSize(),
                    te.getDomeRadius(),
                    te.getStarShellColor(),
                    hypernova,
                    progress);
                // The orbit rings light up in sequence as the shock disc crosses them (the disc's own radius and alpha
                // — the
                // flash dies with the disc). The disc travels from the NOMINAL rim (the body re-inflates during
                // the travel), so the crossing math uses the registered star size.
                final float travel = USSSupernovaExplosion.shellRadiusFraction(progress, hypernova);
                if (travel >= 0f) {
                    final double starSize = te.getStarSize();
                    final float shellRadius = (float) (starSize * USSSupernovaExplosion.SHELL_START_FACTOR
                        + (te.getDomeRadius() - starSize * USSSupernovaExplosion.SHELL_START_FACTOR) * travel);
                    EOHRenderingUtils.renderSupernovaRingFlashes(
                        eyeModel,
                        te.getPlanetSpecs(),
                        (float) starSize,
                        shellRadius,
                        USSSupernovaExplosion.shellAlpha(progress, hypernova),
                        USSSupernovaExplosion.SHELL_COLOR[hypernova ? 1 : 0]);
                }
                spawnExplosionParticles(te, world, hypernova, progress, te.getStarSize(), x, y, z);
            }
        } else {
            EOHRenderingUtils
                .renderEOHStar(eyeModel, IItemRenderer.ItemRenderType.INVENTORY, starSpinTime, te.getStarSize());
        }

        RenderState.restore(GL11.GL_BLEND, blendWas);
    }

    private void renderOrbitObjects(TileEntityEyeOfHarmony te, double time) {

        // Phase 4 pass 5.1: an EXPLICIT planet system (USS) renders as self-contained tinted spheres — it never
        // falls back to the legacy lazy random fill, and does not depend on the legacy orbit shader or the IORE
        // dimension blocks resolving (the USS planet colors carry the look).
        if (te.hasExplicitPlanets()) {
            EOHRenderingUtils.renderUSSOrbits(eyeModel, te.getPlanetSpecs(), time, (float) te.getStarSize());
            return;
        }

        var objects = te.getOrbitingObjects();

        if (objects == null || objects.isEmpty()) {
            te.generateImportantInfo();
            objects = te.getOrbitingObjects();

            if (objects == null || objects.isEmpty()) return;
        }

        bindTexture(TextureMap.locationBlocksTexture);
        EOHRenderingUtils.renderOrbits(eyeModel, objects, time, (float) te.getStarSize(), SPEED_SCALE, STAR_RESCALE);
    }

    /**
     * Nova's post-prelude show progress (0..1), computed independently by {@link #renderNovaStar} and
     * {@link #renderNovaExplosionOverlay}.
     */
    private static float novaProgress(TileEntityEyeOfHarmony te) {
        final long novaLifespan = USSConstants.lifespanForType(USSStarType.NOVA);
        final long elapsed = novaLifespan - te.getStarLifespanRemaining();
        if (USSNovaExplosion.inRedGiantPrelude(elapsed)) {
            return 0f;
        }
        final long explosionNominalLifespan = novaLifespan - USSNovaExplosion.RED_GIANT_PRELUDE_TICKS;
        return USSNovaExplosion.progress(explosionNominalLifespan, te.getStarLifespanRemaining());
    }

    /** Renders the Nova star body across its lifecycle — mirrors the classic branch above, growing to the dome. */
    private void renderNovaStar(TileEntityEyeOfHarmony te, World world, double time, double starSpinTime, double x,
        double y, double z) {
        final long novaLifespan = USSConstants.lifespanForType(USSStarType.NOVA);
        final long elapsed = novaLifespan - te.getStarLifespanRemaining();
        final boolean inRedGiantPrelude = USSNovaExplosion.inRedGiantPrelude(elapsed);
        final boolean exploding = !inRedGiantPrelude;
        final long explosionNominalLifespan = novaLifespan - USSNovaExplosion.RED_GIANT_PRELUDE_TICKS;
        final float progress = exploding
            ? USSNovaExplosion.progress(explosionNominalLifespan, te.getStarLifespanRemaining())
            : 0f;
        // Targets the actual dome radius, not a fixed multiplier — caps at half the dome regardless of rolled size.
        final float novaPeakScale = (float) (te.getDomeRadius() / te.getStarSize());
        final double starRadius = te.getStarSize()
            * (exploding ? USSNovaExplosion.bodyScale(progress, novaPeakScale) : 1.0d);
        if (!exploding) {
            // −1 = "not mid-show": the detonation burst fires only on a crossing the client saw (see
            // explosionLastProgress).
            te.setExplosionLastProgress(-1f);
        }
        // Collapse color ramp runs pre-detonation; the catalog color takes over from the flash onward.
        final boolean collapsing = exploding && progress < USSNovaExplosion.DETONATION_START;
        // Color/texture/fade pace independently of size — see USSNovaExplosion.collapseColorFraction.
        final float colorFraction = collapsing ? USSNovaExplosion.collapseColorFraction(progress) : 0f;
        final boolean energized = exploding && !collapsing;
        // See USSNovaExplosion.collapseFadeIn.
        final float collapseFadeIn = collapsing ? USSNovaExplosion.collapseFadeIn(colorFraction) : 1f;
        final Color bodyColor;
        final int bodyShellColor;
        final double bodyRadius;
        if (inRedGiantPrelude) {
            final float swellFraction = USSNovaExplosion.redGiantSwellFraction(elapsed);
            final int swellColor = USSNovaExplosion.lerpColor(
                USSNovaExplosion.MAIN_SEQUENCE_COLOR,
                USSNovaExplosion.RED_GIANT_COLOR,
                swellFraction);
            bodyColor = new Color(swellColor);
            bodyShellColor = swellColor;
            bodyRadius = te.getStarSize() * (1.0 + (USSNovaExplosion.RED_GIANT_SCALE - 1.0) * swellFraction);
        } else if (collapsing) {
            bodyColor = new Color(USSNovaExplosion.collapseCoreColor(colorFraction));
            bodyShellColor = USSNovaExplosion.collapseShellColor(colorFraction);
            bodyRadius = starRadius;
        } else {
            bodyColor = new Color(te.getStarColor());
            bodyShellColor = te.getStarShellColor();
            bodyRadius = starRadius;
        }
        EOHRenderingUtils.renderNovaStar(
            eyeModel,
            IItemRenderer.ItemRenderType.INVENTORY,
            bodyColor,
            bodyShellColor,
            starSpinTime,
            bodyRadius,
            energized,
            exploding ? USSNovaExplosion.bodyGain(progress, time) : 1f);
        // The explosion's surface churn — drawn right after the star body so the swarm/infrastructure shells
        // pass over it (the star's roiling layer, spinning and pulsing on the virtual clock).
        if (exploding) {
            EOHRenderingUtils.renderNovaChurn(eyeModel, time, (float) starRadius, te.getStarColor(), collapseFadeIn);
        }
    }

    /** Nova's dome flash, shockwave, ring flashes, and particles — the overlay half of {@link #renderNovaStar}. */
    private void renderNovaExplosionOverlay(TileEntityEyeOfHarmony te, World world, double x, double y, double z) {
        final float progress = novaProgress(te);
        EOHRenderingUtils.renderNovaExplosion(
            eyeModel,
            te.getStarSize(),
            te.getDomeRadius(),
            te.getStarShellColor(),
            progress);
        // Rings light up as the shock disc crosses them; travels from the nominal rim, not the re-inflated body.
        final float travel = USSNovaExplosion.shellRadiusFraction(progress);
        if (travel >= 0f) {
            final double starSize = te.getStarSize();
            final float shellStart = (float) starSize * USSNovaExplosion.SHELL_START_FACTOR;
            final float shellRadius = (float) (shellStart + (te.getDomeRadius() - shellStart) * travel);
            EOHRenderingUtils.renderNovaRingFlashes(
                eyeModel,
                te.getPlanetSpecs(),
                (float) starSize,
                shellRadius,
                USSNovaExplosion.shellAlpha(progress),
                USSNovaExplosion.SHELL_COLOR);
        }
        spawnNovaExplosionParticles(te, world, progress, te.getStarSize(), x, y, z);
    }

    /**
     * The Nova explosion's client particles — mirrors {@link #spawnExplosionParticles} on
     * {@link USSNovaExplosion}'s single-class math (no hypernova variant array index).
     */
    private void spawnNovaExplosionParticles(TileEntityEyeOfHarmony te, World world, float progress,
        double starRadius, double x, double y, double z) {
        final float det = USSNovaExplosion.DETONATION_START;
        final float last = te.getExplosionLastProgress();
        te.setExplosionLastProgress(progress);
        final boolean burst = last >= 0f && last < det && progress >= det;
        if (burst) {
            for (int i = 0; i < PARTICLE_BURST_COUNT; i++) {
                spawnNovaSpark(world, x, y, z, starRadius, PARTICLE_BURST_SPEED_FACTOR);
            }
            return; // the burst frame carries its own 240 sparks — skip the per-tick spray
        }
        final float shellEnd = USSNovaExplosion.SHELL_TRAVEL_END;
        if (progress >= det && progress <= shellEnd && world.getTotalWorldTime() != te.getExplosionLastSparkTick()) {
            te.setExplosionLastSparkTick(world.getTotalWorldTime());
            for (int i = 0; i < PARTICLE_SPARKS_PER_TICK; i++) {
                spawnNovaSpark(world, x, y, z, starRadius, PARTICLE_SPARK_SPEED_FACTOR);
            }
        }
    }

    /** One Nova spark: mirrors {@link #spawnSpark} on {@link USSNovaExplosion#SHELL_COLOR}. */
    private void spawnNovaSpark(World world, double x, double y, double z, double starRadius, float speedFactor) {
        final float theta = (float) (PARTICLE_RANDOM.nextDouble() * 2.0 * Math.PI);
        final float cosPhi = (float) (PARTICLE_RANDOM.nextDouble() * 2.0 - 1.0);
        final float sinPhi = (float) Math.sqrt(1.0 - cosPhi * cosPhi);
        final double dx = sinPhi * Math.cos(theta);
        final double dy = cosPhi;
        final double dz = sinPhi * Math.sin(theta);
        final float speed = (float) starRadius * speedFactor;
        final int color = USSNovaExplosion.SHELL_COLOR;
        final int r = mixTowardWhite((color >> 16) & 0xFF);
        final int g = mixTowardWhite((color >> 8) & 0xFF);
        final int b = mixTowardWhite(color & 0xFF);
        final USSSparkFX fx = new USSSparkFX(
            world,
            x + 0.5 + dx * starRadius,
            y + 0.5 + dy * starRadius,
            z + 0.5 + dz * starRadius,
            dx * speed,
            dy * speed,
            dz * speed);
        fx.setRBGColorF(r / 255f, g / 255f, b / 255f);
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }

    /**
     * The explosion's client particles: one radial detonation burst on the frame the show crosses into the
     * detonation (the burst detector, see {@code TileEntityEyeOfHarmony.explosionLastProgress}), plus a thin
     * continuous spray of sparks while the disc is expanding — at most one spawn per world tick (the render
     * pass runs many frames per tick).
     */
    private void spawnExplosionParticles(TileEntityEyeOfHarmony te, World world, boolean hypernova, float progress,
        double starRadius, double x, double y, double z) {
        final int v = hypernova ? 1 : 0;
        final float det = USSSupernovaExplosion.DETONATION_START[v];
        final float last = te.getExplosionLastProgress();
        te.setExplosionLastProgress(progress);
        final boolean burst = last >= 0f && last < det && progress >= det;
        if (burst) {
            for (int i = 0; i < PARTICLE_BURST_COUNT; i++) {
                spawnSpark(world, x, y, z, starRadius, PARTICLE_BURST_SPEED_FACTOR, v);
            }
            return; // the burst frame carries its own 240 sparks — skip the per-tick spray
        }
        final float shellEnd = USSSupernovaExplosion.SHELL_TRAVEL_END[v];
        if (progress >= det && progress <= shellEnd && world.getTotalWorldTime() != te.getExplosionLastSparkTick()) {
            te.setExplosionLastSparkTick(world.getTotalWorldTime());
            for (int i = 0; i < PARTICLE_SPARKS_PER_TICK; i++) {
                spawnSpark(world, x, y, z, starRadius, PARTICLE_SPARK_SPEED_FACTOR, v);
            }
        }
    }

    /** One spark: a uniform random direction from the star's surface, the class color mixed toward white. */
    private void spawnSpark(World world, double x, double y, double z, double starRadius, float speedFactor,
        int variant) {
        final float theta = (float) (PARTICLE_RANDOM.nextDouble() * 2.0 * Math.PI);
        final float cosPhi = (float) (PARTICLE_RANDOM.nextDouble() * 2.0 - 1.0);
        final float sinPhi = (float) Math.sqrt(1.0 - cosPhi * cosPhi);
        final double dx = sinPhi * Math.cos(theta);
        final double dy = cosPhi;
        final double dz = sinPhi * Math.sin(theta);
        final float speed = (float) starRadius * speedFactor;
        final int color = USSSupernovaExplosion.SHELL_COLOR[variant];
        final int r = mixTowardWhite((color >> 16) & 0xFF);
        final int g = mixTowardWhite((color >> 8) & 0xFF);
        final int b = mixTowardWhite(color & 0xFF);
        final USSSparkFX fx = new USSSparkFX(
            world,
            x + 0.5 + dx * starRadius,
            y + 0.5 + dy * starRadius,
            z + 0.5 + dz * starRadius,
            dx * speed,
            dy * speed,
            dz * speed);
        fx.setRBGColorF(r / 255f, g / 255f, b / 255f);
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }

    /** One tint channel mixed toward white by {@link #PARTICLE_WHITE_MIX} (clamped to 0..255). */
    private static int mixTowardWhite(int channel) {
        final int out = (int) Math.floor(channel + (255 - channel) * PARTICLE_WHITE_MIX);
        return out < 0 ? 0 : (out > 255 ? 255 : out);
    }
}
