package com.constructionpilot;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ConstructionPilotPluginTest
{
	public static void main(String[] args) throws Exception
	{
		// Bolt passes launcher style -J JVM options (user.home among them); apply them before RuneLite reads its paths.
		List<String> clientArgs = new ArrayList<>();
		for (String arg : args)
		{
			if (arg.startsWith("-J-D") && arg.contains("="))
			{
				String property = arg.substring(4);
				int eq = property.indexOf('=');
				System.setProperty(property.substring(0, eq), property.substring(eq + 1));
			}
			else if (!arg.startsWith("-J"))
			{
				clientArgs.add(arg);
			}
		}

		// Bolt starts the jar without -ea, which loadBuiltin refuses; classes loaded after this see assertions on.
		ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
		ExternalPluginManager.loadBuiltin(ConstructionPilotPlugin.class);
		RuneLite.main(clientArgs.toArray(new String[0]));
	}
}
