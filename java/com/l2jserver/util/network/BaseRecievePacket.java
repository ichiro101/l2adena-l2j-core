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
package com.l2jserver.util.network;

/**
 * This class ...
 *
 * @version $Revision: 1.2.4.1 $ $Date: 2005/03/27 15:30:12 $
 */
public abstract class BaseRecievePacket
{
	private byte[] _decrypt;
	private int _off;
	
	public BaseRecievePacket(byte[] decrypt)
	{
		_decrypt = decrypt;
		_off = 1;		// skip packet type id
	}
	
	public int readD()
	{
		int result = _decrypt[_off++] & 0xff;
		result |= _decrypt[_off++] << 8 & 0xff00;
		result |= _decrypt[_off++] << 0x10 & 0xff0000;
		result |= _decrypt[_off++] << 0x18 & 0xff000000;
		return result;
	}
	
	public int readC()
	{
		int result = _decrypt[_off++] &0xff;
		return result;
	}
	
	public int readH()
	{
		int result = _decrypt[_off++] &0xff;
		result |= _decrypt[_off++] << 8 &0xff00;
		return result;
	}
	
	public double readF()
	{
		long result = _decrypt[_off++] & 0xff;
		result |= (_decrypt[_off++] & 0xffl) << 8l;
		result |= (_decrypt[_off++] & 0xffl) << 16l;
		result |= (_decrypt[_off++] & 0xffl) << 24l;
		result |= (_decrypt[_off++] & 0xffl) << 32l;
		result |= (_decrypt[_off++] & 0xffl) << 40l;
		result |= (_decrypt[_off++] & 0xffl) << 48l;
		result |= (_decrypt[_off++] & 0xffl) << 56l;
		return Double.longBitsToDouble(result);
	}
	
	public String readS()
	{
		String result = null;
		try
		{
			result = new String(_decrypt,_off,_decrypt.length-_off, "UTF-16LE");
			result = result.substring(0, result.indexOf(0x00));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		_off += result.length()*2 + 2;
		return result;
	}
	
	public final byte[] readB(int length)
	{
		byte[] result = new byte[length];
		for(int i = 0; i < length; i++)
		{
			result[i]=_decrypt[_off+i];
		}
		_off += length;
		return result;
	}
	
	public long readQ()
	{
		long result = _decrypt[_off++] & 0xff;
		result |= (_decrypt[_off++] & 0xffl) << 8l;
		result |= (_decrypt[_off++] & 0xffl) << 16l;
		result |= (_decrypt[_off++] & 0xffl) << 24l;
		result |= (_decrypt[_off++] & 0xffl) << 32l;
		result |= (_decrypt[_off++] & 0xffl) << 40l;
		result |= (_decrypt[_off++] & 0xffl) << 48l;
		result |= (_decrypt[_off++] & 0xffl) << 56l;
		return result;
	}
}
