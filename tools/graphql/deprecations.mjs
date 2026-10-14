// Deprecated-field report for the app's GraphQL operations.
//
//   node deprecations.mjs --schema <schema.graphql> --ops <dir> [--old <older schema.graphql>] [--fail-on-used]
//
// Without --old: lists every deprecated schema element that the operations use.
// With --old:    lists elements that became deprecated since <old> and says whether the app uses them.
// Exit code 1 only with --fail-on-used and when the app uses a (newly) deprecated element.
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import {
  buildSchema, parse, visit, visitWithTypeInfo, TypeInfo,
  getNamedType, isObjectType, isInterfaceType, isInputObjectType, isEnumType,
} from "graphql";

const args = process.argv.slice(2);
const opt = (name) => { const i = args.indexOf(`--${name}`); return i >= 0 ? args[i + 1] : undefined; };
const schemaPath = opt("schema"), opsDir = opt("ops"), oldPath = opt("old");
const failOnUsed = args.includes("--fail-on-used");
if (!schemaPath || !opsDir) {
  console.error("usage: node deprecations.mjs --schema <file> --ops <dir> [--old <file>] [--fail-on-used]");
  process.exit(2);
}

const load = (p) => buildSchema(readFileSync(p, "utf8"));
const schema = load(schemaPath);

/** All deprecated coordinates ("Type.field", "Type.field(arg)", "Enum.VALUE") -> reason. */
function deprecatedElements(s) {
  const out = new Map();
  for (const type of Object.values(s.getTypeMap())) {
    if (type.name.startsWith("__")) continue;
    if (isObjectType(type) || isInterfaceType(type)) {
      for (const f of Object.values(type.getFields())) {
        if (f.deprecationReason != null) out.set(`${type.name}.${f.name}`, f.deprecationReason);
        for (const a of f.args) if (a.deprecationReason != null) out.set(`${type.name}.${f.name}(${a.name})`, a.deprecationReason);
      }
    } else if (isInputObjectType(type)) {
      for (const f of Object.values(type.getFields()))
        if (f.deprecationReason != null) out.set(`${type.name}.${f.name}`, f.deprecationReason);
    } else if (isEnumType(type)) {
      for (const v of type.getValues()) if (v.deprecationReason != null) out.set(`${type.name}.${v.name}`, v.deprecationReason);
    }
  }
  return out;
}

/** Coordinate -> [operation files] for every element the operations select/pass. */
function usedElements(dir) {
  const used = new Map();
  const add = (coord, file) => { if (!used.has(coord)) used.set(coord, new Set()); used.get(coord).add(file); };
  for (const file of readdirSync(dir).filter((f) => f.endsWith(".graphql")).sort()) {
    const info = new TypeInfo(schema);
    visit(parse(readFileSync(join(dir, file), "utf8")), visitWithTypeInfo(info, {
      Field() {
        const def = info.getFieldDef(), parent = info.getParentType();
        if (def && parent) add(`${parent.name}.${def.name}`, file);
      },
      Argument() {
        const arg = info.getArgument(), def = info.getFieldDef(), parent = info.getParentType();
        if (arg && def && parent) add(`${parent.name}.${def.name}(${arg.name})`, file);
      },
      EnumValue() {
        const v = info.getEnumValue(), t = getNamedType(info.getInputType());
        if (v && t) add(`${t.name}.${v.name}`, file);
      },
    }));
  }
  return used;
}

// Input-object fields need the AST node (for the name), so they get their own pass.
function usedInputFields(dir, used) {
  for (const file of readdirSync(dir).filter((f) => f.endsWith(".graphql")).sort()) {
    const info = new TypeInfo(schema);
    visit(parse(readFileSync(join(dir, file), "utf8")), visitWithTypeInfo(info, {
      ObjectField(node) {
        const parent = getNamedType(info.getParentInputType());
        if (parent) {
          const coord = `${parent.name}.${node.name.value}`;
          if (!used.has(coord)) used.set(coord, new Set());
          used.get(coord).add(file);
        }
      },
    }));
  }
}

const used = usedElements(opsDir);
usedInputFields(opsDir, used);
const deprecatedNow = deprecatedElements(schema);
let usedDeprecated = 0;

if (oldPath) {
  const deprecatedBefore = deprecatedElements(load(oldPath));
  const fresh = [...deprecatedNow].filter(([c]) => !deprecatedBefore.has(c));
  console.log(`### Newly deprecated schema elements (${fresh.length})`);
  if (fresh.length === 0) console.log("None.");
  for (const [coord, reason] of fresh) {
    const files = used.get(coord);
    if (files) usedDeprecated++;
    console.log(`- \`${coord}\` — ${reason ? `reason: "${reason}"` : "no reason given"} — ` +
      (files ? `**USED by the app**: ${[...files].join(", ")}` : "not used by the app"));
  }
} else {
  const hits = [...deprecatedNow].filter(([c]) => used.has(c));
  console.log(`### Deprecated elements used by the app (${hits.length} of ${deprecatedNow.size} deprecated in schema)`);
  if (hits.length === 0) console.log("None.");
  for (const [coord, reason] of hits) {
    usedDeprecated++;
    console.log(`- \`${coord}\` (${reason}) used in: ${[...used.get(coord)].join(", ")}`);
  }
}
process.exit(failOnUsed && usedDeprecated > 0 ? 1 : 0);
