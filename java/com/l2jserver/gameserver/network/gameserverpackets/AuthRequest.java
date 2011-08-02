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
package com.l2jserver.gameserver.network.gameserverpackets;

import java.io.IOException;

import com.l2jserver.util.network.BaseSendablePacket;

public class AuthRequest extends BaseSendablePacket
{
	/**
	 * Format: cccSddb
	 * c desired ID
	 * c accept alternative ID
	 * c reserve Host
	 * s ExternalHostName
	 * s InetranlHostName
	 * d max players
	 * d hexid size
	 * b hexid
	 *
	 * @param id
	 * @param acceptAlternate
	 * @param hexid
	 * @param reserveHost
	 * @param maxplayer
	 */
	public AuthRequest(int id, boolean acceptAlternate, byte[] hexid, int port, boolean reserveHost, int maxplayer, String[] subnets, String[] hosts)
	{
		writeC(0x01);
		writeC(id);
		writeC(acceptAlternate? 0x01 : 0x00);
		writeC(reserveHost? 0x01 : 0x00);
		writeH(port);
		writeD(maxplayer);
		writeD(hexid.length);
		writeB(hexid);
		writeD(subnets.length);
		for (int i = 0; i < subnets.length; i++)
		{
			writeS(subnets[i]);
			writeS(hosts[i]);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.gameserverpackets.GameServerBasePacket#getContent()
	 */
	@Override
	public byte[] getContent() throws IOException
	{
		return getBytes();
	}
	
}