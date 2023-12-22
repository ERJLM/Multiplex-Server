import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;



public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    private String DNSName;
    private int port;




    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }


    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    run(false);
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
        DNSName = server;
        this.port = port;


    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        chatArea.append(message + '\n');
    }


    // Método principal do objecto
    public void run(boolean bool) throws IOException {
        // PREENCHER AQUI
        // PREENCHER AQUI com código que envia a mensagem ao servidor

        String sentence;
        String response;
        String text = chatBox.getText();
        InputStream input = bool ? System.in : new ByteArrayInputStream(text.getBytes("UTF-8"));
        BufferedReader inFromUser =
                new BufferedReader(new InputStreamReader(input));
        Socket clientSocket = new Socket(DNSName, port);
        DataOutputStream outToServer =
                new DataOutputStream(clientSocket.getOutputStream());
        BufferedReader inFromServer =
                new BufferedReader(new
                        InputStreamReader(clientSocket.getInputStream()));
        Iterator<String> it = inFromUser.lines().iterator();
        while(it.hasNext()) {
            sentence = it.next();
            outToServer.writeBytes(sentence + '\n');
            System.out.println("FROM CLIENT: " + sentence);
            newMessage(sentence);
            response = inFromServer.readLine();
            System.out.println("FROM SERVER: " + response);
            newMessage(response);
        }

       clientSocket.close();
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run(true); // true if the input is from the terminal, otherwise is false
    }

}


class jtextfieldinputstream extends InputStream {
    private byte[] contents = new byte[0];
    private int pointer = 0;

    public jtextfieldinputstream(final JTextField text) {

        text.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    contents = text.getText().getBytes();
                    pointer = 0;
                    text.setText("");
                }
                super.keyReleased(e);
            }
        });
    }

    @Override
    public int read() throws IOException {
        if (contents == null || pointer >= contents.length) return -1;
        return this.contents[pointer++] & 0xFF;
    }
}