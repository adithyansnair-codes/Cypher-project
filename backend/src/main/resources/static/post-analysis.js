(function (root) {
    "use strict";
    const weapons = new Set(["knife", "pistol", "gun", "rifle", "weapon"]);
    const rank = { NONE: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 };
    function summarize(detections, context = {}) {
        const groups = new Map();
        for (const item of detections) {
            const label = String(item.label).toLowerCase();
            const group = groups.get(label) || { label, count: 0, confidence: 0 };
            group.count++;
            group.confidence = Math.max(group.confidence, Number(item.confidence) || 0);
            groups.set(label, group);
        }
        const objects = [...groups.values()].map(item => {
            let severity = "NONE", reason = "Shown for context; no automatic incident.", action = "Continue monitoring.";
            if (weapons.has(item.label)) {
                severity = "CRITICAL";
                reason = "A possible weapon is visible. The gateway prioritises weapon detections for review.";
                action = "Review the image and surrounding scene. Follow the site's security response procedure if confirmed.";
            } else if (item.label === "fire") {
                severity = "CRITICAL"; reason = "Possible fire requires prompt review.";
                action = "Verify the scene and follow the site's fire response procedure if confirmed.";
            } else if (["smoke", "lying", "fall"].includes(item.label)) {
                severity = item.confidence >= 85 ? "HIGH" : "MEDIUM";
                reason = item.label === "smoke" ? "Possible smoke requires review."
                    : "A lying pose may need a welfare check; this is not proof of a fall.";
                action = "Review recent frames and the surrounding scene before escalating.";
            }
            return { ...item, severity, reason, action };
        }).sort((a, b) => rank[b.severity] - rank[a.severity] || b.confidence - a.confidence);
        return {
            at: new Date().toISOString(), ...context, objects,
            severity: objects[0]?.severity || "NONE",
            count: objects.reduce((sum, item) => sum + item.count, 0),
            summary: !objects.length ? "No objects were detected above the selected threshold. This does not establish that the scene is safe."
                : objects.some(item => item.severity !== "NONE") ? "A reportable class was detected. Review the evidence before confirming an incident."
                : "Objects were detected, but none match the automatic incident rules.",
            limitations: "Confidence is a model score, not the probability of danger. The model cannot establish intent or who is holding an object.",
            incidents: []
        };
    }
    root.CypherPostAnalysis = { summarize };
    if (typeof module !== "undefined") module.exports = { summarize };
})(globalThis);
