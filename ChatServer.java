import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

class ClientData
{
    private String name, room;
    private State state;
    private String buffer;
    enum State
    {
        INIT,
        INSIDE,
        OUTSIDE
    }

    public ClientData() {
        this.state = State.INIT;
        this.buffer = "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getBuffer() {
        return buffer;
    }

    public void setBuffer(String buffer) {
        this.buffer = buffer;
    }
}
public class ChatServer
{
    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();
    static private Map<String,SocketChannel> currentUsers = new HashMap<>();
    static private Map<String,Set<SocketChannel>> currentRooms = new HashMap<>();

    static public void main( String args[] ) throws Exception {
        // Parse port from command line
        int port = Integer.parseInt( args[0] );

        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking( false );

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress( port );
            ss.bind( isa );

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register( selector, SelectionKey.OP_ACCEPT );
            System.out.println( "Listening on port "+port );

            while (true) {
                // See if we've had any activity -- either an incoming connection,
                // or incoming data on an existing connection
                int num = selector.select();

                // If we don't have any activity, loop around and wait again

                if (num == 0) {
                    continue;
                }

                // Get the keys corresponding to the activity that has been
                // detected, and process them one by one
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    // Get a key representing one of bits of I/O activity
                    SelectionKey key = it.next();

                    // What kind of activity is it?
                    if (key.isAcceptable()) {

                        // It's an incoming connection.  Register this socket with
                        // the Selector so we can listen for input on it
                        Socket s = ss.accept();
                        System.out.println( "Got connection from "+s );

                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking( false );

                        // Register it with the selector, for reading
                        SelectionKey selectionKey = sc.register( selector, SelectionKey.OP_READ );
                        selectionKey.attach(new ClientData());

                    } else if (key.isReadable()) {

                        SocketChannel sc = null;

                        try {

                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel)key.channel();
                            boolean ok = processInput( key );

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (!ok) {
                                handleDisconnection(sc,(ClientData) key.attachment());
                                Socket s = null;
                                try {
                                    s = sc.socket();
                                    System.out.println( "Closing connection to "+s );
                                    s.close();
                                } catch( IOException ie ) {
                                    System.err.println( "Error closing socket "+s+": "+ie );
                                }
                                key.cancel();
                            }

                        } catch( IOException ie ) {
                            handleDisconnection(sc,(ClientData) key.attachment());
                            try {
                                sc.close();
                            } catch( IOException ie2 ) { System.out.println( ie2 ); }

                            System.out.println( "Closed "+sc );

                            // On exception, remove this channel from the selector
                            key.cancel();
                        }
                    }
                }

                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        } catch( IOException ie ) {
            System.err.println( ie );
        }
    }

    static private void handleDisconnection(SocketChannel sc,ClientData clientData) throws IOException
    {
        //Leave room if State == INSIDE
        if(clientData.getState().equals(ClientData.State.INSIDE))
            leaveRoom(sc,clientData,false);
        //Remove from current users
        if(clientData.getName()!=null)
            currentUsers.remove(clientData.getName());
    }


    // Just read the message from the socket and send it to stdout
    static private boolean processInput(SelectionKey key) throws IOException {

        SocketChannel sc = (SocketChannel) key.channel();

        // Read the message to the buffer
        buffer.clear();
        sc.read( buffer );
        buffer.flip();

        // If no data, close the connection
        if (buffer.limit()==0) {
            return false;
        }

        // Decode the message
        String message = decoder.decode(buffer).toString();

        ClientData data = (ClientData)key.attachment();
        data.setBuffer(data.getBuffer()+message);

        if(message.charAt(message.length()-1)=='\n')
        {
            int size = data.getBuffer().length();
            message = data.getBuffer().substring(0,size-1);
            data.setBuffer("");
        }
        else return true;

        for(String content:message.split(System.lineSeparator()))
        {
            content = dropCtrlD(content);
            System.out.println(content);

            //Get current client data
            ClientData clientData = (ClientData) key.attachment();

            if(clientData.getState().equals(ClientData.State.INIT))
                clientData = solveInit(sc,content,clientData);
            else if(clientData.getState().equals(ClientData.State.OUTSIDE))
                clientData = solveOutside(sc,content,clientData);
            else clientData = solveInside(sc,content,clientData);

            //Check if is /bye
            if(clientData==null)
                return false;

            //Update client data
            //key.attach(clientData);
        }

        return true;
    }

    static String dropCtrlD(String message)
    {
        String ans = "";
        for(int i=0;i<message.length();++i)
            if(message.charAt(i)!=4)
                ans += message.charAt(i);
        return ans;
    }

    static ClientData solveInit(SocketChannel sc, String message, ClientData clientData) throws IOException
    {
        String response;

        if(message.startsWith("/nick"))
        {
            String name = dropCommand(message);
            if(currentUsers.containsKey(name))
                response = "ERROR";
            else
            {
                currentUsers.put(name,sc);
                clientData.setName(name);
                clientData.setState(ClientData.State.OUTSIDE);
                response = "OK";
            }
        }
        else if(message.equals("/bye"))
        {
            sendMessage(sc,"BYE\n");
            return null;
        }
        else response = "ERROR";

        response += '\n';
        sendMessage(sc,response);

        return clientData;
    }

    static ClientData solveOutside(SocketChannel sc, String message, ClientData clientData) throws IOException
    {
        if(message.startsWith("/nick"))
            changeNick(sc,message,clientData);
        else if(message.startsWith("/join"))
            joinRoom(sc,message,clientData);
        else if(message.startsWith("/priv"))
            sendPrivateMessage(sc,message,clientData);
        else if(message.equals("/bye"))
        {
            sendMessage(sc,"BYE\n");
            return null;
        }
        else
            sendMessage(sc,"ERROR\n");
        return clientData;
    }

    static ClientData solveInside(SocketChannel sc, String message, ClientData clientData) throws IOException
    {

        if(message.startsWith("/nick"))
        {
            String oldname = clientData.getName();
            String newname = dropCommand(message);
            if(changeNick(sc,message,clientData))
                broadcastMessage(clientData.getRoom(),"NEWNICK "+oldname+" "+newname+"\n",sc);
        }
        else if(message.startsWith("/join"))
            changeRoom(sc,message,clientData);
        else if(message.equals("/leave"))
            leaveRoom(sc,clientData,true);
        else if(message.startsWith("/priv"))
            sendPrivateMessage(sc,message,clientData);
        else if(message.equals("/bye"))
        {
            sendMessage(sc,"BYE\n");
            return null;
        }
        else if(message.startsWith("/")&&!message.startsWith("//"))
            sendMessage(sc,"ERROR\n");
        else
            sendNormalMessage(sc,message,clientData);
        return clientData;
    }

    static boolean changeNick(SocketChannel sc, String message, ClientData clientData) throws IOException
    {
        String name = dropCommand(message), response;
        boolean done = false;
        if(currentUsers.containsKey(name))
            response = "ERROR";
        else
        {
            currentUsers.remove(clientData.getName());
            currentUsers.put(name,sc);
            clientData.setName(name);
            response = "OK";
            done = true;
        }
        response += '\n';
        sendMessage(sc,response);
        return done;
    }

    static void changeRoom(SocketChannel sc, String message, ClientData clientData) throws IOException
    {
        leaveRoom(sc,clientData,false);
        joinRoom(sc,message,clientData);
    }

    static void leaveRoom(SocketChannel sc, ClientData clientData,boolean confirmationFlag) throws IOException
    {
        String room = clientData.getRoom();
        broadcastMessage(room,"LEFT "+clientData.getName()+"\n",sc);
        clientData.setState(ClientData.State.OUTSIDE);
        currentRooms.get(room).remove(sc);
        if(confirmationFlag)
            sendMessage(sc,"OK\n");
    }
    static void joinRoom(SocketChannel sc, String message, ClientData clientData) throws IOException
    {
        String room = dropCommand(message);
        if(!currentRooms.containsKey(room))
            currentRooms.put(room,new HashSet<>());
        broadcastMessage(room,"JOINED "+clientData.getName()+"\n",sc);
        currentRooms.get(room).add(sc);
        clientData.setRoom(room);
        clientData.setState(ClientData.State.INSIDE);
        sendMessage(sc,"OK\n");

    }

    static void sendPrivateMessage(SocketChannel sc, String message, ClientData clientData) throws IOException
    {
        message = dropCommand(message);
        String name = message.substring(0,message.indexOf(' ')),response;
        message = message.substring(message.indexOf(' ')+1);
        if(currentUsers.containsKey(name))
        {
            String sender = clientData.getName();
            SocketChannel receiver = currentUsers.get(name);
            sendMessage(receiver,"PRIVATE "+sender+" "+message+"\n");
            response = "OK";
        }
        else response = "ERROR";
        response += '\n';
        sendMessage(sc,response);
    }

    static void sendNormalMessage(SocketChannel sc, String message, ClientData clientData) throws IOException
    {
        if(message.startsWith("/"))
            message = message.substring(1);
        String room = clientData.getRoom();
        String name = clientData.getName();
        broadcastMessage(room,"MESSAGE "+name+" "+message+"\n",null);
    }


    static void broadcastMessage(String room,String message,SocketChannel sender) throws IOException
    {
        for(SocketChannel sc:currentRooms.get(room))
            if(!sc.equals(sender))
                sendMessage(sc,message);
    }

    static void sendMessage(SocketChannel sc,String message) throws IOException
    {
        ByteBuffer buf = ByteBuffer.wrap(message.getBytes());
        while(buf.hasRemaining())
            sc.write(buf);
    }

    static String dropCommand(String message)
    {
        return message.substring(message.indexOf(' ')+1);
    }

}
