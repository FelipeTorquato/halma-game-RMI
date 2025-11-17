package server;

import shared.IClientCallback;
import shared.IGameServer;
import shared.IGameSession;

import java.rmi.RemoteException;

public class GameServerImpl implements IGameServer {

    private IClientCallback waitingPlayer = null;

    // Armazena temporariamente a sessão de jogo criada para o primeiro jogador
    private GameSessionImpl sessionForWaitingPlayer = null;

    public GameServerImpl() throws RemoteException {
        super();
    }

    @Override
    public synchronized IGameSession registerClient(IClientCallback clientCallback) throws RemoteException {
        if (waitingPlayer == null) {
            // Lógica para o PRIMEIRO jogador a se conectar

            waitingPlayer = clientCallback;
            try {
                // Informa ao cliente que ele está aguardando
                clientCallback.onInfo("Aguardando oponente...");
            } catch (RemoteException e) {
                waitingPlayer = null;
                throw e;
            }

            try {
                // Coloca a thread do primeiro jogador em espera (wait). Ela ficará bloqueada aqui até que o segundo jogador chegar
                // e chamar notifyAll()
                wait();
            } catch (InterruptedException e) {
                if (waitingPlayer == clientCallback) {
                    waitingPlayer = null;
                }
                throw new RemoteException("A espera pelo oponente foi interrompida.", e);
            }

            // Quando a thread acorda após o notifyAll, a sessão de jogo foi criada e armazenada em sessionForWaitingPlayer
            GameSessionImpl session = this.sessionForWaitingPlayer;
            this.sessionForWaitingPlayer = null; // Limpa a referência temporária

            if (session == null) {
                throw new RemoteException("Falha ao parear a partida. A sessão não foi criada.");
            }

            // Retorna a sessão de jogo para o primeiro jogador
            return session;
        } else {
            // Lógica para o SEGUNDO jogador a se conectar

            System.out.println("Pareando jogadores e iniciando nova partida.");

            IClientCallback player1Callback = waitingPlayer;
            IClientCallback player2Callback = clientCallback;

            // Limpa o slot de espera, pois a vaga foi preenchida
            waitingPlayer = null;

            // Cria a nova sessão de jogo com os dois callbacks
            GameSessionImpl gameSession = new GameSessionImpl(player1Callback, player2Callback);

            // Armazena a sessão para o primeiro jogador (que ainda está em wait())
            this.sessionForWaitingPlayer = gameSession;

            // Acorda a thread do primeiro jogador
            notifyAll();

            // Inicia o jogo (enviando 'onGameStart', 'onWelcome', etc.). Isso é feito depois de notificar, para que o primeiro jogador
            // já tenha recebido seu stub de sessão quando o jogo começar
            gameSession.startGame();

            // Retorna a sessão de jogo para o segundo jogador
            return gameSession;
        }
    }
}
