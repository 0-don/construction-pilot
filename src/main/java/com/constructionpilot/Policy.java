package com.constructionpilot;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

final class Policy
{
	private static final int DEFAULT_TRIP_TICKS = 12;
	// Remove click to the space being free again, from the tick logs (click, gone one tick later).
	static final int REMOVE_TICKS = 2;

	private Policy()
	{
	}

	static Decision decide(Snapshot s)
	{
		List<Spot> spots = s.getSpots();
		if (!s.isBuildingMode() || spots == null || spots.isEmpty())
		{
			return Decision.IDLE;
		}

		int fetch = fetchAmount(s);
		int sendAt = sendAt(s, fetch);
		int planks = s.getPlanks();
		int holding = s.getButlerHolding();
		int perBuild = s.getPlanksPerBuild();

		// Back but not handed over yet (you were busy): he has your planks, so he is next, or now when you are out.
		boolean waitingOnHim = s.isButlerPresent() && s.isAwaitingHandOver();
		boolean handedOver = s.isButlerPresent() && !s.isAwaitingHandOver();
		Decision bench = benchDecision(s, spots, waitingOnHim || handedOver && holding > 0);
		// Faint butler on a build only when sending is really due once it stands: the send point is worked out
		// again for that state, so the hint never flips to a remove.
		if (bench.getStep() == Step.BUILD && handedOver && holding == 0 && wantsSend(afterBuild(s, bench.getTarget())))
		{
			bench = bench.toBuilder().butlerNext(true).build();
		}

		// Take only when it is worth a click: everything he holds fits, or the next build needs planks.
		boolean allFit = s.getFreeSlots() >= holding;
		boolean wantsTake = holding > 0 && (allFit || planks < perBuild);
		boolean wantsSend = wantsSend(s);

		Step butlerStep = null;
		if (waitingOnHim && planks < perBuild)
		{
			butlerStep = Step.TAKE_PLANKS;
		}
		// Needed now but neither here nor on a trip: he wandered off, so ring for him.
		else if (!s.isButlerPresent() && !s.isButlerOnTrip() && s.getServantType() != null && (wantsTake || wantsSend))
		{
			butlerStep = Step.CALL_BUTLER;
		}
		else if (handedOver && holding > 0 && (allFit || (planks < perBuild && s.getFreeSlots() > 0)))
		{
			butlerStep = Step.TAKE_PLANKS;
		}
		else if (handedOver && wantsSend)
		{
			butlerStep = Step.SEND_BUTLER;
		}

		if (butlerStep == null)
		{
			return bench.toBuilder().fetchAmount(fetch).sendAt(sendAt).build();
		}
		// No faint bench while the butler is due: it drew clicks to the bench first and moved as benches changed.
		return Decision.builder().step(butlerStep).fetchAmount(fetch).sendAt(sendAt).build();
	}

	// He holds nothing and the planks left are at the send point, or would run dry before he is back: a few planks
	// he has to hold cost one take click, waiting with nothing to build costs his whole trip.
	private static boolean wantsSend(Snapshot s)
	{
		boolean runsDry = s.getSendAtOverride() == 0 && s.getPlanks() - usedDuringTrip(s) < s.getPlanksPerBuild();
		return s.getButlerHolding() == 0 && (s.getPlanks() <= sendAt(s, fetchAmount(s)) || runsDry);
	}

	// The state right after building on the target: its planks spent and its space appearing.
	private static Snapshot afterBuild(Snapshot s, List<WorldPoint> target)
	{
		List<WorldPoint> space = target;
		for (Spot spot : s.getSpots())
		{
			if (spot.getTiles().contains(target.get(0)))
			{
				space = spot.getTiles();
			}
		}
		return s.toBuilder().planks(s.getPlanks() - s.getPlanksPerBuild()).pendingBuild(space).removing(null).build();
	}

	private static Decision benchDecision(Snapshot s, List<Spot> spots, boolean butlerNext)
	{
		// Waiting for the bench or table to appear: count down so the remove lands on the tick it shows up.
		if (s.getPendingBuild() != null)
		{
			return Decision.builder().step(Step.REMOVE_SOON).target(s.getPendingBuild()).countdown(Math.max(0, s.getTicksUntilBuilt())).build();
		}
		// A remove already clicked counts as done: the build click on the other side can follow on the next tick.
		if (s.getRemoving() != null)
		{
			List<Spot> after = new ArrayList<>();
			for (Spot spot : spots)
			{
				after.add(spot.getKey().equals(s.getRemoving()) ? new Spot(spot.getKey(), spot.getTiles(), false, spot.getSpace()) : spot);
			}
			spots = after;
		}
		// Light up every space that can take the action: built ones to remove, else empty ones to build.
		List<WorldPoint> built = notUnderButler(spots, true, s.getButlerTile());
		if (!built.isEmpty())
		{
			List<WorldPoint> empty = tiles(spots, false);
			boolean canBuildNext = !empty.isEmpty() && s.getPlanks() >= s.getPlanksPerBuild();
			return Decision.builder()
				.step(Step.REMOVE)
				.target(built)
				.next(canBuildNext ? empty : null)
				.nextStep(canBuildNext ? Step.BUILD : null)
				.butlerNext(butlerNext && s.getButlerHolding() > 0)
				.build();
		}
		if (s.getPlanks() >= s.getPlanksPerBuild())
		{
			String justCleared = s.getRemoving() != null ? s.getRemoving() : s.getLastRemoved();
			return Decision.builder().step(Step.BUILD).target(buildTarget(spots, justCleared, s.getButlerTile())).butlerNext(butlerNext).build();
		}
		return Decision.builder().step(Step.WAIT).build();
	}

	static int fetchAmount(Snapshot s)
	{
		int capacity = s.getServantType() != null ? s.getServantType().getCapacity() : s.getPlankSlots();
		int requested = s.getRequestedFetch() > 0 ? s.getRequestedFetch()
			: s.getLastDelivery() > 0 ? s.getLastDelivery() + s.getButlerHolding()
			: capacity;
		return Math.min(Math.min(requested, capacity), s.getPlankSlots());
	}

	private static int tripTicks(Snapshot s)
	{
		return s.getTripTicks() > 0 ? s.getTripTicks() : s.getServantType() != null ? s.getServantType().getTripTicks() : DEFAULT_TRIP_TICKS;
	}

	// Send so the delivery fits on arrival: the planks left by then plus the fetch must not exceed the slots.
	static int sendAt(Snapshot s, int fetch)
	{
		if (s.getSendAtOverride() > 0)
		{
			return s.getSendAtOverride();
		}
		return Math.max(0, Math.min(s.getPlankSlots(), s.getPlankSlots() - fetch + usedDuringTrip(s)));
	}

	// Walks the remove/build cycle from the current state over the trip: built furniture has to come off
	// before planks are used again, so a send right after building lets fewer planks go than the average rate.
	static int usedDuringTrip(Snapshot s)
	{
		int perBuild = s.getPlanksPerBuild();
		int horizon = tripTicks(s);
		double ticksPerBuild = s.getPlanksPerTick() > 0 ? perBuild / s.getPlanksPerTick() : perBuild;
		int buildTicks = Math.max(2, (int) Math.round(ticksPerBuild) - REMOVE_TICKS);
		int built = 0;
		if (s.getSpots() != null)
		{
			for (Spot spot : s.getSpots())
			{
				// A remove already clicked is gone by the time it matters.
			boolean removing = spot.getKey().equals(s.getRemoving());
			if (!removing && spot.isBuilt() || (s.getPendingBuild() != null && spot.getTiles().containsAll(s.getPendingBuild())))
				{
					built++;
				}
			}
		}
		int planks = s.getPlanks();
		int used = 0;
		// Measured from the send: no building while you talk to him, a bench still appearing finishes meanwhile.
		int tick = 0;
		while (true)
		{
			if (built > 0)
			{
				tick += REMOVE_TICKS;
				if (tick > horizon)
				{
					break;
				}
				built--;
				continue;
			}
			if (planks < perBuild)
			{
				break;
			}
			tick += buildTicks;
			if (tick > horizon)
			{
				break;
			}
			planks -= perBuild;
			used += perBuild;
			built++;
		}
		return used;
	}

	// The other side of the last remove, the space shown faintly during it; before any remove, every empty space
	// except one the butler stands on.
	private static List<WorldPoint> buildTarget(List<Spot> spots, String lastRemoved, WorldPoint butlerTile)
	{
		if (lastRemoved != null)
		{
			List<WorldPoint> other = new ArrayList<>();
			for (Spot spot : spots)
			{
				if (!spot.isBuilt() && !spot.getKey().equals(lastRemoved))
				{
					other.addAll(spot.getTiles());
				}
			}
			if (!other.isEmpty())
			{
				return other;
			}
		}
		return notUnderButler(spots, false, butlerTile);
	}

	// All spaces in the given state, minus the one under the butler unless that would leave none.
	private static List<WorldPoint> notUnderButler(List<Spot> spots, boolean built, WorldPoint butlerTile)
	{
		List<WorldPoint> all = tiles(spots, built);
		if (butlerTile == null)
		{
			return all;
		}
		List<WorldPoint> free = new ArrayList<>();
		for (Spot spot : spots)
		{
			if (spot.isBuilt() == built && !spot.getTiles().contains(butlerTile))
			{
				free.addAll(spot.getTiles());
			}
		}
		return free.isEmpty() ? all : free;
	}

	private static List<WorldPoint> tiles(List<Spot> spots, boolean built)
	{
		List<WorldPoint> result = new ArrayList<>();
		for (Spot spot : spots)
		{
			if (spot.isBuilt() == built)
			{
				result.addAll(spot.getTiles());
			}
		}
		return result;
	}
}
