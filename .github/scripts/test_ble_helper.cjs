// Host-side behavioral tests. The VM mocks Shelly APIs and byte-string .at();
// it does NOT emulate the firmware allocator, BLE radio, or native RPC client.
// Run: node .github/scripts/test_ble_helper.cjs [optional previous helper file]
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const source = fs.readFileSync(path.join(__dirname, "../../Scripts/HubitatBLEHelper.js"), "utf8");
const bytes = (...values) => String.fromCharCode(...values);

function runtime(code = source, legacy = false) {
  const calls = [];
  const timers = [];
  const logs = [];
  let now = 0;
  let scan;
  let options;
  let fail = false;
  const storage = new Map();
  const scanner = {
    SCAN_START: 0, SCAN_STOP: 1, SCAN_RESULT: 2, INFINITE_SCAN: -1,
    isRunning: () => true,
  };
  scanner[legacy ? "Start" : "start"] = opts => (options = opts);
  scanner[legacy ? "Subscribe" : "subscribe"] = callback => { scan = callback; };
  const ctx = vm.createContext({
    print: (...args) => logs.push(args),
    Timer: {
      set: (ms, repeat, callback, data) => {
        timers.push({ ms, repeat, callback, data });
        return timers.length;
      },
      clear: id => { timers[id - 1] = null; },
    },
    BLE: { Scanner: scanner, GAP: { parseManufacturerDataByVendor: data => data } },
    Script: { storage: {
      getItem: key => storage.has(key) ? storage.get(key) : null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key),
    } },
    Shelly: {
      getUptimeMs: () => now,
      getComponentConfig: () => ({ rpc: { enable: true } }),
      call: (method, params, callback, data) => {
        if (method === "KVS.Get") {
          callback({ value: "hubitat.example" }, 0);
          return;
        }
        if (fail) throw Error("RPC unavailable");
        calls.push({ method, params, callback, data });
      },
    },
  });
  // Shelly strings contain bytes and .at() returns a number, unlike Node.
  vm.runInContext("String.prototype.at = function(i) { return this.charCodeAt(i); };", ctx);
  vm.runInContext(code, ctx);
  const evaluate = expression => vm.runInContext(expression, ctx);
  return {
    calls, timers, logs, ctx, evaluate,
    get options() { return options; },
    advance: ms => { now += ms; },
    fail: value => { fail = value; },
    tick: () => {
      for (const timer of timers.slice()) {
        if (timer) timer.callback(timer.data);
      }
    },
    scan: (data, addr = "aa:bb:cc:dd:ee:ff", extra = {}) =>
      scan(2, { addr, rssi: -60, service_data: { fcd2: data }, ...extra }),
    complete: (index, error = 0) => {
      const call = calls[index];
      call.callback({ code: 200 }, error, error ? "timeout" : "", call.data);
    },
    decode: data => JSON.parse(JSON.stringify(ctx.decodeBTHome(data))),
  };
}

// Fixtures cover every forwarded field, signs, scales, repeated button IDs,
// metadata, ignored field widths, and the trigger-based header bit.
const r = runtime();
assert.deepEqual(r.decode(bytes(0x40, 2, 0xc4, 9, 3, 0xbf, 0x13)), {
  temperature: 25, humidity: 50.550000000000004,
});
assert.deepEqual(r.decode(bytes(0x44, 0, 255, 1, 97, 2, 0x9c, 0xff,
  5, 0x13, 0x8a, 0x14, 0x21, 1, 0x2d, 0, 0x3a, 1, 0x3a, 2,
  0x3c, 1, 2, 0x3f, 0xf6, 0xff, 0x40, 12, 0, 0x45, 0xf6, 0xff,
  0x60, 3, 0xf0, 2, 0)), {
  pid: 255, battery: 97, temperature: [-1, -1], illuminance: 13460.67,
  motion: 1, window: 0, button: [1, 2], dimmer: 513, rotation: -1,
  distanceMm: 12, channel: 3, device_type_id: 2,
});
assert.deepEqual(r.decode(bytes(0x40, 4, 1, 2, 3, 0x3e, 1, 2, 3, 4,
  0xf1, 1, 2, 3, 4, 0xf2, 1, 2, 3, 0x2e, 55)), { humidity: 55 });
for (const data of ["", bytes(0x41, 1, 99), bytes(0x20, 1, 99), "x".repeat(256)]) {
  assert.equal(r.decode(data), null);
}
assert.deepEqual(r.decode(bytes(0x40, 1, 99, 2, 0)), { battery: 99 });
assert.deepEqual(r.decode(bytes(0x40, 1, 99, 0xff, 0, 1, 10)), { battery: 99 });
assert.equal(r.options.active, false);
assert.equal(r.timers.length, 1);
assert.equal(r.timers[0].repeat, true);

// Duplicate flood must never enter the decoder after the first advertisement.
r.scan(bytes(0x44, 0, 1, 0x3a, 1), undefined, {
  advData: bytes(0x0b, 2, 0), local_name: "SBBT-002C",
});
const decode = r.ctx.decodeBTHome;
let decodedCalls = 0;
r.ctx.decodeBTHome = data => { decodedCalls++; return decode(data); };
for (let i = 0; i < 10000; i++) r.scan(bytes(0x44, 0, 1, 0x3a, 1));
assert.equal(decodedCalls, 0);
assert.equal(r.calls.length, 0, "BLE callback must not start HTTP");
r.tick();
assert.equal(r.calls.length, 1);
assert.equal(r.calls[0].params.timeout, 10);
assert.equal(r.calls[0].params.url, "http://hubitat.example:39501/webhook/ble/0");
assert.deepEqual(JSON.parse(r.calls[0].params.body), {
  dst: "ble", messages: [{
    pid: 1, button: 1, cid: 0, mac: "AABBCCDDEEFF",
    modelId: 2, model: "SBBT-002C", rssi: -60,
  }],
});

// Simulate a stalled RPC for much longer than the former JS watchdog.
for (let i = 0; i < 10000; i++) {
  r.advance(100);
  r.scan(bytes(0x44, 0, i % 256, 0x3a, 1), i.toString(16).padStart(12, "0"));
  r.tick();
}
assert.equal(r.calls.length, 1, "No overlapping requests, even without callback");
assert.equal(r.evaluate("deviceCacheKeys.length"), 32);
assert.equal(r.evaluate("Object.keys(deviceCache).length"), 32);
assert.equal(r.evaluate("pendingReportCount()"), 8);
assert.ok(r.evaluate("pendingReportBytes() <= MAX_PENDING_BYTES"));
assert.ok(r.evaluate("typeof readPendingReport(0) === 'string'"));
assert.ok(r.evaluate("droppedReports > 0"));
assert.ok(r.logs.length < 10, "Overload must not log once per dropped packet");
r.complete(0, -104);
assert.equal(r.calls.length, 1, "HTTP callback must unwind before next POST");
r.tick();
assert.equal(r.calls.length, 2);
assert.ok(Buffer.byteLength(r.calls[1].params.body) <= 1024);
assert.equal(JSON.parse(r.calls[1].params.body).dst, "ble");

// Byte-bound queue, multi-POST FIFO draining, oversize drop, RPC throw recovery.
const q = runtime();
for (let i = 0; i < 8; i++) q.ctx.sendBleReport({ pid: i, model: "x".repeat(650) });
assert.ok(q.evaluate("pendingReportCount() < MAX_PENDING"));
assert.ok(q.evaluate("pendingReportBytes() <= MAX_PENDING_BYTES"));
q.ctx.sendBleReport({ model: "x".repeat(800) });
const expected = JSON.parse(q.evaluate("(function(){var a=[]; for(var i=0;i<MAX_PENDING;i++){var x=readPendingReport(i); if(x===null) break; a.push(JSON.parse(x));} return JSON.stringify(a);})()")).map(x => x.pid);
const received = [];
while (q.evaluate("pendingReportCount()")) {
  q.tick();
  const index = q.calls.length - 1;
  assert.ok(Buffer.byteLength(q.calls[index].params.body) <= 1024);
  received.push(...JSON.parse(q.calls[index].params.body).messages.map(x => x.pid));
  q.complete(index);
}
assert.deepEqual(received, expected);
assert.equal(q.evaluate("pendingReportBytes()"), 0);
q.fail(true);
q.ctx.sendBleReport({ pid: 9 });
q.tick();
assert.equal(q.evaluate("httpInFlight"), false);
q.fail(false);
q.ctx.sendBleReport({ pid: 10 });
q.tick();
assert.equal(JSON.parse(q.calls.at(-1).params.body).messages[0].pid, 10);

// PID rollover, optional PID, TTL expiry, cached model, legacy scan API.
const c = runtime(source, true);
c.scan(bytes(0x44, 0, 255, 0x3a, 1, 0xf0, 2, 0));
c.scan(bytes(0x44, 0, 0, 0x3a, 2));
c.scan(bytes(0x40, 1, 99));
c.scan(bytes(0x40, 1, 98));
c.advance(30 * 60 * 1000 + 1);
c.scan(bytes(0x44, 0, 0, 0x3a, 3));
assert.equal(c.evaluate("pendingReportCount()"), 5);
assert.equal(JSON.parse(c.evaluate("readPendingReport(1)")).modelId, 2);
assert.equal(JSON.parse(c.evaluate("readPendingReport(4)")).modelId, undefined);
assert.equal(c.options.active, false);
c.tick();
assert.equal(c.calls.length, 1);

// Optional differential check against the pre-edit legacy decoder: all known
// object widths, forwarded values and truncation points, not source snapshots.
if (process.argv[2]) {
  const old = runtime(fs.readFileSync(process.argv[2], "utf8"));
  old.scan(bytes(0x44, 0, 1, 0x3a, 1));
  old.scan(bytes(0x44, 0, 2, 0x3a, 2));
  old.advance(15000);
  old.tick();
  assert.equal(old.calls.length, 2,
    "Pre-fix watchdog starts a second RPC before the first completes");
  console.log("Reproduced pre-fix overlapping HTTP requests.");
  const definitions = old.ctx.getLegacyBthomeDefinitions();
  let comparisons = 0;
  for (const key of Object.keys(definitions)) {
    const id = Number(key);
    const size = old.ctx.getByteSize(definitions[key][1]);
    for (const fill of [0, 1, 127, 128, 255]) {
      const payload = bytes(0x40, id, ...Array(size).fill(fill), 0x3a, 2);
      for (let end = 1; end <= payload.length; end++) {
        const input = payload.slice(0, end);
        const expected = old.decode(input);
        for (const field of Object.keys(expected)) {
          if (field.startsWith("_")) delete expected[field];
        }
        assert.deepEqual(r.decode(input), expected, "object " + id + " length " + end);
        comparisons++;
      }
    }
  }
  console.log("Differential decoder comparisons:", comparisons);
}
console.log("BLE helper behavioral and stress tests passed (not a firmware heap test).");
