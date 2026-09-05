package tectech.voidcraft.uss;

/**
 * A per-body rotation clock: {@code angle = speed × elapsedTime} jumps the instant {@code speed} changes, because
 * the whole angle is recomputed from a large accumulated elapsed time under the NEW speed. This instead anchors
 * the body's own accumulated virtual spin-time at the moment its speed last changed, so a speed change only ever
 * alters the RATE the angle advances at from that point on — never the angle itself.
 *
 * <p>
 * Bare-JVM safe: pure {@code double} math, no world/TE dependencies — unit-testable without a world.
 */
public final class USSRotationClock {

    private USSRotationClock() {
        throw new AssertionError("Math holder");
    }

    /**
     * The body's current virtual spin-time: the anchor plus however much has accumulated at the current speed
     * since the anchor was taken.
     *
     * @param anchorSpinTime   the accumulated spin-time at the anchor (a caller re-anchors on a speed change by
     *                         computing the current spin-time once under the OLD speed and storing that as the
     *                         new anchor — see {@code TileEntityEyeOfHarmony.setStarSpinRate})
     * @param speed            the current speed (virtual spin-time units per unit of {@code currentClockTime})
     * @param anchorClockTime  the reference clock's reading when the anchor was taken (e.g. the world tick the
     *                         speed last changed)
     * @param currentClockTime the reference clock's current reading
     * @return the current virtual spin-time (not reduced modulo anything — the caller reduces it, e.g. mod 360,
     *         right before use, same as any other rotation angle)
     */
    public static double spinTimeAt(double anchorSpinTime, double speed, double anchorClockTime,
        double currentClockTime) {
        return anchorSpinTime + speed * (currentClockTime - anchorClockTime);
    }

    /**
     * The body's virtual spin-time under a continuously, smoothly oscillating speed — {@code speed(τ) = midSpeed +
     * amplitude·sin(angularFrequency·τ)} (τ = elapsed clock time since the anchor) — WITHOUT needing to re-anchor
     * every tick to approximate the curve: this is the closed-form integral of that speed, so it costs exactly as
     * much as the constant-speed case ({@link #spinTimeAt}) to evaluate at any instant.
     *
     * <p>
     * {@code ∫[0..τ] (midSpeed + amplitude·sin(ωs)) ds = midSpeed·τ + (amplitude/ω)·(1 − cos(ωτ))} — continuous
     * with (and its derivative equal to {@code midSpeed}, matching {@link #spinTimeAt} at speed {@code midSpeed})
     * at τ=0, so entering or leaving sweep mode (re-anchoring the same way as a speed change) never jumps either.
     *
     * @param midSpeed        the oscillation's center speed (e.g. 3.0 for a 1..5 range)
     * @param amplitude       the oscillation's half-range (e.g. 2.0 for a 1..5 range — midSpeed ± amplitude)
     * @param angularFrequency 2π / the oscillation's period, in the same clock-time units as {@code τ}
     */
    public static double sweepSpinTimeAt(double anchorSpinTime, double anchorClockTime, double currentClockTime,
        double midSpeed, double amplitude, double angularFrequency) {
        final double tau = currentClockTime - anchorClockTime;
        return anchorSpinTime + midSpeed * tau + (amplitude / angularFrequency) * (1.0 - Math.cos(angularFrequency * tau));
    }
}
