package com.gaspardbruno.staticsafeareainsets;

import android.app.Activity;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.PixelUtil;

import java.util.HashMap;
import java.util.Map;

public class RNStaticSafeAreaInsetsModule extends ReactContextBaseJavaModule
    implements LifecycleEventListener, ViewTreeObserver.OnPreDrawListener {

  private static final String INSETS_CHANGED = "RNStaticSafeAreaInsetsChanged";
  private int listenerCount;
  private View observedView;
  private Insets lastInsets;

  public RNStaticSafeAreaInsetsModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addLifecycleEventListener(this);
  }

  @Override
  public String getName() {
    return "RNStaticSafeAreaInsets";
  }

  private View getDecorView() {
    Activity activity = getReactApplicationContext().getCurrentActivity();
    return activity == null ? null : activity.getWindow().getDecorView();
  }

  private Insets getInsets(View view) {
    WindowInsetsCompat insets = view == null ? null : ViewCompat.getRootWindowInsets(view);
    return insets == null ? null : insets.getInsets(
        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
  }

  private Map<String, Object> toMap(Insets insets) {
    if (insets == null) {
      insets = Insets.NONE;
    }
    Map<String, Object> values = new HashMap<>();
    // Round outward so a fractional DIP never leaves part of a bar or cutout uncovered.
    values.put("safeAreaInsetsTop", (int) Math.ceil(PixelUtil.toDIPFromPixel(insets.top)));
    values.put("safeAreaInsetsBottom", (int) Math.ceil(PixelUtil.toDIPFromPixel(insets.bottom)));
    values.put("safeAreaInsetsLeft", (int) Math.ceil(PixelUtil.toDIPFromPixel(insets.left)));
    values.put("safeAreaInsetsRight", (int) Math.ceil(PixelUtil.toDIPFromPixel(insets.right)));
    return values;
  }

  @Override
  public Map<String, Object> getConstants() {
    return toMap(getInsets(getDecorView()));
  }

  @ReactMethod
  public void getSafeAreaInsets(Callback cb) {
    UiThreadUtil.runOnUiThread(() -> {
      Insets insets = getInsets(getDecorView());
      // A temporarily unavailable window must not overwrite a valid observed value with zero.
      if (insets == null) {
        insets = lastInsets;
      }
      maybeEmitInsets(insets);
      cb.invoke(Arguments.makeNativeMap(toMap(insets)));
    });
  }

  @ReactMethod
  public void addListener(String eventName) {
    if (!INSETS_CHANGED.equals(eventName)) {
      return;
    }
    UiThreadUtil.runOnUiThread(() -> {
      listenerCount++;
      startObserving();
    });
  }

  @ReactMethod
  public void removeListeners(double count) {
    UiThreadUtil.runOnUiThread(() -> {
      listenerCount = Math.max(0, listenerCount - (int) count);
      if (listenerCount == 0) {
        stopObserving();
      }
    });
  }

  private void startObserving() {
    View view = getDecorView();
    if (listenerCount == 0 || view == null || view == observedView) {
      return;
    }
    stopObserving();
    observedView = view;
    view.getViewTreeObserver().addOnPreDrawListener(this);
    // Observe rather than replace the app's window-insets listener.
    ViewCompat.requestApplyInsets(view);
    view.invalidate();
  }

  private void stopObserving() {
    if (observedView != null && observedView.getViewTreeObserver().isAlive()) {
      observedView.getViewTreeObserver().removeOnPreDrawListener(this);
    }
    observedView = null;
    lastInsets = null;
  }

  @Override
  public boolean onPreDraw() {
    maybeEmitInsets(getInsets(observedView));
    return true;
  }

  private void maybeEmitInsets(Insets insets) {
    if (listenerCount > 0 && insets != null && !insets.equals(lastInsets)) {
      lastInsets = insets;
      getReactApplicationContext()
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
          .emit(INSETS_CHANGED, Arguments.makeNativeMap(toMap(insets)));
    }
  }

  @Override
  public void onHostResume() {
    startObserving();
  }

  @Override
  public void onHostPause() {
    stopObserving();
  }

  @Override
  public void onHostDestroy() {
    stopObserving();
  }

  @Override
  public void invalidate() {
    getReactApplicationContext().removeLifecycleEventListener(this);
    UiThreadUtil.runOnUiThread(this::stopObserving);
    super.invalidate();
  }
}
