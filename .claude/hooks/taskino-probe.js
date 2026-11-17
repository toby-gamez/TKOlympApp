// SessionStart hook: one line, ~2 s ceiling. Says whether Taskino (the system of record) answers.
// It never starts anything -- Taskino is the owner's. Exits 0 always; the line is the output.
'use strict';
const http = require('http');
const url = 'http://localhost:8081/';
let done = false;
function say(line) { if (done) return; done = true; process.stdout.write(line + '\n'); process.exit(0); }
const req = http.get(url, { timeout: 2000 }, res => {
  res.resume();
  say('Taskino: reachable (HTTP ' + res.statusCode + ' at ' + url + ') -- run list_tasks before starting work.');
});
req.on('timeout', () => { req.destroy(new Error('timeout')); });
req.on('error', e => say('Taskino: UNREACHABLE at ' + url + ' (' + (e.code || e.message) + ') -- the systemd user service taskino is probably not running. Do NOT start or rebuild it; report and wait.'));
setTimeout(() => say('Taskino: UNREACHABLE (2 s timeout) -- do NOT start Taskino; report and wait.'), 2300);
