package com.constructionpilot;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("construction-pilot")
public interface ConstructionPilotConfig extends Config
{
	@ConfigSection(
		name = "Behaviour",
		description = "What the plugin does for you while you train in building mode",
		position = 0
	)
	String behaviourSection = "behaviour";

	@ConfigSection(
		name = "Butler",
		description = "When the plugin tells you to send the butler for more planks",
		position = 1
	)
	String butlerSection = "butler";

	@ConfigSection(
		name = "Highlights",
		description = "Colours and width of the outlines on the thing to click",
		position = 2
	)
	String highlightSection = "highlights";

	@ConfigSection(
		name = "Advanced",
		description = "Extra information for checking your speed and for troubleshooting",
		position = 3,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "smartLeftClick",
		name = "Smart left click",
		description = "Makes left click do the step that is due right now. When the butler should be sent, left clicking him talks to him even if he stands on a bench. When it is time to build or remove, left clicking the furniture or its space does that even if the butler is in the way. It only changes the order of the normal right click options, it never clicks for you. Recommended: on",
		position = 0,
		section = behaviourSection
	)
	default boolean smartLeftClick()
	{
		return true;
	}

	@ConfigItem(
		keyName = "setupHints",
		name = "Setup check",
		description = "Warns in orange when something you cannot build without is missing (a saw or a hammer). The box disappears once everything is in place",
		position = 1,
		section = behaviourSection
	)
	default boolean setupHints()
	{
		return true;
	}

	@ConfigItem(
		keyName = "setupTips",
		name = "Speed tips",
		description = "Adds optional grey tips to the setup box for the fastest hold 1 and click setup: the Construction QOL plugin with the right key (2 for oak larders, 6 for mahogany tables, 1 for teak garden benches, 2 for gnome benches), the demon butler, a servant's money bag and a fetch amount in whole builds (24 with 28 free slots). Below level 33 it suggests Mahogany Homes instead. Nothing is required: the plugin adapts to any servant, fetch amount and setup",
		position = 2,
		section = behaviourSection
	)
	default boolean setupTips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPanel",
		name = "Show panel",
		description = "Shows a small box at the top left with the next step, your planks, where the butler is and how much this session cost. The outlines and the text above your character already tell you everything, so most players leave this off",
		position = 3,
		section = behaviourSection
	)
	default boolean showPanel()
	{
		return false;
	}

	@Range(min = 0, max = 28)
	@ConfigItem(
		keyName = "sendAtOverride",
		name = "Send butler at",
		description = "Leave at 0 to let the plugin work out the best moment: it looks at your free inventory slots, how many planks the butler brings and how fast you build, so he comes back just as you run low. Set a number to always send him once you hold that many planks or fewer instead",
		position = 0,
		section = butlerSection
	)
	default int sendAtOverride()
	{
		return 0;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline width",
		description = "How thick the outline on the thing to click next is. The faint outline on the step after it always stays thin",
		position = 0,
		section = highlightSection
	)
	default int outlineWidth()
	{
		return 3;
	}

	@Alpha
	@ConfigItem(
		keyName = "buildColor",
		name = "Build color",
		description = "Colour of an empty space you should build on now. A faint version of it marks the space to build on right after",
		position = 1,
		section = highlightSection
	)
	default Color buildColor()
	{
		return new Color(0, 255, 0, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "removeColor",
		name = "Remove color",
		description = "Colour of furniture you should remove now, and of the countdown on one that is still appearing",
		position = 2,
		section = highlightSection
	)
	default Color removeColor()
	{
		return new Color(255, 80, 0, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "butlerColor",
		name = "Butler color",
		description = "Colour of the butler when you should talk to him (send him or take planks), and of the bell pull when you should ring for him. A faint version shows he is due right after your current build",
		position = 3,
		section = highlightSection
	)
	default Color butlerColor()
	{
		return new Color(0, 200, 255, 220);
	}

	@ConfigItem(
		keyName = "tickLog",
		name = "Tick log",
		description = "Writes every click, build, remove and butler trip with its game tick to the RuneLite client log. Only useful for measuring your timing or reporting a problem. Leave off otherwise",
		position = 0,
		section = advancedSection
	)
	default boolean tickLog()
	{
		return false;
	}
}
