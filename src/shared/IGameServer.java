package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGameServer extends Remote {
    IGameSession registerClient(IClientCallback clientCallback) throws RemoteException;
}
