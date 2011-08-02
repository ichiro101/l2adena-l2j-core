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

import com.l2jserver.Config;
import com.l2jserver.gameserver.ai.CtrlIntention;
import com.l2jserver.gameserver.ai.L2CharacterAI;
import com.l2jserver.gameserver.ai.L2FortSiegeGuardAI;
import com.l2jserver.gameserver.ai.L2SiegeGuardAI;
import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.instancemanager.FortManager;
import com.l2jserver.gameserver.instancemanager.TerritoryWarManager;
import com.l2jserver.gameserver.model.L2CharPosition;
import com.l2jserver.gameserver.model.actor.L2Attackable;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.L2Playable;
import com.l2jserver.gameserver.model.actor.knownlist.DefenderKnownList;
import com.l2jserver.gameserver.model.entity.Castle;
import com.l2jserver.gameserver.model.entity.Fort;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;
import com.l2jserver.gameserver.network.serverpackets.MyTargetSelected;
import com.l2jserver.gameserver.network.serverpackets.StatusUpdate;
import com.l2jserver.gameserver.network.serverpackets.ValidateLocation;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;

public class L2DefenderInstance extends L2Attackable
{
	private Castle _castle = null; // the castle which the instance should defend
	private Fort _fort = null; // the fortress which the instance should defend
	
	public L2DefenderInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		setInstanceType(InstanceType.L2DefenderInstance);
	}
	
	@Override
	public DefenderKnownList getKnownList()
	{
		return (DefenderKnownList)super.getKnownList();
	}
	
	@Override
	public void initKnownList()
	{
		setKnownList(new DefenderKnownList(this));
	}
	
	@Override
	public L2CharacterAI getAI()
	{
		L2CharacterAI ai = _ai; // copy handle
		if (ai == null)
		{
			synchronized(this)
			{
				if (_ai == null)
					if (getCastle(10000) == null)
						_ai = new L2FortSiegeGuardAI(new AIAccessor());
					else
						_ai = new L2SiegeGuardAI(new AIAccessor());
				return _ai;
			}
		}
		return ai;
	}
	
	/**
	 * Return True if a siege is in progress and the L2Character attacker isn't a Defender.<BR><BR>
	 *
	 * @param attacker The L2Character that the L2SiegeGuardInstance try to attack
	 *
	 */
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		// Attackable during siege by all except defenders
		if (!(attacker instanceof L2Playable))
			return false;
		
		L2PcInstance player = attacker.getActingPlayer();
		
		// Check if siege is in progress
		if ((_fort != null && _fort.getZone().isActive())
				|| (_castle != null && _castle.getZone().isActive()))
		{
			int activeSiegeId = (_fort != null ? _fort.getFortId() : (_castle != null ? _castle.getCastleId() : 0));
			
			// Check if player is an enemy of this defender npc
			if (player != null && ((player.getSiegeState() == 2 && !player.isRegisteredOnThisSiegeField(activeSiegeId))
					|| (player.getSiegeState() == 1 && !TerritoryWarManager.getInstance().isAllyField(player, activeSiegeId))
					|| player.getSiegeState() == 0))
			{
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean hasRandomAnimation()
	{
		return false;
	}
	
	/**
	 * This method forces guard to return to home location previously set
	 *
	 */
	@Override
	public void returnHome()
	{
		if (getWalkSpeed() <= 0)
			return;
		if (getSpawn() == null) // just in case
			return;
		if (!isInsideRadius(getSpawn().getLocx(), getSpawn().getLocy(), 40, false))
		{
			if (Config.DEBUG) _log.info(getObjectId()+": moving home");
			setisReturningToSpawnPoint(true);
			clearAggroList();
			
			if (hasAI())
				getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new L2CharPosition(getSpawn().getLocx(), getSpawn().getLocy(), getSpawn().getLocz(), 0));
		}
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		
		_fort = FortManager.getInstance().getFort(getX(), getY(), getZ());
		_castle = CastleManager.getInstance().getCastle(getX(), getY(), getZ());
		if (_fort == null && _castle == null)
			_log.warning("L2DefenderInstance spawned outside of Fortress or Castle Zone! NpcId: "+getNpcId()+ " x="+getX()+ " y="+getY()+ " z="+getZ());
	}
	
	/**
	 * Custom onAction behaviour. Note that super() is not called because guards need
	 * extra check to see if a player should interact or ATTACK them when clicked.
	 * 
	 */
	@Override
	public void onAction(L2PcInstance player, boolean interact)
	{
		if (!canTarget(player))
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Check if the L2PcInstance already target the L2NpcInstance
		if (this != player.getTarget())
		{
			if (Config.DEBUG) _log.info("new target selected:"+getObjectId());
			
			// Set the target of the L2PcInstance player
			player.setTarget(this);
			
			// Send a Server->Client packet MyTargetSelected to the L2PcInstance player
			MyTargetSelected my = new MyTargetSelected(getObjectId(), player.getLevel() - getLevel());
			player.sendPacket(my);
			
			// Send a Server->Client packet StatusUpdate of the L2NpcInstance to the L2PcInstance to update its HP bar
			StatusUpdate su = new StatusUpdate(this);
			su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp() );
			su.addAttribute(StatusUpdate.MAX_HP, getMaxHp() );
			player.sendPacket(su);
			
			// Send a Server->Client packet ValidateLocation to correct the L2NpcInstance position and heading on the client
			player.sendPacket(new ValidateLocation(this));
		}
		else if (interact)
		{
			if (isAutoAttackable(player) && !isAlikeDead())
			{
				if (Math.abs(player.getZ() - getZ()) < 600) // this max heigth difference might need some tweaking
				{
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
				}
			}
			if(!isAutoAttackable(player))
			{
				if (!canInteract(player))
				{
					// Notify the L2PcInstance AI with AI_INTENTION_INTERACT
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
				}
			}
		}
		//Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void addDamageHate(L2Character attacker, int damage, int aggro)
	{
		if (attacker == null)
			return;
		
		if (!(attacker instanceof L2DefenderInstance))
		{
			if (damage == 0 && aggro <= 1 && attacker instanceof L2Playable)
			{
				L2PcInstance player = attacker.getActingPlayer();
				// Check if siege is in progress
				if ((_fort != null && _fort.getZone().isActive())
						|| (_castle != null && _castle.getZone().isActive()))
				{
					int activeSiegeId = (_fort != null ? _fort.getFortId() : (_castle != null ? _castle.getCastleId() : 0));
					if (player != null && ((player.getSiegeState() == 2 && player.isRegisteredOnThisSiegeField(activeSiegeId))
							|| (player.getSiegeState() == 1 && TerritoryWarManager.getInstance().isAllyField(player, activeSiegeId))))
						return;
				}
			}
			super.addDamageHate(attacker, damage, aggro);
		}
	}
}