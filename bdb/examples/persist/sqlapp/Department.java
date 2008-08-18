/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: Department.java,v 1.2 2008/05/15 01:54:49 linda Exp $
 */

package persist.sqlapp;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;
import static com.sleepycat.persist.model.Relationship.ONE_TO_ONE;

/**
 * The Department entity class.
 * 
 * @author chao
 */
@Entity
class Department {

    @PrimaryKey
    int departmentId;

    @SecondaryKey(relate = ONE_TO_ONE)
    String departmentName;

    String location;

    public Department(int departmentId,
                      String departmentName,
                      String location) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.location = location;
    }

	private Department() {} // For bindings.

    public int getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return this.departmentId + ", " +
               this.departmentName + ", " +
               this.location;
    }
}
