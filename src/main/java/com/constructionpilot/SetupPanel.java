package com.constructionpilot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

// Required items in orange, optional speed tips in grey; disappears once there is nothing to say.
class SetupPanel extends OverlayPanel
{
	private final SetupCheck setupCheck;
	private final ConstructionPilotConfig config;

	@Inject
	SetupPanel(ConstructionPilotPlugin plugin, SetupCheck setupCheck, ConstructionPilotConfig config)
	{
		super(plugin);
		this.setupCheck = setupCheck;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<String> missing = config.setupHints() ? setupCheck.getMissing() : Collections.emptyList();
		List<String> tips = config.setupTips() ? setupCheck.getTips() : Collections.emptyList();
		if (missing.isEmpty() && tips.isEmpty())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Construction Pilot").color(missing.isEmpty() ? Color.LIGHT_GRAY : Color.ORANGE).build());
		for (String line : missing)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(line).leftColor(Color.ORANGE).build());
		}
		for (String line : tips)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Tip: " + line).leftColor(Color.GRAY).build());
		}
		return super.render(graphics);
	}
}
