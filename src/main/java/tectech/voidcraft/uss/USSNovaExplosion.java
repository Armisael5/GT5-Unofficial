package tectech.voidcraft.uss;

import java.awt.Color;

public final class USSNovaExplosion {

    private USSNovaExplosion() {
        throw new AssertionError("Math holder");
    }

    /** Lifetime progress at which detonation fires: 160 of the 5760-tick post-prelude show (8s). */
    public static final float DETONATION_START = 0.02778f;

    /** The flash attack window (fraction of the lifetime): gains ramp from base to peak over it. */
    public static final float FLASH_ATTACK = 0.005f;

    /** The detonation flash's peak layer gain (clipped past full-bright — the white-hot look). */
    public static final float FLASH_GAIN = 6.0f;

    /** The dome flash's decay window (fraction of the lifetime). */
    public static final float FLASH_WINDOW = 0.015f;

    /** The pre-collapse ember scale (× registered size); also the growth curve's starting point. */
    public static final float PRE_COLLAPSE_SCALE = 0.1f;

    /** Lifetime progress at which the shock shell reaches the dome: 200 ticks (a 2s crossing) past detonation. */
    public static final float SHELL_TRAVEL_END = 0.03472f;

    /** The shock shell's peak alpha — barely visible, a faint true-sphere shockwave, not a solid disc. */
    public static final float SHELL_ALPHA_PEAK = 0.12f;

    /**
     * The shell's bloom window (lifetime fraction): 8 ticks, sized against the shell's own 40-tick travel window
     * rather than a fixed slice of the show, so the bloom finishes before the shell reaches the dome.
     */
    public static final float SHELL_ALPHA_ATTACK = 0.0013889f;

    /** The shell's alpha fade exponent after the bloom: 1.0 = linear. */
    public static final float SHELL_ALPHA_FADE_POWER = 1.0f;

    /** The shock shell's tint: a bright white-blue. */
    public static final int SHELL_COLOR = 0xFFCCEEFF;

    /** The churn layer's radius, × the star's rendered radius. */
    public static final float CHURN_RADIUS_FACTOR = 1.05f;

    /** The churn layer's peak alpha. */
    public static final float CHURN_ALPHA = 0.4f;

    /** The churn's brightness-pulse period in ticks. */
    public static final float CHURN_PULSE_PERIOD = 8f;

    /** The churn's brightness-pulse depth (± this fraction of its peak). */
    public static final float CHURN_PULSE_DEPTH = 0.5f;

    /** The churn layer's radius wobble, × the star's rendered radius. */
    public static final float CHURN_SWELL = 0.03f;

    /** The churn's radius-wobble period in ticks. */
    public static final float CHURN_SWELL_PERIOD = 5f;

    /** The orbit ring's flash peak alpha as the shock shell crosses it. */
    public static final float RING_FLASH_ALPHA = 0.7f;

    /** The ring flash's decay length in blocks past the crossing. */
    public static final float RING_FLASH_DECAY = 8f;

    /** The shock shell's launch radius, × the star's rendered radius — essentially the star's center. */
    public static final float SHELL_START_FACTOR = 0.02f;

    /** The afterglow floor the flash decays to once the shock shell's travel ends. */
    public static final float AFTERGLOW_GAIN = 1.6f;

    /** Lifetime progress at which the collapse starts — the shell's end. */
    public static final float COLLAPSE_START = 0.21f;

    /** The afterglow's settle window (lifetime fraction), starting at the shell's end. */
    public static final float AFTERGLOW_SETTLE = 0.05f;

    /** The collapse's depth: the core's final scale is {@code 1 - this}. */
    public static final float COLLAPSE_DEPTH = 0.85f;

    /** The final flash's peak layer gain, held until the remnant takes over. */
    public static final float FINAL_FLASH_GAIN = 5.0f;

    /** The final flash's ramp, a fraction of the collapse window. */
    public static final float FINAL_FLASH_FRACTION = 0.06f;

    /** The dome flash's peak alpha. */
    public static final float DOME_FLASH_ALPHA = 0.5f;

    /** The dome flash's radius, × the dome radius — a hair inside the space shell to avoid z-fighting. */
    public static final float DOME_FLASH_RADIUS_FACTOR = 0.995f;

    /** The afterglow's brightness-pulse period in ticks. */
    public static final float PULSE_PERIOD_TICKS = 60f;

    /** The pulse's amplitude at the start of the afterglow, decayed to 0 over the shell's travel. */
    public static final float PULSE_AMP_AFTERGLOW = 0.2f;

    /**
     * @param nominalLifespan the star class's nominal lifespan in machine ticks; &le; 0 → 0
     * @param remaining       the synced remaining lifespan (negative = not synced, reads as the show's start)
     * @return the show's lifetime progress, {@code 1 - remaining/nominal}, clamped to [0..1]
     */
    public static float progress(long nominalLifespan, long remaining) {
        if (nominalLifespan <= 0L) {
            return 0f;
        }
        if (remaining < 0L) {
            remaining = nominalLifespan;
        }
        if (remaining > nominalLifespan) {
            remaining = nominalLifespan;
        }
        return (float) (1.0 - (double) remaining / (double) nominalLifespan);
    }

    /**
     * The star body's layer-gain multiplier: flat through the collapse, the detonation's ramp to the flash peak,
     * the afterglow's decay to the floor, and the final flash's ramp + hold.
     */
    public static float bodyGain(float progress, double time) {
        final float p = clamp01(progress);
        final float det = DETONATION_START;
        if (p < det) {
            return 1f;
        }
        final float attackEnd = det + FLASH_ATTACK;
        if (p < attackEnd) {
            return lerp(1f, FLASH_GAIN, smoothstep((p - det) / FLASH_ATTACK));
        }
        final float shellEnd = SHELL_TRAVEL_END;
        if (p < shellEnd) {
            final float t = (p - attackEnd) / (shellEnd - attackEnd);
            final float decay = lerp(FLASH_GAIN, AFTERGLOW_GAIN, t);
            return decay + PULSE_AMP_AFTERGLOW * (1f - t) * pulse(time);
        }
        final float collapse = COLLAPSE_START;
        final float settleEnd = Math.max(collapse, shellEnd + AFTERGLOW_SETTLE);
        if (p < settleEnd) {
            final float t = (p - shellEnd) / (settleEnd - shellEnd);
            return lerp(AFTERGLOW_GAIN, 1f, t);
        }
        if (p < collapse) {
            return 1f;
        }
        final float w = 1f - collapse;
        if (w <= 0f) {
            return FINAL_FLASH_GAIN;
        }
        final float t = (p - collapse) / w;
        final float flashStart = 1f - FINAL_FLASH_FRACTION;
        if (t < flashStart) {
            return 1f;
        }
        return lerp(1f, FINAL_FLASH_GAIN, (t - flashStart) / (1f - flashStart));
    }

    /** The pre-collapse shrink's starting scale: the red-giant prelude's swollen size. */
    private static final float COLLAPSE_START_SCALE = USSNovaExplosion.RED_GIANT_SCALE;

    /**
     * The star body's rendered scale (× registered size): the pre-collapse shrink from the red-giant size to the
     * ember, then one continuous curve (see {@link #novaBodyScale}) through growth and the finale collapse.
     */
    public static float bodyScale(float progress, float peakScale) {
        final float p = clamp01(progress);
        final float det = DETONATION_START;
        if (det > 0f && p < det) {
            return lerp(COLLAPSE_START_SCALE, PRE_COLLAPSE_SCALE, collapseFraction(progress));
        }
        return novaBodyScale(p, peakScale);
    }

    /**
     * The collapse's accelerating-curve exponent: solved so 75% of the collapse's time covers only 2/3 of its
     * size range, leaving the last third of the shrink for the final quarter. {@code 0.75^k = 2/3} →
     * {@code k = ln(2/3) / ln(0.75) ≈ 1.4094}.
     */
    private static final float COLLAPSE_CURVE_EXPONENT = 1.4094f;

    /** @return the pre-collapse shrink's progress (0..1), on the accelerating {@link #COLLAPSE_CURVE_EXPONENT} curve. */
    public static float collapseFraction(float progress) {
        final float p = clamp01(progress);
        final float det = DETONATION_START;
        if (det <= 0f) {
            return 1f;
        }
        final float tau = clamp01(p / det);
        return (float) Math.pow(tau, COLLAPSE_CURVE_EXPONENT);
    }

    /**
     * The collapse's color/texture pacing: evenly eased over the same window {@link #collapseFraction} shrinks
     * over, deliberately not the same accelerating curve (color needs to progress steadily, not rush at the end).
     */
    public static float collapseColorFraction(float progress) {
        final float p = clamp01(progress);
        final float det = DETONATION_START;
        if (det <= 0f) {
            return 1f;
        }
        return smoothstep(clamp01(p / det));
    }

    // region Collapse color ramp

    /** Matches {@link #RED_GIANT_COLOR} exactly — the ramp picks up from the prelude with no color pop. */
    private static final int COLLAPSE_COLOR_ORANGE = USSNovaExplosion.RED_GIANT_COLOR;
    private static final int COLLAPSE_COLOR_YELLOW = 0xFFFFE060;
    private static final int COLLAPSE_COLOR_WHITE = 0xFFFFFAF0;
    private static final int COLLAPSE_COLOR_LIGHT_BLUE = 0xFF9FD8FF;

    /** The collapse fraction at which the ramp reaches yellow, then splits the core from the shell. */
    private static final float COLLAPSE_RAMP_YELLOW = 0.35f;
    /** The collapse fraction at which the core ramp turns pure white. */
    private static final float COLLAPSE_RAMP_SPLIT = 0.65f;

    /**
     * The collapsing core tint: orange → yellow → white → light blue. Blended linearly within each segment —
     * {@code collapseFraction}/{@code collapseColorFraction} are already pre-eased for overall pacing.
     */
    public static int collapseCoreColor(float collapseFraction) {
        final float t = clamp01(collapseFraction);
        if (t < COLLAPSE_RAMP_YELLOW) {
            return lerpColorHSB(COLLAPSE_COLOR_ORANGE, COLLAPSE_COLOR_YELLOW, t / COLLAPSE_RAMP_YELLOW);
        }
        if (t < COLLAPSE_RAMP_SPLIT) {
            return lerpColorHSB(
                COLLAPSE_COLOR_YELLOW,
                COLLAPSE_COLOR_WHITE,
                (t - COLLAPSE_RAMP_YELLOW) / (COLLAPSE_RAMP_SPLIT - COLLAPSE_RAMP_YELLOW));
        }
        return lerpColorHSBBackward(
            COLLAPSE_COLOR_WHITE,
            COLLAPSE_COLOR_LIGHT_BLUE,
            (t - COLLAPSE_RAMP_SPLIT) / (1f - COLLAPSE_RAMP_SPLIT));
    }

    /** The collapsing shell tint: lags the core, holding yellow through the core's white stage. */
    public static int collapseShellColor(float collapseFraction) {
        final float t = clamp01(collapseFraction);
        if (t < COLLAPSE_RAMP_YELLOW) {
            return lerpColorHSB(COLLAPSE_COLOR_ORANGE, COLLAPSE_COLOR_YELLOW, t / COLLAPSE_RAMP_YELLOW);
        }
        if (t < COLLAPSE_RAMP_SPLIT) {
            return COLLAPSE_COLOR_YELLOW;
        }
        return lerpColorHSBBackward(
            COLLAPSE_COLOR_YELLOW,
            COLLAPSE_COLOR_LIGHT_BLUE,
            (t - COLLAPSE_RAMP_SPLIT) / (1f - COLLAPSE_RAMP_SPLIT));
    }

    // endregion

    /** The fraction of the dome radius the post-detonation growth caps at (not the full dome). */
    private static final float EXPANSION_PEAK_FRACTION = 0.5f;

    /** Lifetime progress at which the post-detonation growth finishes: 300 ticks (15s) after detonation. */
    private static final float GROWTH_END = DETONATION_START + 300f / 5760f;

    /** Lifetime progress at which the finale collapse begins — matches {@link #COLLAPSE_START}. */
    private static final float FINALE_COLLAPSE_START = COLLAPSE_START;

    /**
     * One continuous curve from the ember (rebound starts instantly at detonation, no hold) to the capped peak —
     * a single smoothstep across the whole growth span, not two chained together. Two smoothsteps stitched at a
     * shared instant are continuous in value but not velocity (smoothstep's derivative is 0 at both endpoints),
     * so the join reads as the star stalling mid-growth.
     */
    private static float novaBodyScale(float p, float domePeakScale) {
        final float det = DETONATION_START;
        final float growthEnd = GROWTH_END;
        final float peak = domePeakScale * EXPANSION_PEAK_FRACTION;
        if (p < growthEnd) {
            final float t = smoothstep((p - det) / (growthEnd - det));
            return lerp(PRE_COLLAPSE_SCALE, peak, t);
        }
        final float finaleStart = FINALE_COLLAPSE_START;
        if (p < finaleStart) {
            return peak;
        }
        final float w = 1f - finaleStart;
        if (w <= 0f) {
            return peak;
        }
        final float t = (p - finaleStart) / w;
        return peak * (1f - COLLAPSE_DEPTH * t * t);
    }

    /**
     * @return the shock shell's travel as a fraction of the rim→dome distance, eased (fast start, slow end); -1
     *         when not yet launched
     */
    public static float shellRadiusFraction(float progress) {
        final float p = clamp01(progress);
        final float det = DETONATION_START;
        final float end = SHELL_TRAVEL_END;
        if (p < det || end <= det) {
            return -1f;
        }
        final float t = Math.min(1f, (p - det) / (end - det));
        return 1f - (1f - t) * (1f - t);
    }

    /** @return the shock shell's alpha: blooms then fades to 0 as it reaches the dome radius. */
    public static float shellAlpha(float progress) {
        final float p = clamp01(progress);
        final float det = DETONATION_START;
        final float end = SHELL_TRAVEL_END;
        if (p <= det || end <= det) {
            return 0f;
        }
        final float attackEnd = det + SHELL_ALPHA_ATTACK;
        final float attack = Math.min(1f, (p - det) / SHELL_ALPHA_ATTACK);
        final float decaySpan = end - attackEnd;
        final float f = decaySpan <= 0f ? 0f : Math.min(1f, Math.max(0f, 1f - (p - attackEnd) / decaySpan));
        return SHELL_ALPHA_PEAK * attack * (float) Math.pow(f, SHELL_ALPHA_FADE_POWER);
    }

    /** @return the dome flash's alpha: peaks at detonation, decaying quadratically to 0 over the flash window. */
    public static float domeFlashAlpha(float progress) {
        final float p = clamp01(progress);
        final float det = DETONATION_START;
        final float win = FLASH_WINDOW;
        if (p < det || win <= 0f) {
            return 0f;
        }
        final float f = 1f - (p - det) / win;
        if (f <= 0f) {
            return 0f;
        }
        return DOME_FLASH_ALPHA * f * f;
    }

    /** @return the churn layer's alpha: the peak modulated by the fast surface roll. */
    public static float churnAlpha(double time) {
        return CHURN_ALPHA * (1f + CHURN_PULSE_DEPTH * fastSin(time, CHURN_PULSE_PERIOD));
    }

    /** The collapse fraction at which a faded-in effect starts appearing (see {@link #collapseFadeIn}). */
    private static final float COLLAPSE_FADE_IN_START = 0.25f;

    /**
     * The collapse's generic fade-in curve: transparent through the first quarter, then eases to full strength
     * by detonation. Used for the churn effect and the star body's outer layer.
     */
    public static float collapseFadeIn(float collapseFraction) {
        final float t = clamp01(collapseFraction);
        if (t < COLLAPSE_FADE_IN_START) {
            return 0f;
        }
        return smoothstep((t - COLLAPSE_FADE_IN_START) / (1f - COLLAPSE_FADE_IN_START));
    }

    /** @return the churn layer's radius factor, × the star's rendered radius, with the slow surface breathing. */
    public static float churnRadiusFactor(double time) {
        return CHURN_RADIUS_FACTOR + CHURN_SWELL * fastSin(time, CHURN_SWELL_PERIOD);
    }

    /** @return an orbit ring's flash alpha as the shock shell crosses it: 0 before, peaking at, decaying after. */
    public static float ringFlashAlpha(float shellRadius, float ringRadius, float shellAlpha) {
        if (shellRadius <= 0f || ringRadius <= 0f || shellAlpha <= 0f) {
            return 0f;
        }
        final float past = shellRadius - ringRadius;
        if (past < 0f) {
            return 0f;
        }
        return RING_FLASH_ALPHA * shellAlpha * (float) Math.exp(-past / RING_FLASH_DECAY);
    }

    private static float fastSin(double time, float period) {
        final long p = (long) period;
        final long phase = (long) time % p;
        return (float) Math.sin(phase * 2.0 * Math.PI / p);
    }

    private static float pulse(double time) {
        final long phase = (long) time % (long) PULSE_PERIOD_TICKS;
        return (float) Math.sin(phase * 2.0 * Math.PI / PULSE_PERIOD_TICKS);
    }

    // region Red-giant prelude

    /** A ~2s hold at plain main-sequence before the red-giant swell begins. */
    public static final long RED_GIANT_SWELL_DELAY_TICKS = 40L;

    /** The pre-explosion red-giant swell's duration in ticks, in real time (independent of the class lifespan). */
    public static final long RED_GIANT_SWELL_TICKS = 200L;

    /** The red-giant hold's duration in ticks, after the swell and before the normal-speed show. */
    public static final long RED_GIANT_HOLD_TICKS = 40L;

    /** The combined prelude duration in ticks, before {@link #progress} starts driving the show. */
    public static final long RED_GIANT_PRELUDE_TICKS = RED_GIANT_SWELL_DELAY_TICKS + RED_GIANT_SWELL_TICKS
        + RED_GIANT_HOLD_TICKS;

    /** The red giant's rendered scale at the end of the swell, × the star's registered size. */
    public static final float RED_GIANT_SCALE = 3.0f;

    /** The prelude's start (main-sequence) and end (red giant) tints, packed opaque ARGB. */
    public static final int MAIN_SEQUENCE_COLOR = 0xFFFFAA29;
    public static final int RED_GIANT_COLOR = 0xFFE83810;

    /** @return true while the star is still in the red-giant prelude, before the normal-speed show starts */
    public static boolean inRedGiantPrelude(long elapsedTicks) {
        return elapsedTicks >= 0L && elapsedTicks < RED_GIANT_PRELUDE_TICKS;
    }

    /** @return the swell's progress: 0 through the pre-swell delay, ramping to 1, holding through the hold window */
    public static float redGiantSwellFraction(long elapsedTicks) {
        final long sinceDelay = elapsedTicks - RED_GIANT_SWELL_DELAY_TICKS;
        if (sinceDelay <= 0L) {
            return 0f;
        }
        if (sinceDelay >= RED_GIANT_SWELL_TICKS) {
            return 1f;
        }
        return smoothstep((float) sinceDelay / RED_GIANT_SWELL_TICKS);
    }

    // endregion

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Plain per-channel RGB lerp — the red-giant swell's color blend. */
    public static int lerpColor(int a, int b, float t) {
        final int r = Math.round(lerp((a >> 16) & 0xFF, (b >> 16) & 0xFF, t));
        final int g = Math.round(lerp((a >> 8) & 0xFF, (b >> 8) & 0xFF, t));
        final int bl = Math.round(lerp(a & 0xFF, b & 0xFF, t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /**
     * Blends two colors in HSB space rather than RGB — an RGB lerp between colors with uneven per-channel
     * distance reads as stuck on one color for most of the blend.
     */
    private static int lerpColorHSB(int a, int b, float t) {
        final float[] hsbA = Color.RGBtoHSB((a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF, null);
        final float[] hsbB = Color.RGBtoHSB((b >> 16) & 0xFF, (b >> 8) & 0xFF, b & 0xFF, null);
        // Hue is circular (0 and 1 are the same point) — take the shorter way around.
        float dh = hsbB[0] - hsbA[0];
        if (dh > 0.5f) {
            dh -= 1f;
        } else if (dh < -0.5f) {
            dh += 1f;
        }
        float hue = hsbA[0] + dh * t;
        if (hue < 0f) {
            hue += 1f;
        } else if (hue >= 1f) {
            hue -= 1f;
        }
        return Color.HSBtoRGB(hue, lerp(hsbA[1], hsbB[1], t), lerp(hsbA[2], hsbB[2], t));
    }

    /**
     * Like {@link #lerpColorHSB} but always takes the hue path backward instead of the numerically shorter one —
     * yellow to light blue's shorter path cuts through green, which reads wrong for a star cooling toward blue.
     */
    private static int lerpColorHSBBackward(int a, int b, float t) {
        final float[] hsbA = Color.RGBtoHSB((a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF, null);
        final float[] hsbB = Color.RGBtoHSB((b >> 16) & 0xFF, (b >> 8) & 0xFF, b & 0xFF, null);
        float dh = hsbB[0] - hsbA[0];
        if (dh > 0f) {
            dh -= 1f;
        }
        float hue = hsbA[0] + dh * t;
        if (hue < 0f) {
            hue += 1f;
        }
        return Color.HSBtoRGB(hue, lerp(hsbA[1], hsbB[1], t), lerp(hsbA[2], hsbB[2], t));
    }

    private static float smoothstep(float t) {
        final float s = clamp01(t);
        return s * s * (3f - 2f * s);
    }
}
