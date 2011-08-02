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
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.ExAutoSoulShot;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * This class ...
 * 
 * @version $Revision: 1.0.0.0 $ $Date: 2005/07/11 15:29:30 $
 */
public final class RequestAutoSoulShot extends L2GameClientPacket
{
	private static final String _C__D0_0D_REQUESTAUTOSOULSHOT = "[C] D0:0D RequestAutoSoulShot";
	private static Logger _log = Logger.getLogger(RequestAutoSoulShot.class.getName());
	
	// format cd
	private int _itemId;
	private int _type; // 1 = on : 0 = off;
	
	@Override
	protected void readImpl()
	{
		_itemId = readD();
		_type = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.getPrivateStoreType() == 0 && activeChar.getActiveRequester() == null && !activeChar.isDead())
		{
			if (Config.DEBUG)
				_log.fine("AutoSoulShot:" + _itemId);
			
			L2ItemInstance item = activeChar.getInventory().getItemByItemId(_itemId);
			if (item == null)
				return;
			
			if (_type == 1)
			{
				if (!activeChar.getInventory().canManipulateWithItemId(item.getItemId()))
				{
					activeChar.sendMessage("Cannot use this item.");
					return;
				}
				
				// Fishingshots are not automatic on retail
				if (_itemId < 6535 || _itemId > 6540)
				{
					// Attempt to charge first shot on activation
					if (_itemId == 6645 || _itemId == 6646 || _itemId == 6647 || _itemId == 20332 || _itemId == 20333 || _itemId == 20334)
					{
						if (activeChar.getPet() != null)
						{
							if (item.getEtcItem().getHandlerName().equals("BeastSoulShot"))
							{
								if (activeChar.getPet().getSoulShotsPerHit() > item.getCount())
								{
									activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_SOULSHOTS_FOR_PET));
									return;
								}
							}
							else
							{
								if (activeChar.getPet().getSpiritShotsPerHit() > item.getCount())
								{
									activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_SOULSHOTS_FOR_PET));
									return;
								}
							}
							activeChar.addAutoSoulShot(_itemId);
							activeChar.sendPacket(new ExAutoSoulShot(_itemId, _type));
							
							// start the auto soulshot use
							SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.USE_OF_S1_WILL_BE_AUTO);
							sm.addItemName(item);// Update Message by rocknow
							activeChar.sendPacket(sm);
							
							activeChar.rechargeAutoSoulShot(true, true, true);
						}
						else
							activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NO_SERVITOR_CANNOT_AUTOMATE_USE));
					}
					else
					{
						if (activeChar.getActiveWeaponItem() != activeChar.getFistsWeaponItem()
								&& item.getItem().getCrystalType() == activeChar.getActiveWeaponItem().getItemGradeSPlus())
						{
							activeChar.addAutoSoulShot(_itemId);
							activeChar.sendPacket(new ExAutoSoulShot(_itemId, _type));
						}
						else
						{
							if ((_itemId >= 2509 && _itemId <= 2514) || (_itemId >= 3947 && _itemId <= 3952) || _itemId == 5790 || (_itemId >= 22072 && _itemId <= 22081))
								activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SPIRITSHOTS_GRADE_MISMATCH));
							else
								activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SOULSHOTS_GRADE_MISMATCH));
							
							activeChar.addAutoSoulShot(_itemId);
							activeChar.sendPacket(new ExAutoSoulShot(_itemId, _type));
						}
						
						// start the auto soulshot use
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.USE_OF_S1_WILL_BE_AUTO);
						sm.addItemName(item);// Update Message by rocknow
						activeChar.sendPacket(sm);
						
						activeChar.rechargeAutoSoulShot(true, true, false);
					}
				}
			}
			else if (_type == 0)
			{
				activeChar.removeAutoSoulShot(_itemId);
				activeChar.sendPacket(new ExAutoSoulShot(_itemId, _type));
				
				// cancel the auto soulshot use
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.AUTO_USE_OF_S1_CANCELLED);
				sm.addItemName(item);// Update Message by rocknow
				activeChar.sendPacket(sm);
			}
		}
	}
	
	@Override
	public String getType()
	{
		return _C__D0_0D_REQUESTAUTOSOULSHOT;
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}