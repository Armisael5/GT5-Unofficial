package tectech.voidcraft.uss;

/**
 * A per-body rotation clock: re-anchors the accumulated spin-time when the speed changes, so a speed change
 * alters the rate the angle advances at without jumping the angle itself. Pure double math, no world dependency.
 */
public final class USSRotationClock {

    private USSRotationClock() {
        throw new AssertionError("Math holder");
    }

    /** The current virtual spin-time: the anchor plus whatever has accumulated at the current speed since. */
    public static double spinTimeAt(double anchorSpinTime, double speed, double anchorClockTime,
        double currentClockTime) {
        return anchorSpinTime + speed * (currentClockTime - anchorClockTime);
    }

    /** Like {@link #spinTimeAt}, but for a speed oscillating as {@code midSpeed ± amplitude·sin(angularFrequency·t)}. */
    public static double sweepSpinTimeAt(double anchorSpinTime, double anchorClockTime, double currentClockTime,
        double midSpeed, double amplitude, double angularFrequency) {
        final double tau = currentClockTime - anchorClockTime;
        return anchorSpinTime + midSpeed * tau + (amplitude / angularFrequency) * (1.0 - Math.cos(angularFrequency * tau));
    }
}
