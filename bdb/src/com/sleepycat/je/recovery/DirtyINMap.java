/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DirtyINMap.java,v 1.4.2.1 2007/02/01 14:49:49 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.recovery.Checkpointer.CheckpointReference;
import com.sleepycat.je.tree.IN;

/**
 * Map of Integer->Set
 * level->Set of checkpoint references
 */
class DirtyINMap {

    private EnvironmentImpl envImpl;
    private SortedMap dirtyMap;
    private int numEntries;
    private int highestLevelSeen;

    DirtyINMap(EnvironmentImpl envImpl) {
        this.envImpl = envImpl;
    }

    /**
     * Scan the INList for all dirty INs, excluding deferred write INs that
     * are not in the must-sync set.  Save them in a tree-level ordered map for
     * level ordered flushing.
     * 
     * Take this opportunity to reset the memory budget tree value.
     */
    void selectDirtyINsForCheckpoint(Set mustSyncSet)
        throws DatabaseException {

        dirtyMap = new TreeMap();
        numEntries = 0;
        highestLevelSeen = IN.MIN_LEVEL;

        INList inMemINs = envImpl.getInMemoryINs();
        inMemINs.latchMajor();

        /* 
	 * Opportunistically recalculate the environment wide memory count.
	 * Incurs no extra cost because we're walking the IN list anyway.  Not
	 * the best in terms of encapsulation as preferably all memory
	 * calculations are done in MemoryBudget, but done this way to avoid
	 * any extra latching.
         *
         * Note: this addition is not taking the "side" added INList into
         * account properly! Need to fix, but seems to rarely be an issue.
	 */
        long totalSize = 0;
        MemoryBudget mb = envImpl.getMemoryBudget();

        try {
            Iterator iter = inMemINs.iterator();
            while (iter.hasNext()) {
                IN in = (IN) iter.next();
                in.latch(false);

                try {
                    totalSize = mb.accumulateNewUsage(in, totalSize);
                    
                    /* 
                     * Skip deferred-write nodes that are not in the must-sync
                     * set.
                     */
                    if (in.getDatabase().isDeferredWrite() &&
                        !mustSyncSet.contains(in.getDatabase().getId())){
                        continue;
                    }

                    addDirtyIN(in, false);
                } finally {
                    in.releaseLatch();
                }
            }

            /* Set the tree cache size. */
            mb.refreshTreeMemoryUsage(totalSize);

        } finally {
            inMemINs.releaseMajorLatchIfHeld();
        }
    }

    /**
     * Scan the INList for all dirty INs for a given database.  Arrange them in
     * level sorted map for level ordered flushing.
     */
    void selectDirtyINsForDb(DatabaseImpl dbImpl)
        throws DatabaseException {

        dirtyMap = new TreeMap();
        DatabaseId dbId = dbImpl.getId();
        INList inMemINs = envImpl.getInMemoryINs();
        inMemINs.latchMajor();

        try {
            Iterator iter = inMemINs.iterator();
            while (iter.hasNext()) {
                IN in = (IN) iter.next();
                if (in.getDatabaseId().equals(dbId)) {
                    in.latch(false);
                    try {
                        addDirtyIN(in, false);
                    } finally {
                        in.releaseLatch();
                    }
                }
            }
        } finally {
            inMemINs.releaseMajorLatchIfHeld();
        }
    }

    int getNumLevels() {
        return dirtyMap.size();
    }

    int getHighestLevel() {
        return highestLevelSeen;
    }

    void addCostToMemoryBudget() {
        MemoryBudget mb = envImpl.getMemoryBudget();
        int cost = numEntries * MemoryBudget.CHECKPOINT_REFERENCE_SIZE;
        mb.updateMiscMemoryUsage(cost);
    }
    
    void removeCostFromMemoryBudget() {
        MemoryBudget mb = envImpl.getMemoryBudget();
        int cost = numEntries * MemoryBudget.CHECKPOINT_REFERENCE_SIZE;
        mb.updateMiscMemoryUsage(0 - cost);
    }

    /**
     * Add a node to the dirty map. The dirty map is keyed by level (Integers)
     * and holds sets of IN references.
     */
    void addDirtyIN(IN in, boolean updateMemoryBudget) {
        if (in.getDirty()) {
            int levelVal = in.getLevel();
            if (levelVal > highestLevelSeen) {
                highestLevelSeen = levelVal;
            }

            Integer level = new Integer(levelVal);
            Set dirtySet;
            if (dirtyMap.containsKey(level)) {
                dirtySet = (Set) dirtyMap.get(level);
            } else {
                dirtySet = new HashSet();
                dirtyMap.put(level, dirtySet);
            }

            dirtySet.add(new CheckpointReference(in.getDatabase(),
                                                 in.getNodeId(),
                                                 in.containsDuplicates(),
                                                 in.isDbRoot(),
                                                 in.getMainTreeKey(),
                                                 in.getDupTreeKey()));
            numEntries++;

	    if (updateMemoryBudget) {
		MemoryBudget mb = envImpl.getMemoryBudget();
		mb.updateMiscMemoryUsage
		    (MemoryBudget.CHECKPOINT_REFERENCE_SIZE);
	    }
        }
    }

    /** 
     * Get the lowest level currently stored in the map. 
     */
    Integer getLowestLevelSet() {
        return (Integer) dirtyMap.firstKey();
    }

    /** 
     * Get the set corresponding to this level.
     */
    Set getSet(Integer level) {
        return (Set) dirtyMap.get(level);
    }
    
    /** 
     * Get the set corresponding to this level.
     */
    void removeSet(Integer level) {
        dirtyMap.remove(level);
    }
}
