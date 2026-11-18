package com.constructionpilot;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;

// Build and remove clicks with their tick timing, and how fast you build.
// Measured: the Build click only opens the menu; planks go about a tick after the menu closes and the
// furniture appears a tick after that. A Remove click clears the space on the next tick.
@Singleton
class BuildTimer
{
	private static final int DEFAULT_BUILD_TICKS = 1;
	// Until enough builds are measured: about one plank per tick.
	private static final double DEFAULT_PLANKS_PER_TICK = 1.0;
	// From the menu closing, the build counts as done until its planks are taken.
	private static final int BUILD_COMMIT_TICKS = 2;
	// A build menu closing this long after the click is not that build's menu.
	private static final int BUILD_MENU_TICKS = 30;
	// A build that never produced furniture (cancelled menu, not enough planks) expires.
	private static final int PENDING_TICKS = 8;
	// A remove click that has not cleared the space by then (dialog closed, misclick) is dropped.
	private static final int REMOVE_CLICK_TICKS = 3;
	// Longer gaps are breaks, not your building rhythm.
	private static final int MAX_BUILD_GAP = 12;

	private final Client client;
	private final House house;
	private final TickLog tickLog;

	@Getter
	private double planksPerTick = DEFAULT_PLANKS_PER_TICK;
	private final RollingMedian buildGaps = new RollingMedian(8);
	private int lastBuildTick = -1;
	// Something other than building happened since the last build (talking to the butler, taking planks), so
	// that gap is not your building rhythm.
	private boolean interrupted;
	private boolean afterButler;
	private int buildTicks = DEFAULT_BUILD_TICKS;

	// The hotspot the last Build click went to, so the countdown follows the side actually built.
	private WorldPoint buildClickTile;
	private int buildClickTick = -1;
	private int buildCommitTick = -1;
	private int planksAtBuildCommit = -1;
	private List<WorldPoint> pendingBuild;
	private int pendingBuildTick = -1;

	private String removingKey;
	private int removeClickTick = -1;

	@Inject
	BuildTimer(Client client, House house, TickLog tickLog)
	{
		this.client = client;
		this.house = house;
		this.tickLog = tickLog;
	}

	void reset()
	{
		buildGaps.clear();
		lastBuildTick = -1;
		pendingBuild = null;
	}

	void clicked(String option, WorldPoint tile)
	{
		if ("Build".equals(option))
		{
			buildClickTile = tile;
			buildClickTick = client.getTickCount();
		}
		else if ("Remove".equals(option))
		{
			removingKey = house.spaceAt(tile);
			removeClickTick = client.getTickCount();
		}
		else
		{
			interrupted = true;
		}
	}

	void buildMenuClosed(int planks)
	{
		// A cancel simply never takes planks.
		if (buildClickTick >= 0 && client.getTickCount() - buildClickTick <= BUILD_MENU_TICKS)
		{
			buildCommitTick = client.getTickCount();
			planksAtBuildCommit = planks;
		}
	}

	void planksUsed(int before, int now, int perBuild)
	{
		if (before - now != perBuild)
		{
			return;
		}
		recordBuild(perBuild);
		List<WorldPoint> built = clickedSpace();
		if (built != null)
		{
			pendingBuild = built;
			pendingBuildTick = client.getTickCount();
		}
	}

	void planksGained()
	{
		interrupted = true;
		afterButler = true;
	}

	void butlerTalkedTo()
	{
		afterButler = true;
	}

	void furnitureAppeared(GameObject object)
	{
		if (pendingBuild != null && pendingBuild.contains(object.getWorldLocation()))
		{
			int took = client.getTickCount() - pendingBuildTick;
			if (took >= 0 && took <= 4)
			{
				buildTicks = took;
			}
			pendingBuild = null;
			tickLog.add("built after " + took + " ticks");
		}
	}

	void furnitureGone()
	{
		removingKey = null;
	}

	void tick()
	{
		if (pendingBuild != null && client.getTickCount() - pendingBuildTick > PENDING_TICKS)
		{
			pendingBuild = null;
		}
	}

	boolean isPending()
	{
		return pendingBuild != null;
	}

	// Fills in the build side of the snapshot. Between the menu closing and the planks going, the build already
	// counts: its planks as spent and its space as appearing.
	Snapshot.SnapshotBuilder describe(Snapshot.SnapshotBuilder snapshot, int planks, int perBuild)
	{
		int now = client.getTickCount();
		List<WorldPoint> inFlight = pendingBuild == null && buildCommitTick >= 0
			&& now - buildCommitTick <= BUILD_COMMIT_TICKS
			&& planks == planksAtBuildCommit && planks >= perBuild ? clickedSpace() : null;
		return snapshot
			.planks(inFlight != null ? planks - perBuild : planks)
			.planksPerTick(planksPerTick)
			.pendingBuild(inFlight != null ? inFlight : pendingBuild)
			.ticksUntilBuilt(inFlight != null ? buildTicks + 1 - (now - buildCommitTick) : pendingBuild == null ? 0 : buildTicks - (now - pendingBuildTick))
			.removing(removingKey != null && now - removeClickTick <= REMOVE_CLICK_TICKS ? removingKey : null)
			.afterButler(afterButler);
	}

	private List<WorldPoint> clickedSpace()
	{
		return client.getLocalPlayer() == null ? null : house.spaceTiles(buildClickTile, client.getLocalPlayer().getWorldLocation());
	}

	// Plank use from the median gap between recent builds; breaks and butler waits do not drag it down.
	private void recordBuild(int perBuild)
	{
		int tick = client.getTickCount();
		int gap = tick - lastBuildTick;
		if (lastBuildTick >= 0 && gap > 0 && gap <= MAX_BUILD_GAP && !interrupted)
		{
			planksPerTick = (double) perBuild / buildGaps.add(gap);
		}
		lastBuildTick = tick;
		interrupted = false;
		afterButler = false;
	}
}
