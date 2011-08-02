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

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.skills.Env;



/**
 * The Class ConditionPlayerInvSize.
 *
 * @author Kerberos
 */
public class ConditionPlayerInvSize extends Condition {
	
	private final int _size;
	
	/**
	 * Instantiates a new condition player inv size.
	 *
	 * @param size the size
	 */
	public ConditionPlayerInvSize(int size)
	{
		_size = size;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.skills.conditions.Condition#testImpl(com.l2jserver.gameserver.skills.Env)
	 */
	@Override
	public boolean testImpl(Env env)
	{
		if (env.player instanceof L2PcInstance)
			return ((L2PcInstance)env.player).getInventory().getSize(false) <= (((L2PcInstance) env.player).getInventoryLimit()-_size);
		return true;
	}
}
