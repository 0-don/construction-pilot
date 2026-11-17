package com.constructionpilot;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

// Optional session numbers; the scene overlay alone is enough to play.
class StatsPanel extends OverlayPanel
{
	private final ConstructionPilotPlugin plugin;
	private final Butler butler;
	private final BuildTimer buildTimer;
	private final ConstructionPilotConfig config;

	@Inject
	StatsPanel(ConstructionPilotPlugin plugin, Butler butler, BuildTimer buildTimer, ConstructionPilotConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.butler = butler;
		this.buildTimer = buildTimer;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Decision decision = plugin.getDecision();
		Step step = decision.getStep();
		if (!config.showPanel() || step == Step.IDLE)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder().text("Next: " + step.getLabel()).build());
		line("Planks", plugin.getPlanks() + " (" + plugin.getPlanks() / Policy.PLANKS_PER_BUILD + " builds)");

		ServantType type = butler.getType();
		int away = butler.ticksAway();
		String state = butler.getNpc() != null ? "here" : away >= 0 && type != null ? "away " + away + "/" + type.getTripTicks() + " ticks" : "not seen";
		line(type != null ? type.getName() : "Butler", state);
		if (butler.getHolding() > 0)
		{
			line("Holding", butler.getHolding() + " planks");
		}
		if (butler.getLastDelivery() > 0)
		{
			line("Last delivery", String.valueOf(butler.getLastDelivery()));
		}
		line("Plank slots", String.valueOf(plugin.getPlankSlots()));
		line("Fetch / send at", decision.getFetchAmount() + " / " + decision.getSendAt());
		line("Planks per tick", String.format("%.2f", buildTimer.getPlanksPerTick()));
		line("Trips until pay", butler.getTripsUntilPay() >= 0 ? String.valueOf(butler.getTripsUntilPay()) : "? (after next pay)");

		long planksCost = plugin.planksCost();
		line("Planks used", plugin.getPlanksUsed() + " (" + QuantityFormatter.quantityToStackSize(planksCost) + ")");
		line("Wages paid", QuantityFormatter.quantityToStackSize(butler.getWagesPaid()));
		line("Session cost", QuantityFormatter.quantityToStackSize(planksCost + butler.getWagesPaid()));
		return super.render(graphics);
	}

	private void line(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
	}
}
