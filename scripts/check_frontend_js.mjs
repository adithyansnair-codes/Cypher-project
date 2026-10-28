import fs from "node:fs";
import vm from "node:vm";

const html = fs.readFileSync(
  new URL("../backend/src/main/resources/static/index.html", import.meta.url),
  "utf8"
);

const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi)]
  .map(match => match[1])
  .filter(source => source.trim());

if (scripts.length === 0) {
  throw new Error("No inline cockpit scripts found");
}

scripts.forEach((source, index) => {
  new vm.Script(source, { filename: `cockpit-inline-${index + 1}.js` });
});

console.log(`Validated ${scripts.length} cockpit script block(s).`);
