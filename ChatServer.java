import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer
{
    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = StandardCharsets.UTF_8;
    static private final CharsetDecoder decoder = charset.newDecoder();
    static private HashMap<String, String> userStates = new HashMap<>(); // Map that has usernames as keys and states as values


    static private HashMap<Integer, String> userPort = new HashMap<>(); // Map that has usernames as keys and ports as values

    static private HashMap<String, ArrayList<String>> rooms = new HashMap<>(); // Map that has rooms as keys and a list of usernames as values

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
                        sc.register( selector, SelectionKey.OP_READ );

                    } else if (key.isReadable()) {

                        SocketChannel sc = null;

                        try {

                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel)key.channel();

                            boolean ok = processInput( sc );


                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (!ok) {
                                key.cancel();

                                Socket s = null;
                                try {
                                    s = sc.socket();
                                    int Userport = s.getPort();
                                    String name = userPort.get(Userport);
                                    userStates.remove(name);
                                    userPort.remove(Userport);
                                    System.out.println( "Closing connection to "+s );
                                    s.close();
                                } catch( IOException ie ) {
                                    System.err.println( "Error closing socket "+s+": "+ie );
                                }
                            }

                        } catch( IOException ie ) {

                            // On exception, remove this channel from the selector
                            key.cancel();

                            try {
                                sc.close();
                            } catch( IOException ie2 ) { System.out.println( ie2 ); }

                            System.out.println( "Closed11 "+sc );
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


    // Just read the message from the socket and send it to stdout

    //Tratar a mensagem recebida pelo cliente aqui
    static private boolean processInput( SocketChannel sc ) throws IOException {
        // Read the message to the buffer
        buffer.clear();
        sc.read(buffer);
        buffer.flip();

        // If no data, close the connection
        if ( buffer.limit()==0) {
            System.out.println("buffer limit ");
            return false;
        }
        System.out.println("buffer decoder ");
        // Decode and print the message to stdout
        String clientMessage = decoder.decode(buffer).toString();
        System.out.println("message: " + clientMessage);
        String response = "ERROR\n"; //default response

        if(clientMessage.equals("bye\n")){
            response = "BYE\n";
        }
        else if(!clientMessage.startsWith("/")){
            response = message(sc, clientMessage);
        }
        else if (clientMessage.startsWith("/nick ")){
            response = nick(sc, clientMessage);
        }
        else if (clientMessage.startsWith("/join ")){
            response = join(sc, clientMessage);
        }
        else if (clientMessage.startsWith("/leave ")){
            response = leave(sc, clientMessage);
        }

        try {
            ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes());
            while (responseBuffer.hasRemaining()) {
                sc.write(responseBuffer);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
        System.out.print( response );
        return !response.equals("BYE\n");
    }

    private static String leave(SocketChannel sc, String clientMessage) {
        int port = sc.socket().getPort();
        if (!userPort.containsKey(port)) return "ERROR\n"; //user doesn't have a name
        String name = userPort.get(port);
        if (!userStates.get(name).equals("inside")) return "ERROR\n"; //user doesn't have a room

        return "OK\n";
    }

    private static String join(SocketChannel sc, String clientMessage) {
        int port = sc.socket().getPort();
        if (!userPort.containsKey(port)){
            System.out.println("Debuugg000");
            return "ERROR\n"; //user doesn't have a name
        }
        String roomName = clientMessage.replaceAll("/join ", "");
        String name = userPort.get(port);
        String response;
        if(!userStates.containsKey(name)){
            System.out.println("Debuugg join userStates doesn't contains name");
            response = "ERROR\n";
        }
        else {
            if(rooms.containsKey(roomName)){
                rooms.get(roomName).add(name);
            }
            else rooms.put(name, new ArrayList<>());
            userStates.put(name, "inside");
            userPort.put(port, name);
            System.out.println(userStates.keySet());
            System.out.println(userPort.keySet());
            response = "OK\n";
        }
        return response;
    }

    static private String nick(SocketChannel sc, String clientMessage){
        String name = clientMessage.replaceAll("/nick ", "");
        int port = sc.socket().getPort();
        String oldName = userPort.get(port);
        String responseToUser = "OK\n"; //Default response
        if(userStates.containsKey(name) && !userPort.get(port).equals(name)){
            System.out.println("Debuugg Nickname is not available");
            //System.out.println(userStates.keySet());
            responseToUser = "ERROR\n";
        }
        else if(userPort.containsKey(port)){
            String state = userStates.get(name);
            userStates.remove(userPort.get(port));
            if(state.equals("outside")) userStates.put(name, "outside");
            else{
                String responseToEveryone = name + " mudou de nome para " + oldName;
            }

            //System.out.println(userStates.keySet());
            //System.out.println(userPort.keySet());
            responseToUser = "OK\n";
        }
        userPort.put(port, name);
        userStates.put(name, "outside");
        return responseToUser;
    }

    static private String message(SocketChannel sc, String clientMessage){
        int port = sc.socket().getPort();
        if (!userPort.containsKey(port)) return "ERROR\n"; //user doesn't have a name
        String name = userPort.get(port);
        if (!userStates.get(name).equals("inside")) return "ERROR\n"; //user doesn't have a room

        return  name.strip() + ": " + clientMessage;
    }
}