package com.example.camera_fragment;

import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Map;

public class TimeStat {
    private Map<String, ArrayList<Long>> mDurationsAndTicksMap = new ArrayMap<>();
    private long mLastBegin;
    private static final String TAG = TimeStat.class.getSimpleName();

    public void startInterval() {
        mLastBegin = System.currentTimeMillis();
    }

    public void stopInterval(String label, int entries, boolean printMessage) {
        final long duration = System.currentTimeMillis() - mLastBegin;
        if (!mDurationsAndTicksMap.containsKey(label))
            mDurationsAndTicksMap.put(label, new ArrayList<Long>());
        final ArrayList<Long> intervals = mDurationsAndTicksMap.get(label);
        intervals.add(duration);
        while (intervals.size() > entries)
            intervals.remove(0);
        if (printMessage) {
            final float avgDurationMs = getAverageInterval(label);
            final int avgFps = (int) Math.round(1000. / Math.max(0.1, avgDurationMs));
            // frames per sec
            Log.d(TAG, "FPS: " + label + ": " + avgDurationMs + " (max fps: " + avgFps + ")");
        }
    }

    public float getAverageInterval(String label) {
        if (!mDurationsAndTicksMap.containsKey(label))
            return 0;
        final ArrayList<Long> intervals = mDurationsAndTicksMap.get(label);
        if (intervals.isEmpty())
            return 0;
        if (intervals.size() == 1)
            return intervals.get(0);
        long accum = 0;
        for (int i = 0; i < intervals.size(); i++)
            accum += intervals.get(i);
        return round2((float) accum / (float) intervals.size());
    }

    public void tick(String label, int entries) {
        long thisTime = System.currentTimeMillis();
        if (!mDurationsAndTicksMap.containsKey(label))
            mDurationsAndTicksMap.put(label, new ArrayList<Long>());
        final ArrayList<Long> ticks = mDurationsAndTicksMap.get(label);
        ticks.add(thisTime);
        while (ticks.size() > (entries + 1))
            ticks.remove(0);
    }

    public float getAverageTickFrequency(String label) {
        if (!mDurationsAndTicksMap.containsKey(label))
            return 0;
        final ArrayList<Long> ticks = mDurationsAndTicksMap.get(label);
        if (ticks.size() < 2)
            return 0;
        final long firstTime = ticks.get(0);
        final long lastTime = ticks.get(ticks.size() - 1);
        float avgTime = (float) (lastTime - firstTime) / (float) (ticks.size() - 1);
        return round2((float) (1000. / Math.max(0.1, avgTime)));
    }

    private static float round2(float value) {
        return Math.round(value * 10f) / 10f;
    }
}


