import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

const { RNStaticSafeAreaInsets } = NativeModules;
const emitter = Platform.OS === 'android'
  ? new NativeEventEmitter(RNStaticSafeAreaInsets)
  : null;

export default Object.assign(Object.create(RNStaticSafeAreaInsets), {
  addSafeAreaInsetsListener(callback) {
    // iOS continues to use getSafeAreaInsets when the window dimensions change.
    return emitter
      ? emitter.addListener('RNStaticSafeAreaInsetsChanged', callback)
      : { remove() {} };
  },
});
