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
package com.db4o.foundation;


/**
 * @exclude
 */
public class Algorithms4 {
	
	private static class Range {
		int _from;
		int _to;

		public Range(int from, int to) {
			_from = from;
			_to = to;
		}
	}
	
	public static void qsort(QuickSortable4 sortable) {
		Stack4 stack=new Stack4();
		addRange(stack, 0, sortable.size()-1);
		qsort(sortable,stack);
	}

	private static void qsort(QuickSortable4 sortable, Stack4 stack) {
		while(!stack.isEmpty()) {
			Range range=(Range)stack.peek();
			stack.pop();
			int from=range._from;
			int to=range._to;
			int pivot = to;
			int left = from;
			int right = to;
			while (left<right) {
				while (left<right && sortable.compare(left,pivot)<0) {
					left++;
				}
				while(left<right && sortable.compare(right,pivot)>=0) {
					right--;
				}
				swap(sortable, left, right);
			}
			swap(sortable, to, right);
			addRange(stack, from, right-1);
			addRange(stack, right+1, to);
		}
	}

	private static void addRange(Stack4 stack,int from,int to) {
		if (to-from < 1) {
			return;
		}
		stack.push(new Range(from,to));
	}
	
	private static void swap(QuickSortable4 sortable, int left, int right) {
		if (left == right) {
			return;
		}
		sortable.swap(left, right);
	}

}
