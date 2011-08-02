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
package com.l2jserver.gameserver.handler;

import java.util.logging.Logger;

import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.templates.skills.L2SkillType;


/**
 * an IItemHandler implementation has to be stateless
 *
 * @version $Revision: 1.2.2.2.2.3 $ $Date: 2005/04/03 15:55:06 $
 */

public interface ISkillHandler
{
	public static Logger _log = Logger.getLogger(ISkillHandler.class.getName());
	/**
	 * this is the worker method that is called when using an item.
	 * @param activeChar
	 * @param item
	 * @param target
	 * @return count reduction after usage
	 */
	public void useSkill(L2Character activeChar, L2Skill skill, L2Object[] targets);
	
	/**
	 * this method is called at initialization to register all the item ids automatically
	 * @return all known itemIds
	 */
	public L2SkillType[] getSkillIds();
}
