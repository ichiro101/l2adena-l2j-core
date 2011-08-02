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
package com.l2jserver.gameserver.network.serverpackets;

import com.l2jserver.Config;
import com.l2jserver.gameserver.datatables.ClanTable;
import com.l2jserver.gameserver.instancemanager.TownManager;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.L2Summon;
import com.l2jserver.gameserver.model.actor.L2Trap;
import com.l2jserver.gameserver.model.actor.instance.L2MonsterInstance;
import com.l2jserver.gameserver.model.actor.instance.L2NpcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.skills.AbnormalEffect;

/**
 * This class ...
 *
 * @version $Revision: 1.7.2.4.2.9 $ $Date: 2005/04/11 10:05:54 $
 */
public abstract class AbstractNpcInfo extends L2GameServerPacket
{
	//   ddddddddddddddddddffffdddcccccSSddd dddddc
	//   ddddddddddddddddddffffdddcccccSSddd dddddccffd
	
	private static final String _S__22_NPCINFO = "[S] 0c NpcInfo";
	protected int _x, _y, _z, _heading;
	protected int _idTemplate;
	protected boolean _isAttackable, _isSummoned;
	protected int _mAtkSpd, _pAtkSpd;
	
	/**
	 * Run speed, swimming run speed and flying run speed
	 */
	protected int _runSpd;
	
	/**
	 * Walking speed, swimming walking speed and flying walking speed
	 */
	protected int _walkSpd;
	
	protected int _rhand, _lhand, _chest, _enchantEffect;
	protected double _collisionHeight, _collisionRadius;
	protected String _name = "";
	protected String _title = "";
	
	public AbstractNpcInfo(L2Character cha)
	{
		_isSummoned = cha.isShowSummonAnimation();
		_x = cha.getX();
		_y = cha.getY();
		_z = cha.getZ();
		_heading = cha.getHeading();
		_mAtkSpd = cha.getMAtkSpd();
		_pAtkSpd = cha.getPAtkSpd();
		_runSpd = cha.getTemplate().baseRunSpd;
		_walkSpd = cha.getTemplate().baseWalkSpd;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__22_NPCINFO;
	}
	
	/**
	 * Packet for Npcs
	 */
	public static class NpcInfo extends AbstractNpcInfo
	{
		private L2Npc _npc;
		private int _clanCrest = 0;
		private int _allyCrest = 0;
		private int _allyId = 0;
		private int _clanId = 0;
		private int _displayEffect = 0;
		
		public NpcInfo(L2Npc cha, L2Character attacker)
		{
			super(cha);
			_npc = cha;
			_idTemplate = cha.getTemplate().idTemplate; // On every subclass
			_rhand = cha.getRightHandItem(); // On every subclass
			_lhand = cha.getLeftHandItem(); // On every subclass
			_enchantEffect = cha.getEnchantEffect();
			_collisionHeight = cha.getCollisionHeight();// On every subclass
			_collisionRadius = cha.getCollisionRadius();// On every subclass
			_isAttackable = cha.isAutoAttackable(attacker);
			if (cha.getTemplate().serverSideName)
				_name = cha.getName();// On every subclass
			
			if (Config.L2JMOD_CHAMPION_ENABLE && cha.isChampion())
				_title = (Config.L2JMOD_CHAMP_TITLE); // On every subclass
			else if (cha.getTemplate().serverSideTitle)
				_title = cha.getTemplate().title; // On every subclass
			else
				_title = cha.getTitle(); // On every subclass
			
			if (Config.SHOW_NPC_LVL && _npc instanceof L2MonsterInstance)
			{
				String t = "Lv " + cha.getLevel() + (cha.getAggroRange() > 0 ? "*" : "");
				if (_title != null)
					t += " " + _title;
				
				_title = t;
			}
			
			// npc crest of owning clan/ally of castle
			if (cha instanceof L2NpcInstance && cha.isInsideZone(L2Character.ZONE_TOWN) && (Config.SHOW_CREST_WITHOUT_QUEST || cha.getCastle().getShowNpcCrest()) && cha.getCastle().getOwnerId() != 0)
			{
				int townId = TownManager.getTown(_x, _y, _z).getTownId();
				if (townId != 33 && townId != 22)
				{
					L2Clan clan = ClanTable.getInstance().getClan(cha.getCastle().getOwnerId());
					_clanCrest = clan.getCrestId();
					_clanId = clan.getClanId();
					_allyCrest = clan.getAllyCrestId();
					_allyId = clan.getAllyId();
				}
			}
			
			_displayEffect = cha.getDisplayEffect();
		}
		
		@Override
		protected void writeImpl()
		{
			writeC(0x0c);
			writeD(_npc.getObjectId());
			writeD(_idTemplate + 1000000); // npctype id
			writeD(_isAttackable ? 1 : 0);
			writeD(_x);
			writeD(_y);
			writeD(_z);
			writeD(_heading);
			writeD(0x00);
			writeD(_mAtkSpd);
			writeD(_pAtkSpd);
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // swim run speed
			writeD(_walkSpd); // swim walk speed
			writeD(_runSpd); // swim run speed
			writeD(_walkSpd); // swim walk speed
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly run speed
			writeF(_npc.getMovementSpeedMultiplier());
			writeF(_npc.getAttackSpeedMultiplier());
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_rhand); // right hand weapon
			writeD(_chest);
			writeD(_lhand); // left hand weapon
			writeC(1); // name above char 1=true ... ??
			writeC(_npc.isRunning() ? 1 : 0);
			writeC(_npc.isInCombat() ? 1 : 0);
			writeC(_npc.isAlikeDead() ? 1 : 0);
			writeC(_isSummoned ? 2 : 0); // 0=teleported 1=default 2=summoned
			writeD(-1); // High Five NPCString ID
			writeS(_name);
			writeD(-1); // High Five NPCString ID
			writeS(_title);
			writeD(0x00); // Title color 0=client default
			writeD(0x00); //pvp flag
			writeD(0x00); // karma
			
			writeD(_npc.getAbnormalEffect()); // C2
			writeD(_clanId); //clan id
			writeD(_clanCrest); //crest id
			writeD(_allyId); // ally id
			writeD(_allyCrest); // all crest
			writeC(_npc.isFlying() ? 2 : 0); // C2
			writeC(0x00); // title color 0=client
			
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_enchantEffect); // C4
			writeD(_npc.isFlying() ? 1 : 0); // C6
			writeD(0x00);
			writeD(_npc.getColorEffect());// CT1.5 Pet form and skills, Color effect
			writeC(_npc.isHideName() ? 0x00 : 0x01);
			writeC(_npc.isHideName() ? 0x00 : 0x01);
			writeD(_npc.getSpecialEffect());
			writeD(_displayEffect);
		}
	}
	
	public static class TrapInfo extends AbstractNpcInfo
	{
		private L2Trap _trap;
		
		public TrapInfo(L2Trap cha, L2Character attacker)
		{
			super(cha);
			
			_trap = cha;
			_idTemplate = cha.getTemplate().idTemplate;
			_isAttackable = cha.isAutoAttackable(attacker);
			_rhand = 0;
			_lhand = 0;
			_collisionHeight = _trap.getTemplate().fCollisionHeight;
			_collisionRadius = _trap.getTemplate().fCollisionRadius;
			if (cha.getTemplate().serverSideName)
				_name = cha.getName();
			_title = cha.getOwner() != null ? cha.getOwner().getName() : "";
			_runSpd = _trap.getRunSpeed();
			_walkSpd = _trap.getWalkSpeed();
		}
		
		@Override
		protected void writeImpl()
		{
			writeC(0x0c);
			writeD(_trap.getObjectId());
			writeD(_idTemplate + 1000000); // npctype id
			writeD(_isAttackable ? 1 : 0);
			writeD(_x);
			writeD(_y);
			writeD(_z);
			writeD(_heading);
			writeD(0x00);
			writeD(_mAtkSpd);
			writeD(_pAtkSpd);
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // swim run speed
			writeD(_walkSpd); // swim walk speed
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly walk speed
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly walk speed
			writeF(_trap.getMovementSpeedMultiplier());
			writeF(_trap.getAttackSpeedMultiplier());
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_rhand); // right hand weapon
			writeD(_chest);
			writeD(_lhand); // left hand weapon
			writeC(1); // name above char 1=true ... ??
			writeC(1);
			writeC(_trap.isInCombat() ? 1 : 0);
			writeC(_trap.isAlikeDead() ? 1 : 0);
			writeC(_isSummoned ? 2 : 0); //  0=teleported  1=default   2=summoned
			writeD(-1); // High Five NPCString ID
			writeS(_name);
			writeD(-1); // High Five NPCString ID
			writeS(_title);
			writeD(0x00); // title color 0 = client default
			
			writeD(_trap.getPvpFlag());
			writeD(_trap.getKarma());
			
			writeD(_trap.getAbnormalEffect()); // C2
			writeD(0x00); //clan id
			writeD(0x00); //crest id
			writeD(0000); // C2
			writeD(0000); // C2
			writeC(0000); // C2
			
			writeC(0x00); // Title color 0=client default
			
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(0x00); // C4
			writeD(0x00); // C6
			writeD(0x00);
			writeD(0);//CT1.5 Pet form and skills
			writeC(0x01);
			writeC(0x01);
			writeD(0x00);
		}
	}
	
	/**
	 * Packet for summons
	 */
	public static class SummonInfo extends AbstractNpcInfo
	{
		private L2Summon _summon;
		private int _form = 0;
		private int _val = 0;
		
		public SummonInfo(L2Summon cha, L2Character attacker, int val)
		{
			super(cha);
			_summon = cha;
			_val = val;
			if (_summon.isShowSummonAnimation())
				_val = 2; //override for spawn
			
			int npcId = cha.getTemplate().npcId;
			
			if (npcId == 16041 || npcId == 16042)
			{
				if (cha.getLevel() > 84)
					_form = 3;
				else if (cha.getLevel() > 79)
					_form = 2;
				else if (cha.getLevel() > 74)
					_form = 1;
			}
			else if (npcId == 16025 || npcId == 16037)
			{
				if (cha.getLevel() > 69)
					_form = 3;
				else if (cha.getLevel() > 64)
					_form = 2;
				else if (cha.getLevel() > 59)
					_form = 1;
			}
			
			// fields not set on AbstractNpcInfo
			_isAttackable = cha.isAutoAttackable(attacker);
			_rhand = cha.getWeapon();
			_lhand = 0;
			_chest = cha.getArmor();
			_enchantEffect = cha.getTemplate().enchantEffect;
			_name = cha.getName();
			_title = cha.getOwner() != null ? ((!cha.getOwner().isOnline()) ? "" : cha.getOwner().getName()) : ""; // when owner online, summon will show in title owner name
			_idTemplate = cha.getTemplate().idTemplate;
			_collisionHeight = cha.getTemplate().fCollisionHeight;
			_collisionRadius = cha.getTemplate().fCollisionRadius;
			_invisible = cha.getOwner() != null ? cha.getOwner().getAppearance().getInvisible() : false;
		}
		
		@Override
		protected void writeImpl()
		{
			boolean gmSeeInvis = false;
			if (_invisible)
			{
				L2PcInstance tmp = getClient().getActiveChar();
				if (tmp != null && tmp.isGM())
					gmSeeInvis = true;
			}
			
			writeC(0x0c);
			writeD(_summon.getObjectId());
			writeD(_idTemplate + 1000000); // npctype id
			writeD(_isAttackable ? 1 : 0);
			writeD(_x);
			writeD(_y);
			writeD(_z);
			writeD(_heading);
			writeD(0x00);
			writeD(_mAtkSpd);
			writeD(_pAtkSpd);
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // swim run speed
			writeD(_walkSpd); // swim walk speed
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly walk speed
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly walk speed
			writeF(_summon.getMovementSpeedMultiplier());
			writeF(_summon.getAttackSpeedMultiplier());
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_rhand); // right hand weapon
			writeD(_chest);
			writeD(_lhand); // left hand weapon
			writeC(1); // name above char 1=true ... ??
			writeC(1); // always running 1=running 0=walking
			writeC(_summon.isInCombat() ? 1 : 0);
			writeC(_summon.isAlikeDead() ? 1 : 0);
			writeC(_val); //  0=teleported  1=default   2=summoned
			writeD(-1); // High Five NPCString ID
			writeS(_name);
			writeD(-1); // High Five NPCString ID
			writeS(_title);
			writeD(0x01);// Title color 0=client default
			
			writeD(_summon.getPvpFlag());
			writeD(_summon.getKarma());
			
			writeD(gmSeeInvis ? _summon.getAbnormalEffect() | AbnormalEffect.STEALTH.getMask() : _summon.getAbnormalEffect());
			
			writeD(0x00); //clan id
			writeD(0x00); //crest id
			writeD(0000); // C2
			writeD(0000); // C2
			writeC(0000); // C2
			
			writeC(_summon.getTeam());// Title color 0=client default
			
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_enchantEffect); // C4
			writeD(0x00); // C6
			writeD(0x00);
			writeD(_form); //CT1.5 Pet form and skills
			writeC(0x01);
			writeC(0x01);
			writeD(_summon.getSpecialEffect());
		}
	}
}
