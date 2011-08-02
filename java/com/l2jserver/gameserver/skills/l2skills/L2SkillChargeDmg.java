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
package com.l2jserver.gameserver.skills.l2skills;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.L2Effect;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.L2Playable;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.skills.BaseStats;
import com.l2jserver.gameserver.skills.Env;
import com.l2jserver.gameserver.skills.Formulas;
import com.l2jserver.gameserver.templates.StatsSet;
import com.l2jserver.gameserver.templates.item.L2WeaponType;

public class L2SkillChargeDmg extends L2Skill
{
	private static final Logger _logDamage = Logger.getLogger("damage");
	
	public L2SkillChargeDmg(StatsSet set)
	{
		super(set);
	}
	
	@Override
	public void useSkill(L2Character caster, L2Object[] targets)
	{
		if (caster.isAlikeDead())
		{
			return;
		}
		
		double modifier = 0;
		if (caster instanceof L2PcInstance)
		{
			// thanks Diego Vargas of L2Guru: 70*((0.8+0.201*No.Charges) * (PATK+POWER)) / PDEF
			modifier = 0.8+0.201*(getNumCharges()+((L2PcInstance)caster).getCharges());
		}
		L2ItemInstance weapon = caster.getActiveWeaponInstance();
		boolean soul = (weapon != null
				&& weapon.getChargedSoulshot() == L2ItemInstance.CHARGED_SOULSHOT
				&& weapon.getItemType() != L2WeaponType.DAGGER );
		
		for (L2Character target: (L2Character[]) targets)
		{
			if (target.isAlikeDead())
				continue;
			
			//	Calculate skill evasion
			boolean skillIsEvaded = Formulas.calcPhysicalSkillEvasion(target, this);
			if(skillIsEvaded)
			{
				if (caster instanceof L2PcInstance)
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_DODGES_ATTACK);
					sm.addString(target.getName());
					((L2PcInstance) caster).sendPacket(sm);
				}
				if (target instanceof L2PcInstance)
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.AVOIDED_C1_ATTACK2);
					sm.addString(caster.getName());
					((L2PcInstance) target).sendPacket(sm);
				}
				
				//no futher calculations needed.
				continue;
			}
			
			// TODO: should we use dual or not?
			// because if so, damage are lowered but we don't do anything special with dual then
			// like in doAttackHitByDual which in fact does the calcPhysDam call twice
			//boolean dual  = caster.isUsingDualWeapon();
			byte shld = Formulas.calcShldUse(caster, target, this);
			boolean crit = false;
			if (this.getBaseCritRate() > 0)
				crit = Formulas.calcCrit(this.getBaseCritRate() * 10 * BaseStats.STR.calcBonus(caster), true, target);
			// damage calculation, crit is static 2x
			double damage = Formulas.calcPhysDam(caster, target, this, shld, false, false, soul);
			if (crit)
				damage *= 2;
			
			if (damage > 0)
			{
				byte reflect = Formulas.calcSkillReflect(target, this);
				if (hasEffects())
				{
					if ((reflect & Formulas.SKILL_REFLECT_SUCCEED) != 0)
					{
						caster.stopSkillEffects(getId());
						getEffects(target, caster);
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
						sm.addSkillName(this);
						caster.sendPacket(sm);
					}
					else
					{
						// activate attacked effects, if any
						target.stopSkillEffects(getId());
						if (Formulas.calcSkillSuccess(caster, target, this, shld, false, false, true))
						{
							getEffects(caster, target, new Env(shld, false, false, false));
							
							SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
							sm.addSkillName(this);
							target.sendPacket(sm);
						}
						else
						{
							SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RESISTED_YOUR_S2);
							sm.addCharName(target);
							sm.addSkillName(this);
							caster.sendPacket(sm);
						}
					}
				}
				
				double finalDamage = damage*modifier;
				
				if (Config.LOG_GAME_DAMAGE
						&& caster instanceof L2Playable
						&& damage > Config.LOG_GAME_DAMAGE_THRESHOLD)
				{
					LogRecord record = new LogRecord(Level.INFO, "");
					record.setParameters(new Object[]{caster, " did damage ", (int)damage, this, " to ", target});
					record.setLoggerName("pdam");
					_logDamage.log(record);
				}
				
				target.reduceCurrentHp(finalDamage, caster, this);

				// vengeance reflected damage
				if ((reflect & Formulas.SKILL_REFLECT_VENGEANCE) != 0)
				{
					if (target instanceof L2PcInstance)
					{
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.COUNTERED_C1_ATTACK);
						sm.addCharName(caster);
						target.sendPacket(sm);
					}
					if (caster instanceof L2PcInstance)
					{
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_PERFORMING_COUNTERATTACK);
						sm.addCharName(target);
						caster.sendPacket(sm);
					}
					// Formula from Diego post, 700 from rpg tests
					double vegdamage = (700 * target.getPAtk(caster) / (double)caster.getPDef(target));
					caster.reduceCurrentHp(vegdamage, target, this);
				}
				
				caster.sendDamageMessage(target, (int)finalDamage, false, crit, false);
				
			}
			else
			{
				caster.sendDamageMessage(target, 0, false, false, true);
			}
		}
		if (soul && weapon!= null)
			weapon.setChargedSoulshot(L2ItemInstance.CHARGED_NONE);
		
		// effect self :]
		if (hasSelfEffects())
		{
			L2Effect effect = caster.getFirstEffect(getId());
			if (effect != null && effect.isSelfEffect())
			{
				//Replace old effect with new one.
				effect.exit();
			}
			// cast self effect if any
			getEffectsSelf(caster);
		}
	}
}
