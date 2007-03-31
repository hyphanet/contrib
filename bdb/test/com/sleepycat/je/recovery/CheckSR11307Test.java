/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004,2007 Oracle.  All rights reserved.
 *
 * $Id: CheckSR11307Test.java,v 1.14.2.1 2007/02/01 14:50:16 cwl Exp $
 */
package com.sleepycat.je.recovery;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.Tree.SearchType;
import com.sleepycat.je.util.TestUtils;

public class CheckSR11307Test extends CheckBase {

    private static final String DB_NAME = "simpleDB";

    /**
     * SR #11307
     */
    public void testSR11307()
        throws Throwable {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        turnOffEnvDaemons(envConfig);
        envConfig.setAllowCreate(true);
                                 
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
	dbConfig.setSortedDuplicates(true);

        DatabaseConfig validateDbConfig = new DatabaseConfig();
	validateDbConfig.setSortedDuplicates(true);

        EnvironmentConfig restartConfig = TestUtils.initEnvConfig();
        turnOffEnvDaemons(restartConfig);

	setCheckLsns(false);
        testOneCase(DB_NAME,
                    envConfig,
                    dbConfig,
                    new TestGenerator(){
                        void generateData(Database db)
                            throws DatabaseException {

                            addData(db);
                        }
                    },
                    restartConfig,
                    validateDbConfig);
    }

    private void put(Database db, String keyString, String dataString)
	throws DatabaseException {

	DatabaseEntry key = new DatabaseEntry(keyString.getBytes());
	DatabaseEntry data = new DatabaseEntry(dataString.getBytes());
	assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
    }

    private void delete(Database db, String keyString, String dataString)
	throws DatabaseException {

	DatabaseEntry key = new DatabaseEntry(keyString.getBytes());
	DatabaseEntry data = new DatabaseEntry(dataString.getBytes());

	Cursor c = db.openCursor(null, null);
	assertEquals(OperationStatus.SUCCESS,
		     c.getSearchBoth(key, data, null));
	assertEquals(OperationStatus.SUCCESS, c.delete());
	c.close();
    }

    /*
     * We're trying to emulate the following log synopsis.  The replay of this
     * log will end up putting a DBIN in a DBIN if the requiresExactMatch is
     * false in the call to readINs() in pass 7 of recovery.

     <entry lsn="0x3d9/0x1cd2" type="LN/0" prev="0x1ca0" size="36" cksum="2555578370"><ln><node>6809</node><data>yrhwlvlgvq</data></ln><dbId id="3"/><key val="cfhaa"/></entry>

     <entry lsn="0x3d9/0x4787" type="DBIN/1" prev="0x46d2" size="75" cksum="2518487070"><dbin><node>6807</node><key val="yrhwlvlgvq"/><isRoot val="false"/><level val="1"/><entries numEntries="1" length="128"><ref knownDeleted="false"><key val="yrhwlvlgvq"/><DbLsn val="0x3d9/0x1cd2"/></ref></entries><key val="cfhaa"/></dbin><dbId id="3"/></entry>

     <entry lsn="0x3dc/0x1e029" type="DIN/1" prev="0x1dfbd" size="94" cksum="419435527"><din><node>6806</node><key val="ubpgspglfn"/><isRoot val="true"/><level val="2"/><entries numEntries="1" length="128"><ref knownDeleted="false"><key val="ubpgspglfn"/><DbLsn val="0x3d9/0x4787"/></ref></entries><key val="cfhaa"/><ref knownDeleted="false"><key val="cfhaa"/><DbLsn val="0x3dc/0x1c226"/></ref></din><dbId id="3"/></entry>

     <entry lsn="0x3dc/0x4b3b5" type="DBIN/1" prev="0x4b349" size="213" cksum="4263720111"><dbin><node>5904</node><key val="gutjjgrfan"/><isRoot val="false"/><level val="1"/><entries numEntries="7" length="128"><ref knownDeleted="false"><key val="gutjjgrfan"/><DbLsn val="0x3dc/0x4848a"/></ref><ref knownDeleted="false"><key val="insjaepgnc"/><DbLsn val="0x3dc/0x48458"/></ref><ref knownDeleted="false"><key val="lthfpygdej"/><DbLsn val="0x3d8/0x584bb"/></ref><ref knownDeleted="false"><key val="qugxbqwtgd"/><DbLsn val="0x3dc/0x48426"/></ref><ref knownDeleted="false"><key val="savescwzoy"/><DbLsn val="0x3da/0x38d5"/></ref><ref knownDeleted="false"><key val="srbzmnargv"/><DbLsn val="0x3dc/0x484bc"/></ref><ref knownDeleted="false"><key val="ttkwptlkxv"/><DbLsn val="0x3db/0xa2d4a"/></ref></entries><key val="cfgaa"/></dbin><dbId id="3"/></entry>

     <entry lsn="0x3dc/0x4dce2" type="DIN/1" prev="0x4dc76" size="94" cksum="4266267702"><din><node>5903</node><key val="fyfmgvxwux"/><isRoot val="true"/><level val="2"/><entries numEntries="1" length="128"><ref knownDeleted="false"><key val="fyfmgvxwux"/><DbLsn val="0x3dc/0x4b3b5"/></ref></entries><key val="cfgaa"/><ref knownDeleted="false"><key val="cfgaa"/><DbLsn val="0x3d9/0xc08d1"/></ref></din><dbId id="3"/></entry>

     <entry lsn="0x3dd/0xe5c6d" type="CkptStart/0" prev="0xe57cb" size="26" cksum="2882930936"><CkptStart invoker="daemon" time="2004-10-30 13:26:19.89" id="1747"/></entry>

     <entry lsn="0x3dd/0xe5d4f" type="IN/1" prev="0xe5cd7" size="236" cksum="1038102495"><in><node>17</node><key val="aaaaa"/><isRoot val="true"/><level val="10002"/><entries numEntries="11" length="128"><ref knownDeleted="false"><key val="aaaaa"/><DbLsn val="0x3dd/0xda14e"/></ref><ref knownDeleted="false"><key val="bbfaa"/><DbLsn val="0x3dd/0xdf614"/></ref><ref knownDeleted="false"><key val="ceaaa"/><DbLsn val="0x3dd/0xde785"/></ref><ref knownDeleted="false"><key val="dgcaa"/><DbLsn val="0x3dd/0xe063f"/></ref><ref knownDeleted="false"><key val="ecgaa"/><DbLsn val="0x3dd/0xddbe0"/></ref><ref knownDeleted="false"><key val="ejaaa"/><DbLsn val="0x3dd/0xe4ea1"/></ref><ref knownDeleted="false"><key val="gbbaa"/><DbLsn val="0x3dd/0xe40bb"/></ref><ref knownDeleted="false"><key val="hdcaa"/><DbLsn val="0x3dd/0xe36a1"/></ref><ref knownDeleted="false"><key val="hjhaa"/><DbLsn val="0x3dd/0xe0c81"/></ref><ref knownDeleted="false"><key val="igcaa"/><DbLsn val="0x3dd/0xd18dc"/></ref><ref knownDeleted="false"><key val="jdaaa"/><DbLsn val="0x3dd/0xd863a"/></ref></entries></in><dbId id="3"/></entry>

     <entry lsn="0x3dd/0xed260" type="CkptEnd/0" prev="0xed22e" size="71" cksum="4160163625"><CkptEnd invoker="daemon" time="2004-10-30 13:26:20.046" lastNodeId="36927" lastDbId="4" lastTxnId="11033" id="1747" rootExists="true"><ckptStart><DbLsn val="0x3dd/0xe5c6d"/></ckptStart><root><DbLsn val="0x3dd/0xec74d"/></root><firstActive><DbLsn val="0x3dd/0xe5c6d"/></firstActive></CkptEnd></entry>

     <entry lsn="0x3de/0x390ee" type="BIN/1" prev="0x38c70" size="2180" cksum="1778961065"><bin><node>2317</node><key val="ceaaa"/><isRoot val="false"/><level val="10001"/><entries numEntries="119" length="128">...<ref knownDeleted="false"><key val="cfhaa"/><DbLsn val="0x3dc/0x1e029"/></ref>...</entries></bin><dbId id="3"/></entry>

     <entry lsn="0x3de/0x3b64c" type="DelDupLN_TX/0" prev="0x3b607" size="61" cksum="1460079772"><ln><node>6809</node></ln><dbId id="3"/><key val="cfhaa"/><DbLsn val="0x3d9/0x1cd2"/><knownDeleted val="false"/><txn id="11046__Txn"><DbLsn val="0x3de/0x3b3c7"/></txn><key val="yrhwlvlgvq"/></entry>

     <entry lsn="0x3de/0x3b6e2" type="DupCountLN_TX/0" prev="0x3b697" size="55" cksum="4138272827"><dupCountLN><node>6805</node><data></data><count v="0"/></dupCountLN><dbId id="3"/><key val="cfhaa"/><DbLsn val="0x3dc/0x1c226"/><knownDeleted val="false"/><txn id="11046__Txn"><DbLsn val="0x3de/0x3b64c"/></txn></entry>

     <entry lsn="0x3de/0x3b838" type="DBIN/1" prev="0x3b76c" size="75" cksum="2625900628"><dbin><node>6807</node><key val="yrhwlvlgvq"/><isRoot val="false"/><level val="1"/><entries numEntries="1" length="128"><ref knownDeleted="false"><key val="yrhwlvlgvq"/><DbLsn val="0x3de/0x3b64c"/></ref></entries><key val="cfhaa"/></dbin><dbId id="3"/></entry>

     <entry lsn="0x3de/0x3bec7" type="DIN/1" prev="0x3bdcd" size="94" cksum="635376858"><din><node>6806</node><key val="ubpgspglfn"/><isRoot val="true"/><level val="2"/><entries numEntries="1" length="128"><ref knownDeleted="false"><key val="ubpgspglfn"/><DbLsn val="0x3de/0x3b838"/></ref></entries><key val="cfhaa"/><ref knownDeleted="false"><key val="cfhaa"/><DbLsn val="0x3de/0x3b6e2"/></ref></din><dbId id="3"/></entry>

     <entry lsn="0x3de/0x40df6" type="INDupDelete/0" prev="0x40d8a" size="35" cksum="2389575622"><INDupDeleteEntry node="6806"><key val="cfhaa"/><key val="ubpgspglfn"/><dbId id="3"/></INDupDeleteEntry></entry>

     <entry lsn="0x3de/0x46660" type="BIN/1" prev="0x465f4" size="2162" cksum="3104884361"><bin><node>2317</node><key val="ceaaa"/><isRoot val="false"/><level val="10001"/><entries numEntries="118" length="128">...<ref knownDeleted="false"><key val="cfgaa"/><DbLsn val="0x3dc/0x4dce2"/></ref>...</entries></bin><dbId id="3"/></entry>

     <entry lsn="0x3de/0x50fa2" type="BIN/1" prev="0x50f36" size="2162" cksum="111985926"><bin><node>2317</node><key val="ceaaa"/><isRoot val="false"/><level val="10001"/><entries numEntries="118" length="128">...<ref knownDeleted="false"><key val="cfgaa"/>...</entries></bin><dbId id="3"/></entry>

     Trace list:
     lsn=0x3dd/0xe5d4f node=17
     lsn=0x3de/0x50fa2 node=2317
     lsn=0x3dc/0x4dce2 node=5903
     lsn=0x3dc/0x4b3b5 node=5904

    */
    private void addData(Database db) 
        throws DatabaseException {

	DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        CheckpointConfig ckptConfig = new CheckpointConfig();
        ckptConfig.setForce(true);

	/* 
	 * Create a one element dup tree by making a dupe and then reducing it
	 * back to one element.
	 */
	put(db, "cfhaa", "yrhwlvlgvq");
	put(db, "cfhaa", "blort");
	delete(db, "cfhaa", "blort");
	env.compress();
        env.sync();

	/* Same thing for cfgaa. */	  
	put(db, "cfgaa", "urhwlvlgvq");
	put(db, "cfgaa", "blort");
	delete(db, "cfgaa", "blort");
	put(db, "cfiaa", "yrhwlvlgvq");
	put(db, "cffaa", "yrhwlvlgvq");
        env.sync();
        env.sync();

	/* Write out the DelDupLN and DupCountLN. */
	delete(db, "cfhaa", "yrhwlvlgvq");
	BIN bin = (BIN) dbImpl.getTree().search("cfhaa".getBytes(),
						SearchType.NORMAL,
						-1,
                                                null,
                                                true);
	assertNotNull(bin);
	int idx = bin.findEntry("cfhaa".getBytes(), false, true);
	DIN din = (DIN) bin.getTarget(idx);
	din.latch();
	assertNotNull(din);
	idx = din.findEntry("yrhwlvlgvq".getBytes(), false, true);
	DBIN dbin = (DBIN) din.getTarget(idx);
	Key.DUMP_BINARY = false;
	dbin.latch();
	dbin.log(envImpl.getLogManager());
	din.log(envImpl.getLogManager());
	din.releaseLatch();
	dbin.releaseLatch();
	bin.releaseLatch();
	env.compress();
	bin.latch();
	bin.log(envImpl.getLogManager());
	bin.releaseLatch();
    } 
}
