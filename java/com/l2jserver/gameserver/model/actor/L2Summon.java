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

import com.l2jserver.Config;
import com.l2jserver.gameserver.ai.CtrlIntention;
import com.l2jserver.gameserver.ai.L2CharacterAI;
import com.l2jserver.gameserver.ai.L2SummonAI;
import com.l2jserver.gameserver.datatables.ExperienceTable;
import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.instancemanager.TerritoryWarManager;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.L2Skill.SkillTargetType;
import com.l2jserver.gameserver.model.L2WorldRegion;
import com.l2jserver.gameserver.model.actor.L2Attackable.AggroInfo;
import com.l2jserver.gameserver.model.actor.instance.L2DoorInstance;
import com.l2jserver.gameserver.model.actor.instance.L2MerchantSummonInstance;
import com.l2jserver.gameserver.model.actor.instance.L2NpcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PetInstance;
import com.l2jserver.gameserver.model.actor.instance.L2SummonInstance;
import com.l2jserver.gameserver.model.actor.knownlist.SummonKnownList;
import com.l2jserver.gameserver.model.actor.stat.SummonStat;
import com.l2jserver.gameserver.model.actor.status.SummonStatus;
import com.l2jserver.gameserver.model.itemcontainer.PetInventory;
import com.l2jserver.gameserver.model.olympiad.OlympiadGameManager;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.AbstractNpcInfo;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;
import com.l2jserver.gameserver.network.serverpackets.ExPartyPetWindowAdd;
import com.l2jserver.gameserver.network.serverpackets.ExPartyPetWindowDelete;
import com.l2jserver.gameserver.network.serverpackets.ExPartyPetWindowUpdate;
import com.l2jserver.gameserver.network.serverpackets.L2GameServerPacket;
import com.l2jserver.gameserver.network.serverpackets.PetDelete;
import com.l2jserver.gameserver.network.serverpackets.PetInfo;
import com.l2jserver.gameserver.network.serverpackets.PetItemList;
import com.l2jserver.gameserver.network.serverpackets.PetStatusUpdate;
import com.l2jserver.gameserver.network.serverpackets.RelationChanged;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.network.serverpackets.TeleportToLocation;
import com.l2jserver.gameserver.taskmanager.DecayTaskManager;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;
import com.l2jserver.gameserver.templates.item.L2EtcItem;
import com.l2jserver.gameserver.templates.item.L2Weapon;

public abstract class L2Summon extends L2Playable
{
	private L2PcInstance _owner;
	private int _attackRange = 36; //Melee range
	private boolean _follow = true;
	private boolean _previousFollowStatus = true;
	
	private int _chargedSoulShot;
	private int _chargedSpiritShot;
	
	//  /!\ BLACK MAGIC /!\
	// we dont have walk speed in pet data so for now use runspd / 3
	public static final int WALK_SPEED_MULTIPLIER = 3;
	
	public boolean _restoreSummon = true;
	
	public class AIAccessor extends L2Character.AIAccessor
	{
		protected AIAccessor() {}
		public L2Summon getSummon() { return L2Summon.this; }
		public boolean isAutoFollow() {
			return L2Summon.this.getFollowStatus();
		}
		public void doPickupItem(L2Object object) {
			L2Summon.this.doPickupItem(object);
		}
	}
	
	public L2Summon(int objectId, L2NpcTemplate template, L2PcInstance owner)
	{
		super(objectId, template);
		setInstanceType(InstanceType.L2Summon);
		
		setInstanceId(owner.getInstanceId()); // set instance to same as owner
		
		_showSummonAnimation = true;
		_owner = owner;
		_ai = new L2SummonAI(new L2Summon.AIAccessor());
		
		setXYZInvisible(owner.getX()+20, owner.getY()+20, owner.getZ()+100);
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		if (!(this instanceof L2MerchantSummonInstance))
		{
			if (Config.SUMMON_STORE_SKILL_COOLTIME && !isTeleporting())
				restoreEffects();
			
			this.setFollowStatus(true);
			updateAndBroadcastStatus(0);
			getOwner().sendPacket(new RelationChanged(this, getOwner().getRelation(getOwner()), false));
			for (L2PcInstance player : getOwner().getKnownList().getKnownPlayersInRadius(800))
				player.sendPacket(new RelationChanged(this, getOwner().getRelation(player), isAutoAttackable(player)));
			L2Party party = this.getOwner().getParty();
			if (party != null)
			{
				party.broadcastToPartyMembers(this.getOwner(), new ExPartyPetWindowAdd(this));
			}
		}
		setShowSummonAnimation(false); // addVisibleObject created the info packets with summon animation
		// if someone comes into range now, the animation shouldnt show any more
		_restoreSummon = false;
	}
	
	@Override
	public final SummonKnownList getKnownList()
	{
		return (SummonKnownList)super.getKnownList();
	}
	
	@Override
	public void initKnownList()
	{
		setKnownList(new SummonKnownList(this));
	}
	
	@Override
	public SummonStat getStat()
	{
		return (SummonStat)super.getStat();
	}
	
	@Override
	public void initCharStat()
	{
		setStat(new SummonStat(this));
	}
	
	@Override
	public SummonStatus getStatus()
	{
		return (SummonStatus)super.getStatus();
	}
	
	@Override
	public void initCharStatus()
	{
		setStatus(new SummonStatus(this));
	}
	
	@Override
	public L2CharacterAI getAI()
	{
		L2CharacterAI ai = _ai; // copy handle
		if (ai == null)
		{
			synchronized(this)
			{
				if (_ai == null) _ai = new L2SummonAI(new L2Summon.AIAccessor());
				return _ai;
			}
		}
		return ai;
	}
	
	@Override
	public L2NpcTemplate getTemplate()
	{
		return (L2NpcTemplate)super.getTemplate();
	}
	
	// this defines the action buttons, 1 for Summon, 2 for Pets
	public abstract int getSummonType();
	
	@Override
	public final void stopAllEffects()
	{
		super.stopAllEffects();
		updateAndBroadcastStatus(1);
	}
	
	@Override
	public final void stopAllEffectsExceptThoseThatLastThroughDeath()
	{
		super.stopAllEffectsExceptThoseThatLastThroughDeath();
		updateAndBroadcastStatus(1);
	}
	
	@Override
	public void updateAbnormalEffect()
	{
		Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
		//synchronized (getKnownList().getKnownPlayers())
		{
			for (L2PcInstance player : plrs)
				player.sendPacket(new AbstractNpcInfo.SummonInfo(this, player,1));
		}
	}
	
	/**
	 * @return Returns the mountable.
	 */
	public boolean isMountable()
	{
		return false;
	}
	
	public long getExpForThisLevel()
	{
		if(getLevel() >= ExperienceTable.getInstance().getMaxPetLevel())
		{
			return 0;
		}
		return ExperienceTable.getInstance().getExpForLevel(getLevel());
	}
	
	public long getExpForNextLevel()
	{
		if(getLevel() >= ExperienceTable.getInstance().getMaxPetLevel() - 1)
		{
			return 0;
		}
		return ExperienceTable.getInstance().getExpForLevel(getLevel()+1);
	}
	
	@Override
	public final int getKarma()
	{
		return getOwner()!= null ? getOwner().getKarma() : 0;
	}
	
	@Override
	public final byte getPvpFlag()
	{
		return getOwner()!= null ? getOwner().getPvpFlag() : 0;
	}
	
	public final int getTeam()
	{
		return getOwner()!= null ? getOwner().getTeam() : 0;
	}
	
	public final L2PcInstance getOwner()
	{
		return _owner;
	}
	
	public final int getNpcId()
	{
		return getTemplate().npcId;
	}
	
	public int getMaxLoad()
	{
		return 0;
	}
	
	public short getSoulShotsPerHit()
	{
		if (getTemplate().getAIDataStatic().getSoulShot() > 0)
			return (short) getTemplate().getAIDataStatic().getSoulShot();
		else return 1;
	}
	
	public short getSpiritShotsPerHit()
	{
		if (getTemplate().getAIDataStatic().getSpiritShot() > 0)
			return (short) getTemplate().getAIDataStatic().getSpiritShot();
		else return 1;
	}
	
	public void setChargedSoulShot(int shotType)
	{
		_chargedSoulShot = shotType;
	}
	
	public void setChargedSpiritShot(int shotType)
	{
		_chargedSpiritShot = shotType;
	}
	
	public void followOwner()
	{
		setFollowStatus(true);
	}
	
	@Override
	public boolean doDie(L2Character killer)
	{
		if (isNoblesseBlessed())
		{
			stopNoblesseBlessing(null);
			storeEffect(true);
		}
		
		if (!super.doDie(killer))
			return false;
		if (this instanceof L2MerchantSummonInstance)
			return true;
		L2PcInstance owner = getOwner();
		
		if (owner != null)
		{
			Collection<L2Character> KnownTarget = this.getKnownList().getKnownCharacters();
			for (L2Character TgMob : KnownTarget)
			{
				// get the mobs which have aggro on the this instance
				if (TgMob instanceof L2Attackable)
				{
					if (((L2Attackable) TgMob).isDead())
						continue;
					
					AggroInfo info = ((L2Attackable) TgMob).getAggroList().get(this);
					if (info != null)
						((L2Attackable) TgMob).addDamageHate(owner, info.getDamage(), info.getHate());
				}
			}
		}
		
		if (isPhoenixBlessed() && (getOwner() != null))
			getOwner().reviveRequest(getOwner(), null, true);
		
		DecayTaskManager.getInstance().addDecayTask(this);
		return true;
	}
	
	public boolean doDie(L2Character killer, boolean decayed)
	{
		if (!super.doDie(killer))
			return false;
		if (!decayed)
		{
			DecayTaskManager.getInstance().addDecayTask(this);
		}
		return true;
	}
	
	public void stopDecay()
	{
		DecayTaskManager.getInstance().cancelDecayTask(this);
	}
	
	@Override
	public void onDecay()
	{
		deleteMe(_owner);
	}
	
	@Override
	public void broadcastStatusUpdate()
	{
		super.broadcastStatusUpdate();
		updateAndBroadcastStatus(1);
	}
	
	public void deleteMe(L2PcInstance owner)
	{
		owner.sendPacket(new PetDelete(getSummonType(), getObjectId()));
		
		//pet will be deleted along with all his items
		if (getInventory() != null)
		{
			getInventory().destroyAllItems("pet deleted", getOwner(), this);
		}
		decayMe();
		getKnownList().removeAllKnownObjects();
		owner.setPet(null);
		super.deleteMe();
	}
	
	public void unSummon(L2PcInstance owner)
	{
		if (isVisible() && !isDead())
		{
			getAI().stopFollow();
			owner.sendPacket(new PetDelete(getSummonType(),getObjectId()));
			L2Party party;
			if ((party = owner.getParty()) != null)
			{
				party.broadcastToPartyMembers(owner, new ExPartyPetWindowDelete(this));
			}
			
			if(getInventory() != null && getInventory().getSize() > 0)
			{
				getOwner().setPetInvItems(true);
				getOwner().sendPacket(SystemMessageId.ITEMS_IN_PET_INVENTORY);
			}
			else
				getOwner().setPetInvItems(false);
			
			store();
			storeEffect(true);
			owner.setPet(null);
			
			// Stop AI tasks
			if (hasAI())
				getAI().stopAITask();
			
			stopAllEffects();
			L2WorldRegion oldRegion = getWorldRegion();
			decayMe();
			if (oldRegion != null) oldRegion.removeFromZones(this);
			getKnownList().removeAllKnownObjects();
			setTarget(null);
			for (int itemId : owner.getAutoSoulShot())
			{
				String handler = ((L2EtcItem)ItemTable.getInstance().getTemplate(itemId)).getHandlerName();
				if (handler != null && handler.contains("Beast"))
					owner.disableAutoShot(itemId);
			}
		}
	}
	
	public int getAttackRange()
	{
		return _attackRange;
	}
	
	public void setAttackRange(int range)
	{
		if (range < 36)
			range = 36;
		_attackRange = range;
	}
	
	public void setFollowStatus(boolean state)
	{
		_follow = state;
		if (_follow)
			getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, getOwner());
		else
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE, null);
	}
	
	public boolean getFollowStatus()
	{
		return _follow;
	}
	
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return _owner.isAutoAttackable(attacker);
	}
	
	public int getChargedSoulShot()
	{
		return _chargedSoulShot;
	}
	
	public int getChargedSpiritShot()
	{
		return _chargedSpiritShot;
	}
	
	public int getControlObjectId()
	{
		return 0;
	}
	
	public L2Weapon getActiveWeapon()
	{
		return null;
	}
	
	@Override
	public PetInventory getInventory()
	{
		return null;
	}
	
	protected void doPickupItem(L2Object object)
	{
	}
	
	public void setRestoreSummon(boolean val)
	{
	}
	
	@Override
	public L2ItemInstance getActiveWeaponInstance()
	{
		return null;
	}
	
	@Override
	public L2Weapon getActiveWeaponItem()
	{
		return null;
	}
	
	@Override
	public L2ItemInstance getSecondaryWeaponInstance()
	{
		return null;
	}
	
	@Override
	public L2Weapon getSecondaryWeaponItem()
	{
		return null;
	}
	
	/**
	 * Return True if the L2Summon is invulnerable or if the summoner is in spawn protection.<BR><BR>
	 */
	@Override
	public boolean isInvul()
	{
		return super.isInvul() ||  getOwner().isSpawnProtected();
	}
	
	/**
	 * Return the L2Party object of its L2PcInstance owner or null.<BR><BR>
	 */
	@Override
	public L2Party getParty()
	{
		if (_owner == null)
			return null;
		else
			return _owner.getParty();
	}
	
	/**
	 * Return True if the L2Character has a Party in progress.<BR><BR>
	 */
	@Override
	public boolean isInParty()
	{
		if (_owner == null)
			return false;
		else
			return _owner.getParty() != null;
	}
	
	/**
	 * Check if the active L2Skill can be casted.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Check if the target is correct </li>
	 * <li>Check if the target is in the skill cast range </li>
	 * <li>Check if the summon owns enough HP and MP to cast the skill </li>
	 * <li>Check if all skills are enabled and this skill is enabled </li><BR><BR>
	 * <li>Check if the skill is active </li><BR><BR>
	 * <li>Notify the AI with AI_INTENTION_CAST and target</li><BR><BR>
	 *
	 * @param skill The L2Skill to use
	 * @param forceUse used to force ATTACK on players
	 * @param dontMove used to prevent movement, if not in range
	 *
	 */
	@Override
	public boolean useMagic(L2Skill skill, boolean forceUse, boolean dontMove)
	{
		if (skill == null || isDead())
			return false;
		
		// Check if the skill is active
		if (skill.isPassive())
		{
			// just ignore the passive skill request. why does the client send it anyway ??
			return false;
		}
		
		//************************************* Check Casting in Progress *******************************************
		
		// If a skill is currently being used
		if (isCastingNow())
		{
			return false;
		}
		
		// Set current pet skill
		getOwner().setCurrentPetSkill(skill, forceUse, dontMove);
		
		//************************************* Check Target *******************************************
		
		// Get the target for the skill
		L2Object target = null;
		
		switch (skill.getTargetType())
		{
			// OWNER_PET should be cast even if no target has been found
			case TARGET_OWNER_PET:
				target = getOwner();
				break;
				// PARTY, AURA, SELF should be cast even if no target has been found
			case TARGET_PARTY:
			case TARGET_AURA:
			case TARGET_FRONT_AURA:
			case TARGET_BEHIND_AURA:
			case TARGET_SELF:
			case TARGET_AURA_CORPSE_MOB:
				target = this;
				break;
			default:
				// Get the first target of the list
				target = skill.getFirstOfTargetList(this);
				break;
		}
		
		// Check the validity of the target
		if (target == null)
		{
			if (getOwner() != null)
				getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_CANT_FOUND));
			return false;
		}
		
		//************************************* Check skill availability *******************************************
		
		// Check if this skill is enabled (e.g. reuse time)
		if (isSkillDisabled(skill))
		{
			if (getOwner() != null)
				getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.PET_SKILL_CANNOT_BE_USED_RECHARCHING));
			return false;
		}
		
		//************************************* Check Consumables *******************************************
		
		// Check if the summon has enough MP
		if (getCurrentMp() < getStat().getMpConsume(skill) + getStat().getMpInitialConsume(skill))
		{
			// Send a System Message to the caster
			if (getOwner() != null)
				getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_MP));
			return false;
		}
		
		// Check if the summon has enough HP
		if (getCurrentHp() <= skill.getHpConsume())
		{
			// Send a System Message to the caster
			if (getOwner() != null)
				getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_HP));
			return false;
		}
		
		//************************************* Check Summon State *******************************************
		
		// Check if this is offensive magic skill
		if (skill.isOffensive())
		{
			if (isInsidePeaceZone(this, target)
					&& getOwner() != null
					&& (!getOwner().getAccessLevel().allowPeaceAttack()))
			{
				// If summon or target is in a peace zone, send a system message TARGET_IN_PEACEZONE
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IN_PEACEZONE));
				return false;
			}
			
			if (getOwner() != null && getOwner().isInOlympiadMode() && !getOwner().isOlympiadStart()){
				// if L2PcInstance is in Olympia and the match isn't already start, send a Server->Client packet ActionFailed
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			if (target.getActingPlayer() != null && this.getOwner().getSiegeState() > 0 && this.getOwner().isInsideZone(L2Character.ZONE_SIEGE)
					&& target.getActingPlayer().getSiegeState() == this.getOwner().getSiegeState()
					&& target.getActingPlayer() != this.getOwner() && target.getActingPlayer().getSiegeSide() == this.getOwner().getSiegeSide())
			{
				//
				if (TerritoryWarManager.getInstance().isTWInProgress())
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_ATTACK_A_MEMBER_OF_THE_SAME_TERRITORY));
				else
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FORCED_ATTACK_IS_IMPOSSIBLE_AGAINST_SIEGE_SIDE_TEMPORARY_ALLIED_MEMBERS));
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			// Check if the target is attackable
			if (target instanceof L2DoorInstance)
			{
				if(!((L2DoorInstance)target).isAttackable(getOwner()))
					return false;
			}
			else
			{
				if (!target.isAttackable()
						&& getOwner() != null
						&& (!getOwner().getAccessLevel().allowPeaceAttack()))
				{
					return false;
				}
				
				// Check if a Forced ATTACK is in progress on non-attackable target
				if (!target.isAutoAttackable(this) && !forceUse &&
						skill.getTargetType() != SkillTargetType.TARGET_AURA &&
						skill.getTargetType() != SkillTargetType.TARGET_FRONT_AURA &&
						skill.getTargetType() != SkillTargetType.TARGET_BEHIND_AURA &&
						skill.getTargetType() != SkillTargetType.TARGET_CLAN &&
						skill.getTargetType() != SkillTargetType.TARGET_ALLY &&
						skill.getTargetType() != SkillTargetType.TARGET_PARTY &&
						skill.getTargetType() != SkillTargetType.TARGET_SELF)
				{
					return false;
				}
			}
		}
		// Notify the AI with AI_INTENTION_CAST and target
		getAI().setIntention(CtrlIntention.AI_INTENTION_CAST, skill, target);
		return true;
	}
	
	@Override
	public void setIsImmobilized(boolean value)
	{
		super.setIsImmobilized(value);
		
		if (value)
		{
			_previousFollowStatus = getFollowStatus();
			// if immobilized temporarly disable follow mode
			if (_previousFollowStatus)
				setFollowStatus(false);
		}
		else
		{
			// if not more immobilized restore previous follow mode
			setFollowStatus(_previousFollowStatus);
		}
	}
	
	public void setOwner(L2PcInstance newOwner)
	{
		_owner = newOwner;
	}
	
	@Override
	public void sendDamageMessage(L2Character target, int damage, boolean mcrit, boolean pcrit, boolean miss)
	{
		if (miss || getOwner() == null)
			return;
		
		// Prevents the double spam of system messages, if the target is the owning player.
		if (target.getObjectId() != getOwner().getObjectId())
		{
			if (pcrit || mcrit)
				if (this instanceof L2SummonInstance)
					getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CRITICAL_HIT_BY_SUMMONED_MOB));
				else
					getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CRITICAL_HIT_BY_PET));
			
			if (getOwner().isInOlympiadMode() &&
					target instanceof L2PcInstance &&
					((L2PcInstance)target).isInOlympiadMode() &&
					((L2PcInstance)target).getOlympiadGameId() == getOwner().getOlympiadGameId())
			{
				OlympiadGameManager.getInstance().notifyCompetitorDamage(getOwner(), damage);
			}
			
			final SystemMessage sm;
			
			if (target.isInvul() && !(target instanceof L2NpcInstance))
				sm = SystemMessage.getSystemMessage(SystemMessageId.ATTACK_WAS_BLOCKED);
			else
			{
				sm = SystemMessage.getSystemMessage(SystemMessageId.C1_GAVE_C2_DAMAGE_OF_S3);
				sm.addNpcName(this);
				sm.addCharName(target);
				sm.addNumber(damage);
			}
			
			getOwner().sendPacket(sm);
		}
	}
	
	@Override
	public void reduceCurrentHp(double damage, L2Character attacker, L2Skill skill)
	{
		super.reduceCurrentHp(damage, attacker, skill);
		if (getOwner() != null && attacker != null)
		{
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RECEIVED_DAMAGE_OF_S3_FROM_C2);
			sm.addNpcName(this);
			sm.addCharName(attacker);
			sm.addNumber((int)damage);
			getOwner().sendPacket(sm);
		}
	}
	
	@Override
	public void doCast(L2Skill skill)
	{
		final L2PcInstance actingPlayer = getActingPlayer();
		
		if (!actingPlayer.checkPvpSkill(getTarget(), skill, true)
				&& !actingPlayer.getAccessLevel().allowPeaceAttack())
		{
			// Send a System Message to the L2PcInstance
			actingPlayer.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IS_INCORRECT));
			
			// Send a Server->Client packet ActionFailed to the L2PcInstance
			actingPlayer.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		super.doCast(skill);
	}
	
	@Override
	public boolean isInCombat()
	{
		return getOwner() != null ? getOwner().isInCombat() : false;
	}
	
	@Override
	public L2PcInstance getActingPlayer()
	{
		return getOwner();
	}
	
	@Override
	public final void broadcastPacket(L2GameServerPacket mov)
	{
		if (getOwner() != null)
			mov.setInvisible(getOwner().getAppearance().getInvisible());

		super.broadcastPacket(mov);
	}
	
	@Override
	public final void broadcastPacket(L2GameServerPacket mov, int radiusInKnownlist)
	{
		if (getOwner() != null)
			mov.setInvisible(getOwner().getAppearance().getInvisible());

		super.broadcastPacket(mov, radiusInKnownlist);
	}
	
	public void updateAndBroadcastStatus(int val)
	{
		if (getOwner() == null)
			return;
		
		getOwner().sendPacket(new PetInfo(this,val));
		getOwner().sendPacket(new PetStatusUpdate(this));
		if (isVisible())
		{
			broadcastNpcInfo(val);
		}
		L2Party party = this.getOwner().getParty();
		if (party != null)
		{
			party.broadcastToPartyMembers(this.getOwner(), new ExPartyPetWindowUpdate(this));
		}
		updateEffectIcons(true);
	}
	
	public void broadcastNpcInfo(int val)
	{
		Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
		for (L2PcInstance player : plrs)
		{
			if (player == null || (player == getOwner() && !(this instanceof L2MerchantSummonInstance)))
				continue;
			player.sendPacket(new AbstractNpcInfo.SummonInfo(this,player, val));
		}
	}
	public boolean isHungry()
	{
		return false;
	}
	@Override
	public final boolean isAttackingNow()
	{
		return isInCombat();
	}
	
	public int getWeapon()
	{
		return 0;
	}
	
	public int getArmor()
	{
		return 0;
	}

	@Override
	public void sendInfo(L2PcInstance activeChar)
	{
		// Check if the L2PcInstance is the owner of the Pet
		if (activeChar.equals(getOwner()) && !(this instanceof L2MerchantSummonInstance))
		{
			activeChar.sendPacket(new PetInfo(this, 0));
			// The PetInfo packet wipes the PartySpelled (list of active  spells' icons).  Re-add them
			updateEffectIcons(true);
			if (this instanceof L2PetInstance)
			{
				activeChar.sendPacket(new PetItemList((L2PetInstance) this));
			}
		}
		else
			activeChar.sendPacket(new AbstractNpcInfo.SummonInfo(this, activeChar, 0));
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.model.actor.L2Character#onTeleported()
	 */
	@Override
	public void onTeleported()
	{
		super.onTeleported();
		getOwner().sendPacket(new TeleportToLocation(this, getPosition().getX(), getPosition().getY(), getPosition().getZ(), getPosition().getHeading()));
	}
	
	@Override
	public String toString()
	{
		return super.toString()+"("+getNpcId()+") Owner: "+getOwner();
	}
	
	@Override
	public boolean isUndead()
	{
		return getTemplate().isUndead();
	}
}
