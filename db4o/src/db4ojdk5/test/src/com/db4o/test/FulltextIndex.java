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
package com.db4o.test;

import java.util.*;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.messaging.*;
import com.db4o.query.*;


/**
 * 
 */
public class FulltextIndex implements MessageRecipient{
    
    public String toIndex;
    private List indexEntries;
    
    public void configure(){
        Db4o.configure().clientServer().setMessageRecipient(new FulltextIndex());
        Db4o.configure().objectClass(FullTextIndexEntry.class).objectField("text").indexed(true);
    }
    
    public void storeOne(){
        toIndex = "This sentence no verb";
    }
    
    public void test(){
        Query q = Test.query();
        q.constrain(FullTextIndexEntry.class);
        q.descend("text").constrain("sentence");
        boolean found = false;
        ObjectSet objectSet = q.execute();
        while(objectSet.hasNext()){
            FullTextIndexEntry ftie = (FullTextIndexEntry) objectSet.next();
            Iterator i = ftie.objects.iterator();
            while(i.hasNext()){
                FulltextIndex fti = (FulltextIndex)i.next();
                if(fti.toIndex.indexOf("sentence") > -1){
                    found = true;
                }
            }
        }
        Test.ensure(found);
    }
    
    public void objectOnNew(ObjectContainer objectContainer){
        objectOnUpdate(objectContainer);
    }
    
    public void objectOnUpdate(ObjectContainer objectContainer){
        ensureServerMessageRecipient();
        if(objectContainer instanceof ExtClient){
            MessageSender sender = objectContainer.ext().configure().clientServer().getMessageSender();
            sender.send(new IDMessage(objectContainer.ext().getID(this)));
        }else{
            updateIndex(objectContainer);
        }
    }
    
    private void updateIndex(ObjectContainer objectContainer){
        if(indexEntries != null){
            Iterator i = indexEntries.iterator();
            while(i.hasNext()){
                FullTextIndexEntry entry = (FullTextIndexEntry)i.next();
                entry.objects.remove(this);
            }
            indexEntries.clear();
        }else{
            indexEntries = objectContainer.ext().collections().newLinkedList();
        }
        String[] strings = toIndex.split(" ");
        for (int i = 0; i < strings.length; i++) {
            Query q = objectContainer.query();
            q.constrain(FullTextIndexEntry.class);
            q.descend("text").constrain(strings[i]);
            ObjectSet objectSet = q.execute();
            if(objectSet.size() == 1){
                FullTextIndexEntry ftie = (FullTextIndexEntry)objectSet.next();
                ftie.objects.add(this);
            }else{
                FullTextIndexEntry ftie = new FullTextIndexEntry();
                ftie.text = strings[i];
                ftie.objects = objectContainer.ext().collections().newLinkedList();
                ftie.objects.add(this);
                objectContainer.store(ftie);
            }
        }
        objectContainer.commit();
        
    }
    
    public void processMessage(MessageContext context, Object message) {
        final ObjectContainer container = context.container();
		FulltextIndex fti = (FulltextIndex)container.ext().getByID(((IDMessage)message).id);
        container.activate(fti, 1);
        fti.updateIndex(container);
    }
    
    /**
     * There are side effects from other test cases that set different message recipients
     * on the server. We want to make sure that we set the last one.
     */
    private void ensureServerMessageRecipient(){
        ObjectServer server = Test.server();
        if(server != null){
            server.ext().configure().clientServer().setMessageRecipient(new FulltextIndex());
        }
    }
    
    public static class FullTextIndexEntry {
        public String text;
        public List objects; 
    }
    
    public static class IDMessage{
        public long id;
        public IDMessage(){
        }
        public IDMessage(long id){
            this.id = id;
        }
    }
}
