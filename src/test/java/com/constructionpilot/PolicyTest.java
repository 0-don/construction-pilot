package com.constructionpilot;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PolicyTest
{
	private static final WorldPoint A1 = new WorldPoint(100, 100, 0);
	private static final WorldPoint A2 = new WorldPoint(101, 100, 0);
	private static final WorldPoint B1 = new WorldPoint(100, 103, 0);
	private static final WorldPoint B2 = new WorldPoint(101, 103, 0);
	private static final WorldPoint TABLE = new WorldPoint(200, 200, 0);

	private static Spot table(boolean built)
	{
		return new Spot("table", Collections.singletonList(TABLE), built, Space.TABLE);
	}

	private static Spot garden(String key, boolean built, WorldPoint... tiles)
	{
		return new Spot(key, Arrays.asList(tiles), built, Space.GARDEN);
	}

	private static Snapshot.SnapshotBuilder base()
	{
		return Snapshot.builder()
			.buildingMode(true)
			.planks(24)
			.planksPerBuild(6)
			.plankSlots(28)
			.freeSlots(4)
			.servantType(ServantType.DEMON_BUTLER)
			.planksPerTick(1.0)
			.spots(Collections.singletonList(table(false)));
	}

	@Test
	public void idleOutsideBuildingMode()
	{
		assertEquals(Step.IDLE, Policy.decide(base().buildingMode(false).build()).getStep());
	}

	@Test
	public void idleWithoutSpotsNearby()
	{
		assertEquals(Step.IDLE, Policy.decide(base().spots(Collections.emptyList()).build()).getStep());
	}

	@Test
	public void buildsOnEmptyTable()
	{
		Decision d = Policy.decide(base().build());
		assertEquals(Step.BUILD, d.getStep());
		assertEquals(Collections.singletonList(TABLE), d.getTarget());
	}

	@Test
	public void removesBuiltTable()
	{
		Decision d = Policy.decide(base().spots(Collections.singletonList(table(true))).build());
		assertEquals(Step.REMOVE, d.getStep());
	}

	@Test
	public void waitsBelowOneBuild()
	{
		assertEquals(Step.WAIT, Policy.decide(base().butlerOnTrip(true).planks(5).build()).getStep());
	}

	@Test
	public void sendAtFitsDeliveryOnArrival()
	{
		// Empty table, 1 plank per tick (6 ticks per table: 2 remove, 4 build), 14 tick horizon:
		// build 4, remove 6, build 10, remove 12 -> 12 planks used; 28 - 26 + 12 = 14.
		Snapshot s = base().requestedFetch(26).build();
		assertEquals(26, Policy.fetchAmount(s));
		assertEquals(12, Policy.usedDuringTrip(s));
		assertEquals(14, Policy.sendAt(s, 26));
	}

	@Test
	public void builtFurnitureDelaysPlankUse()
	{
		// Both benches standing: two removes first, so fewer planks go during the trip than with empty spaces.
		// 10 tick trip, 12 tick horizon. Empty: build 4, remove 6, build 10 -> 12. Built: remove 2, remove 4, build 8 -> 6.
		Snapshot empty = base().tripTicks(10).spots(Arrays.asList(garden("garden A", false, A1, A2), garden("garden B", false, B1, B2))).build();
		Snapshot built = base().tripTicks(10).spots(Arrays.asList(garden("garden A", true, A1, A2), garden("garden B", true, B1, B2))).build();
		assertEquals(12, Policy.usedDuringTrip(empty));
		assertEquals(6, Policy.usedDuringTrip(built));
	}

	@Test
	public void noPlanksNoUse()
	{
		assertEquals(0, Policy.usedDuringTrip(base().planks(4).build()));
	}

	@Test
	public void sendsButlerAtThreshold()
	{
		Snapshot s = base().butlerPresent(true).requestedFetch(26).planks(14).build();
		assertEquals(Step.SEND_BUTLER, Policy.decide(s).getStep());
	}

	@Test
	public void keepsBuildingAboveThreshold()
	{
		// 20 planks: 12 used during the trip, 8 left keeps you building, and 8 + 26 would not fit yet.
		Snapshot s = base().butlerPresent(true).requestedFetch(26).planks(20).build();
		assertEquals(Step.BUILD, Policy.decide(s).getStep());
	}

	@Test
	public void takesHeldPlanksBeforeSendingAgain()
	{
		Snapshot s = base().butlerPresent(true).butlerHolding(4).planks(2).freeSlots(10).build();
		assertEquals(Step.TAKE_PLANKS, Policy.decide(s).getStep());
	}

	@Test
	public void noTakeWhilePartOfItWouldStayWithHim()
	{
		// Holding 6, only 4 free and enough planks for the next build: keep building.
		Snapshot s = base().butlerPresent(true).butlerHolding(6).planks(22).freeSlots(4).build();
		assertEquals(Step.BUILD, Policy.decide(s).getStep());
	}

	@Test
	public void takeWhenNextBuildNeedsPlanks()
	{
		Snapshot s = base().butlerPresent(true).butlerHolding(6).planks(4).freeSlots(2).build();
		assertEquals(Step.TAKE_PLANKS, Policy.decide(s).getStep());
	}

	@Test
	public void neverSendsWhileButlerStillHoldsPlanks()
	{
		// Inventory full, so the held planks cannot be taken yet; build instead of sending him off.
		Snapshot s = base().butlerPresent(true).butlerHolding(4).planks(6).freeSlots(0).build();
		assertEquals(Step.BUILD, Policy.decide(s).getStep());
	}

	@Test
	public void lightsBothEmptySpaces()
	{
		Snapshot s = base()
			.spots(Arrays.asList(garden("garden A", false, A1, A2), garden("garden B", false, B1, B2)))
			.build();
		assertEquals(Arrays.asList(A1, A2, B1, B2), Policy.decide(s).getTarget());
	}

	@Test
	public void removeClickLightsOtherSideAtOnce()
	{
		// Bench A still standing but its remove is clicked: build B now instead of waiting for A to vanish.
		Snapshot s = base()
			.spots(Arrays.asList(garden("garden A", true, A1, A2), garden("garden B", false, B1, B2)))
			.removing("garden A")
			.build();
		Decision d = Policy.decide(s);
		assertEquals(Step.BUILD, d.getStep());
		assertEquals(Arrays.asList(B1, B2), d.getTarget());
	}

	@Test
	public void avoidsSpaceUnderButlerWhenBuilding()
	{
		Snapshot s = base()
			.spots(Arrays.asList(garden("garden A", false, A1, A2), garden("garden B", false, B1, B2)))
			.butlerTile(A2)
			.build();
		assertEquals(Arrays.asList(B1, B2), Policy.decide(s).getTarget());
	}

	@Test
	public void avoidsBenchUnderButlerWhenRemoving()
	{
		Snapshot s = base()
			.spots(Arrays.asList(garden("garden A", true, A1, A2), garden("garden B", true, B1, B2)))
			.butlerTile(B1)
			.build();
		assertEquals(Arrays.asList(A1, A2), Policy.decide(s).getTarget());
	}

	@Test
	public void lightsBothBuiltBenches()
	{
		Snapshot s = base()
			.spots(Arrays.asList(garden("garden A", true, A1, A2), garden("garden B", true, B1, B2)))
			.build();
		Decision d = Policy.decide(s);
		assertEquals(Step.REMOVE, d.getStep());
		assertEquals(Arrays.asList(A1, A2, B1, B2), d.getTarget());
	}

	@Test
	public void removesWholeBench()
	{
		Snapshot s = base()
			.spots(Arrays.asList(garden("garden A", true, A1, A2), garden("garden B", false, B1, B2)))
			.build();
		Decision d = Policy.decide(s);
		assertEquals(Step.REMOVE, d.getStep());
		assertEquals(Arrays.asList(A1, A2), d.getTarget());
		// The empty side is queued so the build click can follow on the next tick.
		assertEquals(Arrays.asList(B1, B2), d.getNext());
	}

	@Test
	public void measuredTripMovesSendPoint()
	{
		// A 16 tick trip gives an 18 tick horizon: builds finish at 4, 10 and 16 -> 18 planks; 28 - 26 + 18 = 20.
		assertEquals(20, Policy.sendAt(base().tripTicks(16).build(), 26));
	}

	@Test
	public void handOverComesByItself()
	{
		// Back with planks you have not received: never send him off again, and no click needed, he hands over.
		Snapshot s = base().butlerPresent(true).awaitingHandOver(true).requestedFetch(26).planks(2).build();
		assertEquals(Step.WAIT, Policy.decide(s).getStep());
	}

	@Test
	public void keepBuildingWhileHandOverPending()
	{
		Decision d = Policy.decide(base().butlerPresent(true).awaitingHandOver(true).planks(12).build());
		assertEquals(Step.BUILD, d.getStep());
		org.junit.Assert.assertTrue(d.isButlerNext());
	}

	@Test
	public void countsDownWhileBenchIsBuilt()
	{
		Snapshot s = base()
			.spots(Arrays.asList(garden("garden A", false, A1, A2), garden("garden B", false, B1, B2)))
			.pendingBuild(Arrays.asList(B1, B2))
			.ticksUntilBuilt(2)
			.build();
		Decision d = Policy.decide(s);
		assertEquals(Step.REMOVE_SOON, d.getStep());
		assertEquals(2, d.getCountdown());
		assertEquals(Arrays.asList(B1, B2), d.getTarget());
	}

	@Test
	public void sendButlerDuringBuildWait()
	{
		// Waiting on the build animation is the cheapest moment to send him.
		// 8 planks with a table appearing: one more table during the trip, 28 - 26 + 6 = 8, so send now.
		Snapshot s = base().butlerPresent(true).requestedFetch(26).planks(8)
			.pendingBuild(Collections.singletonList(TABLE)).ticksUntilBuilt(2).build();
		assertEquals(Step.SEND_BUTLER, Policy.decide(s).getStep());
	}

	@Test
	public void sendsWhenYouWouldRunDry()
	{
		// 4 planks cannot build anything; send even though 4 + 26 overflows by 2.
		assertEquals(Step.SEND_BUTLER, Policy.decide(base().butlerPresent(true).requestedFetch(26).planks(4).build()).getStep());
		// 10 planks: one more build, then dry before he is back.
		assertEquals(Step.SEND_BUTLER, Policy.decide(base().butlerPresent(true).requestedFetch(26).planks(10).build()).getStep());
	}

	@Test
	public void callsButlerWhenHeWanderedOff()
	{
		// Needed (4 planks) but not near and not on a trip.
		Snapshot s = base().butlerPresent(false).butlerOnTrip(false).requestedFetch(24).planks(4).build();
		assertEquals(Step.CALL_BUTLER, Policy.decide(s).getStep());
	}

	@Test
	public void noCallWhileHeIsOnATrip()
	{
		Snapshot s = base().butlerPresent(false).butlerOnTrip(true).requestedFetch(24).planks(4).build();
		assertEquals(Step.WAIT, Policy.decide(s).getStep());
	}

	@Test
	public void butlerFlaggedNextOneBuildAhead()
	{
		// Send point 14 (see sendAtFitsDeliveryOnArrival): 20 planks build to 14, so he is next.
		Decision d = Policy.decide(base().butlerPresent(true).requestedFetch(26).planks(20).build());
		assertEquals(Step.BUILD, d.getStep());
		org.junit.Assert.assertTrue(d.isButlerNext());
		org.junit.Assert.assertFalse(Policy.decide(base().butlerPresent(true).requestedFetch(26).planks(26).build()).isButlerNext());
	}

	@Test
	public void sendPointSameWhilePendingAndBuilt()
	{
		// No jump between the countdown and the bench standing, so Send shows during the build wait.
		Snapshot pending = base().spots(Collections.singletonList(table(false)))
			.pendingBuild(Collections.singletonList(TABLE)).ticksUntilBuilt(2).planks(12).build();
		Snapshot built = base().spots(Collections.singletonList(table(true))).planks(12).build();
		assertEquals(Policy.sendAt(built, 24), Policy.sendAt(pending, 24));
	}

	@Test
	public void noBenchPreviewDuringButlerStep()
	{
		Snapshot s = base().butlerPresent(true).requestedFetch(24).planks(4)
			.spots(Arrays.asList(garden("garden A", true, A1, A2), garden("garden B", false, B1, B2)))
			.build();
		Decision d = Policy.decide(s);
		assertEquals(Step.SEND_BUTLER, d.getStep());
		assertEquals(null, d.getNext());
	}

	@Test
	public void heldPlanksShowButlerAsNext()
	{
		// He holds the whole fetch, but it does not fit yet and you still have planks: keep building, butler faint.
		Decision d = Policy.decide(base().butlerPresent(true).butlerHolding(24).planks(12).freeSlots(14).build());
		assertEquals(Step.BUILD, d.getStep());
		org.junit.Assert.assertTrue(d.isButlerNext());
	}

	@Test
	public void overrideWins()
	{
		Snapshot s = base().sendAtOverride(10).build();
		assertEquals(10, Policy.sendAt(s, Policy.fetchAmount(s)));
	}

	@Test
	public void fetchCappedBySlotsAndCapacity()
	{
		assertEquals(20, Policy.fetchAmount(base().servantType(ServantType.BUTLER).requestedFetch(26).build()));
		assertEquals(22, Policy.fetchAmount(base().plankSlots(22).requestedFetch(26).build()));
	}

	private static Snapshot.SnapshotBuilder larder()
	{
		return base()
			.planksPerBuild(8)
			.spots(Collections.singletonList(new Spot("larder", Collections.singletonList(TABLE), false, Space.LARDER)));
	}

	@Test
	public void larderNeedsEightPlanks()
	{
		assertEquals(Step.WAIT, Policy.decide(larder().butlerOnTrip(true).planks(7).build()).getStep());
		assertEquals(Step.BUILD, Policy.decide(larder().butlerOnTrip(true).planks(8).build()).getStep());
	}

	@Test
	public void larderTripUsesWholeLarders()
	{
		// One plank a tick: build 6, remove 2, then the next 6 tick build does not finish within the 12 tick trip.
		assertEquals(8, Policy.usedDuringTrip(larder().build()));
	}

	@Test
	public void larderSendsWithOneBuildLeft()
	{
		assertEquals(Step.BUILD, Policy.decide(larder().butlerPresent(true).requestedFetch(24).planks(16).build()).getStep());
		assertEquals(Step.SEND_BUTLER, Policy.decide(larder().butlerPresent(true).requestedFetch(24).planks(8).build()).getStep());
	}

	@Test
	public void butlerNextOnBuildBecomesSendAfterIt()
	{
		for (double rate : new double[]{0.6, 0.75, 0.86, 1.0, 1.2, 1.5})
		{
			for (int planks = 6; planks <= 24; planks += 6)
			{
				Snapshot s = base().butlerPresent(true).requestedFetch(24).planksPerTick(rate).planks(planks)
					.spots(Arrays.asList(garden("garden A", false, A1, A2), garden("garden B", false, B1, B2))).build();
				Decision build = Policy.decide(s);
				if (build.getStep() != Step.BUILD)
				{
					continue;
				}
				Snapshot after = s.toBuilder().planks(planks - 6).pendingBuild(Arrays.asList(A1, A2)).ticksUntilBuilt(1).build();
				boolean sendsAfter = Policy.decide(after).getStep() == Step.SEND_BUTLER;
				assertEquals("rate " + rate + " planks " + planks, sendsAfter, build.isButlerNext());
			}
		}
	}

	@Test
	public void clickedRemoveDoesNotLowerSendPoint()
	{
		Snapshot standing = base().butlerPresent(true).requestedFetch(24)
			.spots(Arrays.asList(garden("garden A", true, A1, A2), garden("garden B", false, B1, B2))).build();
		Snapshot removing = standing.toBuilder().removing("garden A").build();
		Snapshot gone = standing.toBuilder().spots(Arrays.asList(garden("garden A", false, A1, A2), garden("garden B", false, B1, B2))).build();
		assertEquals(Policy.usedDuringTrip(gone), Policy.usedDuringTrip(removing));
	}
}
