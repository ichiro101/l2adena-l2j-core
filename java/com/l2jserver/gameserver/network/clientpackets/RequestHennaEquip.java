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

import com.l2jserver.Config;
import com.l2jserver.gameserver.datatables.HennaTable;
import com.l2jserver.gameserver.datatables.HennaTreeTable;
import com.l2jserver.gameserver.model.L2HennaInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.InventoryUpdate;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.templates.item.L2Henna;
import com.l2jserver.gameserver.util.Util;

/**
 * This class ...
 *
 * @version $Revision$ $Date$
 */
public final class RequestHennaEquip extends L2GameClientPacket
{
	private static final String _C__6F_REQUESTHENNAEQUIP = "[C] 6F RequestHennaEquip";
	private int _symbolId;
	
	@Override
	protected void readImpl()
	{
		_symbolId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (!getClient().getFloodProtectors().getTransaction().tryPerformAction("HennaEquip"))
			return;
		
		L2Henna template = HennaTable.getInstance().getTemplate(_symbolId);
		if (template == null)
			return;
		
		L2HennaInstance henna = new L2HennaInstance(template);
		long _count = 0;
		
		/**
		 *  Prevents henna drawing exploit:
		 * 1) talk to L2SymbolMakerInstance
		 * 2) RequestHennaList
		 * 3) Don't close the window and go to a GrandMaster and change your subclass
		 * 4) Get SymbolMaker range again and press draw
		 * You could draw any kind of henna just having the required subclass...
		 */
		boolean cheater = true;
		for (L2HennaInstance h : HennaTreeTable.getInstance().getAvailableHenna(activeChar.getClassId()))
		{
			if (h.getSymbolId() == henna.getSymbolId())
			{
				cheater = false;
				break;
			}
		}
		try
		{
			_count = activeChar.getInventory().getItemByItemId(henna.getItemIdDye()).getCount();
		}
		catch(Exception e){}
		
		if (activeChar.getHennaEmptySlots() == 0)
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SYMBOLS_FULL));
			return;
		}
		
		if (!cheater && (_count >= henna.getAmountDyeRequire()) && (activeChar.getAdena() >= henna.getPrice()) && activeChar.addHenna(henna))
		{
			activeChar.destroyItemByItemId("Henna", henna.getItemIdDye(), henna.getAmountDyeRequire(), activeChar, true);
			
			activeChar.getInventory().reduceAdena("Henna", henna.getPrice(), activeChar, activeChar.getLastFolkNPC());
			
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(activeChar.getInventory().getAdenaInstance());
			activeChar.sendPacket(iu);
			
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SYMBOL_ADDED));
		}
		else
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_DRAW_SYMBOL));
			if ((!activeChar.isGM()) && (cheater))
				Util.handleIllegalPlayerAction(activeChar,"Exploit attempt: Character "+activeChar.getName()+" of account "+activeChar.getAccountName()+" tryed to add a forbidden henna.",Config.DEFAULT_PUNISH);
		}
	}
	
	@Override
	public String getType()
	{
		return _C__6F_REQUESTHENNAEQUIP;
	}
}
