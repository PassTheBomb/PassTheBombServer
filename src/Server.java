

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

import android.R.integer;


/**
 * Constantly listens for new client connections.
 * Creates a ClientManager thread whenever the correct number of players 
 * have arrived, and continues listening for new connections thereafter.
 */
public class Server {

	static final int PLAYERS_PER_GAME = 4;
	private PROTOCAL protocal;
	private Security s;
	private Keys k;
	
	protected Security getS() {
		return s;
	}

	protected Keys getK() {
		return k;
	}

	public Server(PROTOCAL protocal) {
		this.protocal = protocal;
	}

	@SuppressWarnings("resource")
	public void serverStart() throws Exception {
		
		System.out.println("Server started");
		
		// Suppress warning that serverSocket is never closed.
		ServerSocket serverSocket = new ServerSocket(5432);
		LinkedList<Socket> clientSockets = new LinkedList<Socket>();
		ArrayList<PrintWriter> outArrayList = new ArrayList<PrintWriter>();

		int count = 0; 

		/*
		 * For every 4 clients connected, we create a manager thread which will
		 * manage the client information.
		 * 
		 * Server thread continues to run and listens for new connections.
		 */
		while (true) {
			Socket socket = serverSocket.accept();
			
			boolean result = this.verificaiton(socket);
			if (!result) {
				System.out.println("Verification Failed");
				socket.close();
			} else {
				clientSockets.add(socket);
				outArrayList.add(new PrintWriter(socket.getOutputStream(), true));
				count++;
				System.out.println(count + " p connected.");
				
				for(PrintWriter out : outArrayList) {
					out.println(clientSockets.size());
				}
			}
			
			if (count == PLAYERS_PER_GAME) {

				// Create copy of the list of client sockets.
				LinkedList<Socket> copyOfClientSockets = new LinkedList<Socket>(clientSockets);
				
				// Pass the clientSockets to the clientManager.
				Thread clientManager;
				if (this.protocal == PROTOCAL.NOPROTOCAL || this.protocal == PROTOCAL.T2) {
					clientManager = new Thread(new UnSecureClientManager(copyOfClientSockets));
				} else {
					clientManager = new Thread(new SecureClientManager(copyOfClientSockets, this));
				}
				//Thread clientManager = new Thread(new ClientManager(copyOfClientSockets));
				System.out.println("Manager thread created.");
				clientManager.start();

				// Reset count and clear the existing list of clients sockets. 
				count = 0;
				clientSockets.clear();
				outArrayList.clear();
			}

			Thread.sleep(1000);
		}
	}
	
	private boolean verificaiton(Socket socket) throws IOException {
		if (s == null) {
			s = new Security();
		} 
		if (k == null) {
			k = new Keys();
		}
		
		k.generateRSAKeyPair();
		
		InputStream in = socket.getInputStream();
		OutputStream out = socket.getOutputStream();
		
		ServerAuthentication sa = new ServerAuthentication(s, k);
		
		if (this.protocal == PROTOCAL.NOPROTOCAL) {
			return sa.NOPROTOCOL(in, out);
		} else if (this.protocal == PROTOCAL.T2) {
			return sa.T2(in, out);
		} else if (this.protocal == PROTOCAL.T3) {
			return sa.T3(in, out);
		} else if (this.protocal == PROTOCAL.T4) {
			return sa.T4(in, out);
		} else if (this.protocal == PROTOCAL.T5) {
			return sa.T5(in, out);
		}
		return false;
	}
}

class ClientManager implements Runnable {

	@Override
	public void run() {
	}
	
}

/**
 * Responsible for starting and managing client threads.
 * Receives client updates and pushes out information to all clients.
 * 
 * If performance is too slow, we may need to implement one dedicated thread for receiving updates, per client.
 */
class UnSecureClientManager extends ClientManager {

	private LinkedList<Socket> clientSockets;
	private LinkedList<BufferedReader> inputFromClients;
	private LinkedList<PrintWriter> outputToClients;
	private int size;
	private int bombTimer; 
	private int bombHolder;
	private boolean[] bombList = new boolean[Server.PLAYERS_PER_GAME];

	public UnSecureClientManager(LinkedList<Socket> clients) {
		clientSockets = clients;
		inputFromClients = new LinkedList<BufferedReader>();
		outputToClients = new LinkedList<PrintWriter>();
		size = Server.PLAYERS_PER_GAME;

		// Set bomb timer to 60sec.
		bombTimer = 10000;

		try {
			for (Socket s : clientSockets) {
				inputFromClients.add(
						new BufferedReader(
								new InputStreamReader(s.getInputStream()) ));

				// Set the second param of the PrintWriter constructor to true
				// to enable AUTO-FLUSHING.
				outputToClients.add(new PrintWriter(s.getOutputStream(), true));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			for (int i = 0; i < Server.PLAYERS_PER_GAME; i++){
				bombList[i] = false;
			}
			Random rand = new Random();
			bombHolder = rand.nextInt(Server.PLAYERS_PER_GAME);
			bombList[bombHolder] = true;
			
			// Inform client of their id and who is the bomb holder. 
			for (int i = 0; i < size; i++) {
				inputFromClients.get(i).readLine();
				
				String initInfo = i + ";0,312,512," + bombList[0] + ";1,412,512," + bombList[1] + ";2,712,512," + bombList[2] + ";3,612,512," + bombList[3];
				outputToClients.get(i).println(initInfo);
			}

			long startTime = System.currentTimeMillis();

			// Receive client information and update all clients constantly.
			while (true) {
				for (int i = 0; i < size; i++) {
					
					// Is the buffer ready to be read? If not, I'll check the next buffer.
					if (inputFromClients.get(i).ready()) {
						
						/* Receive: 
						 * 	"id, x_coordinate, y_coordinate, bomb_from, bomb_to" */
						String in = inputFromClients.get(i).readLine();
						//System.out.println(in);
						String input[] = in.split(",");
						
						
						int collidedPlayerNo = Integer.parseInt(input[3]);
						boolean carryBomb = Boolean.parseBoolean(input[4]);
						if (collidedPlayerNo != -1 && carryBomb && carryBomb == bombList[i]){
							bombList[i] = false;
							bombList[collidedPlayerNo] = true;
						}
						//To-Do: Must handle collision checker. "Handshake" the collision
						
						// Transmit to all other clients.
						for (int j = 0; j < size; j++) {
							outputToClients.get(j).println(input[0]+","+input[1]+","+input[2]+","+bombList[i]);
						}
					}
				}

				// Periodically check if bomb has expired then exit loop.
				if (System.currentTimeMillis() - startTime >= bombTimer) {
					System.out.println("Bomb Exploded.");
					break;
				}
			}
			
			// Inform all clients that the bomb has exploded.
			for (int i = 0; i < size; i++) {
				outputToClients.get(i).println("Exploded");
			}
			
			// Perform clean up logic.
			for (int i = 0; i < size; i++) {
				outputToClients.get(i).close();
				inputFromClients.get(i).close();
				clientSockets.get(i).close();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}	
}

/**
 * Responsible for starting and managing client threads.
 * Receives client updates and pushes out information to all clients.
 * 
 * If performance is too slow, we may need to implement one dedicated thread for receiving updates, per client.
 */
class SecureClientManager extends ClientManager {

	private LinkedList<Socket> clientSockets;
//	private LinkedList<BufferedReader> inputFromClients;
//	private LinkedList<PrintWriter> outputToClients;
	private LinkedList<InputStream> ins;
	private LinkedList<OutputStream> outs;
	
	private int size;
	private int bombTimer; 
	private int bombHolder;
	private boolean[] bombList = new boolean[Server.PLAYERS_PER_GAME];
	private Security security;
	private Keys keys;

	public SecureClientManager(LinkedList<Socket> clients, Server server) {
		clientSockets = clients;
//		inputFromClients = new LinkedList<BufferedReader>();
//		outputToClients = new LinkedList<PrintWriter>();
		size = Server.PLAYERS_PER_GAME;
		security = server.getS();
		keys = server.getK();
		ins = new LinkedList<InputStream>();
		outs = new LinkedList<OutputStream>();

		// Set a random timer.
		Random randomExtraTime = new Random();
		int baseTime = 30000;
		int extraTime = 1000*randomExtraTime.nextInt(10);
		
		bombTimer = baseTime + extraTime;

		try {
			for (Socket s : clientSockets) {
				/*inputFromClients.add(
						new BufferedReader(
								new InputStreamReader(s.getInputStream()) ));

				// Set the second param of the PrintWriter constructor to true
				// to enable AUTO-FLUSHING.
				outputToClients.add(new PrintWriter(s.getOutputStream(), true));*/
				ins.add(s.getInputStream());
				outs.add(s.getOutputStream());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			for (int i = 0; i < Server.PLAYERS_PER_GAME; i++){
				bombList[i] = false;
			}
			Random rand = new Random();
			bombHolder = rand.nextInt(Server.PLAYERS_PER_GAME);
			bombList[bombHolder] = true;
			
			// Inform client of their id and who is the bomb holder. 
			for (int i = 0; i < size; i++) {
				//TODO decrypt 
				//inputFromClients.get(i).readLine();
				MsgHandler.acquireNetworkMsg(ins.get(i));
				
				//TODO encrypt
				String initInfo = i + ";0,312,512," + bombList[0] + ";1,412,512," + bombList[1] + ";2,712,512," + bombList[2] + ";3,612,512," + bombList[3];
				//outputToClients.get(i).println(initInfo);
				outs.get(i).write(security.encrypt(initInfo.getBytes(), keys.getDESKey(), "DES"));
			}

			long startTime = System.currentTimeMillis();

			// Receive client information and update all clients constantly.
			while (true) {
				for (int i = 0; i < size; i++) {
					
					//TODO .avaliable
					// Is the buffer ready to be read? If not, I'll check the next buffer.
					//if (inputFromClients.get(i).ready()) {
					if(ins.get(i).available() > 0) {	
						/* Receive: 
						 * 	"id, x_coordinate, y_coordinate, bomb_from, bomb_to" */
						//Get byte and decrypt
						//String in = inputFromClients.get(i).readLine();
						String in = new String(security.decrypt(MsgHandler.acquireNetworkMsg(ins.get(i)), keys.getDESKey(), "DES"));
						String input[] = in.split(",");
						
						
						int collidedPlayerNo = Integer.parseInt(input[3]);
						boolean carryBomb = Boolean.parseBoolean(input[4]);
						if (collidedPlayerNo != -1 && carryBomb && carryBomb == bombList[i]){
							bombList[i] = false;
							bombList[collidedPlayerNo] = true;
						}
						//To-Do: Must handle collision checker. "Handshake" the collision
						
						// Transmit to all other clients.
						//TODO encrypt and send out
						for (int j = 0; j < size; j++) {
							//outputToClients.get(j).println(input[0]+","+input[1]+","+input[2]+","+bombList[i]);
							String msg = input[0]+","+input[1]+","+input[2]+","+bombList[i];
							outs.get(i).write(security.encrypt(msg.getBytes(), keys.getDESKey(), "DES"));
						}
					}
				}

				// Periodically check if bomb has expired then exit loop.
				if (System.currentTimeMillis() - startTime >= bombTimer) {
					System.out.println("Bomb Exploded.");
					break;
				}
			}
			
			// Inform all clients that the bomb has exploded.
			for (int i = 0; i < size; i++) {
				//TODO ddddd
				//outputToClients.get(i).println("Exploded");
				String msg = "Exploded";
				outs.get(i).write(security.encrypt(msg.getBytes(), keys.getDESKey(), "DES"));
			}
			
			// Perform clean up logic.
			for (int i = 0; i < size; i++) {
				/*outputToClients.get(i).close();
				inputFromClients.get(i).close();*/
				clientSockets.get(i).close();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}	
}
