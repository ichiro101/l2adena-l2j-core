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
package com.l2jserver.gameserver.skills;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.gameserver.SevenSigns;
import com.l2jserver.gameserver.SevenSignsFestival;
import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.instancemanager.ClanHallManager;
import com.l2jserver.gameserver.instancemanager.FortManager;
import com.l2jserver.gameserver.instancemanager.SiegeManager;
import com.l2jserver.gameserver.instancemanager.ZoneManager;
import com.l2jserver.gameserver.model.Elementals;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2SiegeClan;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.L2Attackable;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.L2Playable;
import com.l2jserver.gameserver.model.actor.L2Summon;
import com.l2jserver.gameserver.model.actor.instance.L2BabyPetInstance;
import com.l2jserver.gameserver.model.actor.instance.L2CubicInstance;
import com.l2jserver.gameserver.model.actor.instance.L2DoorInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PetInstance;
import com.l2jserver.gameserver.model.actor.instance.L2SummonInstance;
import com.l2jserver.gameserver.model.base.PlayerState;
import com.l2jserver.gameserver.model.entity.Castle;
import com.l2jserver.gameserver.model.entity.ClanHall;
import com.l2jserver.gameserver.model.entity.Fort;
import com.l2jserver.gameserver.model.entity.Siege;
import com.l2jserver.gameserver.model.itemcontainer.Inventory;
import com.l2jserver.gameserver.model.zone.type.L2CastleZone;
import com.l2jserver.gameserver.model.zone.type.L2ClanHallZone;
import com.l2jserver.gameserver.model.zone.type.L2FortZone;
import com.l2jserver.gameserver.model.zone.type.L2MotherTreeZone;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.skills.conditions.ConditionPlayerState;
import com.l2jserver.gameserver.skills.conditions.ConditionUsingItemType;
import com.l2jserver.gameserver.skills.funcs.Func;
import com.l2jserver.gameserver.templates.chars.L2PcTemplate;
import com.l2jserver.gameserver.templates.effects.EffectTemplate;
import com.l2jserver.gameserver.templates.item.L2Armor;
import com.l2jserver.gameserver.templates.item.L2ArmorType;
import com.l2jserver.gameserver.templates.item.L2Item;
import com.l2jserver.gameserver.templates.item.L2Weapon;
import com.l2jserver.gameserver.templates.item.L2WeaponType;
import com.l2jserver.gameserver.templates.skills.L2SkillType;
import com.l2jserver.gameserver.util.Util;
import com.l2jserver.util.Rnd;
import com.l2jserver.util.StringUtil;


/**
 * Global calculations, can be modified by server admins
 */
public final class Formulas
{
	/** Regen Task period */
	protected static final Logger _log = Logger.getLogger(L2Character.class.getName());
	private static final int HP_REGENERATE_PERIOD = 3000; // 3 secs
	
	public static final byte SHIELD_DEFENSE_FAILED = 0; // no shield defense
	public static final byte SHIELD_DEFENSE_SUCCEED = 1; // normal shield defense
	public static final byte SHIELD_DEFENSE_PERFECT_BLOCK = 2; // perfect block
	
	public static final byte SKILL_REFLECT_FAILED = 0; // no reflect
	public static final byte SKILL_REFLECT_SUCCEED = 1; // normal reflect, some damage reflected some other not
	public static final byte SKILL_REFLECT_VENGEANCE = 2; // 100% of the damage affect both
	
	private static final byte MELEE_ATTACK_RANGE = 40;
	
	static class FuncAddLevel3 extends Func
	{
		static final FuncAddLevel3[] _instancies = new FuncAddLevel3[Stats.NUM_STATS];
		
		static Func getInstance(Stats stat)
		{
			int pos = stat.ordinal();
			if (_instancies[pos] == null) _instancies[pos] = new FuncAddLevel3(stat);
			return _instancies[pos];
		}
		
		private FuncAddLevel3(Stats pStat)
		{
			super(pStat, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value += env.player.getLevel() / 3.0;
		}
	}
	
	static class FuncMultLevelMod extends Func
	{
		static final FuncMultLevelMod[] _instancies = new FuncMultLevelMod[Stats.NUM_STATS];
		
		static Func getInstance(Stats stat)
		{
			int pos = stat.ordinal();
			if (_instancies[pos] == null) _instancies[pos] = new FuncMultLevelMod(stat);
			return _instancies[pos];
		}
		
		private FuncMultLevelMod(Stats pStat)
		{
			super(pStat, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value *= env.player.getLevelMod();
		}
	}
	
	static class FuncMultRegenResting extends Func
	{
		static final FuncMultRegenResting[] _instancies = new FuncMultRegenResting[Stats.NUM_STATS];
		
		/**
		 * Return the Func object corresponding to the state concerned.<BR><BR>
		 */
		static Func getInstance(Stats stat)
		{
			int pos = stat.ordinal();
			
			if (_instancies[pos] == null) _instancies[pos] = new FuncMultRegenResting(stat);
			
			return _instancies[pos];
		}
		
		/**
		 * Constructor of the FuncMultRegenResting.<BR><BR>
		 */
		private FuncMultRegenResting(Stats pStat)
		{
			super(pStat, 0x20, null);
			setCondition(new ConditionPlayerState(PlayerState.RESTING, true));
		}
		
		/**
		 * Calculate the modifier of the state concerned.<BR><BR>
		 */
		@Override
		public void calc(Env env)
		{
			if (!cond.test(env)) return;
			
			env.value *= 1.45;
		}
	}
	
	static class FuncPAtkMod extends Func
	{
		static final FuncPAtkMod _fpa_instance = new FuncPAtkMod();
		
		static Func getInstance()
		{
			return _fpa_instance;
		}
		
		private FuncPAtkMod()
		{
			super(Stats.POWER_ATTACK, 0x30, null);
		}
		
		@Override
		public void calc(Env env)
		{
			if (env.player instanceof L2PcInstance)
			{
				env.value *= BaseStats.STR.calcBonus(env.player) * env.player.getLevelMod();
			}
			else
			{
				float level = env.player.getLevel();
				env.value *= BaseStats.STR.calcBonus(env.player) * ((level + 89) / 100);
			}
		}
	}
	
	static class FuncMAtkMod extends Func
	{
		static final FuncMAtkMod _fma_instance = new FuncMAtkMod();
		
		static Func getInstance()
		{
			return _fma_instance;
		}
		
		private FuncMAtkMod()
		{
			super(Stats.MAGIC_ATTACK, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			if (env.player instanceof L2PcInstance)
			{
				double intb = BaseStats.INT.calcBonus(env.player);
				double lvlb = env.player.getLevelMod();
				env.value *= (lvlb * lvlb) * (intb * intb);
			}
			else
			{
				float level = env.player.getLevel();
				double intb = BaseStats.INT.calcBonus(env.player);
				float lvlb = ((level + 89) / 100);
				env.value *= (lvlb * lvlb) * (intb * intb);
			}
		}
	}
	
	static class FuncMDefMod extends Func
	{
		static final FuncMDefMod _fmm_instance = new FuncMDefMod();
		
		static Func getInstance()
		{
			return _fmm_instance;
		}
		
		private FuncMDefMod()
		{
			super(Stats.MAGIC_DEFENCE, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			float level = env.player.getLevel();
			if (env.player instanceof L2PcInstance)
			{
				L2PcInstance p = (L2PcInstance) env.player;
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LFINGER) != null)
					env.value -= 5;
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RFINGER) != null)
					env.value -= 5;
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEAR) != null)
					env.value -= 9;
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_REAR) != null)
					env.value -= 9;
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_NECK) != null)
					env.value -= 13;
				env.value *= BaseStats.MEN.calcBonus(env.player) * env.player.getLevelMod();
			}
			else if ((env.player instanceof L2PetInstance) || (env.player instanceof L2BabyPetInstance))
			{
				if (env.player.getInventory().getPaperdollObjectId(Inventory.PAPERDOLL_NECK) != 0)
				{
					env.value -= 13;
					env.value *= BaseStats.MEN.calcBonus(env.player) * ((level + 89) / 100);
				}
				else
				{
					env.value *= BaseStats.MEN.calcBonus(env.player) * ((level + 89) / 100);
				}					
			}
			else
			{
				env.value *= BaseStats.MEN.calcBonus(env.player) * ((level + 89) / 100);
			}
		}
	}
	
	static class FuncPDefMod extends Func
	{
		static final FuncPDefMod _fmm_instance = new FuncPDefMod();
		
		static Func getInstance()
		{
			return _fmm_instance;
		}
		
		private FuncPDefMod()
		{
			super(Stats.POWER_DEFENCE, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			if (env.player instanceof L2PcInstance)
			{
				L2PcInstance p = (L2PcInstance) env.player;
				boolean hasMagePDef = (p.getClassId().isMage() || p.getClassId().getId() == 0x31); // orc mystics are a special case
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_HEAD) != null)
					env.value -= 12;
				L2ItemInstance chest = p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
				if (chest != null)
					env.value -= hasMagePDef ? 15 : 31;
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS) != null || (chest != null && chest.getItem().getBodyPart() == L2Item.SLOT_FULL_ARMOR))
					env.value -= hasMagePDef ? 8 : 18;
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_GLOVES) != null)
					env.value -= 8;
				if (p.getInventory().getPaperdollItem(Inventory.PAPERDOLL_FEET) != null)
					env.value -= 7;
				env.value *= env.player.getLevelMod();
			}
			else
			{
				float level = env.player.getLevel();
				env.value *= ((level + 89) / 100);
			}
		}
	}
	
	static class FuncGatesPDefMod extends Func
	{
		static final FuncGatesPDefMod _fmm_instance = new FuncGatesPDefMod();
		
		static Func getInstance()
		{
			return _fmm_instance;
		}
		
		private FuncGatesPDefMod()
		{
			super(Stats.POWER_DEFENCE, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			if (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) == SevenSigns.CABAL_DAWN)
				env.value *= Config.ALT_SIEGE_DAWN_GATES_PDEF_MULT;
			else if (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) == SevenSigns.CABAL_DUSK)
				env.value *= Config.ALT_SIEGE_DUSK_GATES_PDEF_MULT;
		}
	}
	
	static class FuncGatesMDefMod extends Func
	{
		static final FuncGatesMDefMod _fmm_instance = new FuncGatesMDefMod();
		
		static Func getInstance()
		{
			return _fmm_instance;
		}
		
		private FuncGatesMDefMod()
		{
			super(Stats.MAGIC_DEFENCE, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			if (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) == SevenSigns.CABAL_DAWN)
				env.value *= Config.ALT_SIEGE_DAWN_GATES_MDEF_MULT;
			else if (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) == SevenSigns.CABAL_DUSK)
				env.value *= Config.ALT_SIEGE_DUSK_GATES_MDEF_MULT;
		}
	}
	
	static class FuncBowAtkRange extends Func
	{
		private static final FuncBowAtkRange _fbar_instance = new FuncBowAtkRange();
		
		static Func getInstance()
		{
			return _fbar_instance;
		}
		
		private FuncBowAtkRange()
		{
			super(Stats.POWER_ATTACK_RANGE, 0x10, null);
			setCondition(new ConditionUsingItemType(L2WeaponType.BOW.mask()));
		}
		
		@Override
		public void calc(Env env)
		{
			if (!cond.test(env))
				return;
			// default is 40 and with bow should be 500
			env.value += 460;
		}
	}
	
	static class FuncCrossBowAtkRange extends Func
	{
		private static final FuncCrossBowAtkRange _fcb_instance = new FuncCrossBowAtkRange();
		
		static Func getInstance()
		{
			return _fcb_instance;
		}
		
		private FuncCrossBowAtkRange()
		{
			super(Stats.POWER_ATTACK_RANGE, 0x10, null);
			setCondition(new ConditionUsingItemType(L2WeaponType.CROSSBOW.mask()));
		}
		
		@Override
		public void calc(Env env)
		{
			if (!cond.test(env))
				return;
			// default is 40 and with crossbow should be 400
			env.value += 360;
		}
	}
	
	static class FuncAtkAccuracy extends Func
	{
		static final FuncAtkAccuracy _faa_instance = new FuncAtkAccuracy();
		
		static Func getInstance()
		{
			return _faa_instance;
		}
		
		private FuncAtkAccuracy()
		{
			super(Stats.ACCURACY_COMBAT, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			final int level = env.player.getLevel();
			if (env.player instanceof L2PcInstance)
			{
				//[Square(DEX)]*6 + lvl + weapon hitbonus;
				env.value += Math.sqrt(env.player.getDEX()) * 6;
				env.value += level;
				if (level > 77)
					env.value += (level - 77) + 1;
				if (level > 69)
					env.value += (level - 69);
				//if (env.player instanceof L2Summon)
					//env.value += (level < 60) ? 4 : 5;
			}
			else
			{
				env.value += Math.sqrt(env.player.getDEX()) * 6;
				env.value += level;
				if (level > 77)
					env.value += (level - 76);
				if (level > 69)
					env.value += (level - 69);
			}
		}
	}
	
	static class FuncAtkEvasion extends Func
	{
		static final FuncAtkEvasion _fae_instance = new FuncAtkEvasion();
		
		static Func getInstance()
		{
			return _fae_instance;
		}
		
		private FuncAtkEvasion()
		{
			super(Stats.EVASION_RATE, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			final int level = env.player.getLevel();
			if (env.player instanceof L2PcInstance)
			{
				//[Square(DEX)]*6 + lvl;
				env.value += Math.sqrt(env.player.getDEX()) * 6;
				env.value += level;
				if (level > 77)
					env.value += (level - 77);
				if (level > 69)
					env.value += (level - 69);
			}
			else
			{
				//[Square(DEX)]*6 + lvl;
				env.value += Math.sqrt(env.player.getDEX()) * 6;
				env.value += level;
				if (level > 69)
					env.value += (level - 69) + 2;
			}
		}
	}
	
	static class FuncAtkCritical extends Func
	{
		static final FuncAtkCritical _fac_instance = new FuncAtkCritical();
		
		static Func getInstance()
		{
			return _fac_instance;
		}
		
		private FuncAtkCritical()
		{
			super(Stats.CRITICAL_RATE, 0x09, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value *= BaseStats.DEX.calcBonus(env.player);
			env.value *= 10;
			env.baseValue = env.value;
		}
	}
	
	static class FuncMAtkCritical extends Func
	{
		static final FuncMAtkCritical _fac_instance = new FuncMAtkCritical();
		
		static Func getInstance()
		{
			return _fac_instance;
		}
		
		private FuncMAtkCritical()
		{
			super(Stats.MCRITICAL_RATE, 0x30, null);
		}
		
		@Override
		public void calc(Env env)
		{
			L2Character p = env.player;
			if(p instanceof L2Summon)
				env.value = 8; // TODO: needs retail value
			else if (p instanceof L2PcInstance && p.getActiveWeaponInstance() != null)
				env.value *= BaseStats.WIT.calcBonus(p);
		}
	}
	
	static class FuncMoveSpeed extends Func
	{
		static final FuncMoveSpeed _fms_instance = new FuncMoveSpeed();
		
		static Func getInstance()
		{
			return _fms_instance;
		}
		
		private FuncMoveSpeed()
		{
			super(Stats.RUN_SPEED, 0x30, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value *= BaseStats.DEX.calcBonus(env.player);
		}
	}
	
	static class FuncPAtkSpeed extends Func
	{
		static final FuncPAtkSpeed _fas_instance = new FuncPAtkSpeed();
		
		static Func getInstance()
		{
			return _fas_instance;
		}
		
		private FuncPAtkSpeed()
		{
			super(Stats.POWER_ATTACK_SPEED, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value *= BaseStats.DEX.calcBonus(env.player);
		}
	}
	
	static class FuncMAtkSpeed extends Func
	{
		static final FuncMAtkSpeed _fas_instance = new FuncMAtkSpeed();
		
		static Func getInstance()
		{
			return _fas_instance;
		}
		
		private FuncMAtkSpeed()
		{
			super(Stats.MAGIC_ATTACK_SPEED, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value *= BaseStats.WIT.calcBonus(env.player);
		}
	}
	
	static class FuncHennaSTR extends Func
	{
		static final FuncHennaSTR _fh_instance = new FuncHennaSTR();
		
		static Func getInstance()
		{
			return _fh_instance;
		}
		
		private FuncHennaSTR()
		{
			super(Stats.STAT_STR, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			//			L2PcTemplate t = (L2PcTemplate)env._player.getTemplate();
			L2PcInstance pc = (L2PcInstance) env.player;
			if (pc != null) env.value += pc.getHennaStatSTR();
		}
	}
	
	static class FuncHennaDEX extends Func
	{
		static final FuncHennaDEX _fh_instance = new FuncHennaDEX();
		
		static Func getInstance()
		{
			return _fh_instance;
		}
		
		private FuncHennaDEX()
		{
			super(Stats.STAT_DEX, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			//			L2PcTemplate t = (L2PcTemplate)env._player.getTemplate();
			L2PcInstance pc = (L2PcInstance) env.player;
			if (pc != null) env.value += pc.getHennaStatDEX();
		}
	}
	
	static class FuncHennaINT extends Func
	{
		static final FuncHennaINT _fh_instance = new FuncHennaINT();
		
		static Func getInstance()
		{
			return _fh_instance;
		}
		
		private FuncHennaINT()
		{
			super(Stats.STAT_INT, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			//			L2PcTemplate t = (L2PcTemplate)env._player.getTemplate();
			L2PcInstance pc = (L2PcInstance) env.player;
			if (pc != null) env.value += pc.getHennaStatINT();
		}
	}
	
	static class FuncHennaMEN extends Func
	{
		static final FuncHennaMEN _fh_instance = new FuncHennaMEN();
		
		static Func getInstance()
		{
			return _fh_instance;
		}
		
		private FuncHennaMEN()
		{
			super(Stats.STAT_MEN, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			//			L2PcTemplate t = (L2PcTemplate)env._player.getTemplate();
			L2PcInstance pc = (L2PcInstance) env.player;
			if (pc != null) env.value += pc.getHennaStatMEN();
		}
	}
	
	static class FuncHennaCON extends Func
	{
		static final FuncHennaCON _fh_instance = new FuncHennaCON();
		
		static Func getInstance()
		{
			return _fh_instance;
		}
		
		private FuncHennaCON()
		{
			super(Stats.STAT_CON, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			//			L2PcTemplate t = (L2PcTemplate)env._player.getTemplate();
			L2PcInstance pc = (L2PcInstance) env.player;
			if (pc != null) env.value += pc.getHennaStatCON();
		}
	}
	
	static class FuncHennaWIT extends Func
	{
		static final FuncHennaWIT _fh_instance = new FuncHennaWIT();
		
		static Func getInstance()
		{
			return _fh_instance;
		}
		
		private FuncHennaWIT()
		{
			super(Stats.STAT_WIT, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			//			L2PcTemplate t = (L2PcTemplate)env._player.getTemplate();
			L2PcInstance pc = (L2PcInstance) env.player;
			if (pc != null) env.value += pc.getHennaStatWIT();
		}
	}
	
	static class FuncMaxHpAdd extends Func
	{
		static final FuncMaxHpAdd _fmha_instance = new FuncMaxHpAdd();
		
		static Func getInstance()
		{
			return _fmha_instance;
		}
		
		private FuncMaxHpAdd()
		{
			super(Stats.MAX_HP, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			L2PcTemplate t = (L2PcTemplate) env.player.getTemplate();
			int lvl = env.player.getLevel() - t.classBaseLevel;
			double hpmod = t.lvlHpMod * lvl;
			double hpmax = (t.lvlHpAdd + hpmod) * lvl;
			double hpmin = (t.lvlHpAdd * lvl) + hpmod;
			env.value += (hpmax + hpmin) / 2;
		}
	}
	
	static class FuncMaxHpMul extends Func
	{
		static final FuncMaxHpMul _fmhm_instance = new FuncMaxHpMul();
		
		static Func getInstance()
		{
			return _fmhm_instance;
		}
		
		private FuncMaxHpMul()
		{
			super(Stats.MAX_HP, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value *= BaseStats.CON.calcBonus(env.player);
		}
	}
	
	static class FuncMaxCpAdd extends Func
	{
		static final FuncMaxCpAdd _fmca_instance = new FuncMaxCpAdd();
		
		static Func getInstance()
		{
			return _fmca_instance;
		}
		
		private FuncMaxCpAdd()
		{
			super(Stats.MAX_CP, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			L2PcTemplate t = (L2PcTemplate) env.player.getTemplate();
			int lvl = env.player.getLevel() - t.classBaseLevel;
			double cpmod = t.lvlCpMod * lvl;
			double cpmax = (t.lvlCpAdd + cpmod) * lvl;
			double cpmin = (t.lvlCpAdd * lvl) + cpmod;
			env.value += (cpmax + cpmin) / 2;
		}
	}
	
	static class FuncMaxCpMul extends Func
	{
		static final FuncMaxCpMul _fmcm_instance = new FuncMaxCpMul();
		
		static Func getInstance()
		{
			return _fmcm_instance;
		}
		
		private FuncMaxCpMul()
		{
			super(Stats.MAX_CP, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value *= BaseStats.CON.calcBonus(env.player);
		}
	}
	
	static class FuncMaxMpAdd extends Func
	{
		static final FuncMaxMpAdd _fmma_instance = new FuncMaxMpAdd();
		
		static Func getInstance()
		{
			return _fmma_instance;
		}
		
		private FuncMaxMpAdd()
		{
			super(Stats.MAX_MP, 0x10, null);
		}
		
		@Override
		public void calc(Env env)
		{
			L2PcTemplate t = (L2PcTemplate) env.player.getTemplate();
			int lvl = env.player.getLevel() - t.classBaseLevel;
			double mpmod = t.lvlMpMod * lvl;
			double mpmax = (t.lvlMpAdd + mpmod) * lvl;
			double mpmin = (t.lvlMpAdd * lvl) + mpmod;
			env.value += (mpmax + mpmin) / 2;
		}
	}
	
	static class FuncMaxMpMul extends Func
	{
		static final FuncMaxMpMul _fmmm_instance = new FuncMaxMpMul();
		
		static Func getInstance()
		{
			return _fmmm_instance;
		}
		
		private FuncMaxMpMul()
		{
			super(Stats.MAX_MP, 0x20, null);
		}
		
		@Override
		public void calc(Env env)
		{
			env.value *= BaseStats.MEN.calcBonus(env.player);
		}
	}
	
	/**
	 * Return the period between 2 regenerations task (3s for L2Character, 5 min for L2DoorInstance).<BR><BR>
	 */
	public static int getRegeneratePeriod(L2Character cha)
	{
		if (cha instanceof L2DoorInstance) return HP_REGENERATE_PERIOD * 100; // 5 mins
		
		return HP_REGENERATE_PERIOD; // 3s
	}
	
	/**
	 * Return the standard NPC Calculator set containing ACCURACY_COMBAT and EVASION_RATE.<BR><BR>
	 *
	 * <B><U> Concept</U> :</B><BR><BR>
	 * A calculator is created to manage and dynamically calculate the effect of a character property (ex : MAX_HP, REGENERATE_HP_RATE...).
	 * In fact, each calculator is a table of Func object in which each Func represents a mathematic function : <BR><BR>
	 *
	 * FuncAtkAccuracy -> Math.sqrt(_player.getDEX())*6+_player.getLevel()<BR><BR>
	 *
	 * To reduce cache memory use, L2NPCInstances who don't have skills share the same Calculator set called <B>NPC_STD_CALCULATOR</B>.<BR><BR>
	 *
	 */
	public static Calculator[] getStdNPCCalculators()
	{
		Calculator[] std = new Calculator[Stats.NUM_STATS];
		
		std[Stats.MAX_HP.ordinal()] = new Calculator();
		std[Stats.MAX_HP.ordinal()].addFunc(FuncMaxHpMul.getInstance());
		
		std[Stats.MAX_MP.ordinal()] = new Calculator();
		std[Stats.MAX_MP.ordinal()].addFunc(FuncMaxMpMul.getInstance());
		
		std[Stats.POWER_ATTACK.ordinal()] = new Calculator();
		std[Stats.POWER_ATTACK.ordinal()].addFunc(FuncPAtkMod.getInstance());
		
		std[Stats.MAGIC_ATTACK.ordinal()] = new Calculator();
		std[Stats.MAGIC_ATTACK.ordinal()].addFunc(FuncMAtkMod.getInstance());
		
		std[Stats.POWER_DEFENCE.ordinal()] = new Calculator();
		std[Stats.POWER_DEFENCE.ordinal()].addFunc(FuncPDefMod.getInstance());
		
		std[Stats.MAGIC_DEFENCE.ordinal()] = new Calculator();
		std[Stats.MAGIC_DEFENCE.ordinal()].addFunc(FuncMDefMod.getInstance());
		
		std[Stats.CRITICAL_RATE.ordinal()] = new Calculator();
		std[Stats.CRITICAL_RATE.ordinal()].addFunc(FuncAtkCritical.getInstance());
		
		std[Stats.MCRITICAL_RATE.ordinal()] = new Calculator();
		std[Stats.MCRITICAL_RATE.ordinal()].addFunc(FuncMAtkCritical.getInstance());
		
		std[Stats.ACCURACY_COMBAT.ordinal()] = new Calculator();
		std[Stats.ACCURACY_COMBAT.ordinal()].addFunc(FuncAtkAccuracy.getInstance());
		
		std[Stats.EVASION_RATE.ordinal()] = new Calculator();
		std[Stats.EVASION_RATE.ordinal()].addFunc(FuncAtkEvasion.getInstance());
		
		std[Stats.POWER_ATTACK_SPEED.ordinal()] = new Calculator();
		std[Stats.POWER_ATTACK_SPEED.ordinal()].addFunc(FuncPAtkSpeed.getInstance());
		
		std[Stats.MAGIC_ATTACK_SPEED.ordinal()] = new Calculator();
		std[Stats.MAGIC_ATTACK_SPEED.ordinal()].addFunc(FuncMAtkSpeed.getInstance());
		
		std[Stats.RUN_SPEED.ordinal()] = new Calculator();
		std[Stats.RUN_SPEED.ordinal()].addFunc(FuncMoveSpeed.getInstance());
		
		return std;
	}
	
	public static Calculator[] getStdDoorCalculators()
	{
		Calculator[] std = new Calculator[Stats.NUM_STATS];
		
		// Add the FuncAtkAccuracy to the Standard Calculator of ACCURACY_COMBAT
		std[Stats.ACCURACY_COMBAT.ordinal()] = new Calculator();
		std[Stats.ACCURACY_COMBAT.ordinal()].addFunc(FuncAtkAccuracy.getInstance());
		
		// Add the FuncAtkEvasion to the Standard Calculator of EVASION_RATE
		std[Stats.EVASION_RATE.ordinal()] = new Calculator();
		std[Stats.EVASION_RATE.ordinal()].addFunc(FuncAtkEvasion.getInstance());
		
		//SevenSigns PDEF Modifier
		std[Stats.POWER_DEFENCE.ordinal()] = new Calculator();
		std[Stats.POWER_DEFENCE.ordinal()].addFunc(FuncGatesPDefMod.getInstance());
		
		//SevenSigns MDEF Modifier
		std[Stats.MAGIC_DEFENCE.ordinal()] = new Calculator();
		std[Stats.MAGIC_DEFENCE.ordinal()].addFunc(FuncGatesMDefMod.getInstance());
		
		return std;
	}
	
	/**
	 * Add basics Func objects to L2PcInstance and L2Summon.<BR><BR>
	 *
	 * <B><U> Concept</U> :</B><BR><BR>
	 * A calculator is created to manage and dynamically calculate the effect of a character property (ex : MAX_HP, REGENERATE_HP_RATE...).
	 * In fact, each calculator is a table of Func object in which each Func represents a mathematic function : <BR><BR>
	 *
	 * FuncAtkAccuracy -> Math.sqrt(_player.getDEX())*6+_player.getLevel()<BR><BR>
	 *
	 * @param cha L2PcInstance or L2Summon that must obtain basic Func objects
	 */
	public static void addFuncsToNewCharacter(L2Character cha)
	{
		if (cha instanceof L2PcInstance)
		{
			cha.addStatFunc(FuncMaxHpAdd.getInstance());
			cha.addStatFunc(FuncMaxHpMul.getInstance());
			cha.addStatFunc(FuncMaxCpAdd.getInstance());
			cha.addStatFunc(FuncMaxCpMul.getInstance());
			cha.addStatFunc(FuncMaxMpAdd.getInstance());
			cha.addStatFunc(FuncMaxMpMul.getInstance());
			//cha.addStatFunc(FuncMultRegenResting.getInstance(Stats.REGENERATE_HP_RATE));
			//cha.addStatFunc(FuncMultRegenResting.getInstance(Stats.REGENERATE_CP_RATE));
			//cha.addStatFunc(FuncMultRegenResting.getInstance(Stats.REGENERATE_MP_RATE));
			cha.addStatFunc(FuncBowAtkRange.getInstance());
			cha.addStatFunc(FuncCrossBowAtkRange.getInstance());
			//cha.addStatFunc(FuncMultLevelMod.getInstance(Stats.POWER_ATTACK));
			//cha.addStatFunc(FuncMultLevelMod.getInstance(Stats.POWER_DEFENCE));
			//cha.addStatFunc(FuncMultLevelMod.getInstance(Stats.MAGIC_DEFENCE));
			cha.addStatFunc(FuncPAtkMod.getInstance());
			cha.addStatFunc(FuncMAtkMod.getInstance());
			cha.addStatFunc(FuncPDefMod.getInstance());
			cha.addStatFunc(FuncMDefMod.getInstance());
			cha.addStatFunc(FuncAtkCritical.getInstance());
			cha.addStatFunc(FuncMAtkCritical.getInstance());
			cha.addStatFunc(FuncAtkAccuracy.getInstance());
			cha.addStatFunc(FuncAtkEvasion.getInstance());
			cha.addStatFunc(FuncPAtkSpeed.getInstance());
			cha.addStatFunc(FuncMAtkSpeed.getInstance());
			cha.addStatFunc(FuncMoveSpeed.getInstance());
			
			cha.addStatFunc(FuncHennaSTR.getInstance());
			cha.addStatFunc(FuncHennaDEX.getInstance());
			cha.addStatFunc(FuncHennaINT.getInstance());
			cha.addStatFunc(FuncHennaMEN.getInstance());
			cha.addStatFunc(FuncHennaCON.getInstance());
			cha.addStatFunc(FuncHennaWIT.getInstance());
		}
		else if (cha instanceof L2PetInstance)
		{
			cha.addStatFunc(FuncMaxHpMul.getInstance());
			cha.addStatFunc(FuncMaxMpMul.getInstance());
			cha.addStatFunc(FuncPAtkMod.getInstance());
			cha.addStatFunc(FuncMAtkMod.getInstance());
			cha.addStatFunc(FuncPDefMod.getInstance());
			cha.addStatFunc(FuncMDefMod.getInstance());
			cha.addStatFunc(FuncAtkCritical.getInstance());
			cha.addStatFunc(FuncMAtkCritical.getInstance());
			cha.addStatFunc(FuncAtkAccuracy.getInstance());
			cha.addStatFunc(FuncAtkEvasion.getInstance());
			cha.addStatFunc(FuncMoveSpeed.getInstance());
			cha.addStatFunc(FuncPAtkSpeed.getInstance());
			cha.addStatFunc(FuncMAtkSpeed.getInstance());
		}
		else if (cha instanceof L2BabyPetInstance)
		{
			cha.addStatFunc(FuncMaxHpMul.getInstance());
			cha.addStatFunc(FuncMaxMpMul.getInstance());
			cha.addStatFunc(FuncPAtkMod.getInstance());
			cha.addStatFunc(FuncMAtkMod.getInstance());
			cha.addStatFunc(FuncPDefMod.getInstance());
			cha.addStatFunc(FuncMDefMod.getInstance());
			cha.addStatFunc(FuncAtkCritical.getInstance());
			cha.addStatFunc(FuncMAtkCritical.getInstance());
			cha.addStatFunc(FuncAtkAccuracy.getInstance());
			cha.addStatFunc(FuncAtkEvasion.getInstance());
			cha.addStatFunc(FuncMoveSpeed.getInstance());
			cha.addStatFunc(FuncPAtkSpeed.getInstance());
			cha.addStatFunc(FuncMAtkSpeed.getInstance());
		}
		else if (cha instanceof L2Summon)
		{
			//cha.addStatFunc(FuncMultRegenResting.getInstance(Stats.REGENERATE_HP_RATE));
			//cha.addStatFunc(FuncMultRegenResting.getInstance(Stats.REGENERATE_MP_RATE));
			cha.addStatFunc(FuncMaxHpMul.getInstance());
			cha.addStatFunc(FuncMaxMpMul.getInstance());
			cha.addStatFunc(FuncPAtkMod.getInstance());
			cha.addStatFunc(FuncMAtkMod.getInstance());
			cha.addStatFunc(FuncPDefMod.getInstance());
			cha.addStatFunc(FuncMDefMod.getInstance());
			cha.addStatFunc(FuncAtkCritical.getInstance());
			cha.addStatFunc(FuncMAtkCritical.getInstance());
			cha.addStatFunc(FuncAtkAccuracy.getInstance());
			cha.addStatFunc(FuncAtkEvasion.getInstance());
			cha.addStatFunc(FuncMoveSpeed.getInstance());
			cha.addStatFunc(FuncPAtkSpeed.getInstance());
			cha.addStatFunc(FuncMAtkSpeed.getInstance());
		}
	}
	
	/**
	 * Calculate the HP regen rate (base + modifiers).<BR>
	 * <BR>
	 */
	public static final double calcHpRegen(L2Character cha)
	{
		double init = cha.getTemplate().baseHpReg;
		double hpRegenMultiplier = cha.isRaid() ? Config.RAID_HP_REGEN_MULTIPLIER : Config.HP_REGEN_MULTIPLIER;
		double hpRegenBonus = 0;
		
		if (Config.L2JMOD_CHAMPION_ENABLE && cha.isChampion())
			hpRegenMultiplier *= Config.L2JMOD_CHAMPION_HP_REGEN;
		
		if (cha instanceof L2PcInstance)
		{
			L2PcInstance player = (L2PcInstance) cha;
			
			// Calculate correct baseHpReg value for certain level of PC
			init += (player.getLevel() > 10) ? ((player.getLevel() - 1) / 10.0) : 0.5;
			
			// SevenSigns Festival modifier
			if (SevenSignsFestival.getInstance().isFestivalInProgress() && player.isFestivalParticipant())
				hpRegenMultiplier *= calcFestivalRegenModifier(player);
			else
			{
				double siegeModifier = calcSiegeRegenModifer(player);
				if (siegeModifier > 0)
					hpRegenMultiplier *= siegeModifier;
			}
			
			if (player.isInsideZone(L2Character.ZONE_CLANHALL) && player.getClan() != null && player.getClan().getHasHideout() > 0)
			{
				L2ClanHallZone zone = ZoneManager.getInstance().getZone(player, L2ClanHallZone.class);
				int posChIndex = zone == null ? -1 : zone.getClanHallId();
				int clanHallIndex = player.getClan().getHasHideout();
				if (clanHallIndex > 0 && clanHallIndex == posChIndex)
				{
					ClanHall clansHall = ClanHallManager.getInstance().getClanHallById(clanHallIndex);
					if (clansHall != null)
						if (clansHall.getFunction(ClanHall.FUNC_RESTORE_HP) != null)
							hpRegenMultiplier *= 1 + (double) clansHall.getFunction(ClanHall.FUNC_RESTORE_HP).getLvl() / 100;
				}
			}
			
			if (player.isInsideZone(L2Character.ZONE_CASTLE) && player.getClan() != null && player.getClan().getHasCastle() > 0)
			{
				L2CastleZone zone = ZoneManager.getInstance().getZone(player, L2CastleZone.class);
				int posCastleIndex = zone == null ? -1 : zone.getCastleId();
				int castleIndex = player.getClan().getHasCastle();
				if (castleIndex > 0 && castleIndex == posCastleIndex)
				{
					Castle castle = CastleManager.getInstance().getCastleById(castleIndex);
					if (castle != null)
						if (castle.getFunction(Castle.FUNC_RESTORE_HP) != null)
							hpRegenMultiplier *= 1 + (double) castle.getFunction(Castle.FUNC_RESTORE_HP).getLvl() / 100;
				}
			}
			
			if (player.isInsideZone(L2Character.ZONE_FORT) && player.getClan() != null && player.getClan().getHasFort() > 0)
			{
				L2FortZone zone = ZoneManager.getInstance().getZone(player, L2FortZone.class);
				int posFortIndex = zone == null ? -1 : zone.getFortId();
				int fortIndex = player.getClan().getHasFort();
				if (fortIndex > 0 && fortIndex == posFortIndex)
				{
					Fort fort = FortManager.getInstance().getFortById(fortIndex);
					if (fort != null)
						if (fort.getFunction(Fort.FUNC_RESTORE_HP) != null)
							hpRegenMultiplier *= 1 + (double) fort.getFunction(Fort.FUNC_RESTORE_HP).getLvl() / 100;
				}
			}
			
			// Mother Tree effect is calculated at last
			if (player.isInsideZone(L2Character.ZONE_MOTHERTREE))
			{
				L2MotherTreeZone zone = ZoneManager.getInstance().getZone(player, L2MotherTreeZone.class);
				int hpBonus = zone == null ? 0 : zone.getHpRegenBonus();
				hpRegenBonus += hpBonus;
			}
			
			// Calculate Movement bonus
			if (player.isSitting())
				hpRegenMultiplier *= 1.5; // Sitting
			else if (!player.isMoving())
				hpRegenMultiplier *= 1.1; // Staying
			else if (player.isRunning())
				hpRegenMultiplier *= 0.7; // Running
			
			// Add CON bonus
			init *= cha.getLevelMod() * BaseStats.CON.calcBonus(cha);
		}
		else if (cha instanceof L2PetInstance)
			init = ((L2PetInstance) cha).getPetLevelData().getPetRegenHP() * Config.PET_HP_REGEN_MULTIPLIER;
		
		if (init < 1)
			init = 1;
		
		return cha.calcStat(Stats.REGENERATE_HP_RATE, init, null, null) * hpRegenMultiplier + hpRegenBonus;
	}
	
	/**
	 * Calculate the MP regen rate (base + modifiers).<BR><BR>
	 */
	public static final double calcMpRegen(L2Character cha)
	{
		double init = cha.getTemplate().baseMpReg;
		double mpRegenMultiplier = cha.isRaid() ? Config.RAID_MP_REGEN_MULTIPLIER : Config.MP_REGEN_MULTIPLIER;
		double mpRegenBonus = 0;
		
		if (cha instanceof L2PcInstance)
		{
			L2PcInstance player = (L2PcInstance) cha;
			
			// Calculate correct baseMpReg value for certain level of PC
			init += 0.3 * ((player.getLevel() - 1) / 10.0);
			
			// SevenSigns Festival modifier
			if (SevenSignsFestival.getInstance().isFestivalInProgress() && player.isFestivalParticipant())
				mpRegenMultiplier *= calcFestivalRegenModifier(player);
			
			// Mother Tree effect is calculated at last'
			if (player.isInsideZone(L2Character.ZONE_MOTHERTREE))
			{
				L2MotherTreeZone zone = ZoneManager.getInstance().getZone(player, L2MotherTreeZone.class);
				int mpBonus = zone == null ? 0 : zone.getMpRegenBonus();
				mpRegenBonus += mpBonus;
			}
			
			if (player.isInsideZone(L2Character.ZONE_CLANHALL) && player.getClan() != null && player.getClan().getHasHideout() > 0)
			{
				L2ClanHallZone zone = ZoneManager.getInstance().getZone(player, L2ClanHallZone.class);
				int posChIndex = zone == null ? -1 : zone.getClanHallId();
				int clanHallIndex = player.getClan().getHasHideout();
				if (clanHallIndex > 0 && clanHallIndex == posChIndex)
				{
					ClanHall clansHall = ClanHallManager.getInstance().getClanHallById(clanHallIndex);
					if (clansHall != null)
						if (clansHall.getFunction(ClanHall.FUNC_RESTORE_MP) != null)
							mpRegenMultiplier *= 1 + (double) clansHall.getFunction(ClanHall.FUNC_RESTORE_MP).getLvl() / 100;
				}
			}
			
			if (player.isInsideZone(L2Character.ZONE_CASTLE) && player.getClan() != null && player.getClan().getHasCastle() > 0)
			{
				L2CastleZone zone = ZoneManager.getInstance().getZone(player, L2CastleZone.class);
				int posCastleIndex = zone == null ? -1 : zone.getCastleId();
				int castleIndex = player.getClan().getHasCastle();
				if (castleIndex > 0 && castleIndex == posCastleIndex)
				{
					Castle castle = CastleManager.getInstance().getCastleById(castleIndex);
					if (castle != null)
					{
						if (castle.getFunction(Castle.FUNC_RESTORE_MP) != null)
							mpRegenMultiplier *= 1 + (double) castle.getFunction(Castle.FUNC_RESTORE_MP).getLvl() / 100;
					}
				}
			}
			
			if (player.isInsideZone(L2Character.ZONE_FORT) && player.getClan() != null && player.getClan().getHasFort() > 0)
			{
				L2FortZone zone = ZoneManager.getInstance().getZone(player, L2FortZone.class);
				int posFortIndex = zone == null ? -1 : zone.getFortId();
				int fortIndex = player.getClan().getHasFort();
				if (fortIndex > 0 && fortIndex == posFortIndex)
				{
					Fort fort = FortManager.getInstance().getFortById(fortIndex);
					if (fort != null)
						if (fort.getFunction(Fort.FUNC_RESTORE_MP) != null)
							mpRegenMultiplier *= 1 + (double) fort.getFunction(Fort.FUNC_RESTORE_MP).getLvl() / 100;
				}
			}
			
			// Calculate Movement bonus
			if (player.isSitting())
				mpRegenMultiplier *= 1.5; // Sitting
			else if (!player.isMoving())
				mpRegenMultiplier *= 1.1; // Staying
			else if (player.isRunning())
				mpRegenMultiplier *= 0.7; // Running
			
			// Add MEN bonus
			init *= cha.getLevelMod() * BaseStats.MEN.calcBonus(cha);
		}
		else if (cha instanceof L2PetInstance)
			init = ((L2PetInstance) cha).getPetLevelData().getPetRegenMP() * Config.PET_MP_REGEN_MULTIPLIER;
		
		if (init < 1)
			init = 1;
		
		return cha.calcStat(Stats.REGENERATE_MP_RATE, init, null, null) * mpRegenMultiplier + mpRegenBonus;
	}
	
	/**
	 * Calculate the CP regen rate (base + modifiers).<BR><BR>
	 */
	public static final double calcCpRegen(L2Character cha)
	{
		double init = cha.getTemplate().baseHpReg;
		double cpRegenMultiplier = Config.CP_REGEN_MULTIPLIER;
		double cpRegenBonus = 0;
		
		if (cha instanceof L2PcInstance)
		{
			L2PcInstance player = (L2PcInstance) cha;
			
			// Calculate correct baseHpReg value for certain level of PC
			init += (player.getLevel() > 10) ? ((player.getLevel() - 1) / 10.0) : 0.5;
			
			// Calculate Movement bonus
			if (player.isSitting())
				cpRegenMultiplier *= 1.5; // Sitting
			else if (!player.isMoving())
				cpRegenMultiplier *= 1.1; // Staying
			else if (player.isRunning())
				cpRegenMultiplier *= 0.7; // Running
		}
		else
		{
			// Calculate Movement bonus
			if (!cha.isMoving())
				cpRegenMultiplier *= 1.1; // Staying
			else if (cha.isRunning())
				cpRegenMultiplier *= 0.7; // Running
		}
		
		// Apply CON bonus
		init *= cha.getLevelMod() * BaseStats.CON.calcBonus(cha);
		if (init < 1)
			init = 1;
		
		return cha.calcStat(Stats.REGENERATE_CP_RATE, init, null, null) * cpRegenMultiplier + cpRegenBonus;
	}
	
	@SuppressWarnings("deprecation")
	public static final double calcFestivalRegenModifier(L2PcInstance activeChar)
	{
		final int[] festivalInfo = SevenSignsFestival.getInstance().getFestivalForPlayer(activeChar);
		final int oracle = festivalInfo[0];
		final int festivalId = festivalInfo[1];
		int[] festivalCenter;
		
		// If the player isn't found in the festival, leave the regen rate as it is.
		if (festivalId < 0) return 0;
		
		// Retrieve the X and Y coords for the center of the festival arena the player is in.
		if (oracle == SevenSigns.CABAL_DAWN) festivalCenter = SevenSignsFestival.FESTIVAL_DAWN_PLAYER_SPAWNS[festivalId];
		else festivalCenter = SevenSignsFestival.FESTIVAL_DUSK_PLAYER_SPAWNS[festivalId];
		
		// Check the distance between the player and the player spawn point, in the center of the arena.
		double distToCenter = activeChar.getDistance(festivalCenter[0], festivalCenter[1]);
		
		if (Config.DEBUG)
			_log.info("Distance: " + distToCenter + ", RegenMulti: " + (distToCenter * 2.5) / 50);
		
		return 1.0 - (distToCenter * 0.0005); // Maximum Decreased Regen of ~ -65%;
	}
	
	public static final double calcSiegeRegenModifer(L2PcInstance activeChar)
	{
		if (activeChar == null || activeChar.getClan() == null) return 0;
		
		Siege siege = SiegeManager.getInstance().getSiege(activeChar.getPosition().getX(),
				activeChar.getPosition().getY(),
				activeChar.getPosition().getZ());
		if (siege == null || !siege.getIsInProgress()) return 0;
		
		L2SiegeClan siegeClan = siege.getAttackerClan(activeChar.getClan().getClanId());
		if (siegeClan == null || siegeClan.getFlag().isEmpty()
				|| !Util.checkIfInRange(200, activeChar, siegeClan.getFlag().get(0), true)) return 0;
		
		return 1.5; // If all is true, then modifer will be 50% more
	}
	
	public static double calcBlowDamage(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean ss)
	{
		double defence = target.getPDef(attacker);
		
		switch(shld)
		{
			case Formulas.SHIELD_DEFENSE_SUCCEED:
				defence += target.getShldDef();
				break;
			case Formulas.SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
				return 1;
		}
		
		boolean isPvP = (attacker instanceof L2Playable) && (target instanceof L2PcInstance);
		boolean isPvE = (attacker instanceof L2Playable) && (target instanceof L2Attackable);
		double power =  skill.getPower(isPvP, isPvE);
		double damage = 0;
		double proximityBonus = 1;
		double graciaPhysSkillBonus = skill.isMagic() ? 1 : 1.10113; // Gracia final physical skill bonus 10.113%
		double ssboost = ss ? (skill.getSSBoost() > 0 ? skill.getSSBoost() : 2.04) : 1; // 104% bonus with SS
		double pvpBonus = 1;
		
		if ((attacker instanceof L2Playable) && (target instanceof L2Playable))
		{
			// Dmg bonusses in PvP fight
			pvpBonus *= attacker.calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null);
			// Def bonusses in PvP fight
			defence *= target.calcStat(Stats.PVP_PHYS_SKILL_DEF, 1, null, null);
		}
		
		if (isBehind(attacker, target))
			proximityBonus = 1.2; // +20% crit dmg when back stabbed
		else if (isInFrontOf(attacker, target))
			proximityBonus = 1.1; // +10% crit dmg when side stabbed
			
		damage += Formulas.calcValakasAttribute(attacker, target, skill);
		
		double element = calcElemental(attacker, target, skill);
		
		// SSBoost > 0 have different calculation
		if (skill.getSSBoost() > 0)
			damage += 70. * graciaPhysSkillBonus * (attacker.getPAtk(target) + power) / defence * (attacker.calcStat(Stats.CRITICAL_DAMAGE, 1, target, skill))
					* (target.calcStat(Stats.CRIT_VULN, 1, target, skill)) * ssboost * proximityBonus * element * pvpBonus
					+ (attacker.calcStat(Stats.CRITICAL_DAMAGE_ADD, 0, target, skill) * 6.1 * 70 / defence * graciaPhysSkillBonus);
		else
			damage += 70. * graciaPhysSkillBonus * (power + (attacker.getPAtk(target) * ssboost)) / defence * (attacker.calcStat(Stats.CRITICAL_DAMAGE, 1, target, skill))
					* (target.calcStat(Stats.CRIT_VULN, 1, target, skill)) * proximityBonus * element * pvpBonus
					+ (attacker.calcStat(Stats.CRITICAL_DAMAGE_ADD, 0, target, skill) * 6.1 * 70 / defence * graciaPhysSkillBonus);

		damage += target.calcStat(Stats.CRIT_ADD_VULN, 0, target, skill) * 6.1;
		
		// get the vulnerability for the instance due to skills (buffs, passives, toggles, etc)
		damage = target.calcStat(Stats.DAGGER_WPN_VULN, damage, target, null);
		// Random weapon damage
		damage *= attacker.getRandomDamageMultiplier();
		
		if (target instanceof L2Attackable && !target.isRaid() && !target.isRaidMinion() && target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY && attacker.getActingPlayer() != null
				&& (target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2)
		{
			int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
			if (lvlDiff > Config.NPC_SKILL_DMG_PENALTY.size())
				damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size());
			else
				damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
			
		}
		
		//TODO: Formulas.calcStunBreak(target, damage);

		return damage < 1 ? 1. : damage;
	}
	
	/** Calculated damage caused by ATTACK of attacker on target,
	 * called separatly for each weapon, if dual-weapon is used.
	 *
	 * @param attacker player or NPC that makes ATTACK
	 * @param target player or NPC, target of ATTACK
	 * @param miss one of ATTACK_XXX constants
	 * @param crit if the ATTACK have critical success
	 * @param dual if dual weapon is used
	 * @param ss if weapon item was charged by soulshot
	 * @return damage points
	 */
	public static final double calcPhysDam(L2Character attacker, L2Character target, L2Skill skill,
			byte shld, boolean crit, boolean dual, boolean ss)
	{
		final boolean isPvP = (attacker instanceof L2Playable) && (target instanceof L2Playable);
		final boolean isPvE = (attacker instanceof L2Playable) && (target instanceof L2Attackable);
		double damage = attacker.getPAtk(target);
		double defence = target.getPDef(attacker);
		damage+=calcValakasAttribute(attacker, target, skill);
		if (attacker instanceof L2Npc)
		{
			if(((L2Npc)attacker)._soulshotcharged)
			{
				ss = true;
			}
			else
				ss = false;
			((L2Npc)attacker)._soulshotcharged = false;
		}
		// Def bonusses in PvP fight
		if(isPvP)
		{
			if(skill == null)
				defence *= target.calcStat(Stats.PVP_PHYSICAL_DEF, 1, null, null);
			else
				defence *= target.calcStat(Stats.PVP_PHYS_SKILL_DEF, 1, null, null);
		}
		
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				if (!Config.ALT_GAME_SHIELD_BLOCKS)
					defence += target.getShldDef();
				break;
			case SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
				return 1.;
		}
		
		if (ss) damage *= 2;
		if (skill != null)
		{
			double skillpower = skill.getPower(attacker, target, isPvP, isPvE);
			float ssboost = skill.getSSBoost();
			if (ssboost <= 0)
				damage += skillpower;
			else if (ssboost > 0)
			{
				if (ss)
				{
					skillpower *= ssboost;
					damage += skillpower;
				}
				else
					damage += skillpower;
			}
		}
		
		// defence modifier depending of the attacker weapon
		L2Weapon weapon = attacker.getActiveWeaponItem();
		Stats stat = null;
		boolean isBow = false;
		if (weapon != null && !attacker.isTransformed())
		{
			switch (weapon.getItemType())
			{
				case BOW:
					isBow = true;
					stat = Stats.BOW_WPN_VULN;
					break;
				case CROSSBOW:
					isBow = true;
					stat = Stats.CROSSBOW_WPN_VULN;
					break;
				case BLUNT:
					stat = Stats.BLUNT_WPN_VULN;
					break;
				case DAGGER:
					stat = Stats.DAGGER_WPN_VULN;
					break;
				case DUAL:
					stat = Stats.DUAL_WPN_VULN;
					break;
				case DUALFIST:
					stat = Stats.DUALFIST_WPN_VULN;
					break;
				case ETC:
					stat = Stats.ETC_WPN_VULN;
					break;
				case FIST:
					stat = Stats.FIST_WPN_VULN;
					break;
				case POLE:
					stat = Stats.POLE_WPN_VULN;
					break;
				case SWORD:
					stat = Stats.SWORD_WPN_VULN;
					break;
				case BIGSWORD:
					stat = Stats.BIGSWORD_WPN_VULN;
					break;
				case BIGBLUNT:
					stat = Stats.BIGBLUNT_WPN_VULN;
					break;
				case DUALDAGGER:
					stat = Stats.DUALDAGGER_WPN_VULN;
					break;
				case RAPIER:
					stat = Stats.RAPIER_WPN_VULN;
					break;
				case ANCIENTSWORD:
					stat = Stats.ANCIENT_WPN_VULN;
					break;
				/*case PET:
					stat = Stats.PET_WPN_VULN;
					break;*/
			}
		}
		
		// for summon use pet weapon vuln, since they cant hold weapon
		if (attacker instanceof L2SummonInstance)
			stat = Stats.PET_WPN_VULN;
		
		/*if (shld && !Config.ALT_GAME_SHIELD_BLOCKS)
		{
			defence += target.getShldDef();
		}*/
		//if (!(attacker instanceof L2RaidBossInstance) &&
		/*
		if ((attacker instanceof L2NpcInstance || attacker instanceof L2SiegeGuardInstance))
		{
			if (attacker instanceof L2RaidBossInstance) damage *= 1; // was 10 changed for temp fix
			//			else
			//			damage *= 2;
			//			if (attacker instanceof L2NpcInstance || attacker instanceof L2SiegeGuardInstance){
			//damage = damage * attacker.getSTR() * attacker.getAccuracy() * 0.05 / defence;
			//			damage = damage * attacker.getSTR()*  (attacker.getSTR() + attacker.getLevel()) * 0.025 / defence;
			//			damage += _rnd.nextDouble() * damage / 10 ;
		}
		 */
		//		else {
		//if (skill == null)
		
		if (crit)
		{
			//Finally retail like formula
			damage = 2 * attacker.calcStat(Stats.CRITICAL_DAMAGE, 1, target, skill) * target.calcStat(Stats.CRIT_VULN, target.getTemplate().baseCritVuln, target, null) * (70 * damage / defence);
			//Crit dmg add is almost useless in normal hits...
			damage += (attacker.calcStat(Stats.CRITICAL_DAMAGE_ADD, 0, target, skill) * 70 / defence);
		}
		else
			damage = 70 * damage / defence;
		
		if (stat != null)
		{
			// get the vulnerability due to skills (buffs, passives, toggles, etc)
			damage = target.calcStat(stat, damage, target, null);
			/*if (target instanceof L2Npc)
			{
				// get the natural vulnerability for the template
				damage *= ((L2Npc) target).getTemplate().getVulnerability(stat);
			}*/
		}
		
		// Weapon random damage
		damage *= attacker.getRandomDamageMultiplier();
		
		//damage += Rnd.nextDouble() * damage / 10;
		//		damage += _rnd.nextDouble()* attacker.getRandomDamage(target);
		//		}
		if (shld > 0 && Config.ALT_GAME_SHIELD_BLOCKS)
		{
			damage -= target.getShldDef();
			if (damage < 0) damage = 0;
		}
		
		if (target instanceof L2Npc)
		{
			switch (((L2Npc) target).getTemplate().getRace())
			{
				case BEAST:
					damage *= attacker.getPAtkMonsters(target);
					break;
				case ANIMAL:
					damage *= attacker.getPAtkAnimals(target);
					break;
				case PLANT:
					damage *= attacker.getPAtkPlants(target);
					break;
				case DRAGON:
					damage *= attacker.getPAtkDragons(target);
					break;
				case BUG:
					damage *= attacker.getPAtkInsects(target);
					break;
				case GIANT:
					damage *= attacker.getPAtkGiants(target);
					break;
				case MAGICCREATURE:
					damage *= attacker.getPAtkMagicCreatures(target);
					break;
				default:
					// nothing
					break;
			}
		}
		
		if (damage > 0 && damage < 1)
		{
			damage = 1;
		}
		else if (damage < 0)
		{
			damage = 0;
		}
		
		// Dmg bonusses in PvP fight
		if(isPvP)
		{
			if(skill == null)
				damage *= attacker.calcStat(Stats.PVP_PHYSICAL_DMG, 1, null, null);
			else
				damage *= attacker.calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null);
		}
		
		// Physical skill dmg boost
		if(skill != null)
			damage *= attacker.calcStat(Stats.PHYSICAL_SKILL_POWER, 1, null, null);
		
		damage *= calcElemental(attacker, target, skill);
		if (target instanceof L2Attackable)
		{
			if (isBow)
			{
				if (skill != null)
					damage *= attacker.calcStat(Stats.PVE_BOW_SKILL_DMG, 1, null, null);
				else
					damage *= attacker.calcStat(Stats.PVE_BOW_DMG, 1, null, null);
			}
			else
				damage *= attacker.calcStat(Stats.PVE_PHYSICAL_DMG, 1, null, null);
			if (!target.isRaid() && !target.isRaidMinion()
					&& target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY && attacker.getActingPlayer() != null
					&& (target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2)
			{
				int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
				if (skill != null)
				{
					if (lvlDiff > Config.NPC_SKILL_DMG_PENALTY.size())
						damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size());
					else
						damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
				}
				else if (crit)
				{
					if (lvlDiff > Config.NPC_CRIT_DMG_PENALTY.size())
						damage *= Config.NPC_CRIT_DMG_PENALTY.get(Config.NPC_CRIT_DMG_PENALTY.size());
					else
						damage *= Config.NPC_CRIT_DMG_PENALTY.get(lvlDiff);
				}
				else
				{
					if (lvlDiff > Config.NPC_DMG_PENALTY.size())
						damage *= Config.NPC_DMG_PENALTY.get(Config.NPC_DMG_PENALTY.size());
					else
						damage *= Config.NPC_DMG_PENALTY.get(lvlDiff);
				}
			}
		}
		
		return damage;
	}
	
	public static final double calcMagicDam(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean ss, boolean bss, boolean mcrit)
	{
		final boolean isPvP = (attacker instanceof L2Playable) && (target instanceof L2Playable);
		final boolean isPvE = (attacker instanceof L2Playable) && (target instanceof L2Attackable);
		double mAtk = attacker.getMAtk(target, skill);
		double mDef = target.getMDef(attacker, skill);
		// AI SpiritShot
		if (attacker instanceof L2Npc)
		{
			if(((L2Npc)attacker)._spiritshotcharged)
			{
				ss = true;
			}
			else
				ss = false;
			((L2Npc)attacker)._spiritshotcharged = false;
		}
		// --------------------------------
		// Pvp bonuses for def
		if(isPvP)
		{
			if(skill.isMagic())
				mDef *= target.calcStat(Stats.PVP_MAGICAL_DEF, 1, null, null);
			else
				mDef *= target.calcStat(Stats.PVP_PHYS_SKILL_DEF, 1, null, null);
		}
		
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				mDef += target.getShldDef(); // kamael
				break;
			case SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
				return 1;
		}
		
		if (bss)
			mAtk *= 4;
		else if (ss)
			mAtk *= 2;
		
		double damage = 91 * Math.sqrt(mAtk) / mDef * skill.getPower(attacker, target, isPvP, isPvE);
		
		// Failure calculation
		if (Config.ALT_GAME_MAGICFAILURES && !calcMagicSuccess(attacker, target, skill))
		{
			if (attacker instanceof L2PcInstance)
			{
				if (calcMagicSuccess(attacker, target, skill)
						&& (target.getLevel() - attacker.getLevel()) <= 9)
				{
					if (skill.getSkillType() == L2SkillType.DRAIN)
						attacker.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.DRAIN_HALF_SUCCESFUL));
					else
						attacker.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ATTACK_FAILED));
					
					damage /= 2;
				}
				else
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RESISTED_YOUR_S2);
					sm.addCharName(target);
					sm.addSkillName(skill);
					attacker.sendPacket(sm);
					
					damage = 1;
				}
			}
			
			if (target instanceof L2PcInstance)
			{
				if (skill.getSkillType() == L2SkillType.DRAIN)
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.RESISTED_C1_DRAIN);
					sm.addCharName(attacker);
					target.sendPacket(sm);
				}
				else
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.RESISTED_C1_MAGIC);
					sm.addCharName(attacker);
					target.sendPacket(sm);
				}
			}
		}
		else if (mcrit)
		{
			if (attacker instanceof L2PcInstance && target instanceof L2PcInstance)
				damage *= 2.5;
			else
				damage *= 3;
			
			damage *= attacker.calcStat(Stats.MAGIC_CRIT_DMG, 1, null, null);
		}
		
		// Weapon random damage
		damage *= attacker.getRandomDamageMultiplier();
		
		// Pvp bonuses for dmg
		if(isPvP)
		{
			if(skill.isMagic())
				damage *= attacker.calcStat(Stats.PVP_MAGICAL_DMG, 1, null, null);
			else
				damage *= attacker.calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null);
		}
		// CT2.3 general magic vuln
		damage *= target.calcStat(Stats.MAGIC_DAMAGE_VULN, 1, null, null);
		
		damage *= calcElemental(attacker, target, skill);
		
		if (target instanceof L2Attackable)
		{
			damage *= attacker.calcStat(Stats.PVE_MAGICAL_DMG, 1, null, null);
			if (!target.isRaid() && !target.isRaidMinion()
					&& target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY && attacker.getActingPlayer() != null
					&& (target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2)
			{
				int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
				if (lvlDiff > Config.NPC_SKILL_DMG_PENALTY.size())
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size());
				else
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
			}
		}
		
		return damage;
	}
	
	public static final double calcMagicDam(L2CubicInstance attacker, L2Character target, L2Skill skill, boolean mcrit, byte shld)
	{
		// Current info include mAtk in the skill power.
		// double mAtk = attacker.getMAtk();
		final boolean isPvP = (target instanceof L2Playable);
		final boolean isPvE = (target instanceof L2Attackable);
		double mDef = target.getMDef(attacker.getOwner(), skill);
		
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				mDef += target.getShldDef(); // kamael
				break;
			case SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
				return 1;
		}
		
		double damage = 91 /* * Math.sqrt(mAtk)*/ / mDef * skill.getPower(isPvP, isPvE);
		L2PcInstance owner = attacker.getOwner();
		// Failure calculation
		if (Config.ALT_GAME_MAGICFAILURES && !calcMagicSuccess(owner, target, skill))
		{
			if (calcMagicSuccess(owner, target, skill) && (target.getLevel() - skill.getMagicLevel()) <= 9){
				if (skill.getSkillType() == L2SkillType.DRAIN)
					owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.DRAIN_HALF_SUCCESFUL));
				else
					owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ATTACK_FAILED));
				
				damage /= 2;
			}
			else
			{
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RESISTED_YOUR_S2);
				sm.addCharName(target);
				sm.addSkillName(skill);
				owner.sendPacket(sm);
				
				damage = 1;
			}
			
			if (target instanceof L2PcInstance)
			{
				if (skill.getSkillType() == L2SkillType.DRAIN)
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.RESISTED_C1_DRAIN);
					sm.addCharName(owner);
					target.sendPacket(sm);
				}
				else
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.RESISTED_C1_MAGIC);
					sm.addCharName(owner);
					target.sendPacket(sm);
				}
			}
		}
		else if (mcrit)
			damage *= 3;
		
		// CT2.3 general magic vuln
		damage *= target.calcStat(Stats.MAGIC_DAMAGE_VULN, 1, null, null);
		
		damage *= calcElemental(owner, target, skill);
		
		if (target instanceof L2Attackable)
		{
			damage *= attacker.getOwner().calcStat(Stats.PVE_MAGICAL_DMG, 1, null, null);
			if (!target.isRaid() && !target.isRaidMinion()
					&& target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY && attacker.getOwner() != null
					&& (target.getLevel() - attacker.getOwner().getLevel()) >= 2)
			{
				int lvlDiff = target.getLevel() - attacker.getOwner().getLevel() - 1;
				if (lvlDiff > Config.NPC_SKILL_DMG_PENALTY.size())
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size());
				else
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
			}
		}
		
		return damage;
	}
	
	/** Returns true in case of critical hit */
	public static final boolean calcCrit(double rate, boolean skill, L2Character target)
	{
		final boolean success = rate > Rnd.get(1000);
		
		// support for critical damage evasion
		if (success)
		{
			if (target == null)
				return true; // no effect
			
			if (skill) //skills are excluded from crit dmg evasion
				return success;
			else
				// little weird, but remember what CRIT_DAMAGE_EVASION > 1 increase chances to _evade_ crit hits
				return Rnd.get((int)target.getStat().calcStat(Stats.CRIT_DAMAGE_EVASION, 100, null, null)) < 100;
		}
		return success;
	}
	/** Calculate value of blow success */
	public static final boolean calcBlow(L2Character activeChar, L2Character target, int chance)
	{
		return activeChar.calcStat(Stats.BLOW_RATE, chance*(1.0+(activeChar.getDEX()-20)/100), target, null)>Rnd.get(100);
	}
	/** Calculate value of lethal chance */
	public static final double calcLethal(L2Character activeChar, L2Character target, int baseLethal, int magiclvl)
	{
		double chance = 0;
		if (magiclvl > 0)
		{
			int delta = ((magiclvl + activeChar.getLevel()) / 2) - 1 - target.getLevel();
			
			// delta [-3,infinite)
			if (delta >= -3)
			{
				chance = (baseLethal * ((double) activeChar.getLevel() / target.getLevel()));
			}
			// delta [-9, -3[
			else if (delta < -3 && delta >= -9)
			{
				//               baseLethal
				// chance = -1 * -----------
				//               (delta / 3)
				chance = (-3) * (baseLethal / (delta));
			}
			//delta [-infinite,-9[
			else
			{
				chance = baseLethal / 15;
			}
		}
		else
		{
			chance = (baseLethal * ((double) activeChar.getLevel() / target.getLevel()));
		}
		return 10 * activeChar.calcStat(Stats.LETHAL_RATE, chance, target, null);
	}
	
	public static final boolean calcLethalHit(L2Character activeChar, L2Character target, L2Skill skill)
	{
		if (!target.isRaid()
				&& !(target instanceof L2DoorInstance)
				&& !(target instanceof L2Npc && ((L2Npc) target).getNpcId() == 35062))
		{
			//activeChar.sendMessage(Double.toString(chance));
			//activeChar.sendMessage(Double.toString(calcLethal(activeChar, target, skill.getLethalChance2(),skill.getMagicLevel())));
			//activeChar.sendMessage(Double.toString(calcLethal(activeChar, target, skill.getLethalChance1(),skill.getMagicLevel())));
			
			// 2nd lethal effect activate (cp,hp to 1 or if target is npc then hp to 1)
			if (skill.getLethalChance2() > 0 && Rnd.get(1000) < calcLethal(activeChar, target, skill.getLethalChance2(),skill.getMagicLevel()))
			{
				if (target instanceof L2Npc)
					target.reduceCurrentHp(target.getCurrentHp() - 1, activeChar, skill);
				else if (target instanceof L2PcInstance) // If is a active player set his HP and CP to 1
				{
					L2PcInstance player = (L2PcInstance) target;
					if (!player.isInvul())
					{
						if (!(activeChar instanceof L2PcInstance &&
								(((L2PcInstance)activeChar).isGM() && !((L2PcInstance)activeChar).getAccessLevel().canGiveDamage())))
						{
							player.setCurrentHp(1);
							player.setCurrentCp(1);
							player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.LETHAL_STRIKE_SUCCESSFUL));
						}
					}
				}
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.LETHAL_STRIKE));
			}
			else if (skill.getLethalChance1() > 0 && Rnd.get(1000) < calcLethal(activeChar, target, skill.getLethalChance1(),skill.getMagicLevel()))
			{
				if (target instanceof L2PcInstance)
				{
					L2PcInstance player = (L2PcInstance) target;
					if (!player.isInvul())
					{
						if (!(activeChar instanceof L2PcInstance &&
								(((L2PcInstance)activeChar).isGM() && !((L2PcInstance)activeChar).getAccessLevel().canGiveDamage())))
						{
							player.setCurrentCp(1); // Set CP to 1
							player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CP_DISAPPEARS_WHEN_HIT_WITH_A_HALF_KILL_SKILL));
							activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CP_SIPHON));
						}
					}
				}
				//TODO: remove half kill since SYSMsg got changed.
				/*else if (target instanceof L2Npc) // If is a monster remove first damage and after 50% of current hp
                    target.reduceCurrentHp(target.getCurrentHp() / 2, activeChar, skill);*/
				
			}
			else
				return false;
		}
		else
			return false;
		
		return true;
	}
	
	public static final boolean calcMCrit(double mRate)
	{
		return mRate > Rnd.get(1000);
	}
	
	/** Returns true in case when ATTACK is canceled due to hit */
	public static final boolean calcAtkBreak(L2Character target, double dmg)
	{
		if (target.getFusionSkill() != null)
			return true;
		
		double init = 0;
		
		if (Config.ALT_GAME_CANCEL_CAST && target.isCastingNow()) init = 15;
		if (Config.ALT_GAME_CANCEL_BOW && target.isAttackingNow())
		{
			L2Weapon wpn = target.getActiveWeaponItem();
			if (wpn != null && wpn.getItemType() == L2WeaponType.BOW) init = 15;
		}
		
		if (target.isRaid() || target.isInvul() || init <= 0) return false; // No attack break
		
		// Chance of break is higher with higher dmg
		init += Math.sqrt(13*dmg);
		
		// Chance is affected by target MEN
		init -= (BaseStats.MEN.calcBonus(target) * 100 - 100);
		
		// Calculate all modifiers for ATTACK_CANCEL
		double rate = target.calcStat(Stats.ATTACK_CANCEL, init, null, null);
		
		// Adjust the rate to be between 1 and 99
		if (rate > 99) rate = 99;
		else if (rate < 1) rate = 1;
		
		return Rnd.get(100) < rate;
	}
	
	/** Calculate delay (in milliseconds) before next ATTACK */
	public static final int calcPAtkSpd(L2Character attacker, L2Character target, double rate)
	{
		// measured Oct 2006 by Tank6585, formula by Sami
		// attack speed 312 equals 1500 ms delay... (or 300 + 40 ms delay?)
		if(rate < 2) return 2700;
		else return (int)(470000/rate);
	}
	
	/** Calculate delay (in milliseconds) for skills cast */
	public static final int calcAtkSpd(L2Character attacker, L2Skill skill, double skillTime)
	{
		if (skill.isMagic()) return (int) (skillTime * 333 / attacker.getMAtkSpd());
		return (int) (skillTime * 333 / attacker.getPAtkSpd());
	}
	
	/** Returns true if hit missed (target evaded)
	 *  Formula based on http://l2p.l2wh.com/nonskillattacks.html
	 **/
	public static boolean calcHitMiss(L2Character attacker, L2Character target)
	{
		int chance = (80 + (2 * (attacker.getAccuracy() - target.getEvasionRate(attacker))))*10;
		
		// Get additional bonus from the conditions when you are attacking
		chance *= hitConditionBonus.getConditionBonus(attacker, target);
		
		chance = Math.max(chance, 200);
		chance = Math.min(chance, 980);
		
		return chance < Rnd.get(1000);
	}
	
	public static boolean __calcHitMiss(L2Character attacker, L2Character target)
	{
		int delta = attacker.getAccuracy() - target.getEvasionRate(attacker);
		int chance;
		if (delta >= 10) chance = 980;
		else
		{
			switch (delta)
			{
				case 9: chance = 975; break;
				case 8: chance = 970; break;
				case 7: chance = 965; break;
				case 6: chance = 960; break;
				case 5: chance = 955; break;
				case 4: chance = 945; break;
				case 3: chance = 935; break;
				case 2: chance = 925; break;
				case 1: chance = 915; break;
				case 0: chance = 905; break;
				case -1: chance = 890; break;
				case -2: chance = 875; break;
				case -3: chance = 860; break;
				case -4: chance = 845; break;
				case -5: chance = 830; break;
				case -6: chance = 815; break;
				case -7: chance = 800; break;
				case -8: chance = 785; break;
				case -9: chance = 770; break;
				case -10: chance = 755; break;
				case -11: chance = 735; break;
				case -12: chance = 715; break;
				case -13: chance = 695; break;
				case -14: chance = 675; break;
				case -15: chance = 655; break;
				case -16: chance = 625; break;
				case -17: chance = 595; break;
				case -18: chance = 565; break;
				case -19: chance = 535; break;
				case -20: chance = 505; break;
				case -21: chance = 455; break;
				case -22: chance = 405; break;
				case -23: chance = 355; break;
				case -24: chance = 305; break;
				default: chance = 275;
			}
			if(!attacker.isInFrontOfTarget())
			{
				if(attacker.isBehindTarget())
					chance *= 1.2;
				else // side
					chance *= 1.1;
				if (chance > 980)
					chance = 980;
			}
		}
		return chance < Rnd.get(1000);
	}
	
	/**
	 * Returns:<br>
	 * 0 = shield defense doesn't succeed<br>
	 * 1 = shield defense succeed<br>
	 * 2 = perfect block<br>
	 * 
	 * @param attacker
	 * @param target
	 * @param sendSysMsg
	 * @return
	 */
	public static byte calcShldUse(L2Character attacker, L2Character target, L2Skill skill, boolean sendSysMsg)
	{
		if (skill != null && skill.ignoreShield())
			return 0;
		
		L2Item item = target.getSecondaryWeaponItem();
		if (item == null || !(item instanceof L2Armor) || ((L2Armor)item).getItemType() == L2ArmorType.SIGIL)
			return 0;
		
		double shldRate = target.calcStat(Stats.SHIELD_RATE, 0, attacker, null)
		* BaseStats.DEX.calcBonus(target);
		if (shldRate == 0.0)
			return 0;
		
		int degreeside = (int) target.calcStat(Stats.SHIELD_DEFENCE_ANGLE, 0, null, null) + 120;
		if (degreeside < 360 && (!target.isFacing(attacker, degreeside)))
			return 0;
		
		byte shldSuccess = SHIELD_DEFENSE_FAILED;
		// if attacker
		// if attacker use bow and target wear shield, shield block rate is multiplied by 1.3 (30%)
		L2Weapon at_weapon = attacker.getActiveWeaponItem();
		if (at_weapon != null && at_weapon.getItemType() == L2WeaponType.BOW)
			shldRate *= 1.3;
		
		if (shldRate > 0 && 100 - Config.ALT_PERFECT_SHLD_BLOCK < Rnd.get(100))
		{
			shldSuccess = SHIELD_DEFENSE_PERFECT_BLOCK;
		}
		else if (shldRate > Rnd.get(100))
		{
			shldSuccess = SHIELD_DEFENSE_SUCCEED;
		}
		
		if (sendSysMsg && target instanceof L2PcInstance)
		{
			L2PcInstance enemy = (L2PcInstance)target;
			
			switch (shldSuccess)
			{
				case SHIELD_DEFENSE_SUCCEED:
					enemy.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SHIELD_DEFENCE_SUCCESSFULL));
					break;
				case SHIELD_DEFENSE_PERFECT_BLOCK:
					enemy.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_EXCELLENT_SHIELD_DEFENSE_WAS_A_SUCCESS));
					break;
			}
		}
		
		return shldSuccess;
	}
	
	public static byte calcShldUse(L2Character attacker, L2Character target, L2Skill skill)
	{
		return calcShldUse(attacker, target, skill, true);
	}
	
	public static byte calcShldUse(L2Character attacker, L2Character target)
	{
		return calcShldUse(attacker, target, null, true);
	}
	
	public static boolean calcMagicAffected(L2Character actor, L2Character target, L2Skill skill)
	{
		// TODO: CHECK/FIX THIS FORMULA UP!!
		double defence = 0;
		if (skill.isActive() && skill.isOffensive() && !skill.isNeutral())
			defence = target.getMDef(actor, skill);
		
		double attack = 2 * actor.getMAtk(target, skill) * (1 + calcSkillVulnerability(actor, target, skill) / 100);
		double d = (attack - defence)/(attack + defence);

		if (target.calcStat(Stats.DEBUFF_IMMUNITY, 0, null, skill) > 0 && skill.isDebuff() && !skill.ignoreResists())
			return false;
		
		d += 0.5 * Rnd.nextGaussian();
		return d > 0;
	}
	
	public static double calcSkillVulnerability(L2Character attacker, L2Character target, L2Skill skill)
	{
		double multiplier = 0;	// initialize...
		
		// Get the skill type to calculate its effect in function of base stats
		// of the L2Character target
		if (skill != null)
		{
			// first, get the natural template vulnerability values for the target
			Stats stat = skill.getStat();
			if (stat != null)
			{
				switch (stat)
				{
					case AGGRESSION:
						multiplier = target.getTemplate().baseAggressionVuln;
						break;
					case BLEED:
						multiplier = target.getTemplate().baseBleedVuln;
						break;
					case POISON:
						multiplier = target.getTemplate().basePoisonVuln;
						break;
					case STUN:
						multiplier = target.getTemplate().baseStunVuln;
						break;
					case ROOT:
						multiplier = target.getTemplate().baseRootVuln;
						break;
					case MOVEMENT:
						multiplier = target.getTemplate().baseMovementVuln;
						break;
					case SLEEP:
						multiplier = target.getTemplate().baseSleepVuln;
						break;
				}
			}
			
			// Finally, calculate skilltype vulnerabilities
			L2SkillType type = skill.getSkillType();
			
			// For additional effects (like STUN, SHOCK, PARALYZE...) on damage skills
			switch (type)
			{
				case PDAM:
				case MDAM:
				case BLOW:
				case DRAIN:
				case CHARGEDAM:
				case FATAL:	
				case DEATHLINK:
				case CPDAM:
				case MANADAM:
				case CPDAMPERCENT:
					type = skill.getEffectType();
			}
			
			multiplier = calcSkillTypeVulnerability(multiplier, target, type);
		}
		return multiplier;
	}
	
	public static double calcSkillTypeVulnerability(double multiplier, L2Character target, L2SkillType type)
	{
		if (type != null)
		{
			switch (type)
			{
				case BLEED:
					multiplier = target.calcStat(Stats.BLEED_VULN, multiplier, target, null);
					break;
				case POISON:
					multiplier = target.calcStat(Stats.POISON_VULN, multiplier, target, null);
					break;
				case STUN:
					multiplier = target.calcStat(Stats.STUN_VULN, multiplier, target, null);
					break;
				case PARALYZE:
					multiplier = target.calcStat(Stats.PARALYZE_VULN, multiplier, target, null);
					break;
				case ROOT:
					multiplier = target.calcStat(Stats.ROOT_VULN, multiplier, target, null);
					break;
				case SLEEP:
					multiplier = target.calcStat(Stats.SLEEP_VULN, multiplier, target, null);
					break;
				case MUTE:
				case FEAR:
				case BETRAY:
				case AGGDEBUFF:
				case AGGREDUCE_CHAR:
				case ERASE:
				case CONFUSION:
				case CONFUSE_MOB_ONLY:
					multiplier = target.calcStat(Stats.DERANGEMENT_VULN, multiplier, target, null);
					break;
				case DEBUFF:
					multiplier = target.calcStat(Stats.DEBUFF_VULN, multiplier, target, null);
					break;
				case BUFF:
					multiplier = target.calcStat(Stats.BUFF_VULN, multiplier, target, null);
					break;
				case CANCEL:
					multiplier = target.calcStat(Stats.CANCEL_VULN, multiplier, target, null);
					break;
				default:
			}
		}
		
		return multiplier;
	}
	
	public static double calcSkillProficiency(L2Skill skill, L2Character attacker, L2Character target)
	{
		double multiplier = 0; // initialize...
		
		if (skill != null)
		{
			// Calculate skilltype vulnerabilities
			L2SkillType type = skill.getSkillType();
			
			// For additional effects (like STUN, SHOCK, PARALYZE...) on damage skills
			switch (type)
			{
				case PDAM:
				case MDAM:
				case BLOW:
				case DRAIN:
				case CHARGEDAM:
				case FATAL:	
				case DEATHLINK:
				case CPDAM:
				case MANADAM:
				case CPDAMPERCENT:
					type = skill.getEffectType();
			}
			
			multiplier = calcSkillTypeProficiency(multiplier, attacker, target, type);
		}
		
		return multiplier;
	}
	
	public static double calcSkillTypeProficiency(double multiplier, L2Character attacker, L2Character target, L2SkillType type)
	{
		if (type != null)
		{
			switch (type)
			{
				case BLEED:
					multiplier = attacker.calcStat(Stats.BLEED_PROF, multiplier, target, null);
					break;
				case POISON:
					multiplier = attacker.calcStat(Stats.POISON_PROF, multiplier, target, null);
					break;
				case STUN:
					multiplier = attacker.calcStat(Stats.STUN_PROF, multiplier, target, null);
					break;
				case PARALYZE:
					multiplier = attacker.calcStat(Stats.PARALYZE_PROF, multiplier, target, null);
					break;
				case ROOT:
					multiplier = attacker.calcStat(Stats.ROOT_PROF, multiplier, target, null);
					break;
				case SLEEP:
					multiplier = attacker.calcStat(Stats.SLEEP_PROF, multiplier, target, null);
					break;
				case MUTE:
				case FEAR:
				case BETRAY:
				case AGGDEBUFF:
				case AGGREDUCE_CHAR:
				case ERASE:
				case CONFUSION:
				case CONFUSE_MOB_ONLY:
					multiplier = attacker.calcStat(Stats.DERANGEMENT_PROF, multiplier, target, null);
					break;
				case DEBUFF:
					multiplier = attacker.calcStat(Stats.DEBUFF_PROF, multiplier, target, null);
					break;
				case CANCEL:
					multiplier = attacker.calcStat(Stats.CANCEL_PROF, multiplier, target, null);
					break;
				default:
					
			}
		}
		
		return multiplier;
	}
	
	public static double calcSkillStatModifier(L2Skill skill, L2Character target)
	{
		final BaseStats saveVs = skill.getSaveVs();
		if (saveVs == null)
			return 1;
		
		return 1 / saveVs.calcBonus(target);
	}
	
	public static int calcLvlDependModifier(L2Character attacker, L2Character target, L2Skill skill)
	{
		if (skill.getLevelDepend() == 0)
			return 0;
		
		final int attackerMod;
		if (skill.getMagicLevel() > 0)
			attackerMod = skill.getMagicLevel();
		else
			attackerMod = attacker.getLevel();
		
		return skill.getLevelDepend() * (attackerMod - target.getLevel());
	}
	
	public static int calcElementModifier(L2Character attacker, L2Character target, L2Skill skill)
	{
		final byte element = skill.getElement();
		
		if (element == Elementals.NONE)
			return 0;
		
		int result = skill.getElementPower();
		if (attacker.getAttackElement() == element)
			result += attacker.getAttackElementValue(element);
		
		result -= target.getDefenseElementValue(element);
		
		if (result < 0)
			return 0;
		
		return Math.round((float)result / 10);
	}
	
	public static boolean calcEffectSuccess(L2Character attacker, L2Character target, EffectTemplate effect, L2Skill skill, byte shld, boolean ss, boolean sps, boolean bss)
	{
		final L2SkillType type = effect.effectType;
		final int value = (int)effect.effectPower;

		if (target.calcStat(Stats.DEBUFF_IMMUNITY, 0, null, skill) > 0 && skill.isDebuff() && !skill.ignoreResists())
			return false;
		
		if (type == null)
		{
			if (attacker.isDebug())
				attacker.sendDebugMessage(skill.getName()+" effect ignoring resists");
			
			return Rnd.get(100) < value;
		}
		else if (type.equals(L2SkillType.CANCEL)) // CANCEL-type effects land always
			return true;
		
		if (shld == SHIELD_DEFENSE_PERFECT_BLOCK) // perfect block
		{
			if (attacker.isDebug())
				attacker.sendDebugMessage(skill.getName()+" effect blocked by shield");
			
			return false;
		}
		
		double statModifier = calcSkillStatModifier(skill, target);
		
		// Calculate BaseRate.
		int rate = (int) (value * statModifier);
		
		// Add Matk/Mdef Bonus
		double mAtkModifier = 0;
		int ssModifier = 0;
		if (skill.isMagic())
		{
			mAtkModifier = target.getMDef(target, skill);
			if (shld == SHIELD_DEFENSE_SUCCEED)
				mAtkModifier += target.getShldDef();
			
			// Add Bonus for Sps/SS
			if (bss)
				ssModifier = 4;
			else if (sps)
				ssModifier = 2;
			else
				ssModifier = 1;
			
			mAtkModifier = 14 * Math.sqrt(ssModifier * attacker.getMAtk(target, skill)) / mAtkModifier;
			
			rate = (int) (rate * mAtkModifier);
		}
		
		// Resists
		double vulnModifier = calcSkillTypeVulnerability(0, target, type);
		double profModifier = calcSkillTypeProficiency(0, attacker, target, type);
		double res = vulnModifier + profModifier;
		double resMod = 1;
		if (res != 0)
		{
			if (res < 0)
			{
				resMod = 1 - 0.075 * res;
				resMod = 1 / resMod;
			}
			else
				resMod = 1 + 0.02 * res;
			
			rate *= resMod;
		}
		
		int elementModifier = calcElementModifier(attacker, target, skill);
		rate += elementModifier;
		
		//lvl modifier.
		int deltamod = calcLvlDependModifier(attacker, target, skill);
		rate += deltamod;
		
		if (rate > skill.getMaxChance())
			rate = skill.getMaxChance();
		else if (rate < skill.getMinChance())
			rate = skill.getMinChance();
		
		if (attacker.isDebug() || Config.DEVELOPER)
		{
			final StringBuilder stat = new StringBuilder(100);
			StringUtil.append(stat,
					skill.getName(),
					" eff.type:", type.toString(),
					" power:", String.valueOf(value),
					" stat:", String.format("%1.2f", statModifier),
					" res:", String.format("%1.2f", resMod), "(",
					String.format("%1.2f", profModifier), "/",
					String.format("%1.2f", vulnModifier),
					") elem:", String.valueOf(elementModifier),
					" mAtk:", String.format("%1.2f", mAtkModifier),
					" ss:", String.valueOf(ssModifier),
					" lvl:", String.valueOf(deltamod),
					" total:", String.valueOf(rate)
			);
			final String result = stat.toString();
			if (attacker.isDebug())
				attacker.sendDebugMessage(result);
			if (Config.DEVELOPER)
				_log.info(result);
		}
		return (Rnd.get(100) < rate);
	}
	
	public static boolean calcSkillSuccess(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean ss, boolean sps, boolean bss)
	{
		final boolean isPvP = (attacker instanceof L2Playable) && (target instanceof L2Playable);
		final boolean isPvE = (attacker instanceof L2Playable) && (target instanceof L2Attackable);
		if (skill.ignoreResists())
		{
			if (attacker.isDebug())
				attacker.sendDebugMessage(skill.getName()+" ignoring resists");
			
			return (Rnd.get(100) < skill.getPower(isPvP, isPvE));
		}
		else if (target.calcStat(Stats.DEBUFF_IMMUNITY, 0, null, skill) > 0 && skill.isDebuff())
			return false;
		
		if (shld == SHIELD_DEFENSE_PERFECT_BLOCK) // perfect block
		{
			if (attacker.isDebug())
				attacker.sendDebugMessage(skill.getName()+" blocked by shield");
			
			return false;
		}
		
		int value = (int) skill.getPower(isPvP, isPvE);
		double statModifier = calcSkillStatModifier(skill, target);
		
		// Calculate BaseRate.
		int rate = (int) (value * statModifier);
		
		// Add Matk/Mdef Bonus
		double mAtkModifier = 0;
		int ssModifier = 0;
		if (skill.isMagic())
		{
			mAtkModifier = target.getMDef(target, skill);
			if (shld == SHIELD_DEFENSE_SUCCEED)
				mAtkModifier += target.getShldDef();
			
			// Add Bonus for Sps/SS
			if (bss)
				ssModifier = 4;
			else if (sps)
				ssModifier = 2;
			else
				ssModifier = 1;
			
			mAtkModifier = 14 * Math.sqrt(ssModifier * attacker.getMAtk(target, skill)) / mAtkModifier;
			
			rate = (int) (rate * mAtkModifier);
		}
		
		// Resists
		double vulnModifier = calcSkillVulnerability(attacker, target, skill);
		double profModifier = calcSkillProficiency(skill, attacker, target);
		double res = vulnModifier + profModifier;
		double resMod = 1;
		if (res != 0)
		{
			if (res < 0)
			{
				resMod = 1 - 0.075 * res;
				resMod = 1 / resMod;
			}
			else
				resMod = 1 + 0.02 * res;
			
			rate *= resMod;
		}
		
		int elementModifier = calcElementModifier(attacker, target, skill);
		rate += elementModifier;
		
		//lvl modifier.
		int deltamod = calcLvlDependModifier(attacker, target, skill);
		rate += deltamod;
		
		if (rate > skill.getMaxChance())
			rate = skill.getMaxChance();
		else if (rate < skill.getMinChance())
			rate = skill.getMinChance();
		
		if (attacker.isDebug() || Config.DEVELOPER)
		{
			final StringBuilder stat = new StringBuilder(100);
			StringUtil.append(stat,
					skill.getName(),
					" type:", skill.getSkillType().toString(),
					" power:", String.valueOf(value),
					" stat:", String.format("%1.2f", statModifier),
					" res:", String.format("%1.2f", resMod), "(",
					String.format("%1.2f", profModifier), "/",
					String.format("%1.2f", vulnModifier),
					") elem:", String.valueOf(elementModifier),
					" mAtk:", String.format("%1.2f", mAtkModifier),
					" ss:", String.valueOf(ssModifier),
					" lvl:", String.valueOf(deltamod),
					" total:", String.valueOf(rate)
			);
			final String result = stat.toString();
			if (attacker.isDebug())
				attacker.sendDebugMessage(result);
			if (Config.DEVELOPER)
				_log.info(result);
		}
		return (Rnd.get(100) < rate);
	}
	
	public static boolean calcCubicSkillSuccess(L2CubicInstance attacker, L2Character target, L2Skill skill, byte shld)
	{
		if (shld == SHIELD_DEFENSE_PERFECT_BLOCK) // perfect block
			return false;
		final boolean isPvP = (target instanceof L2Playable);
		final boolean isPvE = (target instanceof L2Attackable);
		
		if (target.calcStat(Stats.DEBUFF_IMMUNITY, 0, null, skill) > 0 && skill.isDebuff() && !skill.ignoreResists())
			return false;
		
		// if target reflect this skill then the effect will fail
		if (calcSkillReflect(target, skill) != SKILL_REFLECT_FAILED)
			return false;
		
		int value = (int) skill.getPower(isPvP, isPvE);
		double statModifier = calcSkillStatModifier(skill, target);
		int rate = (int) (value * statModifier);
		
		// Add Matk/Mdef Bonus
		double mAtkModifier = 0;
		if (skill.isMagic())
		{
			mAtkModifier = target.getMDef(attacker.getOwner(), skill);
			if (shld == SHIELD_DEFENSE_SUCCEED)
				mAtkModifier += target.getShldDef();
			
			mAtkModifier = Math.pow(attacker.getMAtk() / mAtkModifier, 0.2);
			
			rate += (int) (mAtkModifier * 100) - 100;
		}
		
		// Resists
		double vulnModifier = calcSkillVulnerability(attacker.getOwner(), target, skill);
		double profModifier = calcSkillProficiency(skill, attacker.getOwner(), target);
		double res = vulnModifier + profModifier;
		double resMod = 1;
		if (res != 0)
		{
			if (res < 0)
			{
				resMod = 1 - 0.075 * res;
				resMod = 1 / resMod;
			}
			else
				resMod = 1 + 0.02 * res;
			
			rate *= resMod;
		}
		
		int elementModifier = calcElementModifier(attacker.getOwner(), target, skill);
		rate += elementModifier;
		
		//lvl modifier.
		int deltamod = calcLvlDependModifier(attacker.getOwner(), target, skill);
		rate += deltamod;
		
		if (rate > skill.getMaxChance())
			rate = skill.getMaxChance();
		else if (rate < skill.getMinChance())
			rate = skill.getMinChance();
		
		if (attacker.getOwner().isDebug() || Config.DEVELOPER)
		{
			final StringBuilder stat = new StringBuilder(100);
			StringUtil.append(stat,
					skill.getName(),
					" type:", skill.getSkillType().toString(),
					" power:", String.valueOf(value),
					" stat:", String.format("%1.2f", statModifier),
					" res:", String.format("%1.2f", resMod), "(",
					String.format("%1.2f", profModifier), "/",
					String.format("%1.2f", vulnModifier),
					") elem:", String.valueOf(elementModifier),
					" mAtk:", String.format("%1.2f", mAtkModifier),
					" lvl:", String.valueOf(deltamod),
					" total:", String.valueOf(rate)
			);
			final String result = stat.toString();
			if (attacker.getOwner().isDebug())
				attacker.getOwner().sendDebugMessage(result);
			if (Config.DEVELOPER)
				_log.info(result);
		}
		return (Rnd.get(100) < rate);
	}
	
	public static boolean calcMagicSuccess(L2Character attacker, L2Character target, L2Skill skill)
	{
		// DS: remove skill magic level dependence from nukes
		//int lvlDifference = (target.getLevel() - (skill.getMagicLevel() > 0 ? skill.getMagicLevel() : attacker.getLevel()));
		int lvlDifference = (target.getLevel() - (skill.getSkillType() == L2SkillType.SPOIL ? skill.getMagicLevel() : attacker.getLevel()));
		double lvlModifier = Math.pow(1.3, lvlDifference);
		float targetModifier = 1;
		if (target instanceof L2Attackable && !target.isRaid() && !target.isRaidMinion()
				&& target.getLevel() >= Config.MIN_NPC_LVL_MAGIC_PENALTY && attacker.getActingPlayer() != null
				&& (target.getLevel() - attacker.getActingPlayer().getLevel()) >= 3)
		{
			int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 2;
			if (lvlDiff > Config.NPC_SKILL_CHANCE_PENALTY.size())
				targetModifier = Config.NPC_SKILL_CHANCE_PENALTY.get(Config.NPC_SKILL_CHANCE_PENALTY.size());
			else
				targetModifier = Config.NPC_SKILL_CHANCE_PENALTY.get(lvlDiff);
		}
		// general magic resist
		final double resModifier = target.calcStat(Stats.MAGIC_SUCCESS_RES, 1, null, skill);
		final double failureModifier = attacker.calcStat(Stats.MAGIC_FAILURE_RATE, 1, target, skill);
		int rate = 100 - Math.round((float)(lvlModifier * targetModifier * resModifier * failureModifier));
		
		if (rate > skill.getMaxChance())
			rate = skill.getMaxChance();
		else if (rate < skill.getMinChance())
			rate = skill.getMinChance();

		if (attacker.isDebug() || Config.DEVELOPER)
		{
			final StringBuilder stat = new StringBuilder(100);
			StringUtil.append(stat,
					skill.getName(),
					" lvlDiff:", String.valueOf(lvlDifference),
					" lvlMod:", String.format("%1.2f", lvlModifier),
					" res:", String.format("%1.2f", resModifier),
					" fail:", String.format("%1.2f", failureModifier),
					" tgt:", String.valueOf(targetModifier),
					" total:", String.valueOf(rate)
			);
			final String result = stat.toString();
			if (attacker.isDebug())
				attacker.sendDebugMessage(result);
			if (Config.DEVELOPER)
				_log.info(result);
		}
		return (Rnd.get(100) < rate);
	}
	
	public static double calcManaDam(L2Character attacker, L2Character target, L2Skill skill,
			boolean ss, boolean bss)
	{
		// AI SpiritShot
		if (attacker instanceof L2Npc)
		{
			if(((L2Npc)attacker)._spiritshotcharged)
			{
				ss = true;
			}
			else
				ss = false;
			((L2Npc)attacker)._spiritshotcharged = false;
		}
		//Mana Burnt = (SQR(M.Atk)*Power*(Target Max MP/97))/M.Def
		double mAtk = attacker.getMAtk(target, skill);
		double mDef = target.getMDef(attacker, skill);
		final boolean isPvP = (attacker instanceof L2Playable) && (target instanceof L2Playable);
		final boolean isPvE = (attacker instanceof L2Playable) && (target instanceof L2Attackable);
		double mp = target.getMaxMp();
		if (bss) mAtk *= 4;
		else if (ss) mAtk *= 2;
		
		double damage = (Math.sqrt(mAtk) * skill.getPower(attacker, target, isPvP, isPvE) * (mp/97)) / mDef;
		damage *= (1 + calcSkillVulnerability(attacker, target, skill) / 100);
		if (target instanceof L2Attackable)
		{
			damage *= attacker.calcStat(Stats.PVE_MAGICAL_DMG, 1, null, null);
			if (!target.isRaid() && !target.isRaidMinion()
					&& target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY && attacker.getActingPlayer() != null
					&& (target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2)
			{
				int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
				if (lvlDiff > Config.NPC_SKILL_DMG_PENALTY.size())
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size());
				else
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
			}
		}
		
		return damage;
	}
	
	public static double calculateSkillResurrectRestorePercent(double baseRestorePercent, L2Character caster)
	{
		if (baseRestorePercent == 0 || baseRestorePercent == 100)
			return baseRestorePercent;
		
		double restorePercent = baseRestorePercent * BaseStats.WIT.calcBonus(caster);
		if(restorePercent - baseRestorePercent > 20.0)
			restorePercent += 20.0;
		
		restorePercent = Math.max(restorePercent, baseRestorePercent);
		restorePercent = Math.min(restorePercent, 90.0);
		
		return restorePercent;
	}
	
	public static boolean calcPhysicalSkillEvasion(L2Character target, L2Skill skill)
	{
		if (skill.isMagic() && skill.getSkillType() != L2SkillType.BLOW || skill.isDebuff())
			return false;
		
		return Rnd.get(100) < target.calcStat(Stats.P_SKILL_EVASION, 0, null, skill);
	}
	
	
	public static boolean calcSkillMastery(L2Character actor, L2Skill sk)
	{
		if (sk.getSkillType() == L2SkillType.FISHING)
			return false;
		
		double val = actor.getStat().calcStat(Stats.SKILL_MASTERY, 0, null, null);
		
		if (actor instanceof L2PcInstance)
		{
			if (((L2PcInstance) actor).isMageClass())
				val *= BaseStats.INT.calcBonus(actor);
			else
				val *= BaseStats.STR.calcBonus(actor);
		}
		
		return Rnd.get(100) < val;
	}
	
	public static double calcValakasAttribute(L2Character attacker, L2Character target, L2Skill skill)
	{
		double calcPower = 0;
		double calcDefen = 0;
		
		if (skill != null && skill.getAttributeName().contains("valakas"))
		{
			calcPower = attacker.calcStat(Stats.VALAKAS, calcPower, target, skill);
			calcDefen = target.calcStat(Stats.VALAKAS_RES, calcDefen, target, skill);
		}
		else
		{
			calcPower = attacker.calcStat(Stats.VALAKAS, calcPower, target, skill);
			if (calcPower > 0)
			{
				calcPower = attacker.calcStat(Stats.VALAKAS, calcPower, target, skill);
				calcDefen = target.calcStat(Stats.VALAKAS_RES, calcDefen, target, skill);
			}
		}
		return calcPower - calcDefen;
	}
	
	public static double calcElemental(L2Character attacker, L2Character target, L2Skill skill)
	{
		int calcPower = 0;
		int calcDefen = 0;
		int calcTotal = 0;
		double result = 1.0;
		byte element;
		
		if (skill != null)
		{
			element = skill.getElement();
			if (element >= 0)
			{
				calcPower = skill.getElementPower();
				calcDefen = target.getDefenseElementValue(element);
				
				if (attacker.getAttackElement() == element)
					calcPower += attacker.getAttackElementValue(element);
				
				calcTotal = calcPower - calcDefen;
				if (calcTotal > 0)
				{
					if (calcTotal < 50)
						result += calcTotal * 0.003948;
					else if (calcTotal < 150)
						result = 1.1974;
					else if (calcTotal < 300)
						result = 1.3973;
					else
						result = 1.6963;
				}
				
				if (Config.DEVELOPER)
					_log.info(skill.getName()
							+ ": "
							+ calcPower
							+ ", "
							+ calcDefen
							+ ", "
							+ result);
			}
		}
		else
		{
			element = attacker.getAttackElement();
			if (element >= 0)
			{
				calcTotal = Math.max(attacker.getAttackElementValue(element)
						- target.getDefenseElementValue(element), 0);
				
				if (calcTotal < 50)
					result += calcTotal * 0.003948;
				else if (calcTotal < 150)
					result = 1.1974;
				else if (calcTotal < 300)
					result = 1.3973;
				else
					result = 1.6963;
				
				if (Config.DEVELOPER)
					_log.info("Hit: "
							+ calcPower
							+ ", "
							+ calcDefen
							+ ", "
							+ result);
			}
		}
		return result;
	}
	
	/**
	 * Calculate skill reflection according these three possibilities:
	 * <li>Reflect failed</li>
	 * <li>Mormal reflect (just effects). <U>Only possible for skilltypes: BUFF, REFLECT, HEAL_PERCENT,
	 * MANAHEAL_PERCENT, HOT, CPHOT, MPHOT</U></li>
	 * <li>vengEance reflect (100% damage reflected but damage is also dealt to actor). <U>This is only possible
	 * for skills with skilltype PDAM, BLOW, CHARGEDAM, MDAM or DEATHLINK</U></li>
     <br><br>
	 * 
     @param actor
	 * @param target
	 * @param skill
	 * @return SKILL_REFLECTED_FAILED, SKILL_REFLECT_SUCCEED or SKILL_REFLECT_VENGEANCE
	 */
	public static byte calcSkillReflect(L2Character target, L2Skill skill)
	{
		/*
		 *  Neither some special skills (like hero debuffs...) or those skills
		 *  ignoring resistances can be reflected
		 */
		if (skill.ignoreResists() || !skill.canBeReflected())
			return SKILL_REFLECT_FAILED;
		
		// only magic and melee skills can be reflected
		if (!skill.isMagic() &&
				(skill.getCastRange() == -1 || skill.getCastRange() > MELEE_ATTACK_RANGE))
			return SKILL_REFLECT_FAILED;
		
		byte reflect = SKILL_REFLECT_FAILED;
		// check for non-reflected skilltypes, need additional retail check
		switch (skill.getSkillType())
		{
			case BUFF:
			case HEAL_PERCENT:
			case MANAHEAL_PERCENT:
			case HOT:
			case CPHOT:
			case MPHOT:
			case UNDEAD_DEFENSE:
			case AGGDEBUFF:
			case CONT:
				return SKILL_REFLECT_FAILED;
				// these skill types can deal damage
			case PDAM:
			case MDAM:
			case BLOW:
			case DRAIN:
			case CHARGEDAM:
			case FATAL:	
			case DEATHLINK:
			case CPDAM:
			case MANADAM:
			case CPDAMPERCENT:
				final Stats stat = skill.isMagic() ? Stats.VENGEANCE_SKILL_MAGIC_DAMAGE : Stats.VENGEANCE_SKILL_PHYSICAL_DAMAGE;
				final double venganceChance = target.getStat().calcStat(stat, 0, target, skill);
				if (venganceChance > Rnd.get(100))
					reflect |= SKILL_REFLECT_VENGEANCE;
				break;
		}
		
		final double reflectChance = target.calcStat(skill.isMagic() ? Stats.REFLECT_SKILL_MAGIC : Stats.REFLECT_SKILL_PHYSIC, 0, null, skill);
		
		if( Rnd.get(100) < reflectChance)
			reflect |= SKILL_REFLECT_SUCCEED;
		
		return reflect;
	}
	
	/**
	 * Calculate damage caused by falling
	 * @param cha
	 * @param fallHeight
	 * @return damage
	 */
	public static double calcFallDam(L2Character cha, int fallHeight)
	{
		if (!Config.ENABLE_FALLING_DAMAGE || fallHeight < 0)
			return 0;
		final double damage = cha.calcStat(Stats.FALL, fallHeight * cha.getMaxHp() / 1000, null, null);
		return damage;
	}
	
	private static double FRONT_MAX_ANGLE = 100;
	private static double BACK_MAX_ANGLE = 40;
	
	/**
	 * Calculates blow success depending on base chance and relative position of attacker and target
	 * @param activeChar Target that is performing skill
	 * @param target Target of this skill
	 * @param skill Skill which will be used to get base value of blowChance and crit condition
	 * @return Success of blow
	 */
	public static boolean calcBlowSuccess(L2Character activeChar, L2Character target, L2Skill skill)
	{
		int blowChance = skill.getBlowChance();
		
		// Skill is blow and it has 0% to make dmg... thats just wrong
		if (blowChance == 0)
		{
			_log.log(Level.WARNING, "Skill " + skill.getId() + " - " + skill.getName() + " has 0 blow land chance, yet its a blow skill!");
			//TODO: return false;
			//lets add 20 for now, till all skills are corrected
			blowChance = 20;
		}
		
		if (isBehind(target, activeChar))
			blowChance *= 2; //double chance from behind
		else if (isInFrontOf(target, activeChar))
		{
			if ((skill.getCondition() & L2Skill.COND_BEHIND) != 0)
				return false;

			//base chance from front	
		}
		else
			blowChance *= 1.5; //50% better chance from side
		
		return activeChar.calcStat(Stats.BLOW_RATE, blowChance * (1.0 + (activeChar.getDEX()) / 100.), target, null) > Rnd.get(100);
	}
	
	/**
	 * Those are altered formulas for blow lands
	 * Return True if the target is IN FRONT of the L2Character.<BR><BR>
	 */
	public static boolean isInFrontOf(L2Character target, L2Character attacker)
	{
		if (target == null)
			return false;
		
		double angleChar, angleTarget, angleDiff;
		angleTarget = Util.calculateAngleFrom(target, attacker);
		angleChar = Util.convertHeadingToDegree(target.getHeading());
		angleDiff = angleChar - angleTarget;
		if (angleDiff <= -360 + FRONT_MAX_ANGLE)
			angleDiff += 360;
		if (angleDiff >= 360 - FRONT_MAX_ANGLE)
			angleDiff -= 360;
		if (Math.abs(angleDiff) <= FRONT_MAX_ANGLE)
			return true;
		return false;
	}
	
	/**
	 * Those are altered formulas for blow lands
	 * Return True if the L2Character is behind the target and can't be seen.<BR><BR>
	 */
	public static boolean isBehind(L2Character target, L2Character attacker)
	{
		if (target == null)
			return false;
		
		double angleChar, angleTarget, angleDiff;
		L2Character target1 = target;
		angleChar = Util.calculateAngleFrom(attacker, target1);
		angleTarget = Util.convertHeadingToDegree(target1.getHeading());
		angleDiff = angleChar - angleTarget;
		if (angleDiff <= -360 + BACK_MAX_ANGLE)
			angleDiff += 360;
		if (angleDiff >= 360 - BACK_MAX_ANGLE)
			angleDiff -= 360;
		if (Math.abs(angleDiff) <= BACK_MAX_ANGLE)
			return true;
		return false;
	}
}
