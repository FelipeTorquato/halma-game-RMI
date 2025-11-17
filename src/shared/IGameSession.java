package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGameSession extends Remote {
    void move(int playerId, int startRow, int startCol, int endRow, int endCol) throws RemoteException;

    void sendChat(int playerId, String message) throws RemoteException;

    void forfeit(int playerId) throws RemoteException;

    void endChainJump(int playerId) throws RemoteException;

    void disconnect(int playerId) throws RemoteException;
}
