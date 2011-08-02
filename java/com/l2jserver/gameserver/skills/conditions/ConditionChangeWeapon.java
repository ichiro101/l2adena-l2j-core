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
import com.l2jserver.gameserver.templates.item.L2Weapon;

/**
 * The Class ConditionChangeWeapon.
 *
 * @author nBd
 */
public class ConditionChangeWeapon extends Condition
{
	private final boolean _required;
	
	/**
	 * Instantiates a new condition change weapon.
	 *
	 * @param required the required
	 */
	public ConditionChangeWeapon(boolean required)
	{
		_required = required;
	}
	
	/**
	 * Test impl.
	 *
	 * @param env the env
	 * @return true, if successful
	 * @see com.l2jserver.gameserver.skills.conditions.Condition#testImpl(com.l2jserver.gameserver.skills.Env)
	 */
	@Override
	boolean testImpl(Env env)
	{
		if (!(env.player instanceof L2PcInstance))
			return false;
		
		if (_required)
		{
			L2Weapon weaponItem = env.player.getActiveWeaponItem();
			
			if (weaponItem == null)
				return false;
			
			if (weaponItem.getChangeWeaponId() == 0)
				return false;
			
			if (((L2PcInstance)env.player).isEnchanting())
				return false;
		}
		return true;
	}
	
}