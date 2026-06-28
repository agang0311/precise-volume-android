function planFineTune(targetPercent, maxVolume) {
  const clamped = Math.max(0, Math.min(100, targetPercent));
  const max = Math.max(1, maxVolume);
  let index = clamped <= 0 ? 0 : Math.max(1, Math.ceil((clamped * max) / 100));
  index = Math.min(index, max);
  const coarsePercent = (index * 100) / max;
  const gain = index === 0 ? 0 : Math.max(0, Math.min(1, clamped / coarsePercent));
  return { index, gain };
}

function assertEqual(actual, expected, message) {
  if (actual !== expected) throw new Error(`${message}: expected ${expected}, got ${actual}`);
}

function assertClose(actual, expected, message) {
  if (Math.abs(actual - expected) > 0.000001) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

const p25 = planFineTune(2.5, 15);
const p50 = planFineTune(5.0, 15);
assertEqual(p25.index, 1, "2.5% uses first non-zero system step");
assertEqual(p50.index, 1, "5% uses first non-zero system step");
assertClose(p25.gain / p50.gain, 0.5, "2.5% is half of 5%");

let enabled = false;
function start() {
  enabled = true;
}
function stop() {
  enabled = false;
}
start();
assertEqual(enabled, true, "ON state changes immediately");
stop();
assertEqual(enabled, false, "OFF state changes immediately");

console.log("state tests passed");
