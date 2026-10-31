(function (root) {
    "use strict";
    class DetectionPolicy {
        constructor({ frames = 8, cooldownMs = 90000, maxGapMs = 5000, clock = Date.now } = {}) {
            Object.assign(this, { frames, cooldownMs, maxGapMs, clock });
            this.cameras = new Map();
        }
        state(camera) {
            const key = String(camera);
            if (!this.cameras.has(key)) this.cameras.set(key, { streak: {}, raised: {}, last: null });
            return this.cameras.get(key);
        }
        observe(camera, detections) {
            const state = this.state(camera), now = this.clock(), best = new Map();
            for (const item of detections) {
                if (!best.has(item.label) || Number(item.confidence) > Number(best.get(item.label).confidence)) {
                    best.set(item.label, item);
                }
            }
            if (state.last !== null && now - state.last > this.maxGapMs) state.streak = {};
            for (const label of Object.keys(state.streak)) {
                if (!best.has(label)) delete state.streak[label];
            }
            state.last = now;
            for (const label of best.keys()) state.streak[label] = (state.streak[label] || 0) + 1;
            return [...best.values()].filter(item => state.streak[item.label] >= this.frames
                && (state.raised[item.label] === undefined || now - state.raised[item.label] >= this.cooldownMs));
        }
        markReported(camera, label) { this.state(camera).raised[label] = this.clock(); }
        clearStreaks() { for (const state of this.cameras.values()) { state.streak = {}; state.last = null; } }
        reset(camera) { this.cameras.delete(String(camera)); }
    }
    root.CypherDetectionPolicy = DetectionPolicy;
    if (typeof module !== "undefined") module.exports = DetectionPolicy;
})(globalThis);
