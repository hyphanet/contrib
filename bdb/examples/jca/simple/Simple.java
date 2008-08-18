package jca.simple;

import java.rmi.RemoteException;
import javax.ejb.EJBObject;

public interface Simple extends EJBObject {

    public void put(String key, String data)
        throws RemoteException;

    public String get(String key)
        throws RemoteException;

    public void removeDatabase()
	throws RemoteException;
}
