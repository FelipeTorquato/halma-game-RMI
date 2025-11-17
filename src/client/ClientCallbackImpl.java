package client;

import shared.IClientCallback;

import javax.swing.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Classe que implementa a interface de callback RMI
 * O servidor invocará os métodos desta classe remotamente para enviar atualizações ao cliente
 */
public class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {

    //Referência ao GameFrame principal para atualizar a UI
    private transient GameFrame gameFrame;

    // Referência ao HalmaClient para lógica de estado (ex: salvar estatísticas)
    private transient HalmaClient halmaClient;

    /**
     * Construtor que recebe as referências necessárias
     *
     * @param frame  A instância do GameFrame para atualizações de UI
     * @param client A instância do HalmaClient para controle de estado
     * @throws RemoteException Lançado pelo construtor de UnicastRemoteObject
     */
    public ClientCallbackImpl(GameFrame frame, HalmaClient client) throws RemoteException {
        super();
        this.gameFrame = frame;
        this.halmaClient = client;
    }

    // Metodo de keep-alive chamado pelo servidor para verificar a conexão
    @Override
    public void ping() throws RemoteException {
    }

    // Chamado pelo servidor para atribuir um ID ao jogador e atualizar o título da janela
    @Override
    public void onWelcome(int playerId) throws RemoteException {
        halmaClient.setPlayerId(playerId);
        SwingUtilities.invokeLater(() -> {
            gameFrame.setPlayerId(playerId); // Atualiza o título do GameFrame
        });
    }

    // Exibe uma mensagem de status geral na interface
    @Override
    public void onInfo(String message) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            gameFrame.updateStatus(message);
        });
    }

    // Notifica o cliente que um oponente foi encontrado
    @Override
    public void onOpponentFound() throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            gameFrame.updateStatus("Oponente encontrado. Iniciando partida...");
        });
    }

    // Define se é o turno do jogador e atualiza o status
    @Override
    public void onSetTurn(boolean isMyTurn) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            gameFrame.setMyTurn(isMyTurn);
        });
    }

    // Confirma o movimento válido do próprio jogador no tabuleiro
    @Override
    public void onValidMove(int startRow, int startCol, int endRow, int endCol) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            gameFrame.updateBoard(startRow, startCol, endRow, endCol);
        });
    }

    // Atualiza o tabuleiro com o movimento do oponente
    @Override
    public void onOpponentMoved(int startRow, int startCol, int endRow, int endCol) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            gameFrame.updateBoard(startRow, startCol, endRow, endCol);
        });
    }

    // Atualiza o tabuleiro após um pulo, mantendo a peça selecionada
    @Override
    public void onJumpMove(int startRow, int startCol, int endRow, int endCol) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            gameFrame.updateBoardAndKeepSelection(startRow, startCol, endRow, endCol);
        });
    }

    // Adiciona uma mensagem ao chat
    @Override
    public void onChatMessage(String formattedMessage) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            gameFrame.addChatMessage(formattedMessage);
        });
    }

    // Notifica o jogador sobre uma oferta de pulo em cadeia
    @Override
    public void onChainJumpOffer(int newEndRow, int newEndCol) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            gameFrame.updateBoardAfterJumpAndPrompt(newEndRow, newEndCol);
        });
    }

    // Notifica o jogador sobre a vitória e encerra o jogo
    @Override
    public void onVictory() throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(gameFrame, "Parabéns, você ganhou!", "Fim de jogo",
                    JOptionPane.INFORMATION_MESSAGE);
            halmaClient.showResultsAndExit();
        });
    }

    // Notifica o jogador sobre a derrota e encerra o jogo
    @Override
    public void onDefeat(String reason) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(gameFrame, "Você perdeu a partida. Motivo: " + reason, "Fim de jogo",
                    JOptionPane.WARNING_MESSAGE);
            halmaClient.showResultsAndExit();
        });
    }

    // Notifica o jogador que o oponente desistiu e encerra o jogo
    @Override
    public void onOpponentForfeit() throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(gameFrame, "Seu oponente desistiu. Você ganhou!", "Vitória",
                    JOptionPane.INFORMATION_MESSAGE);
            halmaClient.showResultsAndExit();
        });
    }

    // Recebe os dados de estatísticas do servidor e os armazena no HalmaClient
    @Override
    public void onGameOverStats(String statsData) throws RemoteException {
        halmaClient.setLastGameStats(statsData);
    }

    // Exibe uma mensagem de erro vinda do servidor
    @Override
    public void onError(String message) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(gameFrame, message, "Erro", JOptionPane.ERROR_MESSAGE);
        });
    }
}