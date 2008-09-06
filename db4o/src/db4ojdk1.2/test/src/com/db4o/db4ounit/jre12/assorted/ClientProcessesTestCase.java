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
package com.db4o.db4ounit.jre12.assorted;

import com.db4o.*;
import com.db4o.db4ounit.util.*;
import com.db4o.query.*;

import db4ounit.*;
import db4ounit.extensions.*;
import db4ounit.extensions.fixtures.*;


public class ClientProcessesTestCase extends AbstractDb4oTestCase implements OptOutAllButNetworkingCS {

    public static final int ITEM_COUNT = 10;
    
    public static final String CLIENT_STARTED_OK = "[STARTED]";
    
    public static final String CLIENT_COMPLETED_OK = "[COMPLETED]";

    public static void main(String[] args) {
        new ClientProcessesTestCase().runClientServer();
    }
    
    public void _testMassiveClientConnect() throws InterruptedException{

        final int CLIENT_COUNT = 20;  // more than 200 clients will need more than 3 GB of memory
        
        final StringBuffer results = new StringBuffer();
        
        ThreadServices.spawnAndJoin(CLIENT_COUNT, new CodeBlock() {
            public void run() throws Throwable {
                String result = JavaServices.java(clientRunnerCommand());
                results.append(result);
                Assert.isTrue(result.indexOf(CLIENT_COMPLETED_OK) >= 0);
            }
        });
        System.out.println(results);
        asserItemCount(CLIENT_COUNT * ITEM_COUNT);
    }

    public void _testKillingClients() throws InterruptedException{

        final int CLIENT_COUNT = 3;  
        
        final StringBuffer results = new StringBuffer();
        
        ThreadServices.spawnAndJoin(CLIENT_COUNT, new CodeBlock() {
            public void run() throws Throwable {
                results.append(JavaServices.startAndKillJavaProcess(clientRunnerCommand(), CLIENT_STARTED_OK, 10000));
            }
        });
        
        Assert.areEqual(1, connectedClients());
        System.out.println(results);
    }
    
    private int connectedClients() {
        Db4oClientServer csFixture = (Db4oClientServer) fixture();
        return (csFixture.server()).ext().clientCount();
    }

    private void asserItemCount(final int expectedCount) {
        Query query = db().query();
        query.constrain(Item.class);
        int itemCount = query.execute().size();
        Assert.areEqual(expectedCount, itemCount);
    }

    String clientRunnerCommand() {
        return ClientRunner.class.getName() + " " + ((Db4oClientServer) fixture()).serverPort();
    }
    
    public static class ClientRunner {
        
        private final int _port;
        
        private ClientRunner(int port){
            _port = port;
        }
        
        public static void main(String[] arguments) {
            if(arguments == null || arguments.length == 0){
                return;
            }
            int port = new Integer(arguments[0]).intValue();
            new ClientRunner(port).start();
        }

        private void start() {
            ObjectContainer oc = Db4o.openClient(Db4oClientServer.HOST, _port, Db4oClientServer.USERNAME, Db4oClientServer.PASSWORD);
            oc.store(new Item(0));
            oc.commit();
            print("[0]");
            print(CLIENT_STARTED_OK);
            for (int i = 1; i < ITEM_COUNT; i++) {
                oc.store(new Item(i));
                oc.commit();
                print("[" + i + "]");
            }
            oc.close();
            print(CLIENT_COMPLETED_OK);
        }
        
        private void print(String str){
            System.out.println(str);
        }
        
    }
    
    public static class Item{
        
        public int _number;
        
        public Item(int number){
            _number = number;
        }
        
    }
    

}

