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

package com.l2jserver.gameserver.network.clientpackets;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;


/**
 * Lets drink to code!
 * @author zabbix
 */
public final class RequestLinkHtml extends L2GameClientPacket
{
	private static Logger _log = Logger.getLogger(RequestLinkHtml.class.getName());
	private static final String _C__22_REQUESTLINKHTML = "[C] 22 RequestLinkHtml";
	private String _link;
	
	@Override
	protected void readImpl()
	{
		_link = readS();
	}
	
	@Override
	public void runImpl()
	{
		L2PcInstance actor = getClient().getActiveChar();
		if(actor == null)
			return;
		
		if(_link.contains("..") || !_link.contains(".htm"))
		{
			_log.warning("[RequestLinkHtml] hack? link contains prohibited characters: '"+_link+"', skipped");
			return;
		}
		try
		{
			String filename = "data/html/"+_link;
			NpcHtmlMessage msg = new NpcHtmlMessage(0);
			msg.disableValidation();
			msg.setFile(actor.getHtmlPrefix(), filename);
			sendPacket(msg);
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Bad RequestLinkHtml: ", e);
		}
	}
	
	@Override
	public String getType()
	{
		return _C__22_REQUESTLINKHTML;
	}
}
