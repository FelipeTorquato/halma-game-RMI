package server;

import shared.IGameServer;

import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HalmaServer {
    public static void main(String[] args) {
        try {
            int port = 1099; // Porta padrão
            if (args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.err.println("Argumento de porta inválido. Usando a porta padrão: " + port);
                }
            }

            GameServerImpl gameServer = new GameServerImpl();
            IGameServer stub = (IGameServer) UnicastRemoteObject.exportObject(gameServer, 0);

            Registry registry = LocateRegistry.createRegistry(port); // Porta RMI
            registry.rebind("HalmaGameServer", stub); // Nome do serviço

            System.out.println("Servidor Halma RMI pronto na porta " + port + ".");

            /*
             * Sugestão de Melhoria (Desligamento Gracioso):
             * Atualmente, o servidor termina abruptamente se o processo for interrompido.
             * Para um desligamento mais "gracioso", um Shutdown Hook pode ser adicionado.
             * Isso garante que, ao encerrar o servidor (ex: com Ctrl+C), recursos
             * importantes sejam liberados.
             *
             * Exemplo de implementação:
             *
             * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
             *     try {
             *         registry.unbind("HalmaGameServer");
             *         UnicastRemoteObject.unexportObject(gameServer, true);
             *         System.out.println("Servidor Halma RMI desligado de forma graciosa.");
             *     } catch (Exception e) {
             *         System.err.println("Erro durante o desligamento do servidor: " + e.getMessage());
             *     }
             * }));
             */

        } catch (IOException e) {
            System.err.println("Erro no servidor RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
