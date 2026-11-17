package com.constructionpilot;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

// Plain game state for one tick, so Policy can be tested without the client.
@Value
@Builder(toBuilder = true)
class Snapshot
{
	boolean buildingMode;
	int planks;
	// Of the current method: 8 for an oak larder, 6 otherwise.
	int planksPerBuild;
	int plankSlots;
	int freeSlots;
	// Servant standing close enough to talk to.
	boolean butlerPresent;
	// Out on a bank trip (expected back), as opposed to wandered off or never seen.
	boolean butlerOnTrip;
	// Tile the servant stands on; a space under him is the worse pick when two are otherwise equal.
	WorldPoint butlerTile;
	int butlerHolding;
	// He just appeared and has not handed the planks over yet.
	boolean awaitingHandOver;
	ServantType servantType;
	// Measured leave to return ticks of the last trip; 0 uses the servant's known trip time.
	int tripTicks;
	// From the servant's "Fetch from bank: N x" option; 0 when not seen yet.
	int requestedFetch;
	int lastDelivery;
	double planksPerTick;
	int sendAtOverride;
	// Spots within reach, nearest first.
	List<Spot> spots;
	// Tiles of the space you just clicked Build on, until the furniture appears; null otherwise.
	List<WorldPoint> pendingBuild;
	// Space just cleared within the last few ticks; null once the rhythm has lapsed.
	String freshRemoved;
	// Space you just clicked Remove on; its furniture counts as gone so the other side lights up at once.
	String removing;
	int ticksUntilBuilt;
}
