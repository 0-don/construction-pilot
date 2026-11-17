package com.constructionpilot;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

// The training methods this plugin drives (wiki: Construction training). Build menu keys are for Construction QOL.
@Getter
@RequiredArgsConstructor
enum Method
{
	OAK_LARDER("oak larders", Space.LARDER, ItemID.PLANK_OAK, 8, 33, "2", "oak larder",
		Set.of(ObjectID.POH_LARDER_2)),
	MAHOGANY_TABLE("mahogany tables", Space.TABLE, ItemID.PLANK_MAHOGANY, 6, 52, "6", "mahogany table",
		Set.of(ObjectID.POH_DININGTABLE_6)),
	TEAK_GARDEN_BENCH("teak garden benches", Space.GARDEN, ItemID.PLANK_TEAK, 6, 66, "1", "teak garden bench",
		Set.of(ObjectID.POH_GARDEN_BENCH_TEAK_LEFT, ObjectID.POH_GARDEN_BENCH_TEAK_RIGHT)),
	GNOME_BENCH("gnome benches", Space.GARDEN, ItemID.PLANK_MAHOGANY, 6, 77, "2", "gnome bench",
		Set.of(ObjectID.POH_GARDEN_BENCH_GNOME_LEFT, ObjectID.POH_GARDEN_BENCH_GNOME_RIGHT));

	static final Set<Integer> ALL_FURNITURE = new HashSet<>();
	static final int FIRST_LEVEL = OAK_LARDER.level;

	static
	{
		for (Method method : values())
		{
			ALL_FURNITURE.addAll(method.furniture);
		}
	}

	private final String name;
	private final Space space;
	private final int plank;
	private final int planksPerBuild;
	private final int level;
	private final String qolKey;
	// Menu target of the built furniture's Remove option, lower case.
	private final String furnitureName;
	private final Set<Integer> furniture;

	static Method ofFurniture(int objectId)
	{
		for (Method method : values())
		{
			if (method.furniture.contains(objectId))
			{
				return method;
			}
		}
		return null;
	}

	// The garden takes either bench: go by the furniture standing there, else by the planks you carry, else stay
	// with the current bench (out of planks while the butler is away), else by level.
	static Method at(Space space, Method built, Method current, int teakPlanks, int mahoganyPlanks, int level)
	{
		if (built != null && built.space == space)
		{
			return built;
		}
		switch (space)
		{
			case LARDER:
				return OAK_LARDER;
			case TABLE:
				return MAHOGANY_TABLE;
			default:
				if (teakPlanks != mahoganyPlanks)
				{
					return teakPlanks > mahoganyPlanks ? TEAK_GARDEN_BENCH : GNOME_BENCH;
				}
				if (current.space == Space.GARDEN)
				{
					return current;
				}
				return level >= GNOME_BENCH.level ? GNOME_BENCH : TEAK_GARDEN_BENCH;
		}
	}
}
