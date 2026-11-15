package com.winlator.core;

import com.winlator.math.Mathf;
import com.winlator.renderer.GLRenderer;
import com.winlator.xserver.XServer;

import java.util.Timer;
import java.util.TimerTask;

public class CursorLocker extends TimerTask {
    private final XServer xServer;
    private float damping = 0.25f;
    private short maxDistance;
    private boolean enabled = true;
    private final Object pauseLock = new Object();

    public CursorLocker(XServer xServer) {
        this.xServer = xServer;
        maxDistance = (short)(xServer.screenInfo.width * 0.05f);
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(this, 0, 1000 / 60);
    }

    public short getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(short maxDistance) {
        this.maxDistance = maxDistance;
    }

    public float getDamping() {
        return damping;
    }

    public void setDamping(float damping) {
        this.damping = damping;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            synchronized (pauseLock) {
                this.enabled = true;
                pauseLock.notifyAll();
            }
        }
        else this.enabled = enabled;
    }

    @Override
    public void run() {
        synchronized (pauseLock) {
            if (!enabled) {
                try {
                    pauseLock.wait();
                }
                catch (InterruptedException e) {}
            }
        }

        /* keep the pointer on the part of the screen that is shown: Wine's desktop window */
        GLRenderer renderer = xServer.getRenderer();
        int left = renderer != null ? renderer.getSceneX() : 0;
        int top = renderer != null ? renderer.getSceneY() : 0;
        int right = left + (renderer != null ? renderer.getSceneWidth() : xServer.screenInfo.width);
        int bottom = top + (renderer != null ? renderer.getSceneHeight() : xServer.screenInfo.height);

        short x = (short)Mathf.clamp(xServer.pointer.getX(), left - maxDistance, right + maxDistance);
        short y = (short)Mathf.clamp(xServer.pointer.getY(), top - maxDistance, bottom + maxDistance);

        if (x < left) {
            xServer.pointer.setX((short)Math.ceil(left + (x - left) * damping));
        }
        else if (x >= right) {
            xServer.pointer.setX((short)Math.floor(right + (x - right) * damping));
        }
        if (y < top) {
            xServer.pointer.setY((short)Math.ceil(top + (y - top) * damping));
        }
        else if (y >= bottom) {
            xServer.pointer.setY((short)Math.floor(bottom + (y - bottom) * damping));
        }
    }
}
