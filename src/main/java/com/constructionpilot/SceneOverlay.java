package com.constructionpilot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class SceneOverlay extends Overlay
{
	// Alpha of the thin outline on what comes after the current step.
	private static final int FAINT_ALPHA = 70;

	private final Client client;
	private final ConstructionPilotPlugin plugin;
	private final House house;
	private final Butler butler;
	private final TickClock clock;
	private final ConstructionPilotConfig config;
	private final ModelOutlineRenderer outlines;

	@Inject
	SceneOverlay(Client client, ConstructionPilotPlugin plugin, House house, Butler butler, TickClock clock, ConstructionPilotConfig config, ModelOutlineRenderer outlines)
	{
		this.client = client;
		this.plugin = plugin;
		this.house = house;
		this.butler = butler;
		this.clock = clock;
		this.config = config;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Decision decision = plugin.getDecision();
		Step step = decision.getStep();
		if (step == Step.IDLE)
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player != null)
		{
			String text = butler.isReturnDialogOpen()
				? "Don't continue, click the " + (step == Step.REMOVE || step == Step.REMOVE_SOON ? "furniture" : "next spot")
				: playerLabel(step, butler.ticksLeft() < 0 ? -1 : clock.secondsUntil(butler.ticksLeft()));
			label(graphics, player.getCanvasTextLocation(graphics, text, player.getLogicalHeight() + 40), text, Color.WHITE);
		}

		drawNext(graphics, decision);

		if (step == Step.CALL_BUTLER)
		{
			GameObject bell = house.getBellPull();
			if (bell != null)
			{
				outlines.drawOutline(bell, config.outlineWidth(), config.butlerColor(), 2);
				label(graphics, bell.getCanvasTextLocation(graphics, "Ring", 120), "Ring", config.butlerColor());
			}
			return null;
		}

		NPC servant = butler.getNpc();
		if (servant != null)
		{
			String text = butlerLabel(decision, butler.getHolding());
			boolean act = step == Step.SEND_BUTLER || step == Step.TAKE_PLANKS;
			if (act)
			{
				outlines.drawOutline(servant, config.outlineWidth(), config.butlerColor(), 2);
			}
			else if (decision.isButlerNext())
			{
				outlines.drawOutline(servant, 1, faint(config.butlerColor()), 4);
				if (text == null)
				{
					text = "next";
				}
			}
			if (text != null)
			{
				label(graphics, servant.getCanvasTextLocation(graphics, text, servant.getLogicalHeight() + 40), text, config.butlerColor());
			}
			if (act)
			{
				return null;
			}
		}

		if (decision.getTarget() == null)
		{
			return null;
		}
		List<GameObject> targets = house.objectsAt(decision.getTarget());
		if (targets.isEmpty())
		{
			return null;
		}

		Color color = step == Step.BUILD ? config.buildColor() : config.removeColor();
		String text = step == Step.REMOVE_SOON ? countdownLabel(clock.secondsUntil(decision.getCountdown())) : step.getLabel();
		for (GameObject target : targets)
		{
			outlines.drawOutline(target, config.outlineWidth(), color, 2);
		}
		GameObject first = targets.get(0);
		label(graphics, first.getCanvasTextLocation(graphics, text, 120), text, color);
		return null;
	}

	// Faint and thin so it never reads as the current step; coloured by what it is for.
	private void drawNext(Graphics2D graphics, Decision decision)
	{
		if (decision.getNext() == null)
		{
			return;
		}
		boolean remove = decision.getNextStep() == Step.REMOVE || decision.getNextStep() == Step.REMOVE_SOON;
		Color color = faint(remove ? config.removeColor() : config.buildColor());
		List<GameObject> nextTargets = house.objectsAt(decision.getNext());
		for (GameObject next : nextTargets)
		{
			outlines.drawOutline(next, 1, color, 4);
		}
		if (!nextTargets.isEmpty())
		{
			label(graphics, nextTargets.get(0).getCanvasTextLocation(graphics, "next", 60), "next", Color.GRAY);
		}
	}

	private static String countdownLabel(double seconds)
	{
		return seconds <= 0 ? "Remove NOW" : String.format("Remove in %.1fs", seconds);
	}

	// While the butler is out, count his trip down on the player's head.
	private static String playerLabel(Step step, double butlerSecondsLeft)
	{
		String base = step == Step.REMOVE_SOON ? "Get ready"
			: step == Step.CALL_BUTLER ? "Call butler (bell or house options)"
			: step.getLabel();
		if (butlerSecondsLeft < 0)
		{
			return base;
		}
		String countdown = String.format("Butler %.1fs", butlerSecondsLeft);
		return step == Step.WAIT ? countdown : base + "  (" + countdown + ")";
	}

	// One label per butler, so nothing stacks when zoomed out.
	private static String butlerLabel(Decision decision, int holding)
	{
		Step step = decision.getStep();
		if (step == Step.TAKE_PLANKS)
		{
			return holding > 0 ? "Take " + holding + " planks" : step.getLabel();
		}
		if (step == Step.SEND_BUTLER)
		{
			return step.getLabel();
		}
		return holding > 0 ? "Holding " + holding : null;
	}

	private static Color faint(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), FAINT_ALPHA);
	}

	private static void label(Graphics2D graphics, Point at, String text, Color color)
	{
		if (at != null)
		{
			OverlayUtil.renderTextLocation(graphics, at, text, color);
		}
	}
}
