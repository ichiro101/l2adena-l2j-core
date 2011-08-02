/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.gameserver.model.zone.type;

import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.L2Playable;
import com.l2jserver.gameserver.model.zone.L2ZoneType;

/**
 * The Monster Derby Track Zone
 *
 * @author  durgus
 */
public class L2DerbyTrackZone extends L2ZoneType
{
	public L2DerbyTrackZone(int id)
	{
		super(id);
	}
	
	@Override
	protected void onEnter(L2Character character)
	{
		if (character instanceof L2Playable)
		{
			character.setInsideZone(L2Character.ZONE_MONSTERTRACK, true);
			character.setInsideZone(L2Character.ZONE_PEACE, true);
			character.setInsideZone(L2Character.ZONE_NOSUMMONFRIEND, true);
		}
	}
	
	@Override
	protected void onExit(L2Character character)
	{
		if (character instanceof L2Playable)
		{
			character.setInsideZone(L2Character.ZONE_MONSTERTRACK, false);
			character.setInsideZone(L2Character.ZONE_PEACE, false);
			character.setInsideZone(L2Character.ZONE_NOSUMMONFRIEND, false);
		}
	}
	
	@Override
	public void onDieInside(L2Character character)
	{
	}
	
	@Override
	public void onReviveInside(L2Character character)
	{
	}
	
}
