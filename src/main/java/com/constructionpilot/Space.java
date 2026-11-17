package com.constructionpilot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Kind of build space; its name is the menu target of its Build option.
@Getter
@RequiredArgsConstructor
enum Space
{
	TABLE("table space"),
	LARDER("larder space"),
	GARDEN("seating space");

	private final String hotspotName;
}
