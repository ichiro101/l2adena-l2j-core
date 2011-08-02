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

/**
 * format   dddddd
 *
 */
public class Earthquake extends L2GameServerPacket
{
	private static final String _S__C4_EARTHQUAKE = "[S] d3 Earthquake";
	private int _x;
	private int _y;
	private int _z;
	private int _intensity;
	private int _duration;
	
	/**
	 * @param
	 */
	public Earthquake(int x, int y, int z, int intensity, int duration)
	{
		_x = x;
		_y = y;
		_z = z;
		_intensity = intensity;
		_duration = duration;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xd3);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		writeD(_intensity);
		writeD(_duration);
		writeD(0x00);       // Unknown
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__C4_EARTHQUAKE;
	}
}
