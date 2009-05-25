package org.tanukisoftware.wrapper;

/*
 * Copyright (c) 1999, 2009 Tanuki Software, Ltd.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the proprietary information of Tanuki Software.
 * You shall use it only in accordance with the terms of the
 * license agreement you entered into with Tanuki Software.
 * http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html
 */

import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Provides a Properties object which can be locked to prevent modification
 *  by the user.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
class WrapperProperties
    extends Properties
{
    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = 1991422118345246456L;
    
    boolean m_locked = false;
    
    /**
     * Locks the Properties object against future modification.
     */
    public void lock()
    {
        m_locked = true;
    }
    
    public void load( InputStream inStream )
        throws IOException
    {
        if ( m_locked )
        {
            throw new IllegalStateException( "Read Only" );
        }
        super.load( inStream );
    }
    
    public Object setProperty( String key, String value )
    {
        if ( m_locked )
        {
            throw new IllegalStateException( "Read Only" );
        }
        return super.setProperty( key, value );
    }
    
    public void clear()
    {
        if ( m_locked )
        {
            throw new IllegalStateException( "Read Only" );
        }
        super.clear();
    }
    
    public Set entrySet()
    {
        if ( m_locked )
        {
            return Collections.unmodifiableSet( super.entrySet() );
        }
        else
        {
            return super.entrySet();
        }
    }
    
    public Set keySet()
    {
        if ( m_locked )
        {
            return Collections.unmodifiableSet( super.keySet() );
        }
        else
        {
            return super.keySet();
        }
    }
    
    public Object put( Object key, Object value )
    {
        if ( m_locked )
        {
            throw new IllegalStateException( "Read Only" );
        }
        return super.put( key, value );
    }
    
    public void putAll( Map map )
    {
        if ( m_locked )
        {
            throw new IllegalStateException( "Read Only" );
        }
        super.putAll( map );
    }
    
    public Object remove( Object key )
    {
        if ( m_locked )
        {
            throw new IllegalStateException( "Read Only" );
        }
        return super.remove( key );
    }
    
    public Collection values()
    {
        if ( m_locked )
        {
            return Collections.unmodifiableCollection( super.values() );
        }
        else
        {
            return super.values();
        }
    }
}

