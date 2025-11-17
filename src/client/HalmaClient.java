package client;

import shared.IClientCallback;
import shared.IGameServer;
import shared.IGameSession;

import javax.swing.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HalmaClient {
    // Campos RMI
    private IGameServer serverStub;
    private IGameSession sessionStub;
    private IClientCallback clientCallback;

    private final GameFrame gameFrame;
    private String lastGameStats;
    private int playerId; // Armazena o ID do jogador recebido do servidor

    public HalmaClient() {
        gameFrame = new GameFrame(this);
        gameFrame.setVisible(true);

        String serverInput = JOptionPane.showInputDialog(gameFrame, "Entre com o endereço IP e a porta do servidor RMI (ex: localhost:1099):", "localhost:1099");

        if (serverInput != null && !serverInput.trim().isEmpty()) {
            String[] parts = serverInput.split(":");
            String serverAddress = parts[0];
            int port = 1099; // Porta padrão
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(gameFrame, "Porta inválida. Usando a porta padrão 1099.", "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            }
            connect(serverAddress, port);
        } else {
            System.exit(0);
        }
    }

    // Desconecta o cliente, desregistrando seu objeto de callback RMI
    public void shutdown() {
        try {
            // Tenta desregistrar o objeto de callback RMI
            if (clientCallback != null) {
                UnicastRemoteObject.unexportObject(clientCallback, true);
            }
        } catch (Exception e) {
            System.err.println("Erro ao desregistrar o callback do cliente: " + e.getMessage());
        }
    }

    // Conecta-se ao servidor RMI, registra o callback e aguarda o início do jogo
    public void connect(String serverAddress, int port) {
        try {
            // Cria e exporta o objeto de callback do cliente
            clientCallback = new ClientCallbackImpl(gameFrame, this);

            // Localiza o RMI Registry no servidor
            Registry registry = LocateRegistry.getRegistry(serverAddress, port);

            // Procura pelo serviço de matchmaking (registrado pelo HalmaServer)
            serverStub = (IGameServer) registry.lookup("HalmaGameServer");

            gameFrame.updateStatus("Conectado. Aguardando por um oponente...");

            // Registra-se no servidor
            // A chamada registerClient é síncrona e irá bloquear a thread principal até que o GameServerImpl encontre um par e retorne
            // o stub da sessão de jogo. Para evitar que a interface congele, isso é ser feito em uma nova thread
            new Thread(() -> {
                try {
                    sessionStub = serverStub.registerClient(clientCallback);
                    // Se a chamada retornou, é porque o jogo está prestes a começar.
                    // As atualizações de status serão enviadas pelo servidor via callbacks
                } catch (RemoteException e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(gameFrame, "Erro ao registrar no servidor: " + e.getMessage(), "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
                        e.printStackTrace();
                        System.exit(1);
                    });
                }
            }).start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(gameFrame, "Não foi possível se conectar ao servidor RMI.", "Erro de conexão", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
    }

    // --- Métodos de Envio (invocações RMI) ---

    public void sendMove(int startRow, int startCol, int endRow, int endCol) {
        if (sessionStub != null) {
            try {
                sessionStub.move(this.playerId, startRow, startCol, endRow, endCol);
            } catch (RemoteException e) {
                handleConnectionError(e);
            }
        }
    }

    public void sendChatMessage(String message) {
        if (sessionStub != null) {
            try {
                sessionStub.sendChat(this.playerId, message);
            } catch (RemoteException e) {
                handleConnectionError(e);
            }
        }
    }

    public void sendForfeit() {
        if (sessionStub != null) {
            try {
                sessionStub.forfeit(this.playerId);
            } catch (RemoteException e) {
                handleConnectionError(e);
            }
        }
    }

    public void sendEndChainJump() {
        if (sessionStub != null) {
            try {
                sessionStub.endChainJump(this.playerId);
            } catch (RemoteException e) {
                handleConnectionError(e);
            }
        }
    }

    // --- Métodos chamados pelo ClientCallbackImpl ---

    // Chamado quando o servidor envia o ID do jogador
    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    // Chamado quando o servidor envia as estatísticas
    public void setLastGameStats(String stats) {
        this.lastGameStats = stats;
    }

    // Exibe os resultados do jogo e encerra a aplicação
    public void showResultsAndExit() {
        if (lastGameStats != null) {
            ResultsDialog resultsDialog = new ResultsDialog(gameFrame, lastGameStats);
            resultsDialog.setVisible(true);
        }
        shutdown();
        gameFrame.closeApplication();
    }

    // Centraliza o tratamento de erros de conexão RMI
    private void handleConnectionError(RemoteException e) {
        SwingUtilities.invokeLater(() -> {
            gameFrame.updateStatus("Conexão com o servidor perdida: " + e.getMessage());
            JOptionPane.showMessageDialog(gameFrame, "A conexão com o servidor foi perdida.", "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
        });
        e.printStackTrace();
    }


    // Notifica o servidor que o cliente está desconectando
    public void notifyDisconnect() {
        if (sessionStub != null) {
            try {
                sessionStub.disconnect(this.playerId);
            } catch (RemoteException e) {
                System.err.println("Não foi possível notificar o servidor da desconexão: " + e.getMessage());
            }
        }
        shutdown();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(HalmaClient::new);
    }
}