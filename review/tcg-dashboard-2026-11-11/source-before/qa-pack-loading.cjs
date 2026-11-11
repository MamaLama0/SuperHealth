// Native bootstrap regression, including devices without WebGL. No browser dependency.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const assets = path.join(__dirname, '../app/src/superhealth/assets/arsenal');
function boot(native) {
  const messages = [], nodes = new Map();
  function node() {
    const classes = new Set(), properties = {};
    return {classList: {add: x => classes.add(x), remove: x => classes.delete(x),
      toggle: (x, on) => on ? classes.add(x) : classes.delete(x), contains: x => classes.has(x)},
      style: {setProperty: (k, v) => { properties[k] = v; }, getPropertyValue: k => properties[k]},
      addEventListener() {}, getContext() { return null; }, animate() {}, replaceChildren() {}};
  }
  const window = native ? {ArsenalHost: {action: (kind, value) => messages.push([kind, value])}} : {};
  const ctx = vm.createContext({window, document: {documentElement: node(),
    querySelector: selector => {if (!nodes.has(selector)) nodes.set(selector, node()); return nodes.get(selector);}},
    matchMedia: () => ({matches: true}), performance, setTimeout, clearTimeout});
  for (const file of ['directions.js', 'pack.js']) vm.runInContext(fs.readFileSync(path.join(assets, file), 'utf8'), ctx);
  return {ctx, messages, nodes, arena: window.arena};
}
const host = boot(true);
assert.equal(vm.runInContext('selected', host.ctx), false, 'Wait for the native selection');
assert.equal(host.nodes.get('.stage').style.getPropertyValue('--wrapper'), undefined, 'No default weapon image');
assert.deepEqual(host.messages, [['ready', '']]);
for (const [slot, slug] of [['chest', 'pec-1'], ['guard', 'dos-1'], ['legs', 'jam-1'], ['weapon', 'bra-2']]) {
  host.arena.select(slot, false, true, 0, '');
  assert.equal(host.nodes.get('.stage').style.getPropertyValue('--wrapper'), `url("${slug}-0.webp")`);
  assert.equal(host.nodes.get('.stage').classList.contains('loading-model'), false, 'Fallback cannot leave a loading overlay');
  assert.equal(host.messages.at(-1)[1], `${slot}|0|`);
}
host.arena.select('chest', true, true, 1, 'or-solaire');
assert.equal(host.nodes.get('.stage').style.getPropertyValue('--wrapper'), 'url("or-solaire-chest-0.webp")');
host.arena.select('unknown', false, false);
assert.equal(vm.runInContext('slot', host.ctx), 'chest', 'Ignore unknown families');
assert.equal(vm.runInContext('selected', boot(false).ctx), true, 'Standalone preview still boots');
console.log('Pack bootstrap, restored families, gold variants and fallback: OK');
