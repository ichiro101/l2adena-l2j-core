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

import com.l2jserver.gameserver.network.serverpackets.AcquireSkillList.SkillType;

import javolution.util.FastList;

/**
 * Sample:
 * <code>
 * a4
 * 4d000000 01000000 98030000 			Attack Aura, level 1, sp cost
 * 01000000 							number of requirements
 * 05000000 47040000 0100000 000000000	   1 x spellbook advanced ATTACK                                                 .
 * </code>
 * <br>
 * format   dddd d (ddQd)
 *
 * @version  1.5
 */
public class AcquireSkillInfo extends L2GameServerPacket
{
	private static final String _S__91_ACQUIRESKILLINFO = "[S] 91 AcquireSkillInfo";
	private FastList<Req> _reqs;
	private int _id, _level, _spCost;
	private SkillType _type;
	
	/**
	 * Private class containing learning skill requisites.
	 */
	private static class Req
	{
		public int itemId;
		public int count;
		public int type;
		public int unk;
		
		public Req(int pType, int pItemId, int pCount, int pUnk)
		{
			itemId = pItemId;
			type = pType;
			count = pCount;
			unk = pUnk;
		}
	}
	
	public AcquireSkillInfo(int id, int level, int spCost, SkillType type)
	{
		_reqs = new FastList<Req>();
		_id = id;
		_level = level;
		_spCost = spCost;
		_type = type;
	}
	
	public void addRequirement(int type, int id, int count, int unk)
	{
		_reqs.add(new Req(type, id, count, unk));
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x91);
		writeD(_id);
		writeD(_level);
		writeD(_spCost);
		writeD(_type.ordinal());
		
		writeD(_reqs.size());
		
		for (Req temp : _reqs)
		{
			writeD(temp.type);
			writeD(temp.itemId);
			writeQ(temp.count);
			writeD(temp.unk);
		}
	}
	
	@Override
	public String getType()
	{
		return _S__91_ACQUIRESKILLINFO;
	}
}