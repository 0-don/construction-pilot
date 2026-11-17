package com.constructionpilot;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

// Off by default. Info level so it lands in client.log, which RuneLite writes at INFO.
@Slf4j
@Singleton
class TickLog
{
	private final Client client;
	private final ConstructionPilotConfig config;

	@Inject
	TickLog(Client client, ConstructionPilotConfig config)
	{
		this.client = client;
		this.config = config;
	}

	boolean enabled()
	{
		return config.tickLog();
	}

	void add(String what)
	{
		if (config.tickLog())
		{
			log.info("[tick {}] {}", client.getTickCount(), what);
		}
	}
}
