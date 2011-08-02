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
package com.l2jserver.gameserver.model;

import static com.l2jserver.gameserver.model.itemcontainer.PcInventory.ADENA_ID;
import static com.l2jserver.gameserver.model.itemcontainer.PcInventory.MAX_ADENA;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.GeoData;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.instancemanager.ItemsOnGroundManager;
import com.l2jserver.gameserver.instancemanager.MercTicketManager;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.knownlist.NullKnownList;
import com.l2jserver.gameserver.model.quest.QuestState;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.DropItem;
import com.l2jserver.gameserver.network.serverpackets.GetItem;
import com.l2jserver.gameserver.network.serverpackets.InventoryUpdate;
import com.l2jserver.gameserver.network.serverpackets.SpawnItem;
import com.l2jserver.gameserver.network.serverpackets.StatusUpdate;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.skills.funcs.Func;
import com.l2jserver.gameserver.templates.item.L2Armor;
import com.l2jserver.gameserver.templates.item.L2EtcItem;
import com.l2jserver.gameserver.templates.item.L2EtcItemType;
import com.l2jserver.gameserver.templates.item.L2Item;
import com.l2jserver.gameserver.templates.item.L2ItemType;
import com.l2jserver.gameserver.templates.item.L2Weapon;
import com.l2jserver.gameserver.util.GMAudit;



/**
 * This class manages items.
 *
 * @version $Revision: 1.4.2.1.2.11 $ $Date: 2005/03/31 16:07:50 $
 */
public final class L2ItemInstance extends L2Object
{
	protected static final Logger _log = Logger.getLogger(L2ItemInstance.class.getName());
	private static final Logger _logItems = Logger.getLogger("item");
	
	/** Enumeration of locations for item */
	public static enum ItemLocation 
	{
		VOID,
		INVENTORY,
		PAPERDOLL,
		WAREHOUSE,
		CLANWH,
		PET,
		PET_EQUIP,
		LEASE,
		REFUND,
		MAIL,
		FREIGHT
	}
	
	/** ID of the owner */
	private int _ownerId;
	
	/** ID of who dropped the item last, used for knownlist */
	private int _dropperObjectId = 0;
	
	/** Quantity of the item */
	private long _count;
	/** Initial Quantity of the item */
	private long _initCount;
	/** Remaining time (in miliseconds) */
	private long _time;
	/** Quantity of the item can decrease */
	private boolean _decrease = false;
	
	/** ID of the item */
	private final int _itemId;
	
	/** Object L2Item associated to the item */
	private final L2Item _item;
	
	/** Location of the item : Inventory, PaperDoll, WareHouse */
	private ItemLocation _loc;
	
	/** Slot where item is stored : Paperdoll slot, inventory order ...*/
	private int _locData;
	
	/** Level of enchantment of the item */
	private int _enchantLevel;
	
	/** Wear Item */
	private boolean _wear;
	
	/** Augmented Item */
	private L2Augmentation _augmentation=null;
	
	/** Shadow item */
	private int _mana=-1;
	private boolean _consumingMana = false;
	private static final int MANA_CONSUMPTION_RATE = 60000;
	
	/** Custom item types (used loto, race tickets) */
	private int _type1;
	private int _type2;
	
	private long _dropTime;
	
	private boolean _published = false;
	
	public static final int CHARGED_NONE				=	0;
	public static final int CHARGED_SOULSHOT				=	1;
	public static final int CHARGED_SPIRITSHOT			=	1;
	//public static final int CHARGED_BLESSED_SOULSHOT		=	2; // It's a really exists? ;-)
	public static final int CHARGED_BLESSED_SPIRITSHOT		=	2;
	
	/** Item charged with SoulShot (type of SoulShot) */
	private int				_chargedSoulshot			=	CHARGED_NONE;
	/** Item charged with SpiritShot (type of SpiritShot) */
	private int				_chargedSpiritshot		=	CHARGED_NONE;
	
	private boolean _chargedFishtshot =	false;
	
	private boolean _protected;
	
	public static final int UNCHANGED = 0;
	public static final int ADDED = 1;
	public static final int REMOVED = 3;
	public static final int MODIFIED = 2;
	private int _lastChange = 2;	//1 ??, 2 modified, 3 removed
	private boolean _existsInDb; // if a record exists in DB.
	private boolean _storedInDb; // if DB data is up-to-date.
	
	private final ReentrantLock _dbLock = new ReentrantLock();
	
	private Elementals[] _elementals = null;
	
	private ScheduledFuture<?> itemLootShedule = null;
	public ScheduledFuture<?> _lifeTimeTask;
	
	private final DropProtection _dropProtection = new DropProtection();
	/**
	 * Constructor of the L2ItemInstance from the objectId and the itemId.
	 * @param objectId : int designating the ID of the object in the world
	 * @param itemId : int designating the ID of the item
	 */
	public L2ItemInstance(int objectId, int itemId)
	{
		super(objectId);
		setInstanceType(InstanceType.L2ItemInstance);
		_itemId = itemId;
		_item = ItemTable.getInstance().getTemplate(itemId);
		if (_itemId == 0 || _item == null)
			throw new IllegalArgumentException();
		super.setName(_item.getName());
		setCount(1);
		_loc = ItemLocation.VOID;
		_type1 = 0;
		_type2 = 0;
		_dropTime = 0;
		_mana = _item.getDuration();
		_time = _item.getTime() == -1 ? -1 : System.currentTimeMillis() + ((long)_item.getTime()*60*1000);
		scheduleLifeTimeTask();
	}
	
	/**
	 * Constructor of the L2ItemInstance from the objetId and the description of the item given by the L2Item.
	 * @param objectId : int designating the ID of the object in the world
	 * @param item : L2Item containing informations of the item
	 */
	public L2ItemInstance(int objectId, L2Item item)
	{
		super(objectId);
		setInstanceType(InstanceType.L2ItemInstance);
		_itemId = item.getItemId();
		_item = item;
		if (_itemId == 0)
			throw new IllegalArgumentException();
		super.setName(_item.getName());
		setCount(1);
		_loc = ItemLocation.VOID;
		_mana = _item.getDuration();
		_time = _item.getTime() == -1 ? -1 : System.currentTimeMillis() + ((long)_item.getTime()*60*1000);
		scheduleLifeTimeTask();
	}
	
	@Override
	public void initKnownList()
	{
		setKnownList(new NullKnownList(this));
	}
	
	/**
	 * Remove a L2ItemInstance from the world and send server->client GetItem packets.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a Server->Client Packet GetItem to player that pick up and its _knowPlayers member </li>
	 * <li>Remove the L2Object from the world</li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T REMOVE the object from _allObjects of L2World </B></FONT><BR><BR>
	 *
	 * <B><U> Assert </U> :</B><BR><BR>
	 * <li> this instanceof L2ItemInstance</li>
	 * <li> _worldRegion != null <I>(L2Object is visible at the beginning)</I></li><BR><BR>
	 *
	 * <B><U> Example of use </U> :</B><BR><BR>
	 * <li> Do Pickup Item : PCInstance and Pet</li><BR><BR>
	 *
	 * @param player Player that pick up the item
	 *
	 */
	public final void pickupMe(L2Character player)
	{
		assert getPosition().getWorldRegion() != null;
		
		L2WorldRegion oldregion = getPosition().getWorldRegion();
		
		// Create a server->client GetItem packet to pick up the L2ItemInstance
		GetItem gi = new GetItem(this, player.getObjectId());
		player.broadcastPacket(gi);
		
		synchronized (this)
		{
			setIsVisible(false);
			getPosition().setWorldRegion(null);
		}
		
		// if this item is a mercenary ticket, remove the spawns!
		int itemId = getItemId();
		
		if (MercTicketManager.getInstance().getTicketCastleId(itemId) > 0)
		{
			MercTicketManager.getInstance().removeTicket(this);
			ItemsOnGroundManager.getInstance().removeObject(this);
		}
		
		if (!Config.DISABLE_TUTORIAL && (itemId == 57 || itemId == 6353))
		{
			L2PcInstance actor = player.getActingPlayer();
			if (actor != null)
			{
				QuestState qs = actor.getQuestState("255_Tutorial");
				if (qs != null)
					qs.getQuest().notifyEvent("CE"+itemId+"",null, actor);
			}
		}
		// outside of synchronized to avoid deadlocks
		// Remove the L2ItemInstance from the world
		L2World.getInstance().removeVisibleObject(this, oldregion);
	}
	
	/**
	 * Sets the ownerID of the item
	 * @param process : String Identifier of process triggering this action
	 * @param owner_id : int designating the ID of the owner
	 * @param creator : L2PcInstance Player requesting the item creation
	 * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
	 */
	public void setOwnerId(String process, int owner_id, L2PcInstance creator, Object reference)
	{
		setOwnerId(owner_id);
		
		if (Config.LOG_ITEMS)
		{
			if (!Config.LOG_ITEMS_SMALL_LOG || (Config.LOG_ITEMS_SMALL_LOG && (getItem().isEquipable() || getItem().getItemId() == ADENA_ID)))
			{
				LogRecord record = new LogRecord(Level.INFO, "SETOWNER:" + process);
				record.setLoggerName("item");
				record.setParameters(new Object[]{this, creator, reference});
				_logItems.log(record);
			}
		}
		
		if (creator != null)
		{
			if (creator.isGM())
			{
				String referenceName = "no-reference";
				if (reference instanceof L2Object)
				{
					referenceName = (((L2Object)reference).getName() != null?((L2Object)reference).getName():"no-name");
				}
				else if (reference instanceof String)
					referenceName = (String)reference;
				String targetName = (creator.getTarget() != null?creator.getTarget().getName():"no-target");
				if (Config.GMAUDIT)
					GMAudit.auditGMAction(creator.getName()+" ["+creator.getObjectId()+"]",
							process + "(id: "+getItemId()+" name: "+getName()+")", targetName,
							"L2Object referencing this action is: " + referenceName);
			}
		}
	}
	
	/**
	 * Sets the ownerID of the item
	 * @param owner_id : int designating the ID of the owner
	 */
	public void setOwnerId(int owner_id)
	{
		if (owner_id == _ownerId) return;
		
		_ownerId = owner_id;
		_storedInDb = false;
	}
	
	/**
	 * Returns the ownerID of the item
	 * @return int : ownerID of the item
	 */
	public int getOwnerId()
	{
		return _ownerId;
	}
	
	/**
	 * Sets the location of the item
	 * @param loc : ItemLocation (enumeration)
	 */
	public void setLocation(ItemLocation loc)
	{
		setLocation(loc, 0);
	}
	
	/**
	 * Sets the location of the item.<BR><BR>
	 * <U><I>Remark :</I></U> If loc and loc_data different from database, say datas not up-to-date
	 * @param loc : ItemLocation (enumeration)
	 * @param loc_data : int designating the slot where the item is stored or the village for freights
	 */
	public void setLocation(ItemLocation loc, int loc_data)
	{
		if (loc == _loc && loc_data == _locData)
			return;
		_loc = loc;
		_locData = loc_data;
		_storedInDb = false;
	}
	
	public ItemLocation getLocation()
	{
		return _loc;
	}
	
	/**
	 * Sets the quantity of the item.<BR><BR>
	 * @param count the new count to set
	 */
	public void setCount(long count)
	{
		if (getCount() == count)
		{
			return;
		}
		
		_count = count >= -1 ? count : 0;
		_storedInDb = false;
	}
	
	/**
	 * @return Returns the count.
	 */
	public long getCount()
	{
		return _count;
	}
	
	/**
	 * Sets the quantity of the item.<BR><BR>
	 * <U><I>Remark :</I></U> If loc and loc_data different from database, say datas not up-to-date
	 * @param process : String Identifier of process triggering this action
	 * @param count : int
	 * @param creator : L2PcInstance Player requesting the item creation
	 * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
	 */
	public void changeCount(String process, long count, L2PcInstance creator, Object reference)
	{
		if (count == 0)
		{
			return;
		}
		long old = getCount();
		long max = getItemId() == ADENA_ID ? MAX_ADENA : Integer.MAX_VALUE;
		
		if ( count > 0 && getCount() > max - count)
		{
			setCount(max);
		}
		else
		{
			setCount(getCount() + count);
		}
		
		if (getCount() < 0)
		{
			setCount(0);
		}
		
		_storedInDb = false;
		
		if (Config.LOG_ITEMS && process != null)
		{
			if (!Config.LOG_ITEMS_SMALL_LOG || (Config.LOG_ITEMS_SMALL_LOG && (_item.isEquipable() || _item.getItemId() == ADENA_ID)))
			{
				LogRecord record = new LogRecord(Level.INFO, "CHANGE:" + process);
				record.setLoggerName("item");
				record.setParameters(new Object[]{this, "PrevCount("+old+")",creator, reference});
				_logItems.log(record);
			}
		}
		
		if (creator != null)
		{
			if (creator.isGM())
			{
				String referenceName = "no-reference";
				if (reference instanceof L2Object)
				{
					referenceName = (((L2Object)reference).getName() != null?((L2Object)reference).getName():"no-name");
				}
				else if (reference instanceof String)
					referenceName = (String)reference;
				String targetName = (creator.getTarget() != null?creator.getTarget().getName():"no-target");
				if (Config.GMAUDIT)
					GMAudit.auditGMAction(creator.getName()+" ["+creator.getObjectId()+"]",
							process + "(id: "+getItemId()+" objId: "+getObjectId()+
							" name: "+getName()+" count: "+count+")", targetName,
							"L2Object referencing this action is: " + referenceName);
			}
		}
	}
	
	// No logging (function designed for shots only)
	public void changeCountWithoutTrace(int count, L2PcInstance creator, Object reference)
	{
		this.changeCount(null, count, creator, reference);
	}
	
	
	
	/**
	 * Returns if item is equipable
	 * @return boolean
	 */
	public boolean isEquipable()
	{
		return !(_item.getBodyPart() == 0 || _item.getItemType() == L2EtcItemType.ARROW || _item.getItemType() == L2EtcItemType.BOLT || _item.getItemType() == L2EtcItemType.LURE );
	}
	
	/**
	 * Returns if item is equipped
	 * @return boolean
	 */
	public boolean isEquipped()
	{
		return _loc == ItemLocation.PAPERDOLL || _loc == ItemLocation.PET_EQUIP;
	}
	
	/**
	 * Returns the slot where the item is stored
	 * @return int
	 */
	public int getLocationSlot()
	{
		assert _loc == ItemLocation.PAPERDOLL || _loc == ItemLocation.PET_EQUIP || _loc == ItemLocation.INVENTORY || _loc == ItemLocation.MAIL || _loc == ItemLocation.FREIGHT;
		return _locData;
	}
	
	/**
	 * Returns the characteristics of the item
	 * @return L2Item
	 */
	public L2Item getItem()
	{
		return _item;
	}
	
	public int getCustomType1()
	{
		return _type1;
	}
	public int getCustomType2()
	{
		return _type2;
	}
	public void setCustomType1(int newtype)
	{
		_type1=newtype;
	}
	public void setCustomType2(int newtype)
	{
		_type2=newtype;
	}
	public void setDropTime(long time)
	{
		_dropTime=time;
	}
	public long getDropTime()
	{
		return _dropTime;
	}
	/**
	 * Returns the type of item
	 * @return Enum
	 */
	public L2ItemType getItemType()
	{
		return _item.getItemType();
	}
	
	/**
	 * Returns the ID of the item
	 * @return int
	 */
	public int getItemId()
	{
		return _itemId;
	}
	
	/**
	 * Returns true if item is an EtcItem
	 * @return boolean
	 */
	public boolean isEtcItem()
	{
		return (_item instanceof L2EtcItem);
	}
	
	/**
	 * Returns true if item is a Weapon/Shield
	 * @return boolean
	 */
	public boolean isWeapon()
	{
		return (_item instanceof L2Weapon);
	}
	
	/**
	 * Returns true if item is an Armor
	 * @return boolean
	 */
	public boolean isArmor()
	{
		return (_item instanceof L2Armor);
	}
	
	/**
	 * Returns the characteristics of the L2EtcItem
	 * @return L2EtcItem
	 */
	public L2EtcItem getEtcItem()
	{
		if (_item instanceof L2EtcItem)
		{
			return (L2EtcItem) _item;
		}
		return null;
	}
	
	/**
	 * Returns the characteristics of the L2Weapon
	 * @return L2Weapon
	 */
	public L2Weapon getWeaponItem()
	{
		if (_item instanceof L2Weapon)
		{
			return (L2Weapon) _item;
		}
		return null;
	}
	
	/**
	 * Returns the characteristics of the L2Armor
	 * @return L2Armor
	 */
	public L2Armor getArmorItem()
	{
		if (_item instanceof L2Armor)
		{
			return (L2Armor) _item;
		}
		return null;
	}
	
	/**
	 * Returns the quantity of crystals for crystallization
	 * 
	 * @return int
	 */
	public final int getCrystalCount()
	{
		return _item.getCrystalCount(_enchantLevel);
	}
	
	/**
	 * Returns the reference price of the item
	 * @return int
	 */
	public int getReferencePrice()
	{
		return _item.getReferencePrice();
	}
	
	/**
	 * Returns the name of the item
	 * @return String
	 */
	public String getItemName()
	{
		return _item.getName();
	}
	
	/**
	 * Returns the last change of the item
	 * @return int
	 */
	public int getLastChange()
	{
		return _lastChange;
	}
	
	/**
	 * Sets the last change of the item
	 * @param lastChange : int
	 */
	public void setLastChange(int lastChange)
	{
		_lastChange = lastChange;
	}
	
	/**
	 * Returns if item is stackable
	 * @return boolean
	 */
	public boolean isStackable()
	{
		return _item.isStackable();
	}
	
	/**
	 * Returns if item is dropable
	 * @return boolean
	 */
	public boolean isDropable()
	{
		return isAugmented() ? false : _item.isDropable();
	}
	
	/**
	 * Returns if item is destroyable
	 * @return boolean
	 */
	public boolean isDestroyable()
	{
		return _item.isDestroyable();
	}
	
	/**
	 * Returns if item is tradeable
	 * @return boolean
	 */
	public boolean isTradeable()
	{
		return isAugmented() ? false : _item.isTradeable();
	}
	
	/**
	 * Returns if item is sellable
	 * @return boolean
	 */
	public boolean isSellable()
	{
		return isAugmented() ? false : _item.isSellable();
	}
	
	/**
	 * Returns if item can be deposited in warehouse or freight
	 * @return boolean
	 */
	public boolean isDepositable(boolean isPrivateWareHouse)
	{
		// equipped, hero and quest items
		if (isEquipped() || !_item.isDepositable())
			return false;
		if (!isPrivateWareHouse)
		{
			// augmented not tradeable
			if (!isTradeable() || isShadowItem())
				return false;
		}
		
		return true;
	}
	
	/**
	 * Returns if item is consumable
	 * @return boolean
	 */
	public boolean isConsumable()
	{
		return _item.isConsumable();
	}
	
	public boolean isPotion()
	{
		return _item.isPotion();
	}

	public boolean isElixir()
	{
		return _item.isElixir();
	}

	public boolean isHeroItem()
	{
		return _item.isHeroItem();
	}
	
	public boolean isCommonItem()
	{
		return _item.isCommon();
	}
	
	/**
	 * Returns whether this item is pvp or not
	 * @return boolean
	 */
	public boolean isPvp()
	{
		return _item.isPvpItem();
	}
	
	public boolean isOlyRestrictedItem()
	{
		return (Config.LIST_OLY_RESTRICTED_ITEMS.contains(_itemId));
	}
	
	/**
	 * Returns if item is available for manipulation
	 * @return boolean
	 */
	public boolean isAvailable(L2PcInstance player, boolean allowAdena, boolean allowNonTradeable)
	{
		return (
				(!isEquipped()) // Not equipped
				&& (getItem().getType2() != L2Item.TYPE2_QUEST) // Not Quest Item
				&& (getItem().getType2() != L2Item.TYPE2_MONEY || getItem().getType1() != L2Item.TYPE1_SHIELD_ARMOR) // not money, not shield
				&& (player.getPet() == null || getObjectId() != player.getPet().getControlObjectId()) // Not Control item of currently summoned pet
				&& (player.getActiveEnchantItem() != this) // Not momentarily used enchant scroll
				&& (allowAdena || getItemId() != 57) // Not adena
				&& (player.getCurrentSkill() == null || player.getCurrentSkill().getSkill().getItemConsumeId() != getItemId())
				&& (!player.isCastingSimultaneouslyNow() || player.getLastSimultaneousSkillCast() == null || player.getLastSimultaneousSkillCast().getItemConsumeId() != getItemId())
				&& (allowNonTradeable || isTradeable()
				&& (!(getItem().getItemType() == L2EtcItemType.PET_COLLAR && player.havePetInvItems())))
		);
	}
	
	/**
	 * Returns the level of enchantment of the item
	 * @return int
	 */
	public int getEnchantLevel()
	{
		return _enchantLevel;
	}
	
	/**
	 * Sets the level of enchantment of the item
	 * @param int
	 */
	public void setEnchantLevel(int enchantLevel)
	{
		if (_enchantLevel == enchantLevel)
			return;
		_enchantLevel = enchantLevel;
		_storedInDb = false;
	}
	
	
	/**
	 * Returns whether this item is augmented or not
	 * @return true if augmented
	 */
	public boolean isAugmented()
	{
		return _augmentation == null ? false : true;
	}
	
	/**
	 * Returns the augmentation object for this item
	 * @return augmentation
	 */
	public L2Augmentation getAugmentation()
	{
		return _augmentation;
	}
	
	/**
	 * Sets a new augmentation
	 * @param augmentation
	 * @return return true if sucessfull
	 */
	public boolean setAugmentation(L2Augmentation augmentation)
	{
		// there shall be no previous augmentation..
		if (_augmentation != null)
			return false;
		_augmentation = augmentation;
		updateItemAttributes(null);
		return true;
	}
	
	/**
	 * Remove the augmentation
	 *
	 */
	public void removeAugmentation()
	{
		if (_augmentation == null)
			return;
		_augmentation = null;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = null;
			// Remove the entry
			statement = con.prepareStatement("DELETE FROM item_attributes WHERE itemId = ?");
			
			statement.setInt(1, getObjectId());
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "Could not remove augmentation for item: "+this+" from DB:", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void restoreAttributes()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT augAttributes,augSkillId,augSkillLevel FROM item_attributes WHERE itemId=?");
			statement.setInt(1, getObjectId());
			ResultSet rs = statement.executeQuery();
			if (rs.next())
			{
				int aug_attributes = rs.getInt(1);
				int aug_skillId = rs.getInt(2);
				int aug_skillLevel = rs.getInt(3);
				if (aug_attributes != -1 && aug_skillId != -1 && aug_skillLevel != -1)
					_augmentation = new L2Augmentation(rs.getInt("augAttributes"), rs.getInt("augSkillId"), rs.getInt("augSkillLevel"));
			}
			rs.close();
			statement.close();
			statement = con.prepareStatement("SELECT elemType,elemValue FROM item_elementals WHERE itemId=?");
			statement.setInt(1, getObjectId());
			rs = statement.executeQuery();
			while (rs.next())
			{
				byte elem_type = rs.getByte(1);
				int elem_value = rs.getInt(2);
				if (elem_type != -1 && elem_value != -1)
					applyAttribute(elem_type, elem_value);
			}
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "Could not restore augmentation and elemental data for item " + this + " from DB: "+e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void updateItemAttributes(Connection pooledCon)
	{
		Connection con = null;
		try
		{
			con = pooledCon == null ? L2DatabaseFactory.getInstance().getConnection() : pooledCon;
			PreparedStatement statement = con.prepareStatement("REPLACE INTO item_attributes VALUES(?,?,?,?)");
			statement.setInt(1, getObjectId());
			if (_augmentation == null)
			{
				statement.setInt(2, -1);
				statement.setInt(3, -1);
				statement.setInt(4, -1);
			}
			else
			{
				statement.setInt(2, _augmentation.getAttributes());
				if(_augmentation.getSkill() == null)
				{
					statement.setInt(3, 0);
					statement.setInt(4, 0);
				}
				else
				{
					statement.setInt(3, _augmentation.getSkill().getId());
					statement.setInt(4, _augmentation.getSkill().getLevel());
				}
			}
			statement.executeUpdate();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.log(Level.SEVERE, "Could not update atributes for item: "+this+" from DB:", e);
			
		}
		finally
		{
			if (pooledCon == null)
				L2DatabaseFactory.close(con);
		}
	}
	
	private void updateItemElements(Connection pooledCon)
	{
		Connection con = null;
		try
		{
			con = pooledCon == null ? L2DatabaseFactory.getInstance().getConnection() : pooledCon;
			PreparedStatement statement = con.prepareStatement("DELETE FROM item_elementals WHERE itemId = ?");
			statement.setInt(1, getObjectId());
			statement.executeUpdate();
			statement.close();
			
			if (_elementals == null)
				return;
			
			statement = con.prepareStatement("INSERT INTO item_elementals VALUES(?,?,?)");
			
			for (Elementals elm : _elementals)
			{
				statement.setInt(1, getObjectId());
				statement.setByte(2, elm.getElement());
				statement.setInt(3, elm.getValue());
				statement.executeUpdate();
				statement.clearParameters();
			}
			
			statement.close();
		}
		catch (SQLException e)
		{
			_log.log(Level.SEVERE, "Could not update elementals for item: "+this+" from DB:", e);
		}
		finally
		{
			if (pooledCon == null)
				L2DatabaseFactory.close(con);
		}
	}
	
	public Elementals[] getElementals()
	{
		return _elementals;
	}
	
	public Elementals getElemental(byte attribute)
	{
		if (_elementals == null)
			return null;
		for (Elementals elm : _elementals)
		{
			if (elm.getElement() == attribute)
				return elm;
		}
		return null;
	}
	
	public byte getAttackElementType()
	{
		if (!isWeapon())
			return -2;
		else if (getItem().getElementals() != null)
			return getItem().getElementals()[0].getElement();
		else if (_elementals != null)
			return _elementals[0].getElement();
		return -2;
	}
	
	public int getAttackElementPower()
	{
		if (!isWeapon())
			return 0;
		else if (getItem().getElementals() != null)
			return getItem().getElementals()[0].getValue();
		else if (_elementals != null)
			return _elementals[0].getValue();
		return 0;
	}
	
	public int getElementDefAttr(byte element)
	{
		if (!isArmor())
			return 0;
		else if (getItem().getElementals() != null)
		{
			Elementals elm = getItem().getElemental(element);
			if (elm != null)
				return elm.getValue();
		}
		else if (_elementals != null)
		{
			Elementals elm = getElemental(element);
			if (elm != null )
				return elm.getValue();
		}
		return 0;
	}
	
	private void applyAttribute(byte element, int value)
	{
		if (_elementals == null)
		{
			_elementals = new Elementals[1];
			_elementals[0] = new Elementals(element, value);
		}
		else
		{
			Elementals elm = getElemental(element);
			if (elm != null)
			{
				elm.setValue(value);
			}
			else
			{
				elm = new Elementals(element, value);
				Elementals[] array = new Elementals[_elementals.length+1];
				System.arraycopy(_elementals, 0, array, 0, _elementals.length);
				array[_elementals.length] = elm;
				_elementals = array;
			}
		}
	}
	
	/**
	 * Add elemental attribute to item and save to db
	 * @param element
	 * @param value
	 */
	public void setElementAttr(byte element, int value)
	{
		applyAttribute(element, value);
		updateItemElements(null);
	}
	
	/**
	 * Remove elemental from item
	 * @param element byte element to remove, -1 for all elementals remove
	 */
	public void clearElementAttr(byte element)
	{
		if (getElemental(element) == null && element != -1)
		{
			return;
		}
		
		Elementals[] array = null;
		if (element != -1 && _elementals != null && _elementals.length > 1)
		{
			array = new Elementals[_elementals.length - 1];
			int i = 0;
			for (Elementals elm : _elementals)
			{
				if (elm.getElement() != element)
				{
					array[i++] = elm;
				}
			}
		}
		_elementals = array;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = null;
			if (element != -1)
			{
				//Item can have still others
				statement = con.prepareStatement("DELETE FROM item_elementals WHERE itemId = ? AND elemType = ?");
				statement.setInt(2, element);
			}
			else
			{
				// Remove the entries
				statement = con.prepareStatement("DELETE FROM item_elementals WHERE itemId = ?");
			}
			
			statement.setInt(1, getObjectId());
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "Could not remove elemental enchant for item: "+this+" from DB:", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Used to decrease mana
	 * (mana means life time for shadow items)
	 */
	public static class ScheduleConsumeManaTask implements Runnable
	{
		private final L2ItemInstance _shadowItem;
		
		public ScheduleConsumeManaTask(L2ItemInstance item)
		{
			_shadowItem = item;
		}
		
		@Override
		public void run()
		{
			try
			{
				// decrease mana
				if (_shadowItem != null)
					_shadowItem.decreaseMana(true);
			}
			catch (Exception e)
			{
				_log.log(Level.SEVERE, "", e);
			}
		}
	}
	
	
	/**
	 * Returns true if this item is a shadow item
	 * Shadow items have a limited life-time
	 * @return
	 */
	public boolean isShadowItem()
	{
		return (_mana >= 0);
	}
	
	/**
	 * Returns the remaining mana of this shadow item
	 * @return lifeTime
	 */
	public int getMana()
	{
		return _mana;
	}
	
	/**
	 * Decreases the mana of this shadow item,
	 * sends a inventory update
	 * schedules a new consumption task if non is running
	 * optionally one could force a new task
	 * @param forces a new consumption task if item is equipped
	 */
	public void decreaseMana(boolean resetConsumingMana)
	{
		decreaseMana(resetConsumingMana, 1);
	}
	
	/**
	 * Decreases the mana of this shadow item,
	 * sends a inventory update
	 * schedules a new consumption task if non is running
	 * optionally one could force a new task
	 * @param forces a new consumption task if item is equipped
	 * @param count how much mana decrease
	 */
	public void decreaseMana(boolean resetConsumingMana, int count)
	{
		if (!isShadowItem())
			return;
		
		if (_mana - count >= 0)
			_mana -= count;
		else
			_mana = 0;
		
		if (_storedInDb)
			_storedInDb = false;
		if (resetConsumingMana)
			_consumingMana = false;
		
		final L2PcInstance player = L2World.getInstance().getPlayer(getOwnerId());
		if (player != null)
		{
			SystemMessage sm;
			switch (_mana)
			{
				case 10:
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_10);
					sm.addItemName(_item);
					player.sendPacket(sm);
					break;
				case 5:
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_5);
					sm.addItemName(_item);
					player.sendPacket(sm);
					break;
				case 1:
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_1);
					sm.addItemName(_item);
					player.sendPacket(sm);
					break;
			}
			
			if (_mana == 0) // The life time has expired
			{
				sm = SystemMessage.getSystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_0);
				sm.addItemName(_item);
				player.sendPacket(sm);
				
				// unequip
				if (isEquipped())
				{
					L2ItemInstance[] unequiped = player.getInventory().unEquipItemInSlotAndRecord(getLocationSlot());
					InventoryUpdate iu = new InventoryUpdate();
					for (L2ItemInstance item: unequiped)
					{
						player.checkSShotsMatch(null, item);
						iu.addModifiedItem(item);
					}
					player.sendPacket(iu);
					player.broadcastUserInfo();
				}
				
				if (getLocation() != ItemLocation.WAREHOUSE)
				{
					// destroy
					player.getInventory().destroyItem("L2ItemInstance", this, player, null);
					
					// send update
					InventoryUpdate iu = new InventoryUpdate();
					iu.addRemovedItem(this);
					player.sendPacket(iu);
					
					StatusUpdate su = new StatusUpdate(player);
					su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
					player.sendPacket(su);
					
				}
				else
				{
					player.getWarehouse().destroyItem("L2ItemInstance", this, player, null);
				}
				
				// delete from world
				L2World.getInstance().removeObject(this);
			}
			else
			{
				// Reschedule if still equipped
				if (!_consumingMana && isEquipped())
				{
					scheduleConsumeManaTask();
				}
				if (getLocation() != ItemLocation.WAREHOUSE)
				{
					InventoryUpdate iu = new InventoryUpdate();
					iu.addModifiedItem(this);
					player.sendPacket(iu);
				}
			}
		}
	}
	
	public void scheduleConsumeManaTask()
	{
		if (_consumingMana)
			return;
		_consumingMana = true;
		ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleConsumeManaTask(this), MANA_CONSUMPTION_RATE);
	}
	
	/**
	 * Returns false cause item can't be attacked
	 * @return boolean false
	 */
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return false;
	}
	
	/**
	 * Returns the type of charge with SoulShot of the item.
	 * @return int (CHARGED_NONE, CHARGED_SOULSHOT)
	 */
	public int getChargedSoulshot()
	{
		return 	_chargedSoulshot;
	}
	
	/**
	 * Returns the type of charge with SpiritShot of the item
	 * @return int (CHARGED_NONE, CHARGED_SPIRITSHOT, CHARGED_BLESSED_SPIRITSHOT)
	 */
	public int getChargedSpiritshot()
	{
		return _chargedSpiritshot;
	}
	public boolean getChargedFishshot()
	{
		return _chargedFishtshot;
	}
	
	/**
	 * Sets the type of charge with SoulShot of the item
	 * @param type : int (CHARGED_NONE, CHARGED_SOULSHOT)
	 */
	public void setChargedSoulshot(int type)
	{
		_chargedSoulshot = type;
	}
	
	/**
	 * Sets the type of charge with SpiritShot of the item
	 * @param type : int (CHARGED_NONE, CHARGED_SPIRITSHOT, CHARGED_BLESSED_SPIRITSHOT)
	 */
	public void setChargedSpiritshot(int type)
	{
		_chargedSpiritshot = type;
	}
	public void setChargedFishshot(boolean type)
	{
		_chargedFishtshot = type;
	}
	
	/**
	 * This function basically returns a set of functions from
	 * L2Item/L2Armor/L2Weapon, but may add additional
	 * functions, if this particular item instance is enhanched
	 * for a particular player.
	 * @param player : L2Character designating the player
	 * @return Func[]
	 */
	public Func[] getStatFuncs(L2Character player)
	{
		return getItem().getStatFuncs(this, player);
	}
	
	/**
	 * Updates the database.<BR>
	 */
	public void updateDatabase()
	{
		this.updateDatabase(false);
	}
	
	/**
	 * Updates the database.<BR>
	 * 
	 * @param force if the update should necessarilly be done.
	 */
	public void updateDatabase(boolean force)
	{
		_dbLock.lock();
		
		try
		{
			if (_existsInDb)
			{
				if (_ownerId == 0
						|| _loc == ItemLocation.VOID
						|| _loc == ItemLocation.REFUND
						|| (getCount() == 0 && _loc != ItemLocation.LEASE))
				{
					removeFromDb();
				}
				else if (!Config.LAZY_ITEMS_UPDATE || force)
				{
					updateInDb();
				}
			}
			else
			{
				if (_ownerId == 0
						|| _loc == ItemLocation.VOID
						|| _loc == ItemLocation.REFUND
						|| (getCount() == 0 && _loc != ItemLocation.LEASE))
				{
					return;
				}
				insertIntoDb();
			}
		}
		finally
		{
			_dbLock.unlock();
		}
	}
	
	/**
	 * Returns a L2ItemInstance stored in database from its objectID
	 * @param objectId : int designating the objectID of the item
	 * @return L2ItemInstance
	 */
	public static L2ItemInstance restoreFromDb(int ownerId, ResultSet rs)
	{
		L2ItemInstance inst = null;
		int objectId, item_id, loc_data, enchant_level, custom_type1, custom_type2, manaLeft;
		long time, count;
		ItemLocation loc;
		try
		{
			objectId = rs.getInt(1);
			item_id = rs.getInt("item_id");
			count = rs.getLong("count");
			loc = ItemLocation.valueOf(rs.getString("loc"));
			loc_data = rs.getInt("loc_data");
			enchant_level = rs.getInt("enchant_level");
			custom_type1 =  rs.getInt("custom_type1");
			custom_type2 =  rs.getInt("custom_type2");
			manaLeft = rs.getInt("mana_left");
			time = rs.getLong("time");
		} catch (Exception e) {
			_log.log(Level.SEVERE, "Could not restore an item owned by "+ownerId+" from DB:", e);
			return null;
		}
		L2Item item = ItemTable.getInstance().getTemplate(item_id);
		if (item == null) {
			_log.severe("Item item_id="+item_id+" not known, object_id="+objectId);
			return null;
		}
		inst = new L2ItemInstance(objectId, item);
		inst._ownerId = ownerId;
		inst.setCount(count);
		inst._enchantLevel = enchant_level;
		inst._type1 = custom_type1;
		inst._type2 = custom_type2;
		inst._loc = loc;
		inst._locData = loc_data;
		inst._existsInDb = true;
		inst._storedInDb = true;
		
		// Setup life time for shadow weapons
		inst._mana = manaLeft;
		inst._time = time;
		
		//load augmentation and elemental enchant
		if (inst.isEquipable())
			inst.restoreAttributes();
		
		return inst;
	}
	
	/**
	 * Init a dropped L2ItemInstance and add it in the world as a visible object.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Set the x,y,z position of the L2ItemInstance dropped and update its _worldregion </li>
	 * <li>Add the L2ItemInstance dropped to _visibleObjects of its L2WorldRegion</li>
	 * <li>Add the L2ItemInstance dropped in the world as a <B>visible</B> object</li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T ADD the object to _allObjects of L2World </B></FONT><BR><BR>
	 *
	 * <B><U> Assert </U> :</B><BR><BR>
	 * <li> _worldRegion == null <I>(L2Object is invisible at the beginning)</I></li><BR><BR>
	 *
	 * <B><U> Example of use </U> :</B><BR><BR>
	 * <li> Drop item</li>
	 * <li> Call Pet</li><BR>
	 *
	 */
	public class ItemDropTask implements Runnable
	{
		private int _x,_y,_z;
		private final L2Character _dropper;
		private final L2ItemInstance _itm;
		
		public ItemDropTask(L2ItemInstance item, L2Character dropper, int x, int y, int z)
		{
			_x = x;
			_y = y;
			_z = z;
			_dropper = dropper;
			_itm = item;
		}
		
		@Override
		public final void run()
		{
			assert _itm.getPosition().getWorldRegion() == null;
			
			if (Config.GEODATA > 0 && _dropper != null)
			{
				Location dropDest = GeoData.getInstance().moveCheck(_dropper.getX(), _dropper.getY(), _dropper.getZ(), _x, _y, _z, _dropper.getInstanceId());
				_x = dropDest.getX();
				_y = dropDest.getY();
				_z = dropDest.getZ();
			}
			
			if(_dropper != null)
				setInstanceId(_dropper.getInstanceId()); // Inherit instancezone when dropped in visible world
			else
				setInstanceId(0); // No dropper? Make it a global item...
			
			synchronized (_itm)
			{
				// Set the x,y,z position of the L2ItemInstance dropped and update its _worldregion
				_itm.setIsVisible(true);
				_itm.getPosition().setWorldPosition(_x, _y ,_z);
				_itm.getPosition().setWorldRegion(L2World.getInstance().getRegion(getPosition().getWorldPosition()));
				
				// Add the L2ItemInstance dropped to _visibleObjects of its L2WorldRegion
			}
			
			_itm.getPosition().getWorldRegion().addVisibleObject(_itm);
			_itm.setDropTime(System.currentTimeMillis());
			_itm.setDropperObjectId(_dropper != null ? _dropper.getObjectId() : 0); //Set the dropper Id for the knownlist packets in sendInfo
			
			// this can synchronize on others instancies, so it's out of
			// synchronized, to avoid deadlocks
			// Add the L2ItemInstance dropped in the world as a visible object
			L2World.getInstance().addVisibleObject(_itm, _itm.getPosition().getWorldRegion());
			if (Config.SAVE_DROPPED_ITEM)
				ItemsOnGroundManager.getInstance().save(_itm);
			_itm.setDropperObjectId(0); //Set the dropper Id back to 0 so it no longer shows the drop packet
		}
	}
	
	public final void dropMe(L2Character dropper, int x, int y, int z)
	{
		ThreadPoolManager.getInstance().executeTask(new ItemDropTask(this, dropper, x, y, z));
	}
	
	/**
	 * Update the database with values of the item
	 */
	private void updateInDb()
	{
		assert _existsInDb;
		
		if (_wear)
			return;
		
		if (_storedInDb)
			return;
		
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"UPDATE items SET owner_id=?,count=?,loc=?,loc_data=?,enchant_level=?,custom_type1=?,custom_type2=?,mana_left=?,time=? " +
			"WHERE object_id = ?");
			statement.setInt(1, _ownerId);
			statement.setLong(2, getCount());
			statement.setString(3, _loc.name());
			statement.setInt(4, _locData);
			statement.setInt(5, getEnchantLevel());
			statement.setInt(6, getCustomType1());
			statement.setInt(7, getCustomType2());
			statement.setInt(8, getMana());
			statement.setLong(9, getTime());
			statement.setInt(10, getObjectId());
			statement.executeUpdate();
			_existsInDb = true;
			_storedInDb = true;
			statement.close();
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "Could not update item "+this+" in DB: Reason: "+e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Insert the item in database
	 */
	private void insertIntoDb()
	{
		assert !_existsInDb && getObjectId() != 0;
		
		if (_wear)
			return;
		
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"INSERT INTO items (owner_id,item_id,count,loc,loc_data,enchant_level,object_id,custom_type1,custom_type2,mana_left,time) " +
			"VALUES (?,?,?,?,?,?,?,?,?,?,?)");
			statement.setInt(1, _ownerId);
			statement.setInt(2, _itemId);
			statement.setLong(3, getCount());
			statement.setString(4, _loc.name());
			statement.setInt(5, _locData);
			statement.setInt(6, getEnchantLevel());
			statement.setInt(7, getObjectId());
			statement.setInt(8, _type1);
			statement.setInt(9, _type2);
			statement.setInt(10, getMana());
			statement.setLong(11, getTime());
			
			statement.executeUpdate();
			_existsInDb = true;
			_storedInDb = true;
			statement.close();
			
			if (_augmentation != null)
				updateItemAttributes(con);
			if (_elementals != null)
				updateItemElements(con);
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "Could not insert item " + this + " into DB: Reason: " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Delete item from database
	 */
	private void removeFromDb()
	{
		assert _existsInDb;
		
		if (_wear)
			return;
		
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("DELETE FROM items WHERE object_id=?");
			statement.setInt(1, getObjectId());
			statement.executeUpdate();
			_existsInDb = false;
			_storedInDb = false;
			statement.close();
			
			statement = con.prepareStatement("DELETE FROM item_attributes WHERE itemId = ?");
			statement.setInt(1, getObjectId());
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "Could not delete item "+this+" in DB: "+e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Returns the item in String format
	 * @return String
	 */
	@Override
	public String toString()
	{
		return _item+"["+getObjectId()+"]";
	}
	
	public void resetOwnerTimer()
	{
		if(itemLootShedule != null)
			itemLootShedule.cancel(true);
		itemLootShedule = null;
	}
	
	public void setItemLootShedule(ScheduledFuture<?> sf)
	{
		itemLootShedule = sf;
	}
	
	public ScheduledFuture<?> getItemLootShedule()
	{
		return itemLootShedule;
	}
	
	public void setProtected(boolean is_protected)
	{
		_protected = is_protected;
	}
	
	public boolean isProtected()
	{
		return _protected;
	}
	
	public boolean isNightLure()
	{
		return ((_itemId >= 8505 && _itemId <= 8513) || _itemId == 8485);
	}
	
	public void setCountDecrease(boolean decrease)
	{
		_decrease = decrease;
	}
	
	public boolean getCountDecrease()
	{
		return _decrease;
	}
	
	public void setInitCount(int InitCount)
	{
		_initCount = InitCount;
	}
	
	public long getInitCount()
	{
		return _initCount;
	}
	
	public void restoreInitCount()
	{
		if(_decrease)
			setCount(_initCount);
	}
	
	public boolean isTimeLimitedItem()
	{
		return (_time > 0);
	}
	
	/**
	 * Returns (current system time + time) of this time limited item
	 * @return Time
	 */
	public long getTime()
	{
		return _time;
	}
	
	public long getRemainingTime()
	{
		return _time - System.currentTimeMillis();
	}
	
	public void endOfLife()
	{
		L2PcInstance player = L2World.getInstance().getPlayer(getOwnerId());
		if (player != null)
		{
			if (isEquipped())
			{
				L2ItemInstance[] unequiped = player.getInventory().unEquipItemInSlotAndRecord(getLocationSlot());
				InventoryUpdate iu = new InventoryUpdate();
				for (L2ItemInstance item: unequiped)
				{
					player.checkSShotsMatch(null, item);
					iu.addModifiedItem(item);
				}
				player.sendPacket(iu);
				player.broadcastUserInfo();
			}
			
			if (getLocation() != ItemLocation.WAREHOUSE)
			{
				// destroy
				player.getInventory().destroyItem("L2ItemInstance", this, player, null);
				
				// send update
				InventoryUpdate iu = new InventoryUpdate();
				iu.addRemovedItem(this);
				player.sendPacket(iu);
				
				StatusUpdate su = new StatusUpdate(player);
				su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
				player.sendPacket(su);
				
			}
			else
			{
				player.getWarehouse().destroyItem("L2ItemInstance", this, player, null);
			}
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TIME_LIMITED_ITEM_DELETED));
			// delete from world
			L2World.getInstance().removeObject(this);
		}
	}
	
	public void scheduleLifeTimeTask()
	{
		if (!isTimeLimitedItem()) return;
		if (getRemainingTime() <= 0)
			endOfLife();
		else
		{
			if (_lifeTimeTask != null)
				_lifeTimeTask.cancel(false);
			_lifeTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleLifeTimeTask(this), getRemainingTime());
		}
	}
	
	public static class ScheduleLifeTimeTask implements Runnable
	{
		private final L2ItemInstance _limitedItem;
		
		public ScheduleLifeTimeTask(L2ItemInstance item)
		{
			_limitedItem = item;
		}
		
		@Override
		public void run()
		{
			try
			{
				if (_limitedItem != null)
					_limitedItem.endOfLife();
			}
			catch (Exception e)
			{
				_log.log(Level.SEVERE, "", e);
			}
		}
	}
	
	public void updateElementAttrBonus(L2PcInstance player)
	{
		if (_elementals == null)
			return;
		for (Elementals elm : _elementals)
			elm.updateBonus(player, isArmor());
	}
	
	public void removeElementAttrBonus(L2PcInstance player)
	{
		if (_elementals == null)
			return;
		for (Elementals elm : _elementals)
			elm.removeBonus(player);
	}
	
	public void setDropperObjectId(int id)
	{
		_dropperObjectId = id;
	}
	
	@Override
	public void sendInfo(L2PcInstance activeChar)
	{
		if (_dropperObjectId != 0)
			activeChar.sendPacket(new DropItem(this, _dropperObjectId));
		else
			activeChar.sendPacket(new SpawnItem(this));
	}
	
	public final DropProtection getDropProtection()
	{
		return _dropProtection;
	}
	
	public boolean isPublished()
	{
		return _published;
	}
	
	public void publish()
	{
		_published = true;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.model.L2Object#decayMe()
	 */
	@Override
	public void decayMe()
	{
		if (Config.SAVE_DROPPED_ITEM)
			ItemsOnGroundManager.getInstance().removeObject(this);
		super.decayMe();
	}
	
	public boolean isQuestItem()
	{
		return getItem().isQuestItem();
	}
	
	public boolean isFreightable()
	{
		return getItem().isFreightable();
	}
	
	public int getOlyEnchantLevel()
	{
		L2PcInstance player = L2World.getInstance().getPlayer(getOwnerId());
		int enchant = getEnchantLevel();
		
		if(player == null)
			return enchant;
		
		if (player.isInOlympiadMode() && Config.ALT_OLY_ENCHANT_LIMIT >= 0 && enchant > Config.ALT_OLY_ENCHANT_LIMIT)
				enchant = Config.ALT_OLY_ENCHANT_LIMIT;
		
		return enchant;
	}
}
