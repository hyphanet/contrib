package jca.simple;

import java.rmi.RemoteException;
import javax.ejb.CreateException;
import javax.ejb.EJBHome;

public interface SimpleHome extends EJBHome {

   public Simple create()
      throws RemoteException, CreateException;
}
