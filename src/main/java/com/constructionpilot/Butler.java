package com.constructionpilot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

// The servant: where he is, his bank trips, what he holds and what he costs.
@Singleton
class Butler
{
	// Farther than this he is too far to click without walking; treat him as gone.
	private static final int REACH = 6;
	// He hands over only once you are free; past this the hand-over is not coming (empty bank).
	private static final int HAND_OVER_TICKS = 30;
	// A click elsewhere as he arrives usually still gets the planks on that tick or the next (105 of 120 in the
	// logs); only after this long without them does he keep the fetch until you talk to him.
	private static final int SKIP_TICKS = 2;
	// Servant dialogue, as read by the Butler Info plugin.
	private static final Pattern WAITS_WITH = Pattern.compile("I shall wait with these (\\d+) items");
	private static final String WAITS_WITH_LAST = "I shall wait with the last item";
	private static final String RETURNED_ALL = "Master, I have returned with what you asked me to retrieve.";
	private static final String RETURNED = "I have returned with what";
	private static final String SENT_BACK = "Take them back to the bank";
	private static final String PAID_BY_HAND = "Okay, here's";
	private static final String PAID_FROM_BAG = "Your servant takes some payment from the money bag.";
	private static final Pattern FETCH_OPTION = Pattern.compile("^Fetch from bank: (\\d+) x");

	private final Client client;
	private final TickLog tickLog;
	private final House house;

	@Getter
	private NPC npc;
	@Getter
	private ServantType type;
	@Getter
	private int holding;
	@Getter
	private int lastDelivery;
	// From the "Repeat last task?" option; 0 until seen.
	@Getter
	private int requestedFetch;
	// His return message is open: continuing it makes him walk off, clicking the next spot keeps him next to you.
	@Getter
	private boolean returnDialogOpen;
	// Unknown (-1) until the first wage payment is seen.
	@Getter
	private int tripsUntilPay = -1;
	@Getter
	private int wagesPaid;
	// Set when you pay him by hand, cleared when the money bag pays.
	@Getter
	private boolean paidByHand;

	private boolean awaitingHandOver;
	private int clickedAwayTick = -1;
	private int arrivedTick = -1;
	private int leftTick = -1;
	// Planks arriving on the tick of his return dialogue or the next are still the delivery.
	private int handOverTick = -1;
	private final RollingMedian recentTrips = new RollingMedian(5);
	private int measuredTripTicks = -1;
	private String lastOptions = "";

	@Inject
	Butler(Client client, TickLog tickLog, House house)
	{
		this.client = client;
		this.tickLog = tickLog;
		this.house = house;
	}

	void reset()
	{
		npc = null;
		holding = 0;
		leftTick = -1;
		returnDialogOpen = false;
	}

	void spawned(NPC spawned, WorldPoint player)
	{
		ServantType spawnedType = ServantType.byNpcId(spawned.getId());
		if (spawnedType == null)
		{
			return;
		}
		npc = spawned;
		type = spawnedType;
		arrivedTick = client.getTickCount();
		awaitingHandOver = leftTick >= 0;
		clickedAwayTick = -1;
		if (leftTick >= 0)
		{
			int trip = arrivedTick - leftTick;
			// A despawn without a bank trip (dismissed, room reload) is far off the servant's trip time.
			if (trip >= type.getTripTicks() - 3 && trip <= type.getTripTicks() + 8)
			{
				measuredTripTicks = recentTrips.add(trip);
			}
			tickLog.add("trip took " + trip + " ticks");
		}
		if (tickLog.enabled())
		{
			WorldPoint at = spawned.getWorldLocation();
			String relative = player == null ? "?" : "dx=" + (at.getX() - player.getX()) + " dy=" + (at.getY() - player.getY());
			tickLog.add(type.getName() + " arrives at " + at + " player " + player + " " + relative + (house.isSpace(at) ? " ON " + house.spaceAt(at) : ""));
		}
	}

	void despawned(NPC despawned)
	{
		if (despawned != npc)
		{
			return;
		}
		npc = null;
		holding = 0;
		leftTick = client.getTickCount();
		if (tripsUntilPay > 0)
		{
			tripsUntilPay--;
		}
		tickLog.add("butler leaves");
	}

	// The hand-over after a trip is a delivery; taking leftovers later only empties his hands.
	// True when this was his delivery after a trip, false for leftovers taken later.
	boolean planksGained(int gained)
	{
		if (npc == null)
		{
			return false;
		}
		if (awaitingHandOver || client.getTickCount() - handOverTick <= 1)
		{
			lastDelivery = gained;
			awaitingHandOver = false;
			return true;
		}
		if (holding > 0)
		{
			holding = Math.max(0, holding - gained);
		}
		return false;
	}

	void dialog(String text)
	{
		if (text.contains(RETURNED))
		{
			returnDialogOpen = true;
		}
		Matcher waits = WAITS_WITH.matcher(text);
		if (waits.find())
		{
			handedOver(Integer.parseInt(waits.group(1)));
		}
		else if (text.contains(WAITS_WITH_LAST))
		{
			handedOver(1);
		}
		else if (text.contains(RETURNED_ALL))
		{
			handedOver(0);
		}
		else if (text.contains(SENT_BACK))
		{
			holding = 0;
		}
		if (text.contains(PAID_BY_HAND))
		{
			paidByHand = true;
		}
	}

	void gameMessage(String text)
	{
		if (text.startsWith(PAID_FROM_BAG))
		{
			tripsUntilPay = ServantType.TRIPS_PER_PAYMENT;
			if (type != null)
			{
				wagesPaid += type.getWage();
			}
			paidByHand = false;
			tickLog.add("wage paid from money bag");
		}
	}

	void clicked(NPC target)
	{
		returnDialogOpen = false;
		if (awaitingHandOver && npc != null && target != npc && clickedAwayTick < 0)
		{
			clickedAwayTick = client.getTickCount();
		}
	}

	void dialogClosed()
	{
		returnDialogOpen = false;
	}

	void tick()
	{
		readFetchOption();
		// Clicked away and still nothing: he keeps the whole fetch until you talk to him.
		if (awaitingHandOver && npc != null && clickedAwayTick >= 0 && client.getTickCount() - clickedAwayTick >= SKIP_TICKS)
		{
			awaitingHandOver = false;
			holding = Math.max(holding, requestedFetch > 0 ? requestedFetch : lastDelivery > 0 ? lastDelivery : type != null ? type.getCapacity() : 0);
			tickLog.add("hand-over skipped, butler holds " + holding);
		}
	}

	boolean isNear(WorldPoint player)
	{
		return npc != null && npc.getWorldLocation().distanceTo(player) <= REACH;
	}

	boolean isAwaitingHandOver()
	{
		return awaitingHandOver && client.getTickCount() - arrivedTick <= HAND_OVER_TICKS;
	}

	// Measured trip once seen, else 0 so the policy uses the servant's known trip time.
	int tripTicks()
	{
		return Math.max(0, measuredTripTicks);
	}

	int ticksAway()
	{
		return npc != null || leftTick < 0 ? -1 : client.getTickCount() - leftTick;
	}

	// Ticks until he is back, or -1 while he is here or was never sent.
	int ticksLeft()
	{
		int away = ticksAway();
		if (away < 0 || type == null)
		{
			return -1;
		}
		int trip = measuredTripTicks > 0 ? measuredTripTicks : type.getTripTicks();
		// Far overdue means he is not on a bank trip (dismissed or left the house); stop counting.
		return away > trip + 10 ? -1 : Math.max(0, trip - away);
	}

	private void handedOver(int stillHolding)
	{
		holding = stillHolding;
		awaitingHandOver = false;
		handOverTick = client.getTickCount();
	}

	// His options menu shows the real fetch amount.
	private void readFetchOption()
	{
		Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (options == null || options.isHidden() || options.getDynamicChildren() == null)
		{
			lastOptions = "";
			return;
		}
		StringBuilder all = new StringBuilder();
		for (Widget option : options.getDynamicChildren())
		{
			String text = Text.removeTags(option.getText());
			Matcher fetch = FETCH_OPTION.matcher(text);
			if (fetch.find())
			{
				requestedFetch = Integer.parseInt(fetch.group(1));
			}
			if (!text.isEmpty())
			{
				all.append(text).append(" / ");
			}
		}
		if (!all.toString().equals(lastOptions))
		{
			lastOptions = all.toString();
			tickLog.add("options " + lastOptions);
		}
	}
}
