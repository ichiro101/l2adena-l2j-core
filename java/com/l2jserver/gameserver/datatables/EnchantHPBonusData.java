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
package com.l2jserver.gameserver.datatables;

import gnu.trove.TIntObjectHashMap;

import java.io.File;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.skills.Stats;
import com.l2jserver.gameserver.skills.funcs.FuncTemplate;
import com.l2jserver.gameserver.skills.funcs.LambdaConst;
import com.l2jserver.gameserver.templates.item.L2Item;

/**
 *
 * @author  MrPoke
 */
public class EnchantHPBonusData
{
	protected static final Logger _log = Logger.getLogger(EnchantHPBonusData.class.getName());
	
	private final TIntObjectHashMap<Integer[]> _armorHPBonus = new TIntObjectHashMap<Integer[]>();
	private static final float fullArmorModifier = 1.5f;
	
	public static final EnchantHPBonusData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private EnchantHPBonusData()
	{
		load();
	}
	
	public void reload()
	{
		load();
	}
	
	private void load()
	{
		_armorHPBonus.clear();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setIgnoringComments(true);
		File file = new File(Config.DATAPACK_ROOT, "data/enchantHPBonus.xml");
		Document doc = null;
		
		if (file.exists())
		{
			try
			{
				doc = factory.newDocumentBuilder().parse(file);
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "Could not parse enchantHPBonus.xml file: " + e.getMessage(), e);
			}
			
			for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
			{
				if ("list".equalsIgnoreCase(n.getNodeName()))
				{
					for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
					{
						if ("enchantHP".equalsIgnoreCase(d.getNodeName()))
						{
							NamedNodeMap attrs = d.getAttributes();
							Node att;
							Integer grade;
							
							att = attrs.getNamedItem("grade");
							if (att == null)
							{
								_log.severe("[EnchantHPBonusData] Missing grade, skipping");
								continue;
							}
							grade = Integer.parseInt(att.getNodeValue());
							
							att = attrs.getNamedItem("values");
							if (att == null)
							{
								_log.severe("[EnchantHPBonusData] Missing bonus id: " + grade + ", skipping");
								continue;
							}
							StringTokenizer st = new StringTokenizer(att.getNodeValue(), ",");
							int tokenCount = st.countTokens();
							Integer[] bonus = new Integer[tokenCount];
							for (int i = 0; i < tokenCount; i++)
							{
								Integer value = Integer.decode(st.nextToken().trim());
								if (value == null)
								{
									_log.severe("[EnchantHPBonusData] Bad Hp value!! grade: " + grade + " token: " + i);
									value = 0;
								}
								bonus[i] = value;
							}
							_armorHPBonus.put(grade, bonus);
						}
					}
				}
			}
			if (_armorHPBonus.isEmpty())
				return;
			
			Collection<Integer> itemIds = ItemTable.getInstance().getAllArmorsId();
			int count = 0;
			
			for (Integer itemId : itemIds)
			{
				L2Item item = ItemTable.getInstance().getTemplate(itemId);
				if (item != null && item.getCrystalType() != L2Item.CRYSTAL_NONE)
				{
					switch (item.getBodyPart())
					{
						case L2Item.SLOT_CHEST:
						case L2Item.SLOT_FEET:
						case L2Item.SLOT_GLOVES:
						case L2Item.SLOT_HEAD:
						case L2Item.SLOT_LEGS:
						case L2Item.SLOT_BACK:
						case L2Item.SLOT_FULL_ARMOR:
						case L2Item.SLOT_UNDERWEAR:
						case L2Item.SLOT_L_HAND:
							count++;
							FuncTemplate ft = new FuncTemplate(null, null, "EnchantHp", Stats.MAX_HP, 0x60, new LambdaConst(0));
							item.attach(ft);
							break;
					}
				}
			}
			
			// shields in the weapons table
			itemIds = ItemTable.getInstance().getAllWeaponsId();
			for (Integer itemId : itemIds)
			{
				L2Item item = ItemTable.getInstance().getTemplate(itemId);
				if (item != null && item.getCrystalType() != L2Item.CRYSTAL_NONE)
				{
					switch (item.getBodyPart())
					{
						case L2Item.SLOT_L_HAND:
							count++;
							FuncTemplate ft = new FuncTemplate(null, null, "EnchantHp", Stats.MAX_HP, 0x60, new LambdaConst(0));
							item.attach(ft);
							break;
					}
				}
			}
			_log.info("Enchant HP Bonus registered for " + count + " items!");
		}
	}
	
	public final int getHPBonus(L2ItemInstance item)
	{
		final Integer[] values = _armorHPBonus.get(item.getItem().getItemGradeSPlus());
		
		if (values == null || values.length == 0)
			return 0;
		
		if (item.getItem().getBodyPart() == L2Item.SLOT_FULL_ARMOR)
			return (int) (values[Math.min(item.getOlyEnchantLevel(), values.length) - 1] * fullArmorModifier);
		else
			return values[Math.min(item.getOlyEnchantLevel(), values.length) - 1];
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final EnchantHPBonusData _instance = new EnchantHPBonusData();
	}
}
