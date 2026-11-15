package com.winlator.xserver;

import com.winlator.core.Bitmask;
import com.winlator.math.Mathf;
import com.winlator.renderer.GLRenderer;

import java.util.ArrayList;

public class Pointer {
    public enum Button {
        BUTTON_LEFT, BUTTON_MIDDLE, BUTTON_RIGHT, BUTTON_SCROLL_UP, BUTTON_SCROLL_DOWN, BUTTON_SCROLL_CLICK_LEFT, BUTTON_SCROLL_CLICK_RIGHT;

        public byte code() {
            return (byte)(ordinal() + 1);
        }

        public int flag() {
            return 1<<(code() + MAX_BUTTONS);
        }
    }
    public static final byte MAX_BUTTONS = 7;
    private final ArrayList<OnPointerMotionListener> onPointerMotionListeners = new ArrayList<>();
    private final Bitmask buttonMask = new Bitmask();
    private final XServer xServer;
    private short x;
    private short y;

    public interface OnPointerMotionListener {
        default void onPointerButtonPress(Button button) {}
        default void onPointerButtonRelease(Button button) {}
        default void onPointerMove(short x, short y) {}
    }

    public Pointer(XServer xServer) {
        this.xServer = xServer;
    }

    public void setX(int x) {
        this.x = (short)x;
    }

    public void setY(int y) {
        this.y = (short)y;
    }

    public short getX() {
        return x;
    }

    public short getY() {
        return y;
    }

    /* clamped to the part of the screen the renderer shows (Wine's desktop window) */
    public short getClampedX() {
        GLRenderer renderer = xServer.getRenderer();
        int min = renderer != null ? Math.max(0, renderer.getSceneX()) : 0;
        int max = renderer != null ? Math.min(xServer.screenInfo.width, renderer.getSceneX() + renderer.getSceneWidth()) : xServer.screenInfo.width;
        if (max <= min) { min = 0; max = xServer.screenInfo.width; }
        return (short)Mathf.clamp(x, min, max - 1);
    }

    public short getClampedY() {
        GLRenderer renderer = xServer.getRenderer();
        int min = renderer != null ? Math.max(0, renderer.getSceneY()) : 0;
        int max = renderer != null ? Math.min(xServer.screenInfo.height, renderer.getSceneY() + renderer.getSceneHeight()) : xServer.screenInfo.height;
        if (max <= min) { min = 0; max = xServer.screenInfo.height; }
        return (short)Mathf.clamp(y, min, max - 1);
    }

    public void setPosition(int x, int y) {
        setX(x);
        setY(y);
        triggerOnPointerMove(this.x, this.y);
    }

    public Bitmask getButtonMask() {
        return buttonMask;
    }

    public void setButton(Button button, boolean pressed) {
        boolean oldPressed = isButtonPressed(button);
        buttonMask.set(button.flag(), pressed);
        if (oldPressed != pressed) {
            if (pressed) {
                triggerOnPointerButtonPress(button);
            }
            else triggerOnPointerButtonRelease(button);
        }
    }

    public boolean isButtonPressed(Button button) {
        return buttonMask.isSet(button.flag());
    }

    public void addOnPointerMotionListener(OnPointerMotionListener onPointerMotionListener) {
        onPointerMotionListeners.add(onPointerMotionListener);
    }

    public void removeOnPointerMotionListener(OnPointerMotionListener onPointerMotionListener) {
        onPointerMotionListeners.remove(onPointerMotionListener);
    }

    private void triggerOnPointerButtonPress(Button button) {
        for (int i = onPointerMotionListeners.size()-1; i >= 0; i--) {
            onPointerMotionListeners.get(i).onPointerButtonPress(button);
        }
    }

    private void triggerOnPointerButtonRelease(Button button) {
        for (int i = onPointerMotionListeners.size()-1; i >= 0; i--) {
            onPointerMotionListeners.get(i).onPointerButtonRelease(button);
        }
    }

    private void triggerOnPointerMove(short x, short y) {
        for (int i = onPointerMotionListeners.size()-1; i >= 0; i--) {
            onPointerMotionListeners.get(i).onPointerMove(x, y);
        }
    }
}
