package com.constructionpilot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

// The build spaces, the furniture on them and the bell-pull, kept from spawn events instead of scene scans.
@Singleton
class House
{
	// Build space of each hotspot; a garden seating space is two hotspots, one per bench half.
	private static final Map<Integer, String> HOTSPOT_KEYS = Map.of(
		ObjectID.POH_DINING_ROOM_1, "table",
		ObjectID.POH_KITCHEN_5, "larder",
		ObjectID.POH_SUPERIOR_GARDEN_HOTSPOT_SEATING_A_LEFT, "garden A",
		ObjectID.POH_SUPERIOR_GARDEN_HOTSPOT_SEATING_A_RIGHT, "garden A",
		ObjectID.POH_SUPERIOR_GARDEN_HOTSPOT_SEATING_B_LEFT, "garden B",
		ObjectID.POH_SUPERIOR_GARDEN_HOTSPOT_SEATING_B_RIGHT, "garden B"
	);
	private static final Map<String, Space> KEY_SPACES = Map.of(
		"table", Space.TABLE,
		"larder", Space.LARDER,
		"garden A", Space.GARDEN,
		"garden B", Space.GARDEN
	);
	private static final Set<Integer> BELL_PULLS = Set.of(ObjectID.POH_BELLPULL_1, ObjectID.POH_BELLPULL_2, ObjectID.POH_BELLPULL_3);
	// The dining room and the superior garden can both be loaded; only spaces next to the player count.
	private static final int REACH = 3;

	private final TickLog tickLog;
	private final List<GameObject> hotspots = new ArrayList<>();
	private final List<GameObject> furniture = new ArrayList<>();
	// Which build space each hotspot tile belongs to, so the furniture built there groups with it.
	private final Map<WorldPoint, String> tileSpace = new HashMap<>();
	// Furniture seen on a tile whose hotspot never showed: its method tells the kind of space.
	private final Map<String, Space> seenSpaces = new HashMap<>();

	@Getter
	private GameObject bellPull;

	@Inject
	House(TickLog tickLog)
	{
		this.tickLog = tickLog;
	}

	void reset()
	{
		hotspots.clear();
		furniture.clear();
		bellPull = null;
	}

	// True when furniture appeared.
	boolean spawned(GameObject object)
	{
		int id = object.getId();
		if (BELL_PULLS.contains(id))
		{
			bellPull = object;
		}
		if (HOTSPOT_KEYS.containsKey(id))
		{
			hotspots.add(object);
			tileSpace.put(object.getWorldLocation(), HOTSPOT_KEYS.get(id));
			tickLog.add("hotspot appears " + id + " at " + object.getWorldLocation());
		}
		else if (Method.ALL_FURNITURE.contains(id))
		{
			furniture.add(object);
			seenSpaces.putIfAbsent(spaceAt(object.getWorldLocation()), Method.ofFurniture(id).getSpace());
			tickLog.add("furniture appears " + id + " at " + object.getWorldLocation());
			return true;
		}
		return false;
	}

	// True when furniture went away.
	boolean despawned(GameObject object)
	{
		if (object == bellPull)
		{
			bellPull = null;
		}
		if (hotspots.remove(object))
		{
			tickLog.add("hotspot gone " + object.getId());
		}
		if (furniture.remove(object))
		{
			tickLog.add("furniture gone " + object.getId());
			return true;
		}
		return false;
	}

	// The space a tile belongs to; a tile never seen empty is its own space.
	String spaceAt(WorldPoint tile)
	{
		return tileSpace.getOrDefault(tile, tile.toString());
	}

	String spaceAt(List<WorldPoint> tiles)
	{
		return tiles == null || tiles.isEmpty() ? "-" : spaceAt(tiles.get(0));
	}

	boolean isSpace(WorldPoint tile)
	{
		return tileSpace.containsKey(tile);
	}

	// Spaces within reach of the player, nearest first, with both halves of a garden bench grouped.
	List<Spot> spotsNear(WorldPoint at)
	{
		Map<String, List<WorldPoint>> tiles = new LinkedHashMap<>();
		Map<String, Boolean> built = new HashMap<>();
		List<GameObject> all = new ArrayList<>(furniture);
		all.addAll(hotspots);
		for (GameObject object : all)
		{
			WorldPoint tile = object.getWorldLocation();
			if (tile.distanceTo(at) > REACH)
			{
				continue;
			}
			String key = spaceAt(tile);
			tiles.computeIfAbsent(key, k -> new ArrayList<>()).add(tile);
			built.merge(key, Method.ALL_FURNITURE.contains(object.getId()), Boolean::logicalOr);
		}
		List<Spot> spots = new ArrayList<>();
		for (Map.Entry<String, List<WorldPoint>> entry : tiles.entrySet())
		{
			String key = entry.getKey();
			spots.add(new Spot(key, entry.getValue(), built.get(key), KEY_SPACES.getOrDefault(key, seenSpaces.get(key))));
		}
		spots.sort(Comparator.comparingInt(spot -> spot.getTiles().get(0).distanceTo(at)));
		return spots;
	}

	// The method of furniture standing in one of these spaces, or null when all are empty.
	Method builtMethod(List<Spot> spots)
	{
		for (GameObject object : furniture)
		{
			for (Spot spot : spots)
			{
				if (spot.getTiles().contains(object.getWorldLocation()))
				{
					return Method.ofFurniture(object.getId());
				}
			}
		}
		return null;
	}

	// All tiles of the space containing this tile, or null when it is not a space near the player.
	List<WorldPoint> spaceTiles(WorldPoint tile, WorldPoint player)
	{
		if (tile == null || player == null)
		{
			return null;
		}
		for (Spot spot : spotsNear(player))
		{
			if (spot.getTiles().contains(tile))
			{
				return spot.getTiles();
			}
		}
		return null;
	}

	// Looked up each frame, so a replaced table or hotspot is never stale.
	List<GameObject> objectsAt(List<WorldPoint> spotTiles)
	{
		List<GameObject> result = within(furniture, spotTiles);
		return result.isEmpty() ? within(hotspots, spotTiles) : result;
	}

	private static List<GameObject> within(List<GameObject> objects, List<WorldPoint> tiles)
	{
		List<GameObject> result = new ArrayList<>();
		for (GameObject object : objects)
		{
			if (tiles.contains(object.getWorldLocation()))
			{
				result.add(object);
			}
		}
		return result;
	}
}
