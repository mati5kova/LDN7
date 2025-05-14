import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatClient {
	protected int serverPort = 1234;

	public static void main(String[] args) throws Exception {
		new ChatClient().start();
	}

	public void start() throws Exception {
		// nalozi keystore in truststore
		String ksPath = System.getProperty("javax.net.ssl.keyStore");
		String ksPass = System.getProperty("javax.net.ssl.keyStorePassword");
		String tsPath = System.getProperty("javax.net.ssl.trustStore");
		String tsPass = System.getProperty("javax.net.ssl.trustStorePassword");

		KeyStore clientKeyStore = KeyStore.getInstance("JKS");
		try (FileInputStream fis = new FileInputStream(ksPath)) {
			clientKeyStore.load(fis, ksPass.toCharArray());
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(clientKeyStore, ksPass.toCharArray());

		KeyStore trustStore = KeyStore.getInstance("JKS");
		try (FileInputStream fis = new FileInputStream(tsPath)) {
			trustStore.load(fis, tsPass.toCharArray());
		}
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(trustStore);


		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

		SSLSocketFactory factory = sslContext.getSocketFactory();
		SSLSocket socket = (SSLSocket) factory.createSocket("localhost", serverPort);
		socket.setEnabledProtocols(new String[]{"TLSv1.2"});
		socket.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});
		socket.startHandshake();

		System.out.println("[system] connected via TLS");

		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		// create a separate thread for listening to messages
		ChatClientMessageReceiver receiver = new ChatClientMessageReceiver(in);
		receiver.start();

		BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
		String userInput;
		while ((userInput = stdIn.readLine()) != null) {
			LocalDateTime now = LocalDateTime.now();
			String date = now.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
			String time = now.format(DateTimeFormatter.ofPattern("HHmmss"));

			int type = 2;
			String recipientField = String.format("%-17s", "");
			String payload = userInput;
			if (userInput.startsWith("@")) {
				type = 3;
				String[] parts = userInput.split(" ", 2);
				if (parts.length >= 2) {
					recipientField = String.format("%-17s", parts[0].substring(1));
					payload = parts[1];
				}
			}

			String msg = type + date + time + String.format("%-17s", "") + recipientField + payload;
			out.writeUTF(msg);
			out.flush();
		}

		out.close();
		in.close();
		stdIn.close();
		socket.close();
	}
}

class ChatClientMessageReceiver extends Thread {
	private final DataInputStream in;

	public ChatClientMessageReceiver(DataInputStream in) {
		this.in = in;
	}

	public void run() {
		try {
			String message;
			while ((message = in.readUTF()) != null) {
				int type = Integer.parseInt(message.substring(0, 1));
				String timeHour = message.substring(9, 11);
				String timeMinute = message.substring(11, 13);
				String sender = message.substring(15, 32).trim();
				String payload = message.substring(49);

				String base = String.format("[%s] [%s:%s] ", sender, timeHour, timeMinute);
				switch (type) {
					case 1: System.out.println(base + payload); break;
					case 2: System.out.println("[RKchat] " + base + payload); break;
					case 3: System.out.println("[direct message] " + base + payload); break;
					case 4: System.out.println(base + payload); break;
				}
			}
		} catch (Exception e) {
			System.err.println("[system] could not read message");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
}