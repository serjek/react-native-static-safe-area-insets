const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

async function load(platform, inherited = true) {
  const nativeListeners = new Set();
  const callbacks = new Set();
  const values = { safeAreaInsetsTop: 29, safeAreaInsetsBottom: 16,
    safeAreaInsetsLeft: 0, safeAreaInsetsRight: 0 };
  // TurboModuleBinding returns an initially empty object whose prototype exposes
  // native methods and constants. Object spread cannot copy those properties.
  const nativeProperties = {
    ...values,
    getSafeAreaInsets(callback) { callback(values); },
    addListener(event) { nativeListeners.add(event); },
    removeListeners() { nativeListeners.clear(); },
  };
  const nativeModule = inherited ? Object.create(nativeProperties) : nativeProperties;
  let emitterCount = 0;
  class NativeEventEmitter {
    constructor(module) {
      assert.equal(module, nativeModule);
      emitterCount++;
    }
    addListener(event, callback) {
      nativeModule.addListener(event);
      callbacks.add(callback);
      return { remove() { callbacks.delete(callback); nativeModule.removeListeners(1); } };
    }
  }
  const context = vm.createContext({});
  const reactNative = new vm.SyntheticModule(
    ['NativeModules', 'NativeEventEmitter', 'Platform'], function () {
      this.setExport('NativeModules', { RNStaticSafeAreaInsets: nativeModule });
      this.setExport('NativeEventEmitter', NativeEventEmitter);
      this.setExport('Platform', { OS: platform });
    }, { context });
  const module = new vm.SourceTextModule(
    fs.readFileSync(path.join(__dirname, '../index.js'), 'utf8'), { context });
  await module.link((specifier) => {
    assert.equal(specifier, 'react-native');
    return reactNative;
  });
  await module.evaluate();
  return { api: module.namespace.default, nativeListeners, emitterCount, values,
    emit(insets) { for (const callback of callbacks) callback(insets); } };
}

test('Android preserves snapshots and delivers late inset changes until removed', async () => {
  const { api, nativeListeners, emit, values } = await load('android');
  assert.equal(api.safeAreaInsetsTop, 29);
  let reads = 0;
  api.getSafeAreaInsets((insets) => { assert.equal(insets, values); reads++; });
  const received = [];
  const subscription = api.addSafeAreaInsetsListener((insets) => received.push(insets));
  assert.ok(nativeListeners.has('RNStaticSafeAreaInsetsChanged'));
  emit({ ...values, safeAreaInsetsTop: 0 });
  emit(values); // The status bar appears without another dimensions event.
  emit({ ...values, safeAreaInsetsBottom: 25 });
  assert.deepEqual(received.map((insets) => insets.safeAreaInsetsTop), [0, 29, 29]);
  assert.equal(received[2].safeAreaInsetsBottom, 25);
  assert.equal(reads, 1, 'Native reads must remain one-shot');
  subscription.remove();
  emit(values);
  assert.equal(received.length, 3);
  assert.equal(nativeListeners.size, 0);
});

test('iOS keeps the existing callback without registering unsupported native events', async () => {
  const { api, emitterCount, nativeListeners, values } = await load('ios');
  assert.equal(emitterCount, 0);
  api.getSafeAreaInsets((insets) => assert.equal(insets, values));
  const subscription = api.addSafeAreaInsetsListener(() => assert.fail('Unexpected event'));
  subscription.remove();
  assert.equal(nativeListeners.size, 0);
});

test('legacy modules with enumerable own properties keep their native API', async () => {
  for (const platform of ['android', 'ios']) {
    const { api, values } = await load(platform, false);
    assert.equal(api.safeAreaInsetsTop, values.safeAreaInsetsTop);
    assert.equal(api.safeAreaInsetsBottom, values.safeAreaInsetsBottom);
    assert.equal(api.safeAreaInsetsLeft, values.safeAreaInsetsLeft);
    assert.equal(api.safeAreaInsetsRight, values.safeAreaInsetsRight);
    let reads = 0;
    api.getSafeAreaInsets((insets) => { assert.equal(insets, values); reads++; });
    assert.equal(reads, 1);
  }
});
