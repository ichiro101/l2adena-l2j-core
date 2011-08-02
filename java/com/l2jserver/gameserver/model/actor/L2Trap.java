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
package com.l2jserver.gameserver.model.actor;

import java.util.Collection;
import java.util.logging.Level;

import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.knownlist.TrapKnownList;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.Quest.TrapAction;
import com.l2jserver.gameserver.network.serverpackets.AbstractNpcInfo;
import com.l2jserver.gameserver.network.serverpackets.L2GameServerPacket;
import com.l2jserver.gameserver.network.serverpackets.SocialAction;
import com.l2jserver.gameserver.taskmanager.DecayTaskManager;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;
import com.l2jserver.gameserver.templates.item.L2Weapon;

/**
 *
 * @author nBd
 */
public class L2Trap extends L2Character
{
	protected static final int TICK = 1000; // 1s
	
	private boolean _isTriggered;
	private final L2Skill _skill;
	private final int _lifeTime;
	private int _timeRemaining;
	private boolean _hasLifeTime;
	
	/**
	 * @param objectId
	 * @param template
	 */
	public L2Trap(int objectId, L2NpcTemplate template, int lifeTime, L2Skill skill)
	{
		super(objectId, template);
		setInstanceType(InstanceType.L2Trap);
		setName(template.name);
		setIsInvul(false);
		
		_isTriggered = false;
		_skill = skill;
		_hasLifeTime = true;
		if (lifeTime != 0)
			_lifeTime = lifeTime;
		else
			_lifeTime = 30000;
		_timeRemaining = _lifeTime;
		if (lifeTime < 0)
			_hasLifeTime = false;
		
		if (skill != null)
			ThreadPoolManager.getInstance().scheduleGeneral(new TrapTask(), TICK);
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#getKnownList()
	 */
	@Override
	public TrapKnownList getKnownList()
	{
		return (TrapKnownList) super.getKnownList();
	}
	
	@Override
	public void initKnownList()
	{
		setKnownList(new TrapKnownList(this));
	}
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return !canSee(attacker);
	}
	
	/**
	 * 
	 *
	 */
	public void stopDecay()
	{
		DecayTaskManager.getInstance().cancelDecayTask(this);
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#onDecay()
	 */
	@Override
	public void onDecay()
	{
		deleteMe();
	}
	
	/**
	 * 
	 * @return
	 */
	public final int getNpcId()
	{
		return getTemplate().npcId;
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#doDie(com.l2jserver.gameserver.model.actor.L2Character)
	 */
	@Override
	public boolean doDie(L2Character killer)
	{
		if (!super.doDie(killer))
			return false;
		
		DecayTaskManager.getInstance().addDecayTask(this);
		return true;
	}
	
	/**
	 * 
	 * @param owner
	 */
	@Override
	public void deleteMe()
	{
		decayMe();
		getKnownList().removeAllKnownObjects();
		super.deleteMe();
	}
	
	/**
	 * 
	 * @param owner
	 */
	public synchronized void unSummon()
	{
		if (isVisible() && !isDead())
		{
			if (getWorldRegion() != null)
				getWorldRegion().removeFromZones(this);
			
			deleteMe();
		}
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#getActiveWeaponInstance()
	 */
	@Override
	public L2ItemInstance getActiveWeaponInstance()
	{
		return null;
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#getActiveWeaponItem()
	 */
	@Override
	public L2Weapon getActiveWeaponItem()
	{
		return null;
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#getLevel()
	 */
	@Override
	public int getLevel()
	{
		return getTemplate().level;
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#getTemplate()
	 */
	@Override
	public L2NpcTemplate getTemplate()
	{
		return (L2NpcTemplate) super.getTemplate();
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#getSecondaryWeaponInstance()
	 */
	@Override
	public L2ItemInstance getSecondaryWeaponInstance()
	{
		return null;
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#getSecondaryWeaponItem()
	 */
	@Override
	public L2Weapon getSecondaryWeaponItem()
	{
		return null;
	}
	
	/**
	 * 
	 * @see com.l2jserver.gameserver.model.actor.L2Character#updateAbnormalEffect()
	 */
	@Override
	public void updateAbnormalEffect()
	{
		
	}
	
	public L2Skill getSkill()
	{
		return _skill;
	}
	
	public L2PcInstance getOwner()
	{
		return null;
	}
	
	public int getKarma()
	{
		return 0;
	}
	
	public byte getPvpFlag()
	{
		return 0;
	}
	
	/**
	 * Checks is triggered
	 * @return True if trap is triggered.
	 */
	public boolean isTriggered()
	{
		return _isTriggered;
	}
	
	/**
	 * Checks trap visibility
	 * @param cha - checked character
	 * @return True if character can see trap
	 */
	public boolean canSee(L2Character cha)
	{
		return false;
	}
	
	/**
	 * Reveal trap to the detector (if possible)
	 * @param detector
	 */
	public void setDetected(L2Character detector)
	{
		detector.sendPacket(new AbstractNpcInfo.TrapInfo(this, detector));
	}
	
	/**
	 * Check if target can trigger trap
	 * @param target
	 * @return
	 */
	protected boolean checkTarget(L2Character target)
	{
		return L2Skill.checkForAreaOffensiveSkills(this, target, _skill, false);
	}
	
	private class TrapTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				if (!_isTriggered)
				{
					if (_hasLifeTime)
					{
						_timeRemaining -= TICK;
						if (_timeRemaining < _lifeTime - 15000)
						{
							SocialAction sa = new SocialAction(L2Trap.this, 2);
							broadcastPacket(sa);
						}
						if (_timeRemaining < 0)
						{
							switch (getSkill().getTargetType())
							{
								case TARGET_AURA:
								case TARGET_FRONT_AURA:
								case TARGET_BEHIND_AURA:
									trigger(L2Trap.this);
									break;
								default:
									unSummon();
							}
							return;
						}
					}
					
					for (L2Character target : getKnownList().getKnownCharactersInRadius(_skill.getSkillRadius()))
					{
						if (!checkTarget(target))
							continue;
						
						trigger(target);
						return;
					}
					
					ThreadPoolManager.getInstance().scheduleGeneral(new TrapTask(), TICK);
				}
			}
			catch (Exception e)
			{
				_log.log(Level.SEVERE, "", e);
				unSummon();
			}
		}
	}
	
	/**
	 * Trigger trap
	 * @param target
	 */
	public void trigger(L2Character target)
	{
		_isTriggered = true;
		broadcastPacket(new AbstractNpcInfo.TrapInfo(this, null));
		setTarget(target);
		
		if (getTemplate().getEventQuests(Quest.QuestEventType.ON_TRAP_ACTION) != null)
			for (Quest quest : getTemplate().getEventQuests(Quest.QuestEventType.ON_TRAP_ACTION))
				quest.notifyTrapAction(this, target, TrapAction.TRAP_TRIGGERED);
		
		ThreadPoolManager.getInstance().scheduleGeneral(new TriggerTask(), 300);
	}
	
	private class TriggerTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				doCast(_skill);
				ThreadPoolManager.getInstance().scheduleGeneral(new UnsummonTask(), _skill.getHitTime() + 300);
			}
			catch (Exception e)
			{
				unSummon();
			}
		}
	}
	
	private class UnsummonTask implements Runnable
	{
		@Override
		public void run()
		{
			unSummon();
		}
	}
	
	@Override
	public void sendInfo(L2PcInstance activeChar)
	{
		if (_isTriggered || canSee(activeChar))
			activeChar.sendPacket(new AbstractNpcInfo.TrapInfo(this, activeChar));
	}
	
	@Override
	public void broadcastPacket(L2GameServerPacket mov)
	{
		Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
		for (L2PcInstance player : plrs)
			if (player != null && (_isTriggered || canSee(player)))
				player.sendPacket(mov);
	}
	
	@Override
	public void broadcastPacket(L2GameServerPacket mov, int radiusInKnownlist)
	{
		Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
		for (L2PcInstance player : plrs)
		{
			if (player == null)
				continue;
			if (isInsideRadius(player, radiusInKnownlist, false, false))
				if (_isTriggered || canSee(player))
					player.sendPacket(mov);
		}
	}
}
