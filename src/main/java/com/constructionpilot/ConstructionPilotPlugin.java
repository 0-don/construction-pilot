package com.constructionpilot;

import com.google.inject.Provides;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

// Routes game events to the trackers in a fixed order, then each tick turns their state into a Snapshot and
// asks Policy what to click. Overlays only draw the resulting Decision.
@PluginDescriptor(
	name = "Construction Pilot",
	description = "Shows what to build or remove next and when to send the butler: oak larders, mahogany tables, teak and gnome benches",
	tags = {"construction", "poh", "butler", "gnome bench", "mahogany table"}
)
public class ConstructionPilotPlugin extends Plugin
{
	private static final int INVENTORY_SIZE = 28;

	@Inject
	private Client client;
	@Inject
	private ConstructionPilotConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ItemManager itemManager;
	@Inject
	private SceneOverlay sceneOverlay;
	@Inject
	private StatsPanel statsPanel;
	@Inject
	private SetupPanel setupPanel;
	@Inject
	private House house;
	@Inject
	private Butler butler;
	@Inject
	private BuildTimer buildTimer;
	@Inject
	private SetupCheck setupCheck;
	@Inject
	private TickLog tickLog;
	@Inject
	private TickClock tickClock;

	@Getter
	private Decision decision = Decision.IDLE;
	// Picked each tick from the space you stand at and the planks you carry.
	@Getter
	private Method method = Method.MAHOGANY_TABLE;
	@Getter
	private int planks;
	// Slots planks may use: everything except the other items you carry.
	@Getter
	private int plankSlots = INVENTORY_SIZE;
	private int freeSlots;
	@Getter
	private int planksUsed;
	@Getter
	private long planksCost;
	private String lastSwap = "";
	private int lastConstructionXp = -1;

	@Override
	protected void startUp()
	{
		overlayManager.add(sceneOverlay);
		overlayManager.add(statsPanel);
		overlayManager.add(setupPanel);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(statsPanel);
		overlayManager.remove(setupPanel);
		reset();
	}

	private void reset()
	{
		house.reset();
		butler.reset();
		buildTimer.reset();
		decision = Decision.IDLE;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (house.spawned(event.getGameObject()))
		{
			buildTimer.furnitureAppeared(event.getGameObject());
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (house.despawned(event.getGameObject()))
		{
			buildTimer.furnitureGone();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		Player player = client.getLocalPlayer();
		butler.spawned(event.getNpc(), player != null ? player.getWorldLocation() : null);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		butler.despawned(event.getNpc());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		ItemContainer inventory = event.getItemContainer();
		int now = inventory.count(method.getPlank());
		if (now < planks)
		{
			planksUsed += planks - now;
			planksCost += (long) (planks - now) * itemManager.getItemPrice(method.getPlank());
			buildTimer.planksUsed(planks, now, method.getPlanksPerBuild());
		}
		else if (now > planks)
		{
			buildTimer.planksGained(butler.planksGained(now - planks));
		}
		if (now != planks)
		{
			tickLog.add("planks " + planks + " -> " + now);
		}
		countSlots(inventory);
	}

	// Plank count and slots without treating the change as used or delivered planks.
	private void countSlots(ItemContainer inventory)
	{
		planks = inventory.count(method.getPlank());
		int used = 0;
		int plankStacks = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() != -1 && item.getQuantity() > 0)
			{
				used++;
				if (item.getId() == method.getPlank())
				{
					plankStacks++;
				}
			}
		}
		plankSlots = INVENTORY_SIZE - (used - plankStacks);
		freeSlots = INVENTORY_SIZE - used;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			decision = Decision.IDLE;
			return;
		}
		tickClock.tick();
		butler.tick();
		buildTimer.tick();

		WorldPoint at = player.getWorldLocation();
		List<Spot> spots = house.spotsNear(at);
		pickMethod(spots);
		Snapshot.SnapshotBuilder builder = Snapshot.builder()
			.buildingMode(client.getVarbitValue(VarbitID.POH_BUILDING_MODE) == 1)
			.plankSlots(plankSlots)
			.freeSlots(freeSlots)
			.butlerPresent(butler.isNear(at))
			.butlerOnTrip(butler.getNpc() == null && butler.ticksLeft() >= 0)
			.butlerTile(butler.getNpc() != null ? butler.getNpc().getWorldLocation() : null)
			.butlerHolding(butler.getHolding())
			.awaitingHandOver(butler.isAwaitingHandOver())
			.servantType(butler.getType())
			.tripTicks(butler.tripTicks())
			.requestedFetch(butler.getRequestedFetch())
			.lastDelivery(butler.getLastDelivery())
			.sendAtOverride(config.sendAtOverride())
			.planksPerBuild(method.getPlanksPerBuild())
			.spots(spots);
		Snapshot snapshot = buildTimer.describe(builder, planks, method.getPlanksPerBuild()).build();

		Decision next = Policy.decide(snapshot);
		setupCheck.update(snapshot, method);
		if (tickLog.enabled())
		{
			tickLog.add("state " + next.getStep() + " " + method + " target=" + house.spaceAt(next.getTarget())
				+ " next=" + house.spaceAt(next.getNext()) + " planks=" + planks
				+ " butler=" + (butler.getNpc() != null ? "here holding " + butler.getHolding() : butler.ticksLeft() >= 0 ? "away " + butler.ticksLeft() : "none")
				+ " removing=" + snapshot.getRemoving() + " pending=" + buildTimer.isPending() + " countdown=" + next.getCountdown()
				+ String.format(" rate=%.2f trip=%d", buildTimer.getPlanksPerTick(), butler.tripTicks()));
			if (next.getStep() != decision.getStep())
			{
				tickLog.add("step " + next.getStep() + " (planks " + planks + ", send at " + next.getSendAt() + ", fetch " + next.getFetchAmount() + ")");
			}
		}
		decision = next;
	}

	private void pickMethod(List<Spot> spots)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (spots.isEmpty() || spots.get(0).getSpace() == null || inventory == null)
		{
			return;
		}
		Method picked = Method.at(spots.get(0).getSpace(), house.builtMethod(spots), method,
			inventory.count(ItemID.PLANK_TEAK), inventory.count(ItemID.PLANK_MAHOGANY), client.getBoostedSkillLevel(Skill.CONSTRUCTION));
		if (picked != method)
		{
			tickLog.add("method " + method + " -> " + picked);
			method = picked;
			countSlots(inventory);
		}
	}

	// Lowest priority runs last, after Menu Entry Swapper and other swappers, so this choice is the final one.
	@Subscribe(priority = -100)
	public void onPostMenuSort(PostMenuSort event)
	{
		if (!config.smartLeftClick() || client.isMenuOpen())
		{
			return;
		}
		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		MenuEntry promoted = SmartLeftClick.promote(entries, decision.getStep());
		if (promoted == null)
		{
			return;
		}
		menu.setMenuEntries(entries);
		String swapped = Text.removeTags(promoted.getOption()) + " " + Text.removeTags(promoted.getTarget()).toLowerCase();
		if (!swapped.equals(lastSwap))
		{
			lastSwap = swapped;
			tickLog.add("left click -> " + swapped);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		tickLog.add("click " + event.getMenuOption() + " | " + event.getMenuTarget() + " | " + event.getMenuAction());
		NPC clickedNpc = event.getMenuEntry().getNpc();
		butler.clicked(clickedNpc);
		if (clickedNpc != null && ServantType.byNpcId(clickedNpc.getId()) != null)
		{
			buildTimer.butlerTalkedTo();
		}
		String option = event.getMenuOption();
		WorldPoint tile = null;
		if ("Build".equals(option) || "Remove".equals(option))
		{
			WorldView view = client.getTopLevelWorldView();
			tile = WorldPoint.fromScene(view, event.getParam0(), event.getParam1(), view.getPlane());
		}
		buildTimer.clicked(option, tile);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.CHAT_LEFT)
		{
			butler.dialogClosed();
		}
		if (event.getGroupId() == InterfaceID.POH_FURNITURE_CREATION)
		{
			buildTimer.buildMenuClosed(planks);
		}
		tickLog.add("interface closes " + event.getGroupId());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.DIALOG)
		{
			String text = Text.sanitizeMultilineText(event.getMessage());
			butler.dialog(text);
			tickLog.add("dialog " + text);
		}
		else if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			butler.gameMessage(Text.removeTags(event.getMessage()));
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.CONSTRUCTION)
		{
			return;
		}
		if (lastConstructionXp >= 0 && event.getXp() > lastConstructionXp)
		{
			tickLog.add("xp +" + (event.getXp() - lastConstructionXp) + " total " + event.getXp());
		}
		lastConstructionXp = event.getXp();
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			tickLog.add("player animation " + event.getActor().getAnimation());
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		tickLog.add("interface opens " + event.getGroupId());
	}

	@Provides
	ConstructionPilotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ConstructionPilotConfig.class);
	}
}
