import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatClient extends Thread
{
	protected int serverPort = 1234;

	public static void main(String[] args) throws Exception {
		new ChatClient();
	}

	public ChatClient() throws Exception {
		Socket socket = null;
		DataInputStream in = null;
		DataOutputStream out = null;
		String username = "";
		boolean hasValidUsername = false;

		// connect to the chat server
		try {
			System.out.println("[system] connecting to chat server ...");
			socket = new Socket("localhost", serverPort); // create socket connection
			in = new DataInputStream(socket.getInputStream()); // create input stream for listening for incoming messages
			out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages
			System.out.println("[system] connected");

			ChatClientMessageReceiver message_receiver = new ChatClientMessageReceiver(in); // create a separate thread for listening to messages from the chat server
			message_receiver.start(); // run the new thread
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}

		System.out.println("[system] Enter your username (1-17 characters long): ");

		// read from STDIN and send messages to the chat server
		BufferedReader std_in = new BufferedReader(new InputStreamReader(System.in));
		String userInput;

		while ((userInput = std_in.readLine()) != null) {
			String[] now = getCurrentFormattedDateTime();
			String date = now[0];
			String time = now[1];
			String recipient = String.format("%-17s", "");
			int type = 2;
			String payload = userInput;

			if (!hasValidUsername) {
				if (isValidUsername(userInput)) {
					hasValidUsername = true;
					username = userInput.trim();
					String msg = "1" + date + time + String.format("%-17s", username) + recipient + "LOGIN";
					sendMessage(msg, out);
				} else {
					System.out.println("[system] Invalid username");
				}
			} else {
				if (userInput.startsWith("@")) {
					type = 3;
					String[] parts = userInput.split(" ", 2);
					if (parts.length >= 2) {
						recipient = String.format("%-17s", parts[0].substring(1));
						payload = parts[1];
					}
				}
				String msg = type + date + time + String.format("%-17s", username) + recipient + payload;
				sendMessage(msg, out);
			}
		}

		// cleanup
		out.close();
		in.close();
		std_in.close();
		socket.close();
	}

	private boolean isValidUsername(String input) {
		return input.length() >= 1 && input.length() <= 17 && !input.contains(" ");
	}


	private void sendMessage(String message, DataOutputStream out) {
		try {
			out.writeUTF(message); // send the message to the chat server
			out.flush(); // ensure the message has been sent
		} catch (IOException e) {
			System.err.println("[system] could not send message");
			e.printStackTrace(System.err);
		}
	}

	private String[] getCurrentFormattedDateTime() {
		LocalDateTime now = LocalDateTime.now();
		String date = now.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
		String time = now.format(DateTimeFormatter.ofPattern("HHmmss"));
		return new String[] { date, time };
	}
}

// wait for messages from the chat server and print the out
class ChatClientMessageReceiver extends Thread {
	private DataInputStream in;

	public ChatClientMessageReceiver(DataInputStream in) {
		this.in = in;
	}

	public void run() {
		try {
			String message;
			while ((message = this.in.readUTF()) != null) { // read new message

				int type = Integer.parseInt(message.substring(0, 1));
				String date = message.substring(1, 9);
				String timeHour = message.substring(9, 11);
				String timeMinutes = message.substring(11, 13); //do 15 so še sekunde
				String sender = message.substring(15, 32).trim().strip();
				String recipient = message.substring(32, 49).trim().strip(); // v resnici ne rabimo ker smo recipient mi
				String payload = message.substring(49);

				String base = String.format("[%s] [%s:%s] ", sender, timeHour, timeMinutes);
				if(type == 1) {
					String response = base + payload;
					System.out.println(response);
				} else if (type == 2) {
					String response = "[RKchat] " + base + payload;
					System.out.println(response);
				} else if (type == 3) {
					String response = "[direct message] " + base + payload;
					System.out.println(response);
				} else if (type == 4) {
					String response = base + payload;
					System.out.println(response);
				}

			}
		} catch (Exception e) {
			System.err.println("[system] could not read message");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
