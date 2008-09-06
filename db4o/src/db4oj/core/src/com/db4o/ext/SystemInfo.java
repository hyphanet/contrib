/* Copyright (C) 2004 - 2008  db4objects Inc.  http://www.db4o.com

This file is part of the db4o open source object database.

db4o is free software; you can redistribute it and/or modify it under
the terms of version 2 of the GNU General Public License as published
by the Free Software Foundation and as clarified by db4objects' GPL 
interpretation policy, available at
http://www.db4o.com/about/company/legalpolicies/gplinterpretation/
Alternatively you can write to db4objects, Inc., 1900 S Norfolk Street,
Suite 350, San Mateo, CA 94403, USA.

db4o is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. */
package com.db4o.ext;


/**
 * provides information about system state and system settings.
 */
public interface SystemInfo {
    
    /**
     * returns the number of entries in the Freespace Manager.
     * <br><br>A high value for the number of freespace entries
     * is an indication that the database is fragmented and 
     * that defragment should be run.  
     * @return the number of entries in the Freespace Manager.
     */
    public int freespaceEntryCount();
    
    /**
     * returns the freespace size in the database in bytes.
     * <br><br>When db4o stores modified objects, it allocates
     * a new slot for it. During commit the old slot is freed.
     * Free slots are collected in the freespace manager, so
     * they can be reused for other objects.
     * <br><br>This method returns a sum of the size of all  
     * free slots in the database file.
     * <br><br>To reclaim freespace run defragment.
     * @return  the freespace size in the database in bytes.
     */
    public long freespaceSize();

    /**
     * Returns the total size of the database on disk.
     * @return total size of database on disk
     */
    public long totalSize();

}
