package com.constructionpilot;

import javax.inject.Singleton;

// Turns a tick countdown into seconds that run smoothly between ticks.
@Singleton
class TickClock
{
	private static final int TICK_MILLIS = 600;

	private long lastTickMillis;

	void tick()
	{
		lastTickMillis = System.currentTimeMillis();
	}

	double secondsUntil(int ticks)
	{
		long sinceTick = Math.min(TICK_MILLIS, System.currentTimeMillis() - lastTickMillis);
		return Math.max(0, ticks * TICK_MILLIS - sinceTick) / 1000.0;
	}
}
