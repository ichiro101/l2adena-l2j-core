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
 * @author JIV
 */
public class ExSetPartyLooting extends L2GameServerPacket
{
	private static final String TYPE = "[S] FE:C0 ExSetPartyLooting";
	
	private final int _result;
	private final byte _mode;
	
	public ExSetPartyLooting(int result, byte mode)
	{
		_result = result;
		_mode = mode;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0xC0);
		writeD(_result);
		writeD(_mode);
	}
	
	@Override
	public String getType()
	{
		return TYPE;
	}
}
