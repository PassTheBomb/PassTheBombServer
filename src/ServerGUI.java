import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.Inet4Address;
import java.net.UnknownHostException;

public class ServerGUI extends Frame implements ActionListener, WindowListener{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Label lblIpAdsress;    // Declare component Label
	private Button btnServerStart;   // Declare component Button
	private Button btnT2;
	private Button btnT3;
	private Button btnT4;
	private Button btnT5;
	private Server server;
	
	private enum Actions {
		SERVER,
	    T2,
	    T3,
	    T4,
	    T5
	}
	
	public ServerGUI() {
		setLayout(new FlowLayout());
        // "super" Frame sets its layout to FlowLayout, which arranges the components
        //  from left-to-right, and flow to next row from top-to-bottom.

		lblIpAdsress= new Label(this.getLocalIpAddress());  // construct Label
		add(lblIpAdsress);                    // "super" Frame adds Label

		btnServerStart = new Button("Start Server");   // construct Button
		btnServerStart.setActionCommand(Actions.SERVER.name());
		add(btnServerStart);                    // "super" Frame adds Button
		
		btnT2 = new Button("Start Server with T2");   // construct Button
		btnT2.setActionCommand(Actions.T2.name());
		add(btnT2);                    // "super" Frame adds Button
		
		btnT3 = new Button("Start Server with T3");   // construct Button
		btnT3.setActionCommand(Actions.T3.name());
		add(btnT3);                    // "super" Frame adds Button
		
		btnT4 = new Button("Start Server with T4");   // construct Button
		btnT4.setActionCommand(Actions.T4.name());
		add(btnT4);                    // "super" Frame adds Button
		
		btnT5 = new Button("Start Server with T5");   // construct Button
		btnT5.setActionCommand(Actions.T5.name());
		add(btnT5);                    // "super" Frame adds Button
		
		

		btnServerStart.addActionListener(this);
		btnT2.addActionListener(this);
		btnT3.addActionListener(this);
		btnT4.addActionListener(this);
		btnT5.addActionListener(this);
		// Clicking Button source fires ActionEvent
        // btnCount registers this instance as ActionEvent listener

		setTitle("Server");  // "super" Frame sets title
		setSize(250, 250);        // "super" Frame sets initial window size

		setVisible(true);         // "super" Frame shows
	}
	
	public static void main(String[] args) {
		ServerGUI app = new ServerGUI();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand() == Actions.SERVER.name()) {
			server = new Server(PROTOCAL.NOPROTOCAL);
			System.out.println("Server");
		} else if (e.getActionCommand() == Actions.T2.name()) {
			server = new Server(PROTOCAL.T2);
			System.out.println("T2");
		} else if (e.getActionCommand() == Actions.T3.name()) {
			server = new Server(PROTOCAL.T3);
			System.out.println("T3");
		} else if (e.getActionCommand() == Actions.T4.name()) {
			server = new Server(PROTOCAL.T4);
			System.out.println("T4");
		} else if (e.getActionCommand() == Actions.T5.name()) {
			server = new Server(PROTOCAL.T5);
			System.out.println("T5");
		}
		try {
			this.server.serverStart();
		} catch (Exception e1) {
			System.out.println("Server cannot start");
			e1.printStackTrace();
		}
	}
	
	public String getLocalIpAddress() {
        try {
			return Inet4Address.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
        return "";
    }

	@Override
	public void windowClosing(WindowEvent e) {
	      System.exit(0);  // Terminate the program
	}

	@Override
	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosed(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}
}
