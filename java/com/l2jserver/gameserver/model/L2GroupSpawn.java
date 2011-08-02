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
package com.l2jserver.gameserver.model;

import java.lang.reflect.Constructor;
import java.util.logging.Level;

import com.l2jserver.Config;
import com.l2jserver.gameserver.Territory;
import com.l2jserver.gameserver.idfactory.IdFactory;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;
import com.l2jserver.util.Rnd;


/**
 * @author littlecrow
 * A special spawn implementation to spawn controllable mob
 */
public class L2GroupSpawn extends L2Spawn
{
	private Constructor<?> _constructor;
	private L2NpcTemplate _template;
	
	public L2GroupSpawn(L2NpcTemplate mobTemplate) throws SecurityException, ClassNotFoundException, NoSuchMethodException
	{
		super(mobTemplate);
		_constructor = Class.forName("com.l2jserver.gameserver.model.actor.instance.L2ControllableMobInstance").getConstructors()[0];
		_template = mobTemplate;
		
		setAmount(1);
	}
	
	public L2Npc doGroupSpawn()
	{
		L2Npc mob = null;
		
		try
		{
			if (_template.type.equalsIgnoreCase("L2Pet") ||
					_template.type.equalsIgnoreCase("L2Minion"))
				return null;
			
			Object[] parameters = {IdFactory.getInstance().getNextId(), _template};
			Object  tmp = _constructor.newInstance(parameters);
			
			if (!(tmp instanceof L2Npc))
				return null;
			
			mob = (L2Npc)tmp;
			
			int newlocx, newlocy, newlocz;
			
			if  (getLocx() == 0 && getLocy() == 0)
			{
				if (getLocation() == 0)
					return null;
				
				int p[] = Territory.getInstance().getRandomPoint(getLocation());
				newlocx = p[0];
				newlocy = p[1];
				newlocz = p[2];
			}
			else
			{
				newlocx = getLocx();
				newlocy = getLocy();
				newlocz = getLocz();
			}
			
			mob.setCurrentHpMp(mob.getMaxHp(), mob.getMaxMp());
			
			if (getHeading() == -1)
				mob.setHeading(Rnd.nextInt(61794));
			else
				mob.setHeading(getHeading());
			
			mob.setSpawn(this);
			mob.spawnMe(newlocx, newlocy, newlocz);
			mob.onSpawn();
			
			if (Config.DEBUG)
				_log.finest("spawned Mob ID: "+_template.npcId+" ,at: "
						+mob.getX()+" x, "+mob.getY()+" y, "+mob.getZ()+" z");
			
			return mob;
			
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "NPC class not found: " + e.getMessage(), e);
			return null;
		}
	}
}