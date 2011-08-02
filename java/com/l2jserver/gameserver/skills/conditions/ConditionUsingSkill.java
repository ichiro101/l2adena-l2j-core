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
package com.l2jserver.gameserver.skills.conditions;

import com.l2jserver.gameserver.skills.Env;

/**
 * The Class ConditionUsingSkill.
 *
 * @author mkizub
 */
public final class ConditionUsingSkill extends Condition
{
	private final int _skillId;
	
	/**
	 * Instantiates a new condition using skill.
	 *
	 * @param skillId the skill id
	 */
	public ConditionUsingSkill(int skillId)
	{
		_skillId = skillId;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.skills.conditions.Condition#testImpl(com.l2jserver.gameserver.skills.Env)
	 */
	@Override
	public boolean testImpl(Env env)
	{
		if (env.skill == null)
			return false;
		return env.skill.getId() == _skillId;
	}
}
