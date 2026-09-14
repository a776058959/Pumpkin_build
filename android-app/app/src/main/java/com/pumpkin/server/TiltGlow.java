package com.pumpkin.server;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * 手机倾斜 → 玻璃高光位置。
 *
 * 加速度计读数 + 指数低通滤波，映射到 [-1,1] 后喂给 GlassPanelDrawable.setTilt，
 * 让悬浮导航栏上的高光随手机倾斜流动（SukiSU Ultra 那种「液态玻璃」的关键一环）。
 *
 * 生命周期：MainActivity.onResume 里 start、onPause 里 stop，
 * 退到后台立刻注销监听，不耗电。注册失败（无传感器等）返回 null，按静态玻璃处理。
 */
public final class TiltGlow implements SensorEventListener {

    public interface Listener {
        void onTilt(float x, float y);
    }

    private final SensorManager sensors;
    private final Sensor accelerometer;
    private final Listener listener;

    private float smoothX;
    private float smoothY;
    private float lastX = Float.MIN_VALUE;
    private float lastY = Float.MIN_VALUE;

    private TiltGlow(SensorManager sensors, Sensor accelerometer, Listener listener) {
        this.sensors = sensors;
        this.accelerometer = accelerometer;
        this.listener = listener;
    }

    /** 开始监听；失败返回 null（调用方当静态玻璃用即可，不用判空重试）。 */
    public static TiltGlow start(Context ctx, Listener listener) {
        try {
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            if (sm == null) {
                return null;
            }
            Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (acc == null) {
                return null;
            }
            TiltGlow glow = new TiltGlow(sm, acc, listener);
            sm.registerListener(glow, acc, SensorManager.SENSOR_DELAY_GAME);
            return glow;
        } catch (Throwable t) {
            return null;
        }
    }

    public void stop() {
        try {
            if (sensors != null) {
                sensors.unregisterListener(this);
            }
        } catch (Throwable ignored) {
            // 注销失败无影响
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length < 2) {
            return;
        }
        float gx = clamp(event.values[0] / 9.81f);
        float gy = clamp(event.values[1] / 9.81f);
        // 低通滤波：高光要有「流」的感觉，不能跟着抖动一步一跳
        smoothX = smoothX * 0.82f + gx * 0.18f;
        smoothY = smoothY * 0.82f + gy * 0.18f;
        if (Math.abs(smoothX - lastX) + Math.abs(smoothY - lastY) < 0.006f) {
            return;
        }
        lastX = smoothX;
        lastY = smoothY;
        listener.onTilt(clamp(smoothX), clamp(smoothY));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 精度变化不影响
    }

    private static float clamp(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }
}
