package com.gaspardbruno.staticsafeareainsets;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.PixelUtil;

import java.util.Map;
import java.util.HashMap;

import android.util.Log;
import android.view.WindowInsets;
import android.view.View;
import android.view.WindowInsetsController;
import android.os.Build;
import android.app.Activity;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;

public class RNStaticSafeAreaInsetsModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  public RNStaticSafeAreaInsetsModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNStaticSafeAreaInsets";
  }

  @Override
  public Map<String, Object> getConstants() {
    return this._getSafeAreaInsets();
  }

  private Map<String, Object> _getSafeAreaInsets() {
    final Map<String, Object> constants = new HashMap<>();

    Activity activity = getCurrentActivity();
    if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        final View view = activity.getWindow().getDecorView();
        final WindowInsetsCompat insetsCompat = ViewCompat.getRootWindowInsets(view);

        final boolean isFullscreen =
                (view.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_IMMERSIVE)
                        == View.SYSTEM_UI_FLAG_IMMERSIVE;

        if (insetsCompat != null) {
            final Insets sysBars = insetsCompat.getInsets(WindowInsetsCompat.Type.systemBars());
            float top = PixelUtil.toDIPFromPixel(sysBars.top);
            float bottom = PixelUtil.toDIPFromPixel(sysBars.bottom);
            float left = PixelUtil.toDIPFromPixel(sysBars.left);
            float right = PixelUtil.toDIPFromPixel(sysBars.right);

            constants.put("safeAreaInsetsTop", top);
            constants.put("safeAreaInsetsBottom", bottom);
            constants.put("safeAreaInsetsLeft", left);
            constants.put("safeAreaInsetsRight", right);
        } else {
            constants.put("safeAreaInsetsTop", 0f);
            constants.put("safeAreaInsetsBottom", 0f);
            constants.put("safeAreaInsetsLeft", 0f);
            constants.put("safeAreaInsetsRight", 0f);
        }
    } else {
        constants.put("safeAreaInsetsTop", 0f);
        constants.put("safeAreaInsetsBottom", 0f);
        constants.put("safeAreaInsetsLeft", 0f);
        constants.put("safeAreaInsetsRight", 0f);
    }

    return constants;
}

  @ReactMethod
  public void getSafeAreaInsets(Callback cb) {
    Map<String, Object> constants = this._getSafeAreaInsets();
    WritableMap map = new WritableNativeMap();

    map.putInt("safeAreaInsetsTop", ((Float) constants.get("safeAreaInsetsTop")).intValue());
    map.putInt("safeAreaInsetsBottom", ((Float) constants.get("safeAreaInsetsBottom")).intValue());
    map.putInt("safeAreaInsetsLeft", ((Float) constants.get("safeAreaInsetsLeft")).intValue());
    map.putInt("safeAreaInsetsRight", ((Float) constants.get("safeAreaInsetsRight")).intValue());

    cb.invoke(map);
  }
}
