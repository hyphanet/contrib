/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EventTrace.java,v 1.11.2.1 2007/02/01 14:49:54 cwl Exp $
 */

package com.sleepycat.je.utilint;


/**
 * Internal class used for transient event tracing.  Subclass this with
 * specific events.  Subclasses should have toString methods for display and
 * events should be added by calling EventTrace.addEvent();
 */
public class EventTrace {
    static private int MAX_EVENTS = 100;

    static public final boolean TRACE_EVENTS = false;

    static int currentEvent = 0;

    static final EventTrace[] events = new EventTrace[MAX_EVENTS];
    static final int[] threadIdHashes = new int[MAX_EVENTS];
    static boolean disableEvents = false;

    protected String comment;

    public EventTrace(String comment) {
	this.comment = comment;
    }

    public EventTrace() {
	comment = null;
    }

    public String toString() {
	return comment;
    }

    static public void addEvent(EventTrace event) {
	if (disableEvents) {
	    return;
	}
	int nextEventIdx = currentEvent++ % MAX_EVENTS;
	events[nextEventIdx] = event;
	threadIdHashes[nextEventIdx] =
	    System.identityHashCode(Thread.currentThread());
    }

    static public void addEvent(String comment) {
	if (disableEvents) {
	    return;
	}
	addEvent(new EventTrace(comment));
    }

    static public void dumpEvents() {
	if (disableEvents) {
	    return;
	}
	System.out.println("----- Event Dump -----");
	EventTrace[] oldEvents = events;
	int[] oldThreadIdHashes = threadIdHashes;
	disableEvents = true;

	int j = 0;
	for (int i = currentEvent; j < MAX_EVENTS; i++) {
	    EventTrace ev = oldEvents[i % MAX_EVENTS];
	    if (ev != null) {
		int thisEventIdx = i % MAX_EVENTS;
		System.out.print(oldThreadIdHashes[thisEventIdx] + " ");
		System.out.println(j + "(" + thisEventIdx + "): " + ev);
	    }
	    j++;
	}
    }

    static public class ExceptionEventTrace extends EventTrace {
	private Exception event;

	public ExceptionEventTrace() {
            event = new Exception();
	}

	public String toString() {
            return Tracer.getStackTrace(event);
	}
    }
}

    /*
    static public class EvictEvent extends EventTrace {
	long nodeId;
	int addr;

	public EvictEvent(String comment, long nodeId, int addr) {
	    super(comment);
	    this.nodeId = nodeId;
	    this.addr = addr;
	}

	static public void addEvent(String comment, IN node) {
	    long nodeId = node.getNodeId();
	    int addr = System.identityHashCode(node);
	    EventTrace.addEvent(new EvictEvent(comment, nodeId, addr));
	}

	public String toString() {
	    StringBuffer sb = new StringBuffer(comment);
	    sb.append(" IN: ").append(nodeId);
	    sb.append(" sIH ").append(addr);
	    return sb.toString();
	}
    }

    static public class CursorTrace extends EventTrace {
	long nodeId;
	int index;

	public CursorTrace(String comment, long nodeId, int index) {
	    super(comment);
	    this.nodeId = nodeId;
	    this.index = index;
	}

	static public void addEvent(String comment, CursorImpl cursor) {
	    long nodeId =
		(cursor.getBIN() == null) ? -1 : cursor.getBIN().getNodeId();
	    EventTrace.addEvent
		(new CursorTrace(comment, nodeId, cursor.getIndex()));
	}

	public String toString() {
	    StringBuffer sb = new StringBuffer(comment);
	    sb.append(" BIN: ").append(nodeId);
	    sb.append(" idx: ").append(index);
	    return sb.toString();
	}
    }
    */


/*
    class CursorEventTrace extends EventTrace {
	private String comment;
	private Node node1;
	private Node node2;

	CursorEventTrace(String comment, Node node1, Node node2) {
	    this.comment = comment;
	    this.node1 = node1;
	    this.node2 = node2;
	}

	public String toString() {
	    StringBuffer sb = new StringBuffer(comment);
	    if (node1 != null) {
		sb.append(" ");
		sb.append(node1.getNodeId());
	    }
	    if (node2 != null) {
		sb.append(" ");
		sb.append(node2.getNodeId());
	    }
	    return sb.toString();
	}
    }

*/
/*

    static class UndoEventTrace extends EventTrace {
	private String comment;
	private boolean success;
	private Node node;
	private DbLsn logLsn;
	private Node parent;
	private boolean found;
	private boolean replaced;
	private boolean inserted;
	private DbLsn replacedLsn;
	private DbLsn abortLsn;
	private int index;

	UndoEventTrace(String comment) {
	    this.comment = comment;
	}

	UndoEventTrace(boolean success,
		       Node node,
		       DbLsn logLsn,
		       Node parent,
		       boolean found,
		       boolean replaced,
		       boolean inserted,
		       DbLsn replacedLsn,
		       DbLsn abortLsn,
		       int index) {
	    this.comment = null;
	    this.success = success;
	    this.node = node;
	    this.logLsn = logLsn;
	    this.parent = parent;
	    this.found = found;
	    this.replaced = replaced;
	    this.inserted = inserted;
	    this.replacedLsn = replacedLsn;
	    this.abortLsn = abortLsn;
	    this.index = index;
	}

	public String toString() {
	    if (comment != null) {
		return comment;
	    }
	    StringBuffer sb = new StringBuffer();
            sb.append(" success=").append(success);
            sb.append(" node=");
            sb.append(node.getNodeId());
            sb.append(" logLsn=");
            sb.append(logLsn.getNoFormatString());
            if (parent != null) {
                sb.append(" parent=").append(parent.getNodeId());
            }
            sb.append(" found=");
            sb.append(found);
            sb.append(" replaced=");
            sb.append(replaced);
            sb.append(" inserted=");
            sb.append(inserted);
            if (replacedLsn != null) {
                sb.append(" replacedLsn=");
                sb.append(replacedLsn.getNoFormatString());
            }
            if (abortLsn != null) {
                sb.append(" abortLsn=");
                sb.append(abortLsn.getNoFormatString());
            }
            sb.append(" index=").append(index);
	    return sb.toString();
	}
    }
 */
/*
    class CursorAdjustEventTrace extends EventTrace {
	private int insertIndex;
	private int cursorIndex;
	private long nodeid;

	CursorAdjustEventTrace(int insertIndex, int cursorIndex) {
	    this.insertIndex = insertIndex;
	    this.cursorIndex = cursorIndex;
	    this.nodeid = getNodeId();
	}

	public String toString() {
	    StringBuffer sb = new StringBuffer("cursor adjust ");
	    sb.append(insertIndex).append(" ");
	    sb.append(cursorIndex).append(" ");
	    sb.append(nodeid);
	    return sb.toString();
	}
    }

*/
/*
    class CompressEventTrace extends EventTrace {
	private int entryIndex;
	private long nodeid;

	CompressEventTrace(int entryIndex) {
	    this.entryIndex = entryIndex;
	    this.nodeid = getNodeId();
	}

	public String toString() {
	    StringBuffer sb = new StringBuffer("bin compress ");
	    sb.append(entryIndex).append(" ");
	    sb.append(nodeid);
	    return sb.toString();
	}
    }

*/
/*
    class TreeEventTrace extends EventTrace {
	private String comment;
	private Node node1;
	private Node node2;

	TreeEventTrace(String comment, Node node1, Node node2) {
	    this.comment = comment;
	    this.node1 = node1;
	    this.node2 = node2;
	}

	public String toString() {
	    StringBuffer sb = new StringBuffer(comment);
	    if (node1 != null) {
		sb.append(" ");
		sb.append(node1.getNodeId());
	    }
	    if (node2 != null) {
		sb.append(" ");
		sb.append(node2.getNodeId());
	    }
	    return sb.toString();
	}
    }

*/


