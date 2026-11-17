package com.constructionpilot;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

// What to click now, and faintly what comes right after, so the mouse can already move there.
@Value
@Builder(toBuilder = true)
class Decision
{
	static final Decision IDLE = Decision.builder().step(Step.IDLE).build();

	Step step;
	// Tiles of the space to click for BUILD or REMOVE (both halves of a garden bench); null otherwise.
	List<WorldPoint> target;
	int fetchAmount;
	int sendAt;
	// For REMOVE_SOON: ticks until the furniture being built appears and can be removed.
	int countdown;
	// The build right after a remove, shown faintly so the mouse can already move there.
	List<WorldPoint> next;
	Step nextStep;
	// After this build the butler is due: show him faintly so the mouse can get there early.
	boolean butlerNext;
}
