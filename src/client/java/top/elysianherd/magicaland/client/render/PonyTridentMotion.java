package top.elysianherd.magicaland.client.render;

/** 嘴咬三叉戟的视觉蓄力；实际投出必须由新投掷物确认。 */
final class PonyTridentMotion {
    static final double MIN_CHARGE = 10, EVIDENCE_WINDOW = 10;
    static final double STRIKE_TICKS = 2, SETTLE_TICKS = 5, CANCEL_TICKS = 4;
    record Input(double tick, boolean eligible, boolean mouthTrident, boolean offHand, boolean left,
                 boolean charging, double chargeTicks, boolean riptide, boolean cancelled, long throwEvidenceId) {}
    record Pose(float windup, float release, float settle, boolean left) {
        static final Pose NONE = new Pose(0, 0, 0, false);
        float headYawRadians() {
            return (float) Math.toRadians((left ? -1 : 1) * (28 * windup - 8 * (release + settle)));
        }
        float headPitchRadians() {
            return (float) Math.toRadians(-3 * windup + 2 * (release + settle));
        }
    }
    private enum Stage { IDLE, CHARGING, WAITING, RETURNING, RELEASE }
    private Stage stage = Stage.IDLE;
    private double observed = Double.NaN, charge, maxCharge, changed, returnFrom;
    private long seenEvidence;
    private boolean left, offHand, blockedUse;

    void observe(Input in) {
        if (in == null || !Double.isFinite(in.tick())) { reset(); return; }
        double tick = in.tick(), elapsed = tick - observed;
        Pose before = sample(tick);
        boolean freshEvidence = in.throwEvidenceId() > seenEvidence && in.throwEvidenceId() > 0;
        seenEvidence = Math.max(seenEvidence, in.throwEvidenceId());
        if (!Double.isFinite(observed) || elapsed < 0 || elapsed > 20) {
            clearMotion();
            freshEvidence = false;
        }
        observed = tick;
        if (!in.eligible()) { clearMotion(); return; }
        double nextCharge = Double.isFinite(in.chargeTicks()) ? Math.max(0, in.chargeTicks()) : 0;
        boolean active = in.mouthTrident() && in.charging() && !in.riptide();
        boolean changedHand = stage == Stage.CHARGING && active
                && (offHand != in.offHand() || left != in.left());
        if (in.cancelled() || in.riptide() || changedHand) {
            if (stage == Stage.CHARGING || stage == Stage.WAITING || stage == Stage.RELEASE) {
                returnFrom = before.windup();
                changed = tick;
                stage = Stage.RETURNING;
            }
            maxCharge = 0;
            blockedUse = in.charging();
            charge = nextCharge;
            return;
        }
        if (blockedUse && (!in.charging() || nextCharge + .5 < charge)) blockedUse = false;
        if (stage == Stage.RELEASE) {
            charge = nextCharge;
            if (tick - changed < STRIKE_TICKS + SETTLE_TICKS) return;
            stage = Stage.IDLE;
        }
        if (active && !blockedUse) {
            if (stage != Stage.CHARGING || nextCharge + .5 < charge) {
                maxCharge = 0;
                left = in.left();
                offHand = in.offHand();
            }
            stage = Stage.CHARGING;
            charge = nextCharge;
            maxCharge = Math.max(maxCharge, charge);
        } else if (stage == Stage.CHARGING) {
            // 刚好满十 tick 时可能尚未渲染最后一帧，最多补足一个观察 tick。
            maxCharge = Math.max(maxCharge, Math.max(nextCharge, charge + Math.max(0, Math.min(1, elapsed))));
            returnFrom = before.windup();
            changed = tick;
            stage = maxCharge >= MIN_CHARGE ? Stage.WAITING : Stage.RETURNING;
        }
        if (stage == Stage.WAITING && tick - changed > EVIDENCE_WINDOW) stage = Stage.RETURNING;
        if (freshEvidence && (stage == Stage.CHARGING && maxCharge >= MIN_CHARGE || stage == Stage.WAITING)) {
            returnFrom = sample(tick).windup();
            changed = tick;
            stage = Stage.RELEASE;
            blockedUse = in.charging();
            maxCharge = 0;
        }
        charge = nextCharge;
    }

    /** 只读采样；第三人称重复渲染不会推进状态。 */
    Pose sample(double tick) {
        if (!Double.isFinite(tick) || !Double.isFinite(observed) || tick - observed > 20) return Pose.NONE;
        tick = Math.max(tick, observed);
        if (stage == Stage.CHARGING) {
            return new Pose(smooth((charge + Math.min(1, tick - observed)) / MIN_CHARGE), 0, 0, left);
        }
        if (stage == Stage.WAITING || stage == Stage.RETURNING) {
            return new Pose((float) returnFrom * (1 - smooth((tick - changed) / CANCEL_TICKS)), 0, 0, left);
        }
        if (stage == Stage.RELEASE) {
            double age = tick - changed;
            if (age < STRIKE_TICKS) {
                float strike = smooth(age / STRIKE_TICKS);
                return new Pose((float) returnFrom * (1 - strike), strike, 0, left);
            }
            return new Pose(0, 0, 1 - smooth((age - STRIKE_TICKS) / SETTLE_TICKS), left);
        }
        return Pose.NONE;
    }
    void reset() {
        clearMotion();
        observed = Double.NaN;
        seenEvidence = 0;
    }
    private void clearMotion() {
        stage = Stage.IDLE;
        charge = maxCharge = changed = returnFrom = 0;
        left = offHand = blockedUse = false;
    }
    private static float smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return (float) (t * t * (3 - 2 * t));
    }
}
