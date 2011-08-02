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

import java.util.logging.Logger;

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.skills.Env;

/**
 * The Class ConditionPlayerGrade.
 * @author Gigiikun
 */
public final class ConditionPlayerGrade extends Condition
{
	protected static final Logger _log = Logger.getLogger(ConditionPlayerGrade.class.getName());
	//	conditional values
	public final static int COND_NO_GRADE = 0x0001;
	public final static int COND_D_GRADE = 0x0002;
	public final static int COND_C_GRADE = 0x0004;
	public final static int COND_B_GRADE = 0x0008;
	public final static int COND_A_GRADE = 0x0010;
	public final static int COND_S_GRADE = 0x0020;
	public final static int COND_S80_GRADE = 0x0040;
	public final static int COND_S84_GRADE = 0x0080;
	
	private final int _value;
	
	/**
	 * Instantiates a new condition player grade.
	 * @param value the value
	 */
	public ConditionPlayerGrade(int value)
	{
		_value = value;
	}
	
	/**
	 * @see com.l2jserver.gameserver.skills.conditions.Condition#testImpl(com.l2jserver.gameserver.skills.Env)
	 */
	@Override
	public boolean testImpl(Env env)
	{
		if (env.player instanceof L2PcInstance)
		{
			final byte expIndex = (byte) ((L2PcInstance) env.player).getExpertiseLevel();
			
			return _value == expIndex;
		}
		return false;
	}
}
