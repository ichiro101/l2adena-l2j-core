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
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Cipher;

import com.l2jserver.util.network.BaseSendablePacket;

/**
 * @author -Wooden-
 *
 */
public class BlowFishKey extends BaseSendablePacket
{
	private static Logger _log = Logger.getLogger(BlowFishKey.class.getName());
	/**
	 * @param blowfishKey
	 * @param publicKey
	 */
	public BlowFishKey(byte[] blowfishKey, RSAPublicKey publicKey)
	{
		writeC(0x00);
		byte[] encrypted =null;
		try
		{
			Cipher rsaCipher = Cipher.getInstance("RSA/ECB/nopadding");
			rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
			encrypted = rsaCipher.doFinal(blowfishKey);
		}
		catch(GeneralSecurityException e)
		{
			_log.log(Level.SEVERE, "Error While encrypting blowfish key for transmision (Crypt error): " + e.getMessage(), e);
		}
		writeD(encrypted.length);
		writeB(encrypted);
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
