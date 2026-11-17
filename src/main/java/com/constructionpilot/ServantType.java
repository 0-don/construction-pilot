package com.constructionpilot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.NpcID;

@Getter
@RequiredArgsConstructor
enum ServantType
{
	RICK(NpcID.POH_SERVANT_DOGSBODY, "Rick", 100, 500, 6),
	MAID(NpcID.POH_SERVANT_WAITER_WOMAN, "Maid", 50, 1000, 10),
	COOK(NpcID.POH_SERVANT_COOK_WOMAN, "Cook", 28, 3000, 16),
	BUTLER(NpcID.POH_SERVANT_MAITRE_D_MAN, "Butler", 20, 5000, 20),
	DEMON_BUTLER(NpcID.POH_SERVANT_DEMON, "Demon butler", 12, 10000, 26);

	static final int TRIPS_PER_PAYMENT = 8;

	private final int npcId;
	private final String name;
	private final int tripTicks;
	private final int wage;
	private final int capacity;

	static ServantType byNpcId(int npcId)
	{
		for (ServantType type : values())
		{
			if (type.npcId == npcId)
			{
				return type;
			}
		}
		return null;
	}
}
