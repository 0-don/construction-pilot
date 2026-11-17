package com.constructionpilot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum Step
{
	IDLE(""),
	CALL_BUTLER("Call butler"),
	TAKE_PLANKS("Take planks"),
	SEND_BUTLER("Send butler"),
	REMOVE_SOON("Remove in"),
	REMOVE("Remove"),
	BUILD("Build"),
	WAIT("Wait for planks");

	private final String label;
}
