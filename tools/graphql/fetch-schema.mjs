// Converts an introspection JSON result (stdin file arg) into SDL.
import { readFileSync, writeFileSync } from "node:fs";
import { buildClientSchema, printSchema } from "graphql";

const [input, output] = process.argv.slice(2);
if (!input || !output) {
  console.error("usage: node fetch-schema.mjs <introspection.json> <out.graphql>");
  process.exit(2);
}
const json = JSON.parse(readFileSync(input, "utf8"));
if (json.errors || !json.data) {
  console.error("Introspection failed:", JSON.stringify(json.errors ?? json).slice(0, 500));
  process.exit(1);
}
writeFileSync(output, printSchema(buildClientSchema(json.data)) + "\n");
