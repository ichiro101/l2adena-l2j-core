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
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.zone.L2ZoneType;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * A mother-trees zone
 * Basic type zone for Hp, MP regen
 *
 * @author  durgus
 */
public class L2MotherTreeZone extends L2ZoneType
{
	private int _enterMsg;
	private int _leaveMsg;
	private int _mpRegen;
	private int _hpRegen;
	
	public L2MotherTreeZone(int id)
	{
		super(id);
	}
	
	@Override
	public void setParameter(String name, String value)
	{
		if (name.equals("enterMsgId"))
		{
			_enterMsg = Integer.valueOf(value);
		}
		else if (name.equals("leaveMsgId"))
		{
			_leaveMsg = Integer.valueOf(value);
		}
		else if (name.equals("MpRegenBonus"))
		{
			_mpRegen = Integer.valueOf(value);
		}
		else if (name.equals("HpRegenBonus"))
		{
			_hpRegen = Integer.valueOf(value);
		}
		else
			super.setParameter(name, value);
		
	}
	
	
	@Override
	protected void onEnter(L2Character character)
	{
		if (character instanceof L2PcInstance)
		{
			L2PcInstance player = (L2PcInstance) character;
			player.setInsideZone(L2Character.ZONE_MOTHERTREE, true);
			if (_enterMsg != 0)
				player.sendPacket(SystemMessage.getSystemMessage(_enterMsg));
		}
	}
	
	@Override
	protected void onExit(L2Character character)
	{
		if (character instanceof L2PcInstance)
		{
			L2PcInstance player = (L2PcInstance) character;
			player.setInsideZone(L2Character.ZONE_MOTHERTREE, false);
			if (_leaveMsg != 0)
				player.sendPacket(SystemMessage.getSystemMessage(_leaveMsg));
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
	
	/**
	 * @return the _mpRegen
	 */
	public int getMpRegenBonus()
	{
		return _mpRegen;
	}
	
	/**
	 * @return the _hpRegen
	 */
	public int getHpRegenBonus()
	{
		return _hpRegen;
	}
	
}
