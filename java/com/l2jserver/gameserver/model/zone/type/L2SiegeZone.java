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

import javolution.util.FastList;

import com.l2jserver.Config;
import com.l2jserver.gameserver.datatables.SkillTable;
import com.l2jserver.gameserver.instancemanager.FortManager;
import com.l2jserver.gameserver.instancemanager.FortSiegeManager;
import com.l2jserver.gameserver.instancemanager.MapRegionManager;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.L2Effect;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2SiegeSummonInstance;
import com.l2jserver.gameserver.model.entity.Fort;
import com.l2jserver.gameserver.model.entity.FortSiege;
import com.l2jserver.gameserver.model.entity.Siegable;
import com.l2jserver.gameserver.model.zone.L2ZoneType;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * A  siege zone
 *
 * @author  durgus
 */
public class L2SiegeZone extends L2ZoneType
{
	private int _siegableId = -1;
	private Siegable _siege = null;
	private boolean _isActiveSiege = false;
	private static final int DISMOUNT_DELAY = 5;
	
	public L2SiegeZone(int id)
	{
		super(id);
	}
	
	@Override
	public void setParameter(String name, String value)
	{
		if (name.equals("castleId"))
		{
			if (_siegableId != -1)
				throw new IllegalArgumentException("Siege object already defined!");
			_siegableId = Integer.parseInt(value);
		}
		else if (name.equals("fortId"))
		{
			if (_siegableId != -1)
				throw new IllegalArgumentException("Siege object already defined!");
			_siegableId = Integer.parseInt(value);
		}
		else if (name.equals("clanHallId"))
		{
			if (_siegableId != -1)
				throw new IllegalArgumentException("Siege object already defined!");
			_siegableId = Integer.parseInt(value);
			//TODO clan hall siege
		}
		else
			super.setParameter(name, value);
	}
	
	@Override
	protected void onEnter(L2Character character)
	{
		if (_isActiveSiege)
		{
			character.setInsideZone(L2Character.ZONE_PVP, true);
			character.setInsideZone(L2Character.ZONE_SIEGE, true);
			character.setInsideZone(L2Character.ZONE_NOSUMMONFRIEND, true);
			
			if (character instanceof L2PcInstance)
			{
				
				if (((L2PcInstance) character).isRegisteredOnThisSiegeField(_siegableId))
				{
					((L2PcInstance) character).setIsInSiege(true); // in siege
					if (_siege.giveFame())
						((L2PcInstance) character).startFameTask(_siege.getFameFrequency() * 1000, _siege.getFameAmount());
				}
				((L2PcInstance) character).sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ENTERED_COMBAT_ZONE));
				if (!Config.ALLOW_WYVERN_DURING_SIEGE && ((L2PcInstance) character).getMountType() == 2)
				{
					character.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.AREA_CANNOT_BE_ENTERED_WHILE_MOUNTED_WYVERN));
					((L2PcInstance) character).enteredNoLanding(DISMOUNT_DELAY);
				}
			}
		}
	}
	
	@Override
	protected void onExit(L2Character character)
	{
		character.setInsideZone(L2Character.ZONE_PVP, false);
		character.setInsideZone(L2Character.ZONE_SIEGE, false);
		character.setInsideZone(L2Character.ZONE_NOSUMMONFRIEND, false);
		if (_isActiveSiege)
		{
			if (character instanceof L2PcInstance)
			{
				((L2PcInstance) character).sendPacket(SystemMessage.getSystemMessage(SystemMessageId.LEFT_COMBAT_ZONE));
				if (((L2PcInstance) character).getMountType() == 2)
				{
					((L2PcInstance) character).exitedNoLanding();
				}
				// Set pvp flag
				if (((L2PcInstance) character).getPvpFlag() == 0)
					((L2PcInstance) character).startPvPFlag();
			}
		}
		if (character instanceof L2PcInstance)
		{
			L2PcInstance activeChar = (L2PcInstance) character;
			activeChar.stopFameTask();
			activeChar.setIsInSiege(false);
			
			if (_siege instanceof FortSiege && activeChar.getInventory().getItemByItemId(9819) != null)
			{
				// drop combat flag
				Fort fort = FortManager.getInstance().getFortById(_siegableId);
				if (fort != null)
				{
					FortSiegeManager.getInstance().dropCombatFlag(activeChar, fort.getFortId());
				}
				else
				{
					int slot = activeChar.getInventory().getSlotFromItem(activeChar.getInventory().getItemByItemId(9819));
					activeChar.getInventory().unEquipItemInBodySlot(slot);
					activeChar.destroyItem("CombatFlag", activeChar.getInventory().getItemByItemId(9819), null, true);
				}
			}
		}
		
		if (character instanceof L2SiegeSummonInstance)
		{
			((L2SiegeSummonInstance) character).unSummon(((L2SiegeSummonInstance) character).getOwner());
		}
	}
	
	@Override
	public void onDieInside(L2Character character)
	{
		if (_isActiveSiege)
		{
			// debuff participants only if they die inside siege zone
			if (character instanceof L2PcInstance && ((L2PcInstance) character).isRegisteredOnThisSiegeField(_siegableId))
			{
				int lvl = 1;
				final L2Effect e = character.getFirstEffect(5660);
				if (e != null)
					lvl = Math.min(lvl + e.getLevel(), 5);
				
				final L2Skill skill = SkillTable.getInstance().getInfo(5660, lvl);
				if (skill != null)
					skill.getEffects(character, character);
			}
		}
	}
	
	@Override
	public void onReviveInside(L2Character character)
	{
	}
	
	public void updateZoneStatusForCharactersInside()
	{
		if (_isActiveSiege)
		{
			for (L2Character character : _characterList.values())
			{
				if (character != null)
					onEnter(character);
			}
		}
		else
		{
			for (L2Character character : _characterList.values())
			{
				if (character == null)
					continue;
				character.setInsideZone(L2Character.ZONE_PVP, false);
				character.setInsideZone(L2Character.ZONE_SIEGE, false);
				character.setInsideZone(L2Character.ZONE_NOSUMMONFRIEND, false);
				
				if (character instanceof L2PcInstance)
				{
					((L2PcInstance) character).sendPacket(SystemMessage.getSystemMessage(SystemMessageId.LEFT_COMBAT_ZONE));
					((L2PcInstance) character).stopFameTask();
					if (((L2PcInstance) character).getMountType() == 2)
					{
						((L2PcInstance) character).exitedNoLanding();
					}
				}
				if (character instanceof L2SiegeSummonInstance)
				{
					((L2SiegeSummonInstance) character).unSummon(((L2SiegeSummonInstance) character).getOwner());
				}
				
			}
		}
	}
	
	
	/**
	 * Sends a message to all players in this zone
	 * @param message
	 */
	public void announceToPlayers(String message)
	{
		for (L2Character temp : _characterList.values())
		{
			if (temp instanceof L2PcInstance)
				((L2PcInstance) temp).sendMessage(message);
		}
	}
	
	/**
	 * Returns all players within this zone
	 * @return
	 */
	public FastList<L2PcInstance> getAllPlayers()
	{
		FastList<L2PcInstance> players = new FastList<L2PcInstance>();
		
		for (L2Character temp : _characterList.values())
		{
			if (temp instanceof L2PcInstance)
				players.add((L2PcInstance) temp);
		}
		
		return players;
	}
	
	public int getSiegeObjectId()
	{
		return _siegableId;
	}
	
	public boolean isActive()
	{
		return _isActiveSiege;
	}
	
	public void setIsActive(boolean val)
	{
		_isActiveSiege = val;
	}
	
	public void setSiegeInstance(Siegable siege)
	{
		_siege = siege;
	}
	
	/**
	 * Removes all foreigners from the zone
	 * @param owningClan
	 */
	public void banishForeigners(L2Clan owningClan)
	{
		for (L2Character temp : _characterList.values())
		{
			if (!(temp instanceof L2PcInstance))
				continue;
			if (((L2PcInstance) temp).getClan() == owningClan || ((L2PcInstance) temp).isGM())
				continue;
			
			((L2PcInstance) temp).teleToLocation(MapRegionManager.TeleportWhereType.Town);
		}
	}
}
