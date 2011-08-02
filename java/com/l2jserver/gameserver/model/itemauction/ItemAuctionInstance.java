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
package com.l2jserver.gameserver.model.itemauction;

import gnu.trove.TIntObjectHashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.l2jserver.Config;
import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.datatables.CharNameTable;
import com.l2jserver.gameserver.instancemanager.ItemAuctionManager;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2ItemInstance.ItemLocation;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.templates.StatsSet;
import com.l2jserver.util.Rnd;

public final class ItemAuctionInstance
{
	static final Logger _log = Logger.getLogger(ItemAuctionInstance.class.getName());
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
	
	private static final long START_TIME_SPACE = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
	private static final long FINISH_TIME_SPACE = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);
	
	private final int _instanceId;
	private final AtomicInteger _auctionIds;
	private final TIntObjectHashMap<ItemAuction> _auctions;
	private final ArrayList<AuctionItem> _items;
	private final AuctionDateGenerator _dateGenerator;
	
	private ItemAuction _currentAuction;
	private ItemAuction _nextAuction;
	private ScheduledFuture<?> _stateTask;
	
	public ItemAuctionInstance(final int instanceId, final AtomicInteger auctionIds, final Node node) throws Exception
	{
		_instanceId = instanceId;
		_auctionIds = auctionIds;
		_auctions = new TIntObjectHashMap<ItemAuction>();
		_items = new ArrayList<AuctionItem>();
		
		final NamedNodeMap nanode = node.getAttributes();
		final StatsSet generatorConfig = new StatsSet();
		for (int i = nanode.getLength(); i-- > 0;)
		{
			final Node n = nanode.item(i);
			if (n != null)
				generatorConfig.set(n.getNodeName(), n.getNodeValue());
		}
		
		_dateGenerator = new AuctionDateGenerator(generatorConfig);
		
		for (Node na = node.getFirstChild(); na != null; na = na.getNextSibling())
		{
			try
			{
				if ("item".equalsIgnoreCase(na.getNodeName()))
				{
					final NamedNodeMap naa = na.getAttributes();
					final int auctionItemId = Integer.parseInt(naa.getNamedItem("auctionItemId").getNodeValue());
					final int auctionLenght = Integer.parseInt(naa.getNamedItem("auctionLenght").getNodeValue());
					final long auctionInitBid = Integer.parseInt(naa.getNamedItem("auctionInitBid").getNodeValue());
					
					final int itemId = Integer.parseInt(naa.getNamedItem("itemId").getNodeValue());
					final int itemCount = Integer.parseInt(naa.getNamedItem("itemCount").getNodeValue());
					
					if (auctionLenght < 1)
						throw new IllegalArgumentException("auctionLenght < 1 for instanceId: " + _instanceId + ", itemId " + itemId);
					
					final StatsSet itemExtra = new StatsSet();
					final AuctionItem item = new AuctionItem(auctionItemId, auctionLenght, auctionInitBid, itemId, itemCount, itemExtra);
					
					if (!item.checkItemExists())
						throw new IllegalArgumentException("Item with id " + itemId + " not found");
					
					for (final AuctionItem tmp : _items)
					{
						if (tmp.getAuctionItemId() == auctionItemId)
							throw new IllegalArgumentException("Dublicated auction item id " + auctionItemId);
					}
					
					_items.add(item);
					
					for (Node nb = na.getFirstChild(); nb != null; nb = nb.getNextSibling())
					{
						if ("extra".equalsIgnoreCase(nb.getNodeName()))
						{
							final NamedNodeMap nab = nb.getAttributes();
							for (int i = nab.getLength(); i-- > 0;)
							{
								final Node n = nab.item(i);
								if (n != null)
									itemExtra.set(n.getNodeName(), n.getNodeValue());
							}
						}
					}
				}
			}
			catch (final IllegalArgumentException e)
			{
				_log.log(Level.WARNING, "ItemAuctionInstance: Failed loading auction item", e);
			}
		}
		
		if (_items.isEmpty())
			throw new IllegalArgumentException("No items defined");
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT auctionId FROM item_auction WHERE instanceId=?");
			statement.setInt(1, _instanceId);
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				final int auctionId = rset.getInt(1);
				try
				{
					final ItemAuction auction = loadAuction(auctionId);
					if (auction != null)
					{
						_auctions.put(auctionId, auction);
					}
					else
					{
						ItemAuctionManager.deleteAuction(auctionId);
					}
				}
				catch (final SQLException e)
				{
					_log.log(Level.WARNING, "ItemAuctionInstance: Failed loading auction: " + auctionId, e);
				}
			}
		}
		catch (final SQLException e)
		{
			_log.log(Level.SEVERE, "L2ItemAuctionInstance: Failed loading auctions.", e);
			return;
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.log(Level.INFO, "L2ItemAuctionInstance: Loaded " + _items.size() + " item(s) and registered " + _auctions.size() + " auction(s) for instance " + _instanceId + ".");
		checkAndSetCurrentAndNextAuction();
	}
	
	public final ItemAuction getCurrentAuction()
	{
		return _currentAuction;
	}
	
	public final ItemAuction getNextAuction()
	{
		return _nextAuction;
	}
	
	public final void shutdown()
	{
		final ScheduledFuture<?> stateTask = _stateTask;
		if (stateTask != null)
			stateTask.cancel(false);
	}
	
	private final AuctionItem getAuctionItem(final int auctionItemId)
	{
		for (int i = _items.size(); i-- > 0;)
		{
			final AuctionItem item = _items.get(i);
			if (item.getAuctionItemId() == auctionItemId)
				return item;
		}
		return null;
	}
	
	final void checkAndSetCurrentAndNextAuction()
	{
		final ItemAuction[] auctions = _auctions.getValues(new ItemAuction[_auctions.size()]);
		
		ItemAuction currentAuction = null;
		ItemAuction nextAuction = null;
		
		switch (auctions.length)
		{
			case 0:
			{
				nextAuction = createAuction(System.currentTimeMillis() + START_TIME_SPACE);
				break;
			}
			
			case 1:
			{
				switch (auctions[0].getAuctionState())
				{
					case CREATED:
					{
						if (auctions[0].getStartingTime() < (System.currentTimeMillis() + START_TIME_SPACE))
						{
							currentAuction = auctions[0];
							nextAuction = createAuction(System.currentTimeMillis() + START_TIME_SPACE);
						}
						else
						{
							nextAuction = auctions[0];
						}
						break;
					}
					
					case STARTED:
					{
						currentAuction = auctions[0];
						nextAuction = createAuction(Math.max(currentAuction.getEndingTime() + FINISH_TIME_SPACE, System.currentTimeMillis() + START_TIME_SPACE));
						break;
					}
					
					case FINISHED:
					{
						currentAuction = auctions[0];
						nextAuction = createAuction(System.currentTimeMillis() + START_TIME_SPACE);
						break;
					}
					
					default:
						throw new IllegalArgumentException();
				}
				break;
			}
			
			default:
			{
				Arrays.sort(auctions, new Comparator<ItemAuction>() {
					@Override
					public final int compare(final ItemAuction o1, final ItemAuction o2)
					{
						return ((Long) o2.getStartingTime()).compareTo(o1.getStartingTime());
					}
				});
				
				// just to make sure we won`t skip any auction because of little different times
				final long currentTime = System.currentTimeMillis();
				
				for (int i = 0; i < auctions.length; i++)
				{
					final ItemAuction auction = auctions[i];
					if (auction.getAuctionState() == ItemAuctionState.STARTED)
					{
						currentAuction = auction;
						break;
					}
					else if (auction.getStartingTime() <= currentTime)
					{
						currentAuction = auction;
						break; // only first
					}
				}
				
				for (int i = 0; i < auctions.length; i++)
				{
					final ItemAuction auction = auctions[i];
					if (auction.getStartingTime() > currentTime && currentAuction != auction)
					{
						nextAuction = auction;
						break;
					}
				}
				
				if (nextAuction == null)
					nextAuction = createAuction(System.currentTimeMillis() + START_TIME_SPACE);
				break;
			}
		}
		
		_auctions.put(nextAuction.getAuctionId(), nextAuction);
		
		_currentAuction = currentAuction;
		_nextAuction = nextAuction;
		
		if (currentAuction != null && currentAuction.getAuctionState() != ItemAuctionState.FINISHED)
		{
			if (currentAuction.getAuctionState() == ItemAuctionState.STARTED)
				setStateTask(ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleAuctionTask(currentAuction), Math.max(currentAuction.getEndingTime() - System.currentTimeMillis(), 0L)));
			else
				setStateTask(ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleAuctionTask(currentAuction), Math.max(currentAuction.getStartingTime() - System.currentTimeMillis(), 0L)));
			_log.log(Level.INFO, "L2ItemAuctionInstance: Schedule current auction " + currentAuction.getAuctionId() + " for instance " + _instanceId);
		}
		else
		{
			setStateTask(ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleAuctionTask(nextAuction), Math.max(nextAuction.getStartingTime() - System.currentTimeMillis(), 0L)));
			_log.log(Level.INFO, "L2ItemAuctionInstance: Schedule next auction " + nextAuction.getAuctionId() + " on " + DATE_FORMAT.format(new Date(nextAuction.getStartingTime())) + " for instance " + _instanceId);
		}
	}
	
	public final ItemAuction getAuction(final int auctionId)
	{
		return _auctions.get(auctionId);
	}
	
	public final ItemAuction[] getAuctionsByBidder(final int bidderObjId)
	{
		final ItemAuction[] auctions = getAuctions();
		final ArrayList<ItemAuction> stack = new ArrayList<ItemAuction>(auctions.length);
		for (final ItemAuction auction : getAuctions())
		{
			if (auction.getAuctionState() != ItemAuctionState.CREATED)
			{
				final ItemAuctionBid bid = auction.getBidFor(bidderObjId);
				if (bid != null)
					stack.add(auction);
			}
		}
		return stack.toArray(new ItemAuction[stack.size()]);
	}
	
	public final ItemAuction[] getAuctions()
	{
		final ItemAuction[] auctions;
		
		synchronized (_auctions)
		{
			auctions = _auctions.getValues(new ItemAuction[_auctions.size()]);
		}
		
		return auctions;
	}
	
	private final class ScheduleAuctionTask implements Runnable
	{
		private final ItemAuction _auction;
		
		public ScheduleAuctionTask(final ItemAuction auction)
		{
			_auction = auction;
		}
		
		@Override
		public final void run()
		{
			try
			{
				runImpl();
			}
			catch (final Exception e)
			{
				_log.log(Level.SEVERE, "L2ItemAuctionInstance: Failed scheduling auction " + _auction.getAuctionId(), e);
			}
		}
		
		private final void runImpl() throws Exception
		{
			final ItemAuctionState state = _auction.getAuctionState();
			switch (state)
			{
				case CREATED:
				{
					if (!_auction.setAuctionState(state, ItemAuctionState.STARTED))
						throw new IllegalStateException("Could not set auction state: " + ItemAuctionState.STARTED.toString() + ", expected: " + state.toString());
					
					_log.log(Level.INFO, "L2ItemAuctionInstance: Auction " + _auction.getAuctionId() + " has started for instance " + _auction.getInstanceId());
					checkAndSetCurrentAndNextAuction();
					break;
				}
				
				case STARTED:
				{
					switch (_auction.getAuctionEndingExtendState())
					{
						case EXTEND_BY_5_MIN:
						{
							if (_auction.getScheduledAuctionEndingExtendState() == ItemAuctionExtendState.INITIAL)
							{
								_auction.setScheduledAuctionEndingExtendState(ItemAuctionExtendState.EXTEND_BY_5_MIN);
								setStateTask(ThreadPoolManager.getInstance().scheduleGeneral(this, Math.max(_auction.getEndingTime() - System.currentTimeMillis(), 0L)));
								return;
							}
							break;
						}
						
						case EXTEND_BY_3_MIN:
						{
							if (_auction.getScheduledAuctionEndingExtendState() != ItemAuctionExtendState.EXTEND_BY_3_MIN)
							{
								_auction.setScheduledAuctionEndingExtendState(ItemAuctionExtendState.EXTEND_BY_3_MIN);
								setStateTask(ThreadPoolManager.getInstance().scheduleGeneral(this, Math.max(_auction.getEndingTime() - System.currentTimeMillis(), 0L)));
								return;
							}
							break;
						}
						
						case EXTEND_BY_CONFIG_PHASE_A:
						{
							if (_auction.getScheduledAuctionEndingExtendState() != ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_B)
							{
								_auction.setScheduledAuctionEndingExtendState(ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_B);
								setStateTask(ThreadPoolManager.getInstance().scheduleGeneral(this, Math.max(_auction.getEndingTime() - System.currentTimeMillis(), 0L)));
								return;
							}
							break;
						}
						
						case EXTEND_BY_CONFIG_PHASE_B:
						{
							if (_auction.getScheduledAuctionEndingExtendState() != ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_A)
							{
								_auction.setScheduledAuctionEndingExtendState(ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_A);
								setStateTask(ThreadPoolManager.getInstance().scheduleGeneral(this, Math.max(_auction.getEndingTime() - System.currentTimeMillis(), 0L)));
								return;
							}
						}
					}
					
					if (!_auction.setAuctionState(state, ItemAuctionState.FINISHED))
						throw new IllegalStateException("Could not set auction state: " + ItemAuctionState.FINISHED.toString() + ", expected: " + state.toString());
					
					onAuctionFinished(_auction);
					checkAndSetCurrentAndNextAuction();
					break;
				}
				
				default:
					throw new IllegalStateException("Invalid state: " + state);
			}
		}
	}
	
	final void onAuctionFinished(final ItemAuction auction)
	{
		auction.broadcastToAllBiddersInternal(SystemMessage.getSystemMessage(SystemMessageId.S1_AUCTION_ENDED).addNumber(auction.getAuctionId()));
		
		final ItemAuctionBid bid = auction.getHighestBid();
		if (bid != null)
		{
			final L2ItemInstance item = auction.createNewItemInstance();
			final L2PcInstance player = bid.getPlayer();
			if (player != null)
			{
				player.getWarehouse().addItem("ItemAuction", item, null, null);
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.WON_BID_ITEM_CAN_BE_FOUND_IN_WAREHOUSE));
				
				_log.log(Level.INFO, "L2ItemAuctionInstance: Auction " + auction.getAuctionId() + " has finished. Highest bid by " + player.getName() + " for instance " + _instanceId);
			}
			else
			{
				item.setOwnerId(bid.getPlayerObjId());
				item.setLocation(ItemLocation.WAREHOUSE);
				item.updateDatabase();
				L2World.getInstance().removeObject(item);
				
				_log.log(Level.INFO, "L2ItemAuctionInstance: Auction " + auction.getAuctionId() + " has finished. Highest bid by " + CharNameTable.getInstance().getNameById(bid.getPlayerObjId()) + " for instance " + _instanceId);
			}
			
			// Clean all canceled bids
			auction.clearCanceledBids();
		}
		else
		{
			_log.log(Level.INFO, "L2ItemAuctionInstance: Auction " + auction.getAuctionId() + " has finished. There have not been any bid for instance " + _instanceId);
		}
	}
	
	final void setStateTask(final ScheduledFuture<?> future)
	{
		final ScheduledFuture<?> stateTask = _stateTask;
		if (stateTask != null)
			stateTask.cancel(false);
		
		_stateTask = future;
	}
	
	private final ItemAuction createAuction(final long after)
	{
		final AuctionItem auctionItem = _items.get(Rnd.get(_items.size()));
		final long startingTime = _dateGenerator.nextDate(after);
		final long endingTime = startingTime + TimeUnit.MILLISECONDS.convert(auctionItem.getAuctionLength(), TimeUnit.MINUTES);
		final ItemAuction auction = new ItemAuction(_auctionIds.getAndIncrement(), _instanceId, startingTime, endingTime, auctionItem);
		auction.storeMe();
		return auction;
	}
	
	private final ItemAuction loadAuction(final int auctionId) throws SQLException
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT auctionItemId,startingTime,endingTime,auctionStateId FROM item_auction WHERE auctionId=?");
			statement.setInt(1, auctionId);
			ResultSet rset = statement.executeQuery();
			
			if (!rset.next())
			{
				_log.log(Level.WARNING, "ItemAuctionInstance: Auction data not found for auction: " + auctionId);
				return null;
			}
			
			final int auctionItemId = rset.getInt(1);
			final long startingTime = rset.getLong(2);
			final long endingTime = rset.getLong(3);
			final byte auctionStateId = rset.getByte(4);
			statement.close();
			
			if (startingTime >= endingTime)
			{
				_log.log(Level.WARNING, "ItemAuctionInstance: Invalid starting/ending paramaters for auction: " + auctionId);
				return null;
			}
			
			final AuctionItem auctionItem = getAuctionItem(auctionItemId);
			if (auctionItem == null)
			{
				_log.log(Level.WARNING, "ItemAuctionInstance: AuctionItem: " + auctionItemId + ", not found for auction: " + auctionId);
				return null;
			}
			
			final ItemAuctionState auctionState = ItemAuctionState.stateForStateId(auctionStateId);
			if (auctionState == null)
			{
				_log.log(Level.WARNING, "ItemAuctionInstance: Invalid auctionStateId: " + auctionStateId + ", for auction: " + auctionId);
				return null;
			}
			
			if (auctionState == ItemAuctionState.FINISHED
					&& startingTime < System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(Config.ALT_ITEM_AUCTION_EXPIRED_AFTER, TimeUnit.DAYS))
			{
				_log.log(Level.INFO, "ItemAuctionInstance: Clearing expired auction: " + auctionId);
				statement = con.prepareStatement("DELETE FROM item_auction WHERE auctionId=?");
				statement.setInt(1, auctionId);
				statement.execute();
				statement.close();
				
				statement = con.prepareStatement("DELETE FROM item_auction_bid WHERE auctionId=?");
				statement.setInt(1, auctionId);
				statement.execute();
				statement.close();
				return null;
			}
			
			statement = con.prepareStatement("SELECT playerObjId,playerBid FROM item_auction_bid WHERE auctionId=?");
			statement.setInt(1, auctionId);
			rset = statement.executeQuery();
			
			final ArrayList<ItemAuctionBid> auctionBids = new ArrayList<ItemAuctionBid>();
			
			while (rset.next())
			{
				final int playerObjId = rset.getInt(1);
				final long playerBid = rset.getLong(2);
				final ItemAuctionBid bid = new ItemAuctionBid(playerObjId, playerBid);
				auctionBids.add(bid);
			}
			
			return new ItemAuction(auctionId, _instanceId, startingTime, endingTime, auctionItem, auctionBids, auctionState);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
}