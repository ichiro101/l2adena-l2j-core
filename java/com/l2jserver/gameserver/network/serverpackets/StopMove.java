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
package com.l2jserver.gameserver.network.serverpackets;

import com.l2jserver.gameserver.model.actor.L2Character;

/**
 * format   ddddd
 *
 * sample
 * 0000: 59 1a 95 20 48 44 17 02 00 03 f0 fc ff 98 f1 ff    Y.. HD..........
 * 0010: ff c1 1a 00 00                                     .....
 *
 * @version $Revision: 1.3.2.1.2.3 $ $Date: 2005/03/27 15:29:57 $
 */
public final class StopMove extends L2GameServerPacket
{
	private static final String _S__59_STOPMOVE = "[S] 47 StopMove";
	private int _objectId;
	private int _x;
	private int _y;
	private int _z;
	private int _heading;
	
	
	public StopMove(L2Character cha)
	{
		this(cha.getObjectId(), cha.getX(), cha.getY(), cha.getZ(), cha.getHeading());
	}
	
	/**
	 * @param _characters
	 */
	public StopMove(int objectId, int x, int y, int z, int heading)
	{
		_objectId = objectId;
		_x = x;
		_y = y;
		_z = z;
		_heading = heading;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x47);
		writeD(_objectId);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		writeD(_heading);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__59_STOPMOVE;
	}
}
