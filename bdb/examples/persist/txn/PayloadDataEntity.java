package persist.txn;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;
import static com.sleepycat.persist.model.Relationship.*;

@Entity
public class PayloadDataEntity {
    @PrimaryKey
    private int oID;

    @SecondaryKey(relate=MANY_TO_ONE)
    private String threadName;

    private double doubleData;

    PayloadDataEntity() {}

    public double getDoubleData() { return doubleData; }
    public int getID() { return oID; }
    public String getThreadName() { return threadName; }

    public void setDoubleData(double dd) { doubleData = dd; }
    public void setID(int id) { oID = id; }
    public void setThreadName(String tn) { threadName = tn; }
}
