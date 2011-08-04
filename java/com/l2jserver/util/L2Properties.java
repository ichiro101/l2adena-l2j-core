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
package com.l2jserver.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Specialized {@link java.util.Properties} class.<br>
 * Simplifies loading of property files and adds logging if a non existing property is requested.<br>
 * 
 * @author Noctarius
 */
public final class L2Properties extends Properties
{
	private static final long serialVersionUID = 1L;
	
	private static Logger _log = Logger.getLogger(L2Properties.class.getName());
	
	public L2Properties() { }
	
	public L2Properties(String name) throws IOException
	{
		load(new FileInputStream(name));
	}
	
	public L2Properties(File file) throws IOException
	{
		load(new FileInputStream(file));
	}
	
	public L2Properties(InputStream inStream) throws IOException
	{
		load(inStream);
	}
	
	public L2Properties(Reader reader) throws IOException
	{
		load(reader);
	}
	
	public void load(String name) throws IOException
	{
		load(new FileInputStream(name));
	}
	
	public void load(File file) throws IOException
	{
		load(new FileInputStream(file));
	}
	
	@Override
	public void load(InputStream inStream) throws IOException
	{
		InputStreamReader reader = null;
		try
		{
			reader = new InputStreamReader(inStream, Charset.defaultCharset());
			super.load(reader);
		}
		finally
		{
			inStream.close();
			if (reader != null)
				reader.close();
		}
	}
	
	@Override
	public void load(Reader reader) throws IOException
	{
		try
		{
			super.load(reader);
		}
		finally
		{
			reader.close();
		}
	}

	/**
	 * @see Properties#getProperty(String)
	 */
	@Override
	public String getProperty(String key)
	{
		String property = super.getProperty(key);
		
		if (property == null)
		{
			_log.info("L2Properties: Missing property for key - " + key);
			
			return null;
		}
		
		return property.trim();
	}

	/**
	 * @see Properties#getProperty(String,String)
	 */
	@Override
	public String getProperty(String key, String defaultValue)
	{
		String property = super.getProperty(key, defaultValue);
		
		if (property == null)
		{
			_log.warning("L2Properties: Missing defaultValue for key - " + key);
			
			return null;
		}
		
		return property.trim();
	}
}
