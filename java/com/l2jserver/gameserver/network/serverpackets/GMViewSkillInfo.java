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

import com.l2jserver.gameserver.datatables.SkillTable;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

public class GMViewSkillInfo extends L2GameServerPacket
{
	private static final String _S__91_GMViewSkillInfo = "[S] 97 GMViewSkillInfo";
	private L2PcInstance _activeChar;
	private L2Skill[] _skills;
	
	public GMViewSkillInfo (L2PcInstance cha)
	{
		_activeChar = cha;
		_skills = _activeChar.getAllSkills();
		if (_skills.length == 0)
			_skills = new L2Skill[0];
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x97);
		writeS(_activeChar.getName());
		writeD(_skills.length);
		
		boolean isDisabled = false;
		if (_activeChar.getClan() != null)
			isDisabled = _activeChar.getClan().getReputationScore() < 0;
		
		for (L2Skill skill: _skills)
		{
			writeD(skill.isPassive() ? 1 : 0);
			writeD(skill.getLevel());
			writeD(skill.getId());
			writeC(isDisabled && skill.isClanSkill() ? 1 : 0);
			writeC(SkillTable.getInstance().isEnchantable(skill.getId()) ? 1 : 0);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__91_GMViewSkillInfo;
	}
}