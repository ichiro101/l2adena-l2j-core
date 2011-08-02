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
import java.util.logging.Logger;

import javolution.util.FastList;

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.actor.instance.L2MerchantInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * This class ...
 *
 * @version $Revision: 1.4.2.3.2.4 $ $Date: 2005/03/27 15:29:39 $
 */
public class SellList extends L2GameServerPacket
{
	private static final String _S__10_SELLLIST = "[S] 06 SellList";
	private static Logger _log = Logger.getLogger(SellList.class.getName());
	private final L2PcInstance _activeChar;
	private final L2MerchantInstance _lease;
	private long _money;
	private List<L2ItemInstance> _selllist = new FastList<L2ItemInstance>();
	
	public SellList(L2PcInstance player)
	{
		_activeChar = player;
		_lease = null;
		_money = _activeChar.getAdena();
		doLease();
	}
	
	public SellList(L2PcInstance player, L2MerchantInstance lease)
	{
		_activeChar = player;
		_lease = lease;
		_money = _activeChar.getAdena();
		doLease();
	}
	
	private void doLease()
	{
		if (_lease == null)
		{
			for (L2ItemInstance item : _activeChar.getInventory().getItems())
			{
				if (!item.isEquipped() &&														// Not equipped
						item.isSellable() &&													// Item is sellable
						(_activeChar.getPet() == null ||										// Pet not summoned or
								item.getObjectId() != _activeChar.getPet().getControlObjectId()))			// Pet is summoned and not the item that summoned the pet
				{
					_selllist.add(item);
					if (Config.DEBUG)
						_log.fine("item added to selllist: " + item.getItem().getName());
				}
			}
		}
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x06);
		writeQ(_money);
		writeD(_lease == null ? 0x00 : 1000000 + _lease.getTemplate().npcId);
		writeH(_selllist.size());
		
		for (L2ItemInstance item : _selllist)
		{
			writeH(item.getItem().getType1());
			writeD(item.getObjectId());
			writeD(item.getItemId());
			writeQ(item.getCount());
			writeH(item.getItem().getType2());
			writeH(0x00);
			writeD(item.getItem().getBodyPart());
			writeH(item.getEnchantLevel());
			writeH(0x00);
			writeH(item.getCustomType2());
			writeQ(item.getItem().getReferencePrice()/2);
			
			// T1
			writeH(item.getAttackElementType());
			writeH(item.getAttackElementPower());
			for (byte i = 0; i < 6; i++)
				writeH(item.getElementDefAttr(i));
			
			writeH(0x00); // Enchant effect 1
			writeH(0x00); // Enchant effect 2
			writeH(0x00); // Enchant effect 3
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__10_SELLLIST;
	}
}
