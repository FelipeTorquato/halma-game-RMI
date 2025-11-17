package server;

import game.Board;
import shared.IClientCallback;
import shared.IGameSession;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

// Gerencia uma partida entre dois jogadores, fazendoo pareamento entre os dois jogadores
public class GameSessionImpl extends UnicastRemoteObject implements IGameSession {
    private final IClientCallback player1Callback;
    private final IClientCallback player2Callback;
    private final Board board;
    private int currentPlayer;

    private int player1MoveCount = 0;
    private int player2MoveCount = 0;
    private int player1InvalidAttempts = 0;
    private int player2InvalidAttempts = 0;
    private final List<String> chatHistory = new ArrayList<>();
    private String winnerInfo = "O jogo encerrou inesperadamente.";
    private boolean gameEnded = false;
    private List<int[]> chainJumpPath;
    private Set<String> forbiddenChainJumps;

    private boolean isChainJumpActive = false;
    private int chainJumpRow;
    private int chainJumpCol;

    /**
     * Construtor da sessão de jogo. Inicializa os callbacks dos jogadores, o tabuleiro
     * e define o Jogador 1 como o primeiro a jogar
     * @param player1 Callback do primeiro jogador
     * @param player2 Callback do segundo jogador
     * @throws RemoteException Se ocorrer um erro de comunicação RMI
     */
    public GameSessionImpl(IClientCallback player1, IClientCallback player2) throws RemoteException {
        super();
        this.player1Callback = player1;
        this.player2Callback = player2;
        this.board = new Board();
        this.currentPlayer = 1; // Jogador 1 inicia o jogo
    }

    /**
     * Inicia a partida, enviando mensagens de boas-vindas e de início de jogo
     * para ambos os clientes e definindo o turno inicial
     * @throws RemoteException Se ocorrer um erro de comunicação RMI com os clientes
     */
    public void startGame() throws RemoteException {
        try {
            player1Callback.onWelcome(1);
            player2Callback.onWelcome(2);

            player1Callback.onOpponentFound();
            player2Callback.onOpponentFound();

            updateTurn();
        } catch (RemoteException e) {
            System.out.println("Erro ao iniciar a partida: " + e.getMessage());
            handleDisconnect(e);
        }
    }

    // Atualiza o estado de turno dos jogadores, notificando-os sobre quem deve jogar
    private void updateTurn() {
        try {
            if (currentPlayer == 1) {
                player1Callback.onSetTurn(true);
                player2Callback.onSetTurn(false);
            } else {
                player2Callback.onSetTurn(true);
                player1Callback.onSetTurn(false);
            }
        } catch (RemoteException e) {
            System.err.println("Erro ao atualizar turno (jogador desconectado): " + e.getMessage());
            handleDisconnect(e);
        }
    }

    // Realiza a troca de turno
    private void switchTurn() {
        currentPlayer = (currentPlayer == 1) ? 2 : 1;
        updateTurn();
    }

    /**
     * Executa o movimento para o player atual. Esse metodo valida o movimento, trata da sequencia de pulos
     * e atualiza o estado do jogo posteriormente. Esse metodo também gerencia a comunicação entre os players
     * através dos callbacks
     *
     * @param playerId O ID do player que está fazendo o movimento
     * @param startRow A linha inicial da peça a ser movida
     * @param startCol A coluna inicial da peça a ser movida
     * @param endRow   A linha final da peça a ser movida
     * @param endCol   A coluna final da peça a ser movida
     * @throws RemoteException Se ocorrer um erro de comunicação RMI
     */
    @Override
    public synchronized void move(int playerId, int startRow, int startCol, int endRow, int endCol) throws RemoteException {
        IClientCallback senderCallback = (playerId == 1) ? player1Callback : player2Callback;
        IClientCallback opponentCallback = (playerId == 1) ? player2Callback : player1Callback;

        if (playerId != currentPlayer) {
            try {
                senderCallback.onError("Não é o seu turno.");
            } catch (RemoteException e) {
                handleDisconnect(e);
            }
            if (playerId == 1) player1InvalidAttempts++;
            else player2InvalidAttempts++;
            return;
        }

        try {
            if (isChainJumpActive) {
                if (startRow != chainJumpRow || startCol != chainJumpCol) {
                    senderCallback.onError("Você deve continuar pulando com a mesma peça.");
                    return;
                }
                // Lógica anti-loop: verifica se o pulo está na lista de proibidos
                String proposedJump = startRow + "," + startCol + "," + endRow + "," + endCol;
                if (forbiddenChainJumps.contains(proposedJump)) {
                    senderCallback.onError("Este pulo foi desfeito e não pode ser refeito na mesma jogada.");
                    return;
                }
            }

            if (board.movePiece(startRow, startCol, endRow, endCol, currentPlayer, isChainJumpActive)) {
                if (playerId == 1) player1MoveCount++;
                else player2MoveCount++;

                boolean wasJump = Math.abs(startRow - endRow) > 1 || Math.abs(startCol - endCol) > 1;

                if (wasJump && board.canJumpFrom(endRow, endCol, startRow, startCol)) {
                    if (!isChainJumpActive) {
                        // Início de uma nova cadeia de pulos
                        isChainJumpActive = true;
                        chainJumpPath = new ArrayList<>();
                        chainJumpPath.add(new int[]{startRow, startCol});
                        forbiddenChainJumps = new HashSet<>();
                    }

                    // Detecta um movimento de desfazer e proíbe o movimento original
                    if (chainJumpPath.size() >= 2) {
                        int[] previousPos = chainJumpPath.get(chainJumpPath.size() - 2);
                        if (endRow == previousPos[0] && endCol == previousPos[1]) {
                            // O jogador pulou de volta (ex: de X para Y, vindo de ...Z->Y->X)
                            // O pulo a ser proibido é o que foi desfeito (Y -> X)
                            int[] lastPos = chainJumpPath.get(chainJumpPath.size() - 1); // Posição X (de onde o pulo X->Y começou)
                            // O pulo proibido é Y -> X. previousPos é Y, lastPos é X
                            String forbiddenJump = previousPos[0] + "," + previousPos[1] + "," + lastPos[0] + "," + lastPos[1];
                            forbiddenChainJumps.add(forbiddenJump);
                        }
                    }

                    chainJumpPath.add(new int[]{endRow, endCol});
                    chainJumpRow = endRow;
                    chainJumpCol = endCol;

                    senderCallback.onJumpMove(startRow, startCol, endRow, endCol);
                    opponentCallback.onOpponentMoved(startRow, startCol, endRow, endCol);
                    senderCallback.onChainJumpOffer(endRow, endCol);
                } else {
                    isChainJumpActive = false;
                    chainJumpPath = null;
                    forbiddenChainJumps = null;

                    senderCallback.onValidMove(startRow, startCol, endRow, endCol);
                    opponentCallback.onOpponentMoved(startRow, startCol, endRow, endCol);

                    if (board.checkForWinner(currentPlayer)) {
                        if (gameEnded) return;
                        winnerInfo = "Jogador " + currentPlayer + " ganhou por chegar no destino!";
                        endGame(senderCallback, opponentCallback);
                    } else {
                        switchTurn();
                    }
                }
            } else {
                if (playerId == 1) player1InvalidAttempts++;
                else player2InvalidAttempts++;
                senderCallback.onError("Movimento inválido.");
            }
        } catch (RemoteException e) {
            System.err.println("Erro durante o movimento (jogador desconectado): " + e.getMessage());
            handleDisconnect(e);
        } catch (Exception e) {
            try {
                senderCallback.onError("Comando de movimento malformado.");
            } catch (RemoteException re) {
                handleDisconnect(re);
            }
        }
    }

    /**
     * Envia uma mensagem de chat de um jogador para o outro jogador na sessão de jogo
     * A mensagem é formatada com o ID do jogador e armazenada no histórico do chat
     * A mensagem formatada é então enviada para os callbacks de ambos os jogadores
     *
     * @param playerId O ID do player que enviou a mensagem
     * @param message A mensagem enviada
     * @throws RemoteException Se ocorrer um erro de comunicação RMI
     */
    @Override
    public synchronized void sendChat(int playerId, String message) throws RemoteException {
        // Lógica de broadcastChat
        String formattedMessage = "Jogador " + playerId + ": " + message;
        chatHistory.add(formattedMessage);

        try {
            player1Callback.onChatMessage(formattedMessage);
            player2Callback.onChatMessage(formattedMessage);
        } catch (RemoteException e) {
            System.err.println("Erro ao enviar chat (jogador desconectado): " + e.getMessage());
            handleDisconnect(e);
        }
    }

    /**
     * Lida com a desistência de um jogador na sessão de jogo.
     * Este metodo atualiza as informações do vencedor, notifica ambos os jogadores sobre o evento de desistência
     * por meio de seus respectivos callbacks e encerra a sessão de jogo
     *
     * @param playerId O ID do player que está desistindo da partida
     * @throws RemoteException Se ocorrer um erro de comunicação RMI
     */
    @Override
    public synchronized void forfeit(int playerId) throws RemoteException {
        // Lógica de 'handleForfeit'
        IClientCallback forfeiterCallback = (playerId == 1) ? player1Callback : player2Callback;
        IClientCallback winnerCallback = (playerId == 1) ? player2Callback : player1Callback;
        int winnerId = (playerId == 1) ? 2 : 1;
        winnerInfo = "Jogador " + winnerId + " ganhou pela desistência do oponente.";

        endGame(winnerCallback, forfeiterCallback, true, false);
    }

    /**
     * Finaliza uma sequencia de pulos ativa para o jogador da vez.
     * Este metodo verifica se a sequencia de pulos está ativo e sendo executado pelo jogador atual,
     * então redefine o estado do salto em cadeia e atualiza o status do jogo de acordo
     *
     * @param playerId O ID do player a ser encerrada a sequencia de pulos
     * @throws RemoteException Se ocorrer um erro de comunicação RMI
     */
    @Override
    public synchronized void endChainJump(int playerId) throws RemoteException {
        if (isChainJumpActive && playerId == currentPlayer) {
            isChainJumpActive = false;
            chainJumpPath = null; // Limpa o caminho do pulo
            forbiddenChainJumps = null; // Limpa os pulos proibidos

            IClientCallback senderCallback = (playerId == 1) ? player1Callback : player2Callback;
            IClientCallback opponentCallback = (playerId == 1) ? player2Callback : player1Callback;

            if (board.checkForWinner(currentPlayer)) {
                winnerInfo = "Jogador " + currentPlayer + " ganhou por chegar no destino!";
                endGame(senderCallback, opponentCallback);
            } else {
                switchTurn();
            }
        }
    }

    /**
     * Trata a desconexão de um jogador durante a partida.
     * Se a partida já tiver terminado, o metodo retorna imediatamente.
     * Caso contrário, determina o vencedor como o oponente do jogador desconectado, atualiza as informações do vencedor
     * e encerra a partida notificando apenas o vencedor sobre a desconexão.
     *
     * @param playerId O ID do player que está desconectando
     * @throws RemoteException Se ocorrer um erro de comunicação RMI
     */
    @Override
    public synchronized void disconnect(int playerId) throws RemoteException {
        if (gameEnded) return;

        IClientCallback winnerCallback;
        IClientCallback loserCallback = (playerId == 1) ? player1Callback : player2Callback;
        int winnerId;

        if (playerId == 1) {
            winnerCallback = player2Callback;
            winnerId = 2;
        } else {
            winnerCallback = player1Callback;
            winnerId = 1;
        }

        winnerInfo = "Jogador " + winnerId + " ganhou porque o oponente se desconectou.";

        // Encerra o jogo, notificando apenas o vencedor.
        endGame(winnerCallback, loserCallback, false, true);
    }

    // Metodo responsável por tratar da desconexão abrupta de um jogador (ex: fechamento do jogo)
    private synchronized void handleDisconnect(RemoteException e) {
        if (gameEnded) return;
        gameEnded = true;

        // Tenta pingar os jogadores para ver quem caiu
        boolean player1Alive = isAlive(player1Callback);

        IClientCallback winnerCallback, loserCallback;
        int winnerId;

        if (!player1Alive) {
            winnerCallback = player2Callback;
            loserCallback = player1Callback;
            winnerId = 2;
        } else {
            winnerCallback = player1Callback;
            loserCallback = player2Callback;
            winnerId = 1;
        }

        winnerInfo = "Jogador " + winnerId + " ganhou porque o oponente se desconectou.";
        String stats = buildGameOverStats();

        try {
            // Notifica o vencedor
            winnerCallback.onGameOverStats(stats);
            winnerCallback.onOpponentForfeit();
        } catch (RemoteException re) {
            System.err.println("Ambos os jogadores parecem desconectados.");
        }

        shutdownGame();
    }

    // Metodo auxiliar para checar conexão
    private boolean isAlive(IClientCallback callback) {
        try {
            callback.ping();
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Constrói uma string formatada com as estatísticas finais do jogo, incluindo
     * informações do vencedor, contagem de movimentos, tentativas inválidas e histórico de chat
     * @return Uma string contendo as estatísticas do jogo
     */
    private String buildGameOverStats() {
        String chatLog = String.join("|", chatHistory);

        StringJoiner stats = new StringJoiner(":"); // Usando : como no protocolo original
        stats.add(winnerInfo);
        stats.add(String.valueOf(player1MoveCount));
        stats.add(String.valueOf(player1InvalidAttempts));
        stats.add(String.valueOf(player2MoveCount));
        stats.add(String.valueOf(player2InvalidAttempts));
        stats.add(chatLog);

        return stats.toString();
    }


    // Encerra o jogo e notifica os clientes.
    // Sobrecarga para vitória/derrota normal.
    private void endGame(IClientCallback winner, IClientCallback loser) {
        endGame(winner, loser, false, false);
    }

    /**
     * Lógica principal de encerramento do jogo
     *
     * @param forfeit    Indica se o encerramento foi por desistência.
     * @param disconnect Indica se o encerramento foi por desconexão.
     */
    private void endGame(IClientCallback winner, IClientCallback loser, boolean forfeit, boolean disconnect) {
        if (gameEnded) return;
        gameEnded = true;

        String stats = buildGameOverStats();

        try {
            winner.onGameOverStats(stats);
            if (disconnect) {
                winner.onOpponentForfeit(); // Reutiliza a mensagem de desistência para desconexão
            } else if (forfeit) {
                winner.onOpponentForfeit();
                loser.onGameOverStats(stats);
                loser.onDefeat("Você desistiu da partida.");
            } else {
                winner.onVictory();
                loser.onGameOverStats(stats);
                loser.onDefeat("Você perdeu a partida.");
            }
        } catch (RemoteException e) {
            System.err.println("Erro ao notificar o fim de jogo (jogador desconectado): " + e.getMessage());
        }

        shutdownGame();
    }

    // Desregistra o objeto de sessão de jogo do runtime RMI
    private void shutdownGame() {
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception e) {
            System.err.println("Erro ao desregistrar GameSessionImpl: " + e.getMessage());
        }
    }
}
