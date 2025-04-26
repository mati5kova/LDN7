import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
	protected int serverPort = 1234;
	protected List<Socket> clients = new ArrayList<>(); // list of clients
	protected Map<String, Integer> username2socketPort = new HashMap<>();

	public static void main(String[] args) throws Exception {
		new ChatServer();
	}

	public ChatServer() {
		ServerSocket serverSocket = null;

		// create socket
		try {
			serverSocket = new ServerSocket(this.serverPort); // create the ServerSocket
		} catch (Exception e) {
			System.err.println("[system] could not create socket on port " + this.serverPort);
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// start listening for new connections
		System.out.println("[system] listening ...");
		try {
			while (true) {
				Socket newClientSocket = serverSocket.accept(); // wait for a new client connection
				synchronized(this) {
					clients.add(newClientSocket); // add client to the list of clients
				}
				ChatServerConnector conn = new ChatServerConnector(this, newClientSocket); // create a new thread for communication with the new client
				conn.start(); // run the new thread
			}
		} catch (Exception e) {
			System.err.println("[error] Accept failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// close socket
		System.out.println("[system] closing server socket ...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	// send a message to all clients connected to the server
	public void sendToAllClients(String message) throws Exception {
		Iterator<Socket> i = clients.iterator();
		while (i.hasNext()) { // iterate through the client list
			Socket socket = (Socket) i.next(); // get the socket for communicating with this client
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	public void sendToSpecificClient(String message, String sender, String recipient) throws Exception {
		Integer port = getPortByUsername(recipient);
		if (port != null) {
			for (Socket socket : clients) {
				if (socket.getPort() == port) {
					try {
						DataOutputStream out = new DataOutputStream(socket.getOutputStream());
						out.writeUTF(message);
					} catch (Exception e) {
						System.err.println("[system] could not send message to a client");
					}
				}
			}
		}
	}

	public void removeClient(Socket socket) {
		synchronized(this) {
			clients.remove(socket);
		}
	}

	public synchronized void registerUsername(String username, int port) {
		username2socketPort.putIfAbsent(username, port);
	}

	public synchronized Integer getPortByUsername(String username) {
		return username2socketPort.get(username);
	}

	public synchronized  String getUsernameByPort(Integer port) {
		for(Map.Entry<String, Integer> entry : username2socketPort.entrySet()) {
			if(entry.getValue().equals(port)) {
				return entry.getKey();
			}
		}
		return "";
	}
}

class ChatServerConnector extends Thread {
	private ChatServer server;
	private Socket socket;

	public ChatServerConnector(ChatServer server, Socket socket) {
		this.server = server;
		this.socket = socket;
	}

	public void run() {
		System.out.println("[system] connected with " + this.socket.getInetAddress().getHostName() + ":" + this.socket.getPort());

		DataInputStream in;
		try {
			in = new DataInputStream(this.socket.getInputStream()); // create input stream for listening for incoming messages
		} catch (IOException e) {
			System.err.println("[system] could not open input stream!");
			e.printStackTrace(System.err);
			this.server.removeClient(socket);
			return;
		}

		while (true) { // infinite loop in which this thread waits for incoming messages and processes them
			try {
				String msg_received = in.readUTF();

				System.out.println("[system RKchat] " + msg_received);

				int type = Integer.parseInt(msg_received.substring(0, 1));
				String date = msg_received.substring(1, 9);
				String time = msg_received.substring(9, 15);
				String sender = msg_received.substring(15, 32).trim();
				String recipient = msg_received.substring(32, 49).trim();
				String payload = msg_received.substring(49);

				if (type == 1) {
					if (server.getPortByUsername(sender) == null) {
						server.registerUsername(sender, socket.getPort());
						String response = "1" + date + time + String.format("%-17s", "system") + String.format("%-17s", sender) + "You are now logged in as @ " + sender;
						server.sendToSpecificClient(response, "server", sender);
					} else {
						String response = "4" + date + time + String.format("%-17s", "system") + String.format("%-17s", sender) + "Username already exists! [USER_EXISTS]";
						DataOutputStream out = new DataOutputStream(socket.getOutputStream());
						out.writeUTF(response);
					}
				} else if (type == 2) {
					String message = "2" + date + time + String.format("%-17s", sender) + String.format("%-17s", "") + payload;
					server.sendToAllClients(message);
				} else if (type == 3) {
					if (server.getPortByUsername(recipient) != null) {
						String message = "3" + date + time + String.format("%-17s", sender) + String.format("%-17s", recipient) + payload;
						server.sendToSpecificClient(message, sender, recipient);
					} else {
						String errorMsg = "4" + date + time + String.format("%-17s", "system") + String.format("%-17s", sender) + "User @" + recipient + " does not exist! [USER_NOT_FOUND]";
						server.sendToSpecificClient(errorMsg, "server", sender);
					}
				}
			} catch (Exception e) {
				System.err.println("[system] there was a problem while sending the message to all clients");
				e.printStackTrace(System.err);
				try {
					socket.close();
				} catch (IOException ex) {
					ex.printStackTrace(System.err);
				}

				this.server.removeClient(socket);
				// da se iz mapa odstrani username in port
				// drugace ce se npr loginamo notri, ugasnemo chatclient in se spet loginamo z istim imenom nas ne spusti notri ker ni entry izbrisan
				this.server.username2socketPort.remove(this.server.getUsernameByPort(socket.getPort()));
				break;
			}
		}
	}
}
