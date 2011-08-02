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

import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.network.L2GameClient;
import com.l2jserver.gameserver.network.serverpackets.ExRpItemLink;

/**
 * @author  KenM
 */
public class RequestExRqItemLink extends L2GameClientPacket
{
	private static String _C__D0_1E_REQUESTEXRQITEMLINK = "[C] D0:1E RequestExRqItemLink";
	private int _objectId;
	private Logger _log = Logger.getLogger(RequestExRqItemLink.class.getName());
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2GameClient client = this.getClient();
		if (client != null)
		{
			L2Object object = L2World.getInstance().findObject(_objectId);
			if (object instanceof L2ItemInstance)
			{
				L2ItemInstance item = (L2ItemInstance)object;
				if (item.isPublished())
				{
					client.sendPacket(new ExRpItemLink(item));
				}
				else
				{
					if (Config.DEBUG)
						_log.info(getClient()+" requested item link for item which wasnt published! ID:"+_objectId);
				}
			}
		}
	}
	
	@Override
	public String getType()
	{
		return _C__D0_1E_REQUESTEXRQITEMLINK;
	}
}
