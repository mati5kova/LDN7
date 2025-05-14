import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatServer {
	protected int serverPort = 1234;
	protected final List<SSLSocket> clients = new ArrayList<>();
	protected final Map<String, Integer> username2socketPort = new HashMap<>();

	public static void main(String[] args) throws Exception {
		new ChatServer().start();
	}

	public void start() throws Exception {
		// nalozi keystore in truststore
		String ksPath = System.getProperty("javax.net.ssl.keyStore");
		String ksPass = System.getProperty("javax.net.ssl.keyStorePassword");
		String tsPath = System.getProperty("javax.net.ssl.trustStore");
		String tsPass = System.getProperty("javax.net.ssl.trustStorePassword");

		KeyStore serverKeyStore = KeyStore.getInstance("JKS");
		try (FileInputStream fis = new FileInputStream(ksPath)) {
			serverKeyStore.load(fis, ksPass.toCharArray());
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(serverKeyStore, ksPass.toCharArray());

		KeyStore trustStore = KeyStore.getInstance("JKS");
		try (FileInputStream fis = new FileInputStream(tsPath)) {
			trustStore.load(fis, tsPass.toCharArray());
		}
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(trustStore);

		// TLSv1.2
		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

		// create server socket
		SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
		SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(serverPort);
		serverSocket.setNeedClientAuth(true);
		serverSocket.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});

		System.out.println("[system] listening for TLS connections ...");
		while (true) {
			SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
			synchronized (this) {
				clients.add(clientSocket);
			}
			new ChatServerConnector(this, clientSocket).start();
		}
	}

	// send message to all clients
	public void sendToAllClients(String message) {
		for (SSLSocket socket : clients) {
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				out.writeUTF(message);
				out.flush();
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	// send message to specific client
	public void sendToSpecificClient(String message, String recipient) {
		Integer port = getPortByUsername(recipient);
		if (port != null) {
			for (SSLSocket socket : clients) {
				if (socket.getPort() == port) {
					try {
						DataOutputStream out = new DataOutputStream(socket.getOutputStream());
						out.writeUTF(message);
						out.flush();
					} catch (Exception e) {
						System.err.println("[system] could not send message to a client");
						e.printStackTrace(System.err);
					}
					break;
				}
			}
		}
	}

	public void removeClient(SSLSocket socket) {
		synchronized (this) {
			clients.remove(socket);
		}
	}

	public synchronized void registerUsername(String username, int port) {
		username2socketPort.putIfAbsent(username, port);
	}

	public synchronized Integer getPortByUsername(String username) {
		return username2socketPort.get(username);
	}

	private static class ChatServerConnector extends Thread {
		private final ChatServer server;
		private final SSLSocket socket;
		private String clientName;

		ChatServerConnector(ChatServer server, SSLSocket socket) {
			this.server = server;
			this.socket = socket;
		}

		public void run() {
			try {
				socket.startHandshake();
				SSLSession session = socket.getSession();
				String dn = session.getPeerPrincipal().getName();
				for (String part : dn.split(",")) {
					if (part.trim().startsWith("CN=")) {
						clientName = part.trim().substring(3);
						break;
					}
				}
				server.registerUsername(clientName, socket.getPort());

				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				LocalDateTime now = LocalDateTime.now();
				String date = now.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
				String time = now.format(DateTimeFormatter.ofPattern("HHmmss"));
				String response = "1" + date + time + String.format("%-17s", "system") +String.format("%-17s", clientName) + "You are now connected as @" + clientName;
				out.writeUTF(response);
				out.flush();

				DataInputStream in = new DataInputStream(socket.getInputStream());
				String msg;
				while ((msg = in.readUTF()) != null) {
					int type = Integer.parseInt(msg.substring(0, 1));
					String dateField = msg.substring(1, 9);
					String timeField = msg.substring(9, 15);
					String recipient = msg.substring(32, 49).trim();
					String payload = msg.substring(49);

					System.out.println(msg);
					if (type == 2) {
						String broadcast = "2" + dateField + timeField + String.format("%-17s", clientName) + String.format("%-17s", "") + payload;
						server.sendToAllClients(broadcast);
					} else if (type == 3) {
						if (server.getPortByUsername(recipient) != null) {
							String direct = "3" + dateField + timeField + String.format("%-17s", clientName) + String.format("%-17s", recipient) + payload;
							server.sendToSpecificClient(direct, recipient);
						} else {
							String err = "4" + dateField + timeField + String.format("%-17s", "system") + String.format("%-17s", clientName) + "User @" + recipient + " does not exist!";
							out.writeUTF(err);
							out.flush();
						}
					}
				}
			} catch (Exception e) {
				System.err.println("[system] connection with " + clientName + " closed due to error");
				e.printStackTrace(System.err);
			} finally {
				server.removeClient(socket);
				if (clientName != null) {
					server.username2socketPort.remove(clientName);
				}
				try { socket.close(); } catch (IOException ignored) {}
			}
		}
	}
}