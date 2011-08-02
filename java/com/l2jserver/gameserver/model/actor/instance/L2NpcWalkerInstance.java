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
package com.l2jserver.gameserver.model.actor.instance;

import com.l2jserver.gameserver.ai.L2CharacterAI;
import com.l2jserver.gameserver.ai.L2NpcWalkerAI;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.network.clientpackets.Say2;
import com.l2jserver.gameserver.network.serverpackets.NpcSay;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;
import com.l2jserver.gameserver.util.Broadcast;


/**
 * This class manages some npcs can walk in the city. <br>
 * It inherits all methods from L2NpcInstance. <br><br>
 *
 * @original author Rayan RPG for L2Emu Project
 * @since 819
 */
public class L2NpcWalkerInstance extends L2Npc
{
	/**
	 * Constructor of L2NpcWalkerInstance (use L2Character and L2NpcInstance constructor).<BR><BR>
	 * @param objectId given object id
	 * @param template L2NpcTemplateForThisAi
	 */
	public L2NpcWalkerInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		setInstanceType(InstanceType.L2NpcWalkerInstance);
		setAI(new L2NpcWalkerAI(new L2NpcWalkerAIAccessor()));
	}
	
	/**
	 * AI can't be deattached, npc must move always with the same AI instance.
	 * @param newAI AI to set for this L2NpcWalkerInstance
	 */
	@Override
	public void setAI(L2CharacterAI newAI)
	{
		if(!(_ai instanceof L2NpcWalkerAI))
			_ai = newAI;
	}
	
	@Override
	public void onSpawn()
	{
		getAI().setHomeX(getX());
		getAI().setHomeY(getY());
		getAI().setHomeZ(getZ());
	}
	
	/**
	 * Sends a chat to all _knowObjects
	 * @param chat message to say
	 */
	public void broadcastChat(String chat, int id)
	{
		NpcSay cs;
		if (id == 0)
			cs = new NpcSay(getObjectId(), Say2.ALL, getNpcId(), chat);
		else
			cs = new NpcSay(getObjectId(), Say2.ALL, getNpcId(), id);
		Broadcast.toKnownPlayers(this, cs);
	}
	
	/**
	 * NPCs are immortal
	 * @param i ignore it
	 * @param attacker  ignore it
	 * @param awake  ignore it
	 */
	@Override
	public void reduceCurrentHp(double i, L2Character attacker, boolean awake, boolean isDOT, L2Skill skill)
	{}
	
	/**
	 * NPCs are immortal
	 * @param killer ignore it
	 * @return false
	 */
	@Override
	public boolean doDie(L2Character killer)
	{
		return false;
	}
	
	@Override
	public L2NpcWalkerAI getAI()
	{
		return (L2NpcWalkerAI)_ai;
	}
	
	protected class L2NpcWalkerAIAccessor extends L2Character.AIAccessor
	{
		/**
		 * AI can't be deattached.
		 */
		@Override
		public void detachAI()
		{}
	}
}
