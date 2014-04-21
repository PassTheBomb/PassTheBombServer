package enwei;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class ClientAuthentication {
	private final String serverPassword = "serverPassword";
	private final String clientPassword = "clientPassword";
	private final SecureRandom random = new SecureRandom();
	private Security s;
	private Keys k;

	// Test code start
	public static void main(String[] args) throws Exception {
		Security s = new Security();
		Keys k = new Keys();
		k.generateRSAKeyPair();

		int port = 8888;
		String host = "localhost";
		Socket client = new Socket(host, port);
		InputStream in = client.getInputStream();
		OutputStream out = client.getOutputStream();

		ClientAuthentication sa = new ClientAuthentication(s, k);

		/*
		 * System.out.println(sa.T2(in, out));
		 * 
		 * System.out.println(sa.T3(in, out));
		 * out.write(MsgHandler.createNetworkMsg
		 * (s.encrypt("testing1".getBytes(), k.getDESKey(), "DES")));
		 * out.flush();
		 * 
		 * System.out.println(sa.T4(in, out));
		 * out.write(MsgHandler.createNetworkMsg
		 * (s.encrypt("testing2".getBytes(), k.getDESKey(), "DES")));
		 * out.flush();
		 * 
		 * System.out.println(sa.T5(in, out));
		 * out.write(MsgHandler.createNetworkMsg
		 * (s.encrypt("testing3".getBytes(), k.getDESKey(), "DES")));
		 * out.flush();
		 */

		System.out.println(sa.T5(in, out));

		client.close();
	}

	// Test code end

	/**
	 * Creates a class containing the set of security protocols to be
	 * implemented on the client side. Requires a Security class and a Keys
	 * class as an input, and both must already be instantiated and set up.
	 * 
	 * @param s
	 *            the instantiated Security class
	 * @param k
	 *            the instantiated Keys class with RSA keypairs generated
	 */
	ClientAuthentication(Security s, Keys k) {
		this.s = s;
		this.k = k;
	}

	/**
	 * The 1st protocol.
	 * 
	 * @param in
	 *            input stream to receive the encrypted messages from server
	 * @param out
	 *            output stream to send the encrypted messages to server
	 * @return true if authentication success
	 */
	public boolean T2(InputStream in, OutputStream out) {
		// Verify Protocol
		byte[] protocol = ByteBuffer.allocate(4).putInt(1).array();
		try {
			out.write(protocol);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send protocol confirmation.");
			e.printStackTrace();
			return false;
		}
		byte[] verifyProtocol = new byte[4];
		try {
			in.read(verifyProtocol);
		} catch (IOException e2) {
			System.err.println("Unable to receive protocol confirmation.");
			e2.printStackTrace();
		}
		if (ByteBuffer.wrap(verifyProtocol).getInt() != 1) {
			System.err.println("Protocol mismatch.");
			return false;
		}
		// Send client RSA public key and client Nonce to server
		byte[] clientNonce = new byte[4];
		byte[] serverNonce = new byte[4];
		random.nextBytes(clientNonce);
		byte[] byteArray = k.getRSAPubKey().getEncoded();
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
			byteArray = MsgHandler.createNetworkMsg(clientNonce);
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send client public key/nonce.");
			e.printStackTrace();
			return false;
		}

		// Acquire server RSA public key and server Nonce from server
		PublicKey serverPubKey = null;
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
			serverNonce = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire server public key.");
			e.printStackTrace();
			return false;
		}
		try {
			serverPubKey = k.PublicKeyFromByteCode(byteArray);
		} catch (Exception e) {
			System.err.println("Unable to decode server public key.");
			e.printStackTrace();
			return false;
		}

		// Encrypt client password + server nonce using server RSA public key
		byteArray = clientPassword.getBytes();
		ByteBuffer byteBuffer = ByteBuffer.allocate(byteArray.length
				+ serverNonce.length);
		byteBuffer.put(byteArray);
		byteBuffer.put(serverNonce);
		try {
			byteArray = s.encrypt(byteBuffer.array(), serverPubKey, "RSA");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}
		byteBuffer.clear();

		// Split encrypted client password + server nonce
		byteBuffer = ByteBuffer.wrap(byteArray);
		byte[][] clientSplitByteMsg = new byte[2][];
		clientSplitByteMsg[0] = new byte[byteBuffer.capacity() / 2];
		clientSplitByteMsg[1] = new byte[byteBuffer.capacity()
				- byteBuffer.capacity() / 2];
		byteBuffer.get(clientSplitByteMsg[0]);
		byteBuffer.get(clientSplitByteMsg[1]);
		byteBuffer.clear();

		// Send first half of encrypted client password + server nonce to server
		byteArray = MsgHandler.createNetworkMsg(clientSplitByteMsg[0]);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send first half to server.");
			e.printStackTrace();
			return false;
		}

		// Receive first half of ciphertext from server
		byte[][] serverSplitByteMsg = new byte[2][];
		try {
			serverSplitByteMsg[0] = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire first half from server.");
			e.printStackTrace();
			return false;
		}

		// Send second half of encrypted client password + server nonce to
		// server
		byteArray = MsgHandler.createNetworkMsg(clientSplitByteMsg[1]);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send second half to server.");
			e.printStackTrace();
			return false;
		}

		// Receive second half of ciphertext from server
		try {
			serverSplitByteMsg[1] = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire second half from server.");
			e.printStackTrace();
			return false;
		}

		// Concentate server ciphertext
		byteBuffer = ByteBuffer.allocate(serverSplitByteMsg[0].length
				+ serverSplitByteMsg[1].length);
		byteBuffer.put(serverSplitByteMsg[0]);
		byteBuffer.put(serverSplitByteMsg[1]);
		byteArray = byteBuffer.array();
		byteBuffer.clear();

		// Decrypt server ciphertext using server RSA private key
		try {
			byteArray = s.decrypt(byteArray, k.getRSAPrivKey(), "RSA");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}

		// Verify server password and client nonce
		byteBuffer = ByteBuffer.wrap(byteArray);
		byteArray = new byte[byteBuffer.capacity() - 4];
		byteBuffer.get(byteArray);
		String verifyPW = null;
		try {
			verifyPW = new String(byteArray, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.err.println("UTF-8 format unsupported.");
			e.printStackTrace();
			return false;
		}
		int verifyNonce = byteBuffer.getInt();
		byteBuffer.clear();
		if (verifyNonce == ByteBuffer.wrap(clientNonce).getInt()
				&& verifyPW.contentEquals(serverPassword)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * The 2nd protocol.
	 * 
	 * @param in
	 *            input stream to receive the encrypted messages from server
	 * @param out
	 *            output stream to send the encrypted messages to server
	 * @return true if authentication success
	 */
	public boolean T3(InputStream in, OutputStream out) {
		// Verify Protocol
		byte[] protocol = ByteBuffer.allocate(4).putInt(2).array();
		try {
			out.write(protocol);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send protocol confirmation.");
			e.printStackTrace();
			return false;
		}
		byte[] verifyProtocol = new byte[4];
		try {
			in.read(verifyProtocol);
		} catch (IOException e2) {
			System.err.println("Unable to receive protocol confirmation.");
			e2.printStackTrace();
		}
		if (ByteBuffer.wrap(verifyProtocol).getInt() != 2) {
			System.err.println("Protocol mismatch.");
			return false;
		}
		// Send client RSA public key and client Nonce to server
		byte[] clientNonce = new byte[4];
		byte[] serverNonce = new byte[4];
		random.nextBytes(clientNonce);
		byte[] byteArray = k.getRSAPubKey().getEncoded();
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
			byteArray = MsgHandler.createNetworkMsg(clientNonce);
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send client public key/nonce.");
			e.printStackTrace();
			return false;
		}

		// Acquire server RSA public key and server Nonce from server
		PublicKey serverPubKey = null;
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
			serverNonce = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire server public key.");
			e.printStackTrace();
			return false;
		}
		try {
			serverPubKey = k.PublicKeyFromByteCode(byteArray);
		} catch (Exception e) {
			System.err.println("Unable to decode server public key.");
			e.printStackTrace();
			return false;
		}

		// Encrypt client password + server nonce using server RSA public key
		byteArray = clientPassword.getBytes();
		ByteBuffer byteBuffer = ByteBuffer.allocate(byteArray.length
				+ serverNonce.length);
		byteBuffer.put(byteArray);
		byteBuffer.put(serverNonce);
		try {
			byteArray = s.encrypt(byteBuffer.array(), serverPubKey, "RSA");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}
		byteBuffer.clear();

		// Split encrypted client password + server nonce
		byteBuffer = ByteBuffer.wrap(byteArray);
		byte[][] clientSplitByteMsg = new byte[2][];
		clientSplitByteMsg[0] = new byte[byteBuffer.capacity() / 2];
		clientSplitByteMsg[1] = new byte[byteBuffer.capacity()
				- byteBuffer.capacity() / 2];
		byteBuffer.get(clientSplitByteMsg[0]);
		byteBuffer.get(clientSplitByteMsg[1]);
		byteBuffer.clear();

		// Send first half of encrypted client password + server nonce to server
		byteArray = MsgHandler.createNetworkMsg(clientSplitByteMsg[0]);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send first half to server.");
			e.printStackTrace();
			return false;
		}

		// Receive first half of ciphertext from server
		byte[][] serverSplitByteMsg = new byte[2][];
		try {
			serverSplitByteMsg[0] = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire first half from server.");
			e.printStackTrace();
			return false;
		}

		// Send second half of encrypted client password + server nonce to
		// server
		byteArray = MsgHandler.createNetworkMsg(clientSplitByteMsg[1]);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send second half to server.");
			e.printStackTrace();
			return false;
		}

		// Receive second half of ciphertext from server
		try {
			serverSplitByteMsg[1] = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire second half from server.");
			e.printStackTrace();
			return false;
		}

		// Concentate server ciphertext
		byteBuffer = ByteBuffer.allocate(serverSplitByteMsg[0].length
				+ serverSplitByteMsg[1].length);
		byteBuffer.put(serverSplitByteMsg[0]);
		byteBuffer.put(serverSplitByteMsg[1]);
		byteArray = byteBuffer.array();
		byteBuffer.clear();

		// Decrypt server ciphertext using server RSA private key
		try {
			byteArray = s.decrypt(byteArray, k.getRSAPrivKey(), "RSA");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}

		// Acquire symmetric key
		byteBuffer = ByteBuffer.wrap(byteArray);
		int keySize = byteBuffer.getInt();
		if (keySize > byteBuffer.remaining()) {
			System.err.println("Unable to decode DES key.");
			return false;
		}
		byteArray = new byte[keySize];
		byteBuffer.get(byteArray);
		try {
			k.setDESKey(k.DESKeyFromByteCode(byteArray));
		} catch (Exception e1) {
			System.err.println("Unable to decode DES key.");
			e1.printStackTrace();
		}

		// Verify server password and client nonce
		byteArray = new byte[byteBuffer.remaining() - 4];
		byteBuffer.get(byteArray);
		String verifyPW = null;
		try {
			verifyPW = new String(byteArray, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.err.println("UTF-8 format unsupported.");
			e.printStackTrace();
			return false;
		}
		int verifyNonce = byteBuffer.getInt();
		byteBuffer.clear();
		if (verifyNonce == ByteBuffer.wrap(clientNonce).getInt()
				&& verifyPW.contentEquals(serverPassword)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * The 3rd protocol.
	 * 
	 * @param in
	 *            input stream to receive the encrypted messages from server
	 * @param out
	 *            output stream to send the encrypted messages to server
	 * @return true if authentication success
	 */
	public boolean T4(InputStream in, OutputStream out) {
		// Verify Protocol
		byte[] protocol = ByteBuffer.allocate(4).putInt(3).array();
		try {
			out.write(protocol);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send protocol confirmation.");
			e.printStackTrace();
			return false;
		}
		byte[] verifyProtocol = new byte[4];
		try {
			in.read(verifyProtocol);
		} catch (IOException e2) {
			System.err.println("Unable to receive protocol confirmation.");
			e2.printStackTrace();
		}
		if (ByteBuffer.wrap(verifyProtocol).getInt() != 3) {
			System.err.println("Protocol mismatch.");
			return false;
		}
		// Send client RSA public key and client Nonce to server
		byte[] clientNonce = new byte[4];
		byte[] serverNonce = new byte[4];
		random.nextBytes(clientNonce);
		byte[] byteArray = k.getRSAPubKey().getEncoded();
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
			byteArray = MsgHandler.createNetworkMsg(clientNonce);
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send client public key/nonce.");
			e.printStackTrace();
			return false;
		}

		// Acquire server RSA public key and server Nonce from server
		PublicKey serverPubKey = null;
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
			serverNonce = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire server public key.");
			e.printStackTrace();
			return false;
		}
		try {
			serverPubKey = k.PublicKeyFromByteCode(byteArray);
		} catch (Exception e) {
			System.err.println("Unable to decode server public key.");
			e.printStackTrace();
			return false;
		}

		// Encrypt client password + server nonce using server RSA public key
		byteArray = clientPassword.getBytes();
		ByteBuffer byteBuffer = ByteBuffer.allocate(byteArray.length
				+ serverNonce.length);
		byteBuffer.put(byteArray);
		byteBuffer.put(serverNonce);
		byte[] clientCiphertext;
		try {
			clientCiphertext = s.encrypt(byteBuffer.array(), serverPubKey,
					"RSA");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}
		byteBuffer.clear();

		// Create client MD5 digest MD5 digest function
		byte[] clientMD5 = s.MD5Digest(clientCiphertext);

		// Send client MD5 digest to server
		byteArray = MsgHandler.createNetworkMsg(clientMD5);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send client MD5 digest to server.");
			e.printStackTrace();
			return false;
		}

		// Receive server MD5 digest from server
		byte[] serverMD5 = null;
		try {
			serverMD5 = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err
					.println("Unable to acquire server MD5 digest from server.");
			e.printStackTrace();
			return false;
		}

		// Send client ciphertext to server
		byteArray = MsgHandler.createNetworkMsg(clientCiphertext);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send client ciphertext to server.");
			e.printStackTrace();
			return false;
		}

		// Receive server ciphertext from server
		byte[] serverCiphertext = null;
		try {
			serverCiphertext = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err
					.println("Unable to acquire server ciphertext from server.");
			e.printStackTrace();
			return false;
		}

		// Create verification MD5 digest using MD5 digest function
		byte[] verificationMD5 = s.MD5Digest(serverCiphertext);

		// Verify MD5 digest
		try {
			if (!(new String(serverMD5, "UTF-8")).contentEquals(new String(
					verificationMD5, "UTF-8"))) {
				return false;
			}
		} catch (UnsupportedEncodingException e1) {
			System.err.println("UTF-8 format unsupported.");
			e1.printStackTrace();
			return false;
		}

		// Decrypt server ciphertext using server RSA private key
		try {
			byteArray = s.decrypt(serverCiphertext, k.getRSAPrivKey(), "RSA");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}

		// Acquire symmetric key
		byteBuffer = ByteBuffer.wrap(byteArray);
		int keySize = byteBuffer.getInt();
		if (keySize > byteBuffer.remaining()) {
			System.err.println("Unable to decode DES key.");
			return false;
		}
		byteArray = new byte[keySize];
		byteBuffer.get(byteArray);
		try {
			k.setDESKey(k.DESKeyFromByteCode(byteArray));
		} catch (Exception e1) {
			System.err.println("Unable to decode DES key.");
			e1.printStackTrace();
		}

		// Verify server password and client nonce
		byteArray = new byte[byteBuffer.remaining() - 4];
		byteBuffer.get(byteArray);
		String verifyPW = null;
		try {
			verifyPW = new String(byteArray, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.err.println("UTF-8 format unsupported.");
			e.printStackTrace();
			return false;
		}
		int verifyNonce = byteBuffer.getInt();
		byteBuffer.clear();
		if (verifyNonce == ByteBuffer.wrap(clientNonce).getInt()
				&& verifyPW.contentEquals(serverPassword)) {
			return true;
		} else {
			return false;
		}

	}

	/**
	 * The 4th protocol.
	 * 
	 * @param in
	 *            input stream to receive the encrypted messages from server
	 * @param out
	 *            output stream to send the encrypted messages to server
	 * @return true if authentication success
	 */
	public boolean T5(InputStream in, OutputStream out) {
		// Verify Protocol
		byte[] protocol = ByteBuffer.allocate(4).putInt(4).array();
		try {
			out.write(protocol);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send protocol confirmation.");
			e.printStackTrace();
			return false;
		}
		byte[] verifyProtocol = new byte[4];
		try {
			in.read(verifyProtocol);
		} catch (IOException e2) {
			System.err.println("Unable to receive protocol confirmation.");
			e2.printStackTrace();
		}
		if (ByteBuffer.wrap(verifyProtocol).getInt() != 4) {
			System.err.println("Protocol mismatch.");
			return false;
		}
		// Send client RSA key to server
		byte[] byteArray = k.getRSAPubKey().getEncoded();
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send client public key.");
			e.printStackTrace();
			return false;
		}

		// Acquire server RSA key from server
		PublicKey serverPubKey = null;
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire server public key.");
			e.printStackTrace();
			return false;
		}
		try {
			serverPubKey = k.PublicKeyFromByteCode(byteArray);
		} catch (Exception e) {
			System.err.println("Unable to decode server public key.");
			e.printStackTrace();
			return false;
		}

		// --------------------------------------------------------------------
		// -Base Condition Established, P and G owns each other's RSA a priori-
		// --------------------------------------------------------------------

		// Receive encrypted symmetric key from server
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e2) {
			System.err.println("Unable acquire encrypted key");
			e2.printStackTrace();
			return false;
		}

		// Decrypt symmetric key
		try {
			byteArray = s.decrypt(byteArray, k.getRSAPrivKey(), "RSA");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}
		try {
			k.setDESKey(k.DESKeyFromByteCode(byteArray));
		} catch (Exception e1) {
			System.err.println("Unable to decode DES key.");
			e1.printStackTrace();
		}

		// Receive doubly encrypted nonce from server
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e1) {
			System.err.println("Unable acquire encrypted nonce");
			e1.printStackTrace();
			return false;
		}

		// Decrypt second encryption layer using symmetric key
		try {
			byteArray = s.decrypt(byteArray, k.getDESKey(), "DES");

			// Decrypt first encryption layer using server public key
			byteArray = s.decrypt(byteArray, serverPubKey, "RSA");

			// Encrypt nonce using symmetric key
			byteArray = s.encrypt(byteArray, k.getDESKey(), "DES");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}

		// Send encrypted nonce to server
		try {
			out.write(MsgHandler.createNetworkMsg(byteArray));
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send encrypted nonce");
			e.printStackTrace();
			return false;
		}

		// Receive verification status
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable acquire verification status");
			e.printStackTrace();
			return false;
		}

		// Decrypt verification status
		try {
			byteArray = s.decrypt(byteArray, k.getDESKey(), "DES");
		} catch (InvalidKeyException e1) {
			System.err.println("Wrong key used.");
			e1.printStackTrace();
			return false;
		} catch (IllegalArgumentException e1) {
			System.err.println("Wrong format specified.");
			e1.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e1) {
			System.err.println("Plaintext is too long.");
			e1.printStackTrace();
			return false;
		} catch (BadPaddingException e1) {
			System.err.println("Wrong padding used.");
			e1.printStackTrace();
			return false;
		}
		String verification = null;
		try {
			verification = new String(byteArray, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.err.println("UTF-8 format unsupported");
			e.printStackTrace();
			return false;
		}

		// Return verification status
		if (verification.contentEquals("Verified")) {
			return true;
		} else {
			return false;
		}
	}
}
