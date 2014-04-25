import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class UnSecureClientManagerTest extends ClientManager {

	private LinkedList<Socket> clientSockets;
	private LinkedList<BufferedReader> inputFromClients;
	private LinkedList<PrintWriter> outputToClients;
	private int size;
	private int bombTimer;
	private int bombHolder;
	private boolean[] bombList = new boolean[Server.PLAYERS_PER_GAME];
	private float[][] posList = new float[Server.PLAYERS_PER_GAME][2];
	private Bomb bomb = new Bomb();
	private CountDownLatch countDown = new CountDownLatch(4);

	public UnSecureClientManagerTest(LinkedList<Socket> clients) {
		clientSockets = clients;
		inputFromClients = new LinkedList<BufferedReader>();
		outputToClients = new LinkedList<PrintWriter>();
		size = Server.PLAYERS_PER_GAME;

		// Set bomb timer to 60sec.
		// Set a random timer.
		Random randomExtraTime = new Random();
		int baseTime = 5000;
		int extraTime = 1000 * randomExtraTime.nextInt(10);

		bombTimer = baseTime + extraTime;

		// Set bomb carrier
		for (int i = 0; i < Server.PLAYERS_PER_GAME; i++) {
			bombList[i] = false;
		}
		Random rand = new Random();
		bombHolder = rand.nextInt(Server.PLAYERS_PER_GAME);
		bombList[bombHolder] = true;

		// Set starting position
		posList[0][0] = 312;
		posList[0][1] = 512;
		posList[1][0] = 412;
		posList[1][1] = 512;
		posList[2][0] = 612;
		posList[2][1] = 512;
		posList[3][0] = 712;
		posList[3][1] = 512;

		try {
			for (Socket s : clientSockets) {
				inputFromClients.add(new BufferedReader(new InputStreamReader(s
						.getInputStream())));

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
			// Inform client of their id and who is the bomb holder.
			for (int i = 0; i < size; i++) {
				inputFromClients.get(i).readLine();

				String initInfo = i + ";0,312,512," + bombList[0]
						+ ";1,412,512," + bombList[1] + ";2,612,512,"
						+ bombList[2] + ";3,712,512," + bombList[3];
				outputToClients.get(i).println(initInfo);
			}

			// long startTime = System.currentTimeMillis();

			BroadcastThread broadcast = new BroadcastThread(outputToClients,
					posList, bombList, countDown);
			LinkedList<PlayerListener> playerListenerList = new LinkedList<PlayerListener>();
			for (int i = 0; i < 4; i++) {
				playerListenerList.add(new PlayerListener(clientSockets.get(i),
						inputFromClients.get(i), i, posList, bombList, bomb,
						countDown));
			}
			broadcast.start();
			for (int i = 0; i < 4; i++) {
				playerListenerList.get(i).start();
			}
			Thread.sleep(bombTimer);

			// Inform all clients that the bomb has exploded.
			broadcast.sendExplodeMsg();

			countDown.await();
			
			

			broadcast.deactivate();
			for (int i = 0; i < size; i++) {
				playerListenerList.get(i).deactivate();
			}
			
			// Perform clean up logic.
			System.out.println("Cleaning Up");
			for (int i = 0; i < size; i++) {
				inputFromClients.get(i).close();
			}
			for (int i = 0; i < size; i++) {
				outputToClients.get(i).close();
			}
			for (int i = 0; i < size; i++) {
				clientSockets.get(i).close();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}

class PlayerListener extends Thread {
	private Socket socket;
	private BufferedReader in;
	private int id;
	private float[][] posList;
	private boolean[] bombList;
	private boolean active;
	private Bomb bomb;
	private CountDownLatch countDown;

	PlayerListener(Socket socket, BufferedReader in, int id, float[][] posList,
			boolean[] bombList, Bomb bomb, CountDownLatch countDown) {
		this.in = in;
		this.id = id;
		this.posList = posList;
		this.bombList = bombList;
		this.active = true;
		this.bomb = bomb;
		this.countDown = countDown;
		this.socket = socket;
	}

	@Override
	public void run() {
		while (active) {
			// Is the buffer ready to be read? If not, I'll check the next
			// buffer.

			/*
			 * Receive: "id, x_coordinate, y_coordinate, bomb_from, bomb_to"
			 */
			String input = null;
			if (!socket.isClosed()){
				System.out.println("Socket is not closed");
				try {
					input = in.readLine();
				} catch (IOException e) {
				}
				if (!bomb.getPassable()) {
					if (System.currentTimeMillis() - bomb.getBombPassTime() > 1000) {
						bomb.enablePassable();
					}
				}
				// System.out.println(in);
				if (input == null){
				}
				else if (input.contentEquals("Terminated") ) {
					System.out.println("CountDown");
					countDown.countDown();
				}
				else{
					String splitInput[] = input.split(",");
					posList[id][0] = Float.parseFloat(splitInput[1]);
					posList[id][1] = Float.parseFloat(splitInput[2]);
					int collidedPlayerNo = Integer.parseInt(splitInput[3]);
					boolean carryBomb = Boolean.parseBoolean(splitInput[4]);
					if (collidedPlayerNo != -1 && carryBomb
							&& carryBomb == bombList[id]
							&& !bombList[collidedPlayerNo] && bomb.getPassable()) {
						bomb.disablePassable();
						bomb.setBombPassTime();
						bombList[id] = false;
						bombList[collidedPlayerNo] = true;
	
					}
				}
			}
			else{
				System.out.println("CountDown from Socket Closure");
				countDown.countDown();
			}
		}
		try {
			in.close();
		} catch (IOException e) {
			System.err.println("Unable to close BufferedReader on Listener");
			e.printStackTrace();
		}
	}

	public void deactivate() {
		active = false;
	}
}

class BroadcastThread extends Thread {
	private LinkedList<PrintWriter> outList;
	private float[][] posList;
	private boolean[] bombList;
	private boolean active;
	private CountDownLatch countDown;
	private boolean signaling;

	BroadcastThread(LinkedList<PrintWriter> outList, float[][] posList,
			boolean[] bombList, CountDownLatch countDown) {
		this.outList = outList;
		this.posList = posList;
		this.bombList = bombList;
		this.active = true;
		this.signaling = true;
		this.countDown = countDown;
	}

	@Override
	public void run() {
		while (active) {
			boolean[] bombListCpy;
			bombListCpy = bombList.clone();
			for (int i = 0; i < 4; i++) {
				for (PrintWriter out : outList) {
					out.println(i + "," + posList[i][0] + "," + posList[i][1]
							+ "," + bombListCpy[i]);
				}
			}
			try {
				Thread.sleep(16);
			} catch (InterruptedException e) {
				System.err.println("Failed to sleep");
				e.printStackTrace();
			}
		}

		System.out.println("Exploded");
		while (signaling){
			//System.out.println("Printing...");
			for (PrintWriter out : outList) {
				out.println("Exploded");
			}
		}

		//try {
			//countDown.await();
		//} catch (InterruptedException e) {
		//	System.err.println("Countdown Interrupted");
		//	e.printStackTrace();
		//}

		System.out.println("Broadcast Ended");
	}

	public void deactivate() {
		signaling = false;
	}
	public void sendExplodeMsg(){
		active = false;
	}
}

class Bomb {
	private boolean passable = true;
	private long bombPassTime = 0;

	Bomb() {
	}

	public synchronized void disablePassable() {
		passable = false;
	}

	public synchronized void enablePassable() {
		passable = true;
	}

	public synchronized boolean getPassable() {
		return passable;
	}

	public synchronized void setBombPassTime() {
		bombPassTime = System.currentTimeMillis();
	}

	public synchronized long getBombPassTime() {
		return bombPassTime;
	}
}