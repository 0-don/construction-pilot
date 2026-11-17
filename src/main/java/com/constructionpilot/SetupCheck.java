package com.constructionpilot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

// Required items go to missing; everything the plugin adapts to anyway is only a tip.
@Singleton
class SetupCheck
{
	private static final Set<Integer> HAMMERS = Set.of(ItemID.HAMMER, ItemID.IMCANDO_HAMMER, ItemID.IMCANDO_HAMMER_OFFHAND);
	private static final Set<Integer> SAWS = Set.of(ItemID.POH_SAW, ItemID.EYEGLO_CRYSTAL_SAW, ItemID.WEARABLE_SAW, ItemID.WEARABLE_SAW_OFFHAND);
	private static final String QOL_PLUGIN = "Construction QOL";
	private static final String QOL_GROUP = "zom-con-qol";

	private final Client client;
	private final ConfigManager configManager;
	private final PluginManager pluginManager;
	private final Butler butler;

	@Getter
	private List<String> missing = Collections.emptyList();
	@Getter
	private List<String> tips = Collections.emptyList();

	@Inject
	SetupCheck(Client client, ConfigManager configManager, PluginManager pluginManager, Butler butler)
	{
		this.client = client;
		this.configManager = configManager;
		this.pluginManager = pluginManager;
		this.butler = butler;
	}

	void update(Snapshot snapshot)
	{
		List<String> missing = new ArrayList<>();
		List<String> tips = new ArrayList<>();
		if (snapshot.getSpots() != null && !snapshot.getSpots().isEmpty())
		{
			check(snapshot, missing, tips);
		}
		this.missing = missing;
		this.tips = tips;
	}

	private void check(Snapshot snapshot, List<String> missing, List<String> tips)
	{
		if (!carries(HAMMERS))
		{
			missing.add("Bring a hammer (or wear the Imcando hammer)");
		}
		if (!carries(SAWS))
		{
			missing.add("Bring a saw (or wear Amy's saw)");
		}

		boolean atTable = snapshot.getSpots().get(0).isTable();
		String wantKey = atTable ? "6" : "2";
		if (!qolRunning())
		{
			tips.add("Construction QOL lets you hold 1 instead of picking the menu");
		}
		else if (!wantKey.equals(configManager.getConfiguration(QOL_GROUP, "output"))
			|| !"1".equals(configManager.getConfiguration(QOL_GROUP, "input")))
		{
			tips.add("Construction QOL: input 1, output " + wantKey + (atTable ? " for the table" : " for gnome benches"));
		}

		ServantType type = butler.getType();
		if (type != null && type != ServantType.DEMON_BUTLER)
		{
			tips.add("The demon butler is the fastest servant (" + type.getTripTicks() + " vs " + ServantType.DEMON_BUTLER.getTripTicks() + " ticks)");
		}
		if (butler.isPaidByHand())
		{
			tips.add("A servant's money bag (58) skips the wage chat");
		}
		int fetch = butler.getRequestedFetch();
		int capacity = type != null ? Math.min(type.getCapacity(), snapshot.getPlankSlots()) : snapshot.getPlankSlots();
		int ideal = capacity / Policy.PLANKS_PER_BUILD * Policy.PLANKS_PER_BUILD;
		if (fetch > 0 && ideal > 0 && fetch % Policy.PLANKS_PER_BUILD != 0)
		{
			tips.add("Fetching " + ideal + " (whole builds) avoids leftover planks");
		}
	}

	private boolean qolRunning()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (QOL_PLUGIN.equals(plugin.getName()))
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}

	private boolean carries(Set<Integer> wanted)
	{
		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (wanted.contains(item.getId()))
				{
					return true;
				}
			}
		}
		return false;
	}
}
