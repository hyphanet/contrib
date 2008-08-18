// file ItemNameKeyCreator.java
// $Id: ItemNameKeyCreator.java,v 1.5 2006/03/30 00:39:55 sarette Exp $

package je.gettingStarted;

import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.bind.tuple.TupleBinding;

import java.io.IOException;

public class ItemNameKeyCreator implements SecondaryKeyCreator {

    private TupleBinding theBinding;

    // Use the constructor to set the tuple binding
    ItemNameKeyCreator(TupleBinding binding) {
        theBinding = binding;
    }

    // Abstract method that we must implement
    public boolean createSecondaryKey(SecondaryDatabase secDb,
             DatabaseEntry keyEntry,    // From the primary
             DatabaseEntry dataEntry,   // From the primary
             DatabaseEntry resultEntry) // set the key data on this.
         throws DatabaseException {

        if (dataEntry != null) {
            // Convert dataEntry to an Inventory object
            Inventory inventoryItem =
                  (Inventory)theBinding.entryToObject(dataEntry);
            // Get the item name and use that as the key
            String theItem = inventoryItem.getItemName();
            try {
                resultEntry.setData(theItem.getBytes("UTF-8"));
            } catch (IOException willNeverOccur) {}
        }
        return true;
    }
}
