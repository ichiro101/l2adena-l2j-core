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

import java.util.List;

import javolution.util.FastList;

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * Format: (ch)d[S]
 * d: Number of Contacts
 * [
 * 	S: Character Name
 * ]
 * 
 * @author UnAfraid & mrTJO
 */
public class ExShowContactList extends L2GameServerPacket
{
	private static final String _S__FE_D3_EXSHOWCONTACTLIST = "[S] FE:D3 ExShowContactList";
	private final List<String> _contacts;
	
	public ExShowContactList(L2PcInstance player)
	{
		_contacts = new FastList<String>();
		_contacts.addAll(player.getContactList().getAllContacts());
	}
	
	@Override
	protected void writeImpl()
	{
		if (_contacts.size() < 1)
			return;
		
		writeC(0xFE);
		writeH(0xD3);
		writeD(_contacts.size());
		for (String name : _contacts)
		{
			writeS(name);
		}
	}
	
	@Override
	public String getType()
	{
		return _S__FE_D3_EXSHOWCONTACTLIST;
	}
	
}
