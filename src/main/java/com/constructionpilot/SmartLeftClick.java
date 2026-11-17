package com.constructionpilot;

import net.runelite.api.MenuEntry;
import net.runelite.client.util.Text;

// Moves the entry for the current step to left click: the servant's Talk-to when he is due, the Build or Remove
// on a bench or table when that is due. It only reorders; it never adds, removes or clicks anything.
final class SmartLeftClick
{
	private SmartLeftClick()
	{
	}

	// Returns the promoted entry, or null when nothing moved. The last entry is the left click.
	static MenuEntry promote(MenuEntry[] entries, Step step)
	{
		boolean butlerStep = step == Step.SEND_BUTLER || step == Step.TAKE_PLANKS;
		boolean benchStep = step == Step.BUILD || step == Step.REMOVE || step == Step.REMOVE_SOON;
		if (!butlerStep && !benchStep)
		{
			return null;
		}
		int top = entries.length - 1;
		for (int i = top; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			if (butlerStep ? isServantTalk(entry) : isBenchAction(entry))
			{
				if (i == top)
				{
					return null;
				}
				entries[i] = entries[top];
				entries[top] = entry;
				return entry;
			}
		}
		return null;
	}

	private static boolean isServantTalk(MenuEntry entry)
	{
		return "Talk-to".equals(Text.removeTags(entry.getOption())) && entry.getNpc() != null && ServantType.byNpcId(entry.getNpc().getId()) != null;
	}

	private static boolean isBenchAction(MenuEntry entry)
	{
		String option = Text.removeTags(entry.getOption());
		String target = Text.removeTags(entry.getTarget()).toLowerCase();
		return ("Remove".equals(option) && (target.contains("gnome bench") || target.contains("mahogany table")))
			|| ("Build".equals(option) && (target.contains("seating space") || target.contains("table space")));
	}
}
