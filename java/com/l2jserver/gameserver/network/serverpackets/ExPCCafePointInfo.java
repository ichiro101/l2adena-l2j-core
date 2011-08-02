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
 * Format: ch ddcdc
 * @author  KenM
 */
public class ExPCCafePointInfo extends L2GameServerPacket
{
	private static final String _S__FE_31_EXPCCAFEPOINTINFO = "[S] FE:32 ExPCCafePointInfo";
	private int _unk1, _unk2, _unk3, _unk4, _unk5 = 0;
	
	public ExPCCafePointInfo(int val1, int val2, int val3, int val4, int val5)
	{
		_unk1 = val1;
		_unk2 = val2;
		_unk3 = val3;
		_unk4 = val4;
		_unk5 = val5;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x32);
		writeD(_unk1); // num points
		writeD(_unk2); // points inc display
		writeC(_unk3); // period(0=don't show window,1=acquisition,2=use points)
		writeD(_unk4); // period hours left
		writeC(_unk5); // points inc display color(0=yellow,1=cyan-blue,2=red,all other black)
	}
	
	/**
	 * @see com.l2jserver.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FE_31_EXPCCAFEPOINTINFO;
	}
	
}
