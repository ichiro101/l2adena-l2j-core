/*
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 */

package com.l2jserver.gameserver.model;

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.itemcontainer.Inventory;

/**
 * 
 * @author Luno
 */
public final class L2ArmorSet
{
	private final int _chest;
	private final int _legs;
	private final int _head;
	private final int _gloves;
	private final int _feet;
	private final int _mw_legs;
	private final int _mw_head;
	private final int _mw_gloves;
	private final int _mw_feet;
	
	private final String[] _skills;
	
	private final int _shield;
	private final int _mw_shield;
	private final int _shieldSkillId;
	
	private final int _enchant6Skill;
	
	public L2ArmorSet(int chest, int legs, int head, int gloves, int feet, String[] skills, int shield, int shield_skill_id, int enchant6skill, int mw_legs, int mw_head, int mw_gloves, int mw_feet, int mw_shield)
	{
		_chest = chest;
		_legs = legs;
		_head = head;
		_gloves = gloves;
		_feet = feet;
		_mw_legs = mw_legs;
		_mw_head = mw_head;
		_mw_gloves = mw_gloves;
		_mw_feet = mw_feet;
		_mw_shield = mw_shield;
		_skills = skills;
		
		_shield = shield;
		_shieldSkillId = shield_skill_id;
		
		_enchant6Skill = enchant6skill;
	}
	
	/**
	 * Checks if player have equiped all items from set (not checking shield)
	 * 
	 * @param player
	 *            whose inventory is being checked
	 * @return True if player equips whole set
	 */
	public boolean containAll(L2PcInstance player)
	{
		Inventory inv = player.getInventory();
		
		L2ItemInstance legsItem = inv.getPaperdollItem(Inventory.PAPERDOLL_LEGS);
		L2ItemInstance headItem = inv.getPaperdollItem(Inventory.PAPERDOLL_HEAD);
		L2ItemInstance glovesItem = inv.getPaperdollItem(Inventory.PAPERDOLL_GLOVES);
		L2ItemInstance feetItem = inv.getPaperdollItem(Inventory.PAPERDOLL_FEET);
		
		int legs = 0;
		int head = 0;
		int gloves = 0;
		int feet = 0;
		
		if (legsItem != null)
			legs = legsItem.getItemId();
		if (headItem != null)
			head = headItem.getItemId();
		if (glovesItem != null)
			gloves = glovesItem.getItemId();
		if (feetItem != null)
			feet = feetItem.getItemId();
		
		return containAll(_chest, legs, head, gloves, feet);
	}
	
	public boolean containAll(int chest, int legs, int head, int gloves, int feet)
	{
		if (_chest != 0 && _chest != chest)
			return false;
		if (_legs != 0 && _legs != legs && (_mw_legs == 0 || _mw_legs != legs))
			return false;
		if (_head != 0 && _head != head && (_mw_head == 0 || _mw_head != head))
			return false;
		if (_gloves != 0 && _gloves != gloves && (_mw_gloves == 0 || _mw_gloves != gloves))
			return false;
		if (_feet != 0 && _feet != feet && (_mw_feet == 0 || _mw_feet != feet))
			return false;
		
		return true;
	}
	
	public boolean containItem(int slot, int itemId)
	{
		switch (slot)
		{
			case Inventory.PAPERDOLL_CHEST:
				return _chest == itemId;
			case Inventory.PAPERDOLL_LEGS:
				return (_legs == itemId || _mw_legs == itemId);
			case Inventory.PAPERDOLL_HEAD:
				return (_head == itemId || _mw_head == itemId);
			case Inventory.PAPERDOLL_GLOVES:
				return (_gloves == itemId || _mw_gloves == itemId);
			case Inventory.PAPERDOLL_FEET:
				return (_feet == itemId || _mw_feet == itemId);
			default:
				return false;
		}
	}
	
	public String[] getSkills()
	{
		return _skills;
	}
	
	public boolean containShield(L2PcInstance player)
	{
		Inventory inv = player.getInventory();
		
		L2ItemInstance shieldItem = inv.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		if (shieldItem != null && (shieldItem.getItemId() == _shield || shieldItem.getItemId() == _mw_shield))
			return true;
		
		return false;
	}
	
	public boolean containShield(int shield_id)
	{
		if (_shield == 0)
			return false;
		
		return (_shield == shield_id || _mw_shield == shield_id);
	}
	
	public int getShieldSkillId()
	{
		return _shieldSkillId;
	}
	
	public int getEnchant6skillId()
	{
		return _enchant6Skill;
	}
	
	/**
	 * Checks if all parts of set are enchanted to +6 or more
	 * 
	 * @param player
	 * @return
	 */
	public boolean isEnchanted6(L2PcInstance player)
	{
		// Player don't have full set
		if (!containAll(player))
			return false;
		
		Inventory inv = player.getInventory();
		
		L2ItemInstance chestItem = inv.getPaperdollItem(Inventory.PAPERDOLL_CHEST);
		L2ItemInstance legsItem = inv.getPaperdollItem(Inventory.PAPERDOLL_LEGS);
		L2ItemInstance headItem = inv.getPaperdollItem(Inventory.PAPERDOLL_HEAD);
		L2ItemInstance glovesItem = inv.getPaperdollItem(Inventory.PAPERDOLL_GLOVES);
		L2ItemInstance feetItem = inv.getPaperdollItem(Inventory.PAPERDOLL_FEET);
		
		if (chestItem == null || chestItem.getEnchantLevel() < 6)
			return false;
		if (_legs != 0 && (legsItem == null || legsItem.getEnchantLevel() < 6))
			return false;
		if (_gloves != 0 && (glovesItem == null || glovesItem.getEnchantLevel() < 6))
			return false;
		if (_head != 0 && (headItem == null || headItem.getEnchantLevel() < 6))
			return false;
		if (_feet != 0 && (feetItem == null || feetItem.getEnchantLevel() < 6))
			return false;
		
		return true;
	}
}
