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

import com.l2jserver.gameserver.datatables.CharNameTable;
import com.l2jserver.gameserver.model.BlockList;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

public final class RequestBlock extends L2GameClientPacket
{
	private static final String _C__A9_REQUESTBLOCK = "[C] A9 RequestBlock";
	private static Logger _log = Logger.getLogger(L2PcInstance.class.getName());
	
	private final static int BLOCK = 0;
	private final static int UNBLOCK = 1;
	private final static int BLOCKLIST = 2;
	private final static int ALLBLOCK = 3;
	private final static int ALLUNBLOCK = 4;
	
	private String _name;
	private Integer _type;
	
	@Override
	protected void readImpl()
	{
		_type = readD(); //0x00 - block, 0x01 - unblock, 0x03 - allblock, 0x04 - allunblock
		
		if (_type == BLOCK || _type == UNBLOCK)
		{
			_name = readS();
		}
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		final int targetId = CharNameTable.getInstance().getIdByName(_name);
		final int targetAL = CharNameTable.getInstance().getAccessLevelById(targetId);
		
		if (activeChar == null)
			return;
		
		switch (_type)
		{
			case BLOCK:
			case UNBLOCK:
				// can't use block/unblock for locating invisible characters
				if (targetId <= 0)
				{
					// Incorrect player name.
					activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_REGISTER_TO_IGNORE_LIST));
					return;
				}
				
				if (targetAL > 0)
				{
					// Cannot block a GM character.
					activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_MAY_NOT_IMPOSE_A_BLOCK_ON_GM));
					return;
				}
				
				if (activeChar.getObjectId() == targetId)
					return;
				
				if (_type == BLOCK)
					BlockList.addToBlockList(activeChar, targetId);
				else
					BlockList.removeFromBlockList(activeChar, targetId);
				break;
			case BLOCKLIST:
				BlockList.sendListToOwner(activeChar);
				break;
			case ALLBLOCK:
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.MESSAGE_REFUSAL_MODE));//Update by rocknow
				BlockList.setBlockAll(activeChar, true);
				break;
			case ALLUNBLOCK:
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.MESSAGE_ACCEPTANCE_MODE));//Update by rocknow
				BlockList.setBlockAll(activeChar, false);
				break;
			default:
				_log.info("Unknown 0xA9 block type: " + _type);
		}
	}
	
	@Override
	public String getType()
	{
		return _C__A9_REQUESTBLOCK;
	}
}
