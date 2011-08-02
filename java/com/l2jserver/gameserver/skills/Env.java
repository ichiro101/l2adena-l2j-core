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

import com.l2jserver.gameserver.model.L2Effect;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2CubicInstance;

/**
 *
 * An Env object is just a class to pass parameters to a calculator such as L2PcInstance, L2ItemInstance, Initial value.
 *
 */

public final class Env {
	
	public L2Character    player;
	public L2CubicInstance cubic;
	public L2Character    target;
	public L2ItemInstance item;
	public L2Skill        skill;
	public L2Effect       effect;
	public double value;
	public double baseValue;
	public boolean skillMastery = false;
	public byte shld = 0;
	public boolean ss = false;
	public boolean sps = false;
	public boolean bss = false;
	
	public Env()
	{
		
	}
	
	public Env(byte shd, boolean s, boolean ps, boolean bs)
	{
		shld = shd;
		ss = s;
		sps = ps;
		bss = bs;
	}
}
