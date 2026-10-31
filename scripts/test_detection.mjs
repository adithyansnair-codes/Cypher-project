import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import test from "node:test";
const require = createRequire(import.meta.url);
const Policy = require("../backend/src/main/resources/static/detection-policy.js");
const knife = { label: "knife", confidence: 80, box: [1, 2, 3, 4] };

test("confirmation, missing frames, camera isolation and duplicate boxes", () => {
    const p = new Policy();
    for (let i = 0; i < 7; i++) assert.equal(p.observe(1, [knife, knife]).length, 0);
    assert.equal(p.observe(2, [knife]).length, 0);
    p.observe(1, []);
    assert.equal(p.observe(1, [knife]).length, 0);
    for (let i = 0; i < 6; i++) p.observe(1, [knife]);
    assert.equal(p.observe(1, [knife]).length, 1);
});
test("cooldown survives stop/start, expires, and stale frames reset", () => {
    let now = 10000;
    const p = new Policy({ clock: () => now });
    for (let i = 0; i < 8; i++) p.observe(1, [knife]);
    p.markReported(1, "knife");
    p.clearStreaks();
    for (let i = 0; i < 8; i++) assert.equal(p.observe(1, [knife]).length, 0);
    now += 90000;
    assert.equal(p.observe(1, [knife]).length, 0);
    for (let i = 0; i < 6; i++) p.observe(1, [knife]);
    assert.equal(p.observe(1, [knife]).length, 1);
});
const html = readFileSync(new URL("../backend/src/main/resources/static/index.html", import.meta.url), "utf8");
const monitor = html.slice(html.indexOf("async function monitorMlFrame("), html.indexOf("\nfunction showMlIncident"));
function context(fetch) {
    const calls = { submitted: [], timers: [], stopped: 0 };
    const ctx = vm.createContext({
        mlMonitorActive: true, mlWebcamStream: {}, mlGeneration: 1, mlAbort: new AbortController(),
        mlDemoCamera: { value: "1" }, mlMonitorFrame: 0, mlPolicy: new Policy({ frames: 1 }),
        mlMonitorTrace: [], mlDemoLog: {}, mlDemoProgress: {}, mlProgressText: {}, mlMonitorTimer: null,
        ML_ENGINE: "http://localhost", ML_LIVE_CLASSES: new Set(["knife", "fire"]), FormData,
        captureWebcamBlob: async () => new Blob(["frame"]), fetch,
        drawDetectionResult: async () => {}, describeDetections: () => "knife",
        submitDetectionIncident: async label => { calls.submitted.push(label); return { incidentId: 1 }; },
        showMlIncident() {}, notify() {}, refreshAll: async () => {},
        stopMlMonitoring() { calls.stopped++; },
        setTimeout(fn) { calls.timers.push(fn); return 1; }
    });
    vm.runInContext(monitor, ctx);
    return { ctx, calls };
}
test("live loop reports all confirmed classes and schedules the next frame", async () => {
    const {ctx, calls} = context(async () => ({ ok: true, json: async () => ({
        detections: [knife, { ...knife, label: "fire" }]
    }) }));
    await ctx.monitorMlFrame();
    assert.deepEqual(calls.submitted, ["knife", "fire"]);
    assert.equal(calls.stopped, 0);
    assert.equal(calls.timers.length, 1);
    await ctx.monitorMlFrame();
    assert.equal(calls.submitted.length, 2);
});
test("stopping during inference prevents late incident submission", async () => {
    let reply;
    const {ctx, calls} = context(() => new Promise(resolve => { reply = resolve; }));
    const pending = ctx.monitorMlFrame();
    await new Promise(resolve => setImmediate(resolve));
    ctx.mlGeneration++;
    ctx.mlMonitorActive = false;
    reply({ ok: true, json: async () => ({ detections: [knife] }) });
    await pending;
    assert.equal(calls.submitted.length, 0);
    assert.equal(calls.timers.length, 0);
});
