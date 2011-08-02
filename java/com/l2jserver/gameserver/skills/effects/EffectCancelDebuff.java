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
package com.l2jserver.gameserver.skills.effects;
 
import com.l2jserver.gameserver.model.L2Effect;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.skills.Env;
import com.l2jserver.gameserver.skills.Formulas;
import com.l2jserver.gameserver.templates.effects.EffectTemplate;
import com.l2jserver.gameserver.templates.skills.L2EffectType;
import com.l2jserver.gameserver.templates.skills.L2SkillType;
import com.l2jserver.util.Rnd;
 
/**
 *
 * @author UnAfraid
 *
 */
public class EffectCancelDebuff extends L2Effect
{
    public EffectCancelDebuff(Env env, EffectTemplate template)
    {
        super(env, template);
    }
   
    /**
     *
     * @see com.l2jserver.gameserver.model.L2Effect#getEffectType()
     */
    @Override
    public L2EffectType getEffectType()
    {
        return L2EffectType.CANCEL_DEBUFF;
    }
   
    /**
     *
     * @see com.l2jserver.gameserver.model.L2Effect#onStart()
     */
    @Override
    public boolean onStart()
    {
        return cancel(getEffector(), getEffected(), getSkill(), getEffectTemplate().effectType);
    }
   
    /**
     *
     * @see com.l2jserver.gameserver.model.L2Effect#onActionTime()
     */
    @Override
    public boolean onActionTime()
    {
        return false;
    }
   
    private static boolean cancel(L2Character caster, L2Character target, L2Skill skill, L2SkillType effectType)
    {
        if (!(target instanceof L2PcInstance)|| target.isDead())
            return false;
       
        final int cancelLvl = skill.getMagicLevel();
        int count = skill.getMaxNegatedEffects();
        double baseRate = Formulas.calcSkillTypeProficiency(skill.getPower(), caster, target, effectType);
        baseRate += Formulas.calcSkillTypeVulnerability(baseRate, caster, effectType);
       
        L2Effect effect;
        int lastCanceledSkillId = 0;
        final L2Effect[] effects = target.getAllEffects();
        for (int i = effects.length; --i >= 0;)
        {
            effect = effects[i];
            if (effect == null)
                continue;
           
            if (!effect.getSkill().isDebuff() || !effect.getSkill().canBeDispeled())
            {
                effects[i] = null;
                continue;
            }
           
            if (effect.getSkill().getId() == lastCanceledSkillId)
            {
                effect.exit(); // this skill already canceled
                continue;
            }
           
            if (!calcCancelSuccess(effect, cancelLvl, (int)baseRate))
                continue;
           
            lastCanceledSkillId = effect.getSkill().getId();
            effect.exit();
            count--;
           
            if (count == 0)
                break;
        }
       
        if (count != 0)
        {
            lastCanceledSkillId = 0;
            for (int i = effects.length; --i >= 0;)
            {
                effect = effects[i];
                if (effect == null)
                    continue;
               
                if (!effect.getSkill().isDebuff() || !effect.getSkill().canBeDispeled())
                {
                    effects[i] = null;
                    continue;
                }
               
                if (effect.getSkill().getId() == lastCanceledSkillId)
                {
                    effect.exit(); // this skill already canceled
                    continue;
                }
               
                if (!calcCancelSuccess(effect, cancelLvl, (int)baseRate))
                    continue;
               
                lastCanceledSkillId = effect.getSkill().getId();
                effect.exit();
                count--;
               
                if (count == 0)
                    break;
            }
        }
        return true;
    }
   
    private static boolean calcCancelSuccess(L2Effect effect, int cancelLvl, int baseRate)
    {
        int rate = 2 * (cancelLvl - effect.getSkill().getMagicLevel());
        rate += (effect.getAbnormalTime() - effect.getTime()) / 1200;
        rate += baseRate;
       
        if (rate < 25)
            rate = 25;
        else if (rate > 75)
            rate = 75;
       
        return Rnd.get(100) < rate;
    }
}