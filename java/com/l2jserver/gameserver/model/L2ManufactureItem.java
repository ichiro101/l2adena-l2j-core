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

import com.l2jserver.gameserver.RecipeController;


/**
 * This class ...
 *
 * @version $Revision: 1.1.2.2.2.1 $ $Date: 2005/03/27 15:29:32 $
 */
public class L2ManufactureItem
{
	private int _recipeId;
	private long _cost;
	private boolean _isDwarven;
	
	public L2ManufactureItem(int recipeId, long cost)
	{
		_recipeId = recipeId;
		_cost = cost;
		
		_isDwarven = RecipeController.getInstance().getRecipeList(_recipeId).isDwarvenRecipe();
	}
	
	public int getRecipeId()
	{
		return _recipeId;
	}
	
	public long getCost()
	{
		return _cost;
	}
	
	public boolean isDwarven()
	{
		return _isDwarven;
	}
}
