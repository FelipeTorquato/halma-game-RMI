package server;

import shared.IGameServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HalmaServer extends JFrame {

    private JTextField portField;
    private JLabel statusLabel;
    private JButton startButton;

    public HalmaServer() {
        // Configuração da janela
        setTitle("Halma Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(350, 200);
        setLayout(new BorderLayout(10, 10));
        setLocationRelativeTo(null); // Centraliza na tela

        // Texto superior "Halma Server"
        JLabel titleLabel = new JLabel("Halma Server", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        add(titleLabel, BorderLayout.NORTH);

        // Painel central para porta e status
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 5, 5));

        // Campo para a porta com padrão 1099
        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel portLabel = new JLabel("Porta:");
        portField = new JTextField("1099", 6);
        portPanel.add(portLabel);
        portPanel.add(portField);
        centerPanel.add(portPanel);

        // Texto de status (vermelho inicialmente)
        statusLabel = new JLabel("Servidor não inicializado", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setForeground(Color.RED);
        centerPanel.add(statusLabel);

        add(centerPanel, BorderLayout.CENTER);

        // Botão "Iniciar"
        startButton = new JButton("Iniciar");
        startButton.addActionListener(this::startServer);
        JPanel bottomPanel = new JPanel();
        bottomPanel.add(startButton);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void startServer(ActionEvent e) {
        String portText = portField.getText().trim();
        int port = 1099;

        // Validação da porta
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            System.err.println("Argumento de porta inválido.");
            JOptionPane.showMessageDialog(this, "Número de porta inválido.", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Inicialização do RMI
        try {
            GameServerImpl gameServer = new GameServerImpl();
            IGameServer stub = (IGameServer) UnicastRemoteObject.exportObject(gameServer, 0);

            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind("HalmaGameServer", stub); // Registra o serviço

            // Sucesso no terminal
            System.out.println("Servidor Halma RMI pronto na porta " + port + ".");

            // Atualiza a interface para o estado "Inicializado"
            statusLabel.setText("Servidor inicializado");
            statusLabel.setForeground(Color.GREEN); // Verde

            // Bloqueia controles para evitar reinicialização
            startButton.setEnabled(false);
            portField.setEditable(false);

        } catch (IOException ex) {
            System.err.println("Erro no servidor RMI: " + ex.getMessage());
            ex.printStackTrace();

            JOptionPane.showMessageDialog(this, "Erro ao iniciar servidor:\n" + ex.getMessage(), "Erro de Inicialização", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            HalmaServer serverFrame = new HalmaServer();
            serverFrame.setVisible(true);
        });
    }
}