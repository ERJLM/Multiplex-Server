import java.io.*;
import java.net.*;
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

    private String server;

    private int port;

    private DataOutputStream outToServer;

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
                    newMessage(chatBox.getText());
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

        this.server = server;
        this.port = port;

    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
        outToServer.write(normalizeOutput(message).getBytes("UTF-8"));
    }

    private String normalizeOutput(String message)
    {
        if(message.startsWith("/")&&!isCommand(message))
            message = "/"+message;
        message += '\n';
        return message;
    }

    private boolean isCommand(String message)
    {
        return message.startsWith("/nick ")||message.startsWith("/join ")
                ||message.startsWith("/priv ")||message.equals("/leave")
                ||message.equals("/bye");
    }

    private String normalizeResponse(String message)
    {
        String response = "";
        if(message.startsWith("ERROR"))
            response = "ERRO";

        else if(message.startsWith("JOINED"))
            response = joinedResponse(message);

        else if(message.startsWith("LEFT"))
            response = leftResponse(message);

        else if(message.startsWith("MESSAGE"))
            response = messageResponse(message);

        else if(message.startsWith("PRIVATE"))
            response = privateResponse(message);

        else if(message.startsWith("NEWNICK"))
            response = newNickResponse(message);

        else if(message.startsWith("BYE"))
            response = "Tchau!";

        else response = message;

        response += "\n";
        return response;
    }

    private String joinedResponse(String message) {
        return message.substring(message.indexOf(" ")+1)+" entrou na sala";
    }

    private String leftResponse(String message) {
        return message.substring(message.indexOf(" ")+1)+" saiu da sala";
    }

    private String messageResponse(String message) {
        message = message.substring(message.indexOf(" ")+1);
        int space = message.indexOf(" ");
        String name = message.substring(0,space);
        return name+": "+message.substring(space+1);
    }

    private String privateResponse(String message) {
        message = message.substring(message.indexOf(" ")+1);
        int space = message.indexOf(" ");
        String name = message.substring(0,space);
        return "[MENSAGEM PRIVADA] "+name+": "+message.substring(space+1);
    }

    private String newNickResponse(String message) {
        message = message.substring(message.indexOf(" ")+1);
        int space = message.indexOf(" ");
        String oldName = message.substring(0,space);
        return oldName+" mudou de nome para "+message.substring(space+1);
    }

    // Método principal do objecto
    public void run() throws IOException{
        // PREENCHER AQUI
        Socket clientSocket = null;
        try {
            clientSocket =  new Socket(server, port);
            outToServer = new DataOutputStream(clientSocket.getOutputStream());
            InputStream inputStream = clientSocket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferedReader.readLine())!=null) {
                printMessage(normalizeResponse(line));
            }
            clientSocket.close();
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
        finally {
            if(clientSocket!=null&&!clientSocket.isClosed())
                clientSocket.close();
        }
        //System.exit(0);
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
