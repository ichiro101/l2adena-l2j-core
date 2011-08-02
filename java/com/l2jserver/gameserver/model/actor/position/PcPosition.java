/* This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package com.l2jserver.gameserver.model.actor.position;

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 *
 * @author  Erb
 */
public class PcPosition extends CharPosition
{
	// =========================================================
	// Constructor
	public PcPosition(L2PcInstance activeObject)
	{
		super(activeObject);
	}
	
	@Override
	public L2PcInstance getActiveObject()
	{
		return ((L2PcInstance)super.getActiveObject());
	}
	
	@Override
	protected void badCoords()
	{
		getActiveObject().teleToLocation(0,0,0, false);
		getActiveObject().sendMessage("Error with your coords, Please ask a GM for help!");
	}
}
