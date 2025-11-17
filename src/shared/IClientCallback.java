package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Métodos a serem implementados pelo servidor
public interface IClientCallback extends Remote {
    void ping() throws RemoteException;

    void onWelcome(int playerId) throws RemoteException;

    void onOpponentFound() throws RemoteException;

    void onSetTurn(boolean isMyTurn) throws RemoteException;

    void onValidMove(int startRow, int startCol, int endRow, int endCol) throws RemoteException;

    void onOpponentMoved(int startRow, int startCol, int endRow, int endCol) throws RemoteException;

    void onJumpMove(int startRow, int startCol, int endRow, int endCol) throws RemoteException;

    void onChatMessage(String message) throws RemoteException;

    void onChainJumpOffer(int endRow, int endCol) throws RemoteException;

    void onVictory() throws RemoteException;

    void onDefeat(String reason) throws RemoteException;

    void onOpponentForfeit() throws RemoteException;

    void onGameOverStats(String statsData) throws RemoteException;

    void onError(String message) throws RemoteException;

    void onInfo(String message) throws RemoteException;
}
