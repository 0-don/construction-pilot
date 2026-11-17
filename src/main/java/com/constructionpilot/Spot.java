package com.constructionpilot;

import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

// One build space near the player (a garden seating space is two halves, the table and larder one tile).
@Value
class Spot
{
	String key;
	List<WorldPoint> tiles;
	boolean built;
	// Null for furniture on a tile whose space was never seen.
	Space space;
}
