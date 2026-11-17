package com.constructionpilot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// Median of the last few samples, so one slow trip or a pause does not swing the estimate.
class RollingMedian
{
	private final int size;
	private final Deque<Integer> window = new ArrayDeque<>();

	RollingMedian(int size)
	{
		this.size = size;
	}

	int add(int sample)
	{
		window.addLast(sample);
		if (window.size() > size)
		{
			window.removeFirst();
		}
		List<Integer> sorted = new ArrayList<>(window);
		sorted.sort(null);
		return sorted.get(sorted.size() / 2);
	}

	void clear()
	{
		window.clear();
	}
}
