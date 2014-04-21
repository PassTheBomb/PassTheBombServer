

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

/**
 * Class containing the set of 4 protocols to be implemented
 * 
 */
public class ServerAuthentication {
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
		k.generateDESKey();

		int port = 8888;
		ServerSocket ssock = new ServerSocket(port);
		Socket client = ssock.accept();
		InputStream in = client.getInputStream();
		OutputStream out = client.getOutputStream();

		ServerAuthentication sa = new ServerAuthentication(s, k);
		/*
		 * System.out.println(sa.T2(in, out));
		 * 
		 * System.out.println(sa.T3(in, out)); System.out.println((new
		 * String(s.decrypt( MsgHandler.acquireNetworkMsg(in), k.getDESKey(),
		 * "DES"), "UTF-8")).contentEquals("testing1"));
		 * 
		 * System.out.println(sa.T4(in, out)); System.out.println((new
		 * String(s.decrypt( MsgHandler.acquireNetworkMsg(in), k.getDESKey(),
		 * "DES"), "UTF-8")).contentEquals("testing2"));
		 * 
		 * System.out.println(sa.T5(in, out)); System.out.println((new
		 * String(s.decrypt( MsgHandler.acquireNetworkMsg(in), k.getDESKey(),
		 * "DES"), "UTF-8")).contentEquals("testing3"));
		 */

		System.out.println(sa.T5(in, out));

		ssock.close();
	}

	// Test code end

	/**
	 * Creates a class containing the set of security protocols to be
	 * implemented on the server side. Requires a Security class and a Keys
	 * class as an input, and both must already be instantiated and set up.
	 * 
	 * @param s
	 *            the instantiated Security class
	 * @param k
	 *            the instantiated Keys class with RSA keypairs and DES key
	 *            generated
	 */
	ServerAuthentication(Security s, Keys k) {
		this.s = s;
		this.k = k;
	}
	
	public boolean NOPROTOCOL(InputStream in, OutputStream out) {
		// Verify Protocol
		byte[] protocol = ByteBuffer.allocate(4).putInt(0).array();
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
		if (ByteBuffer.wrap(verifyProtocol).getInt() != 0) {
			System.err.println("Protocol mismatch.");
			return false;
		}
		return true;
	}
	/**
	 * The 1st protocol.
	 * 
	 * @param in
	 *            input stream to receive the encrypted messages from client
	 * @param out
	 *            output stream to send the encrypted messages to client
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

		// Send server RSA public key and server Nonce to client
		byte[] clientNonce = new byte[4];
		byte[] serverNonce = new byte[4];
		random.nextBytes(serverNonce);
		byte[] byteArray = k.getRSAPubKey().getEncoded();
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
			byteArray = MsgHandler.createNetworkMsg(serverNonce);
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send server public key/nonce.");
			e.printStackTrace();
			return false;
		}

		// Acquire client RSA public key and client Nonce from client
		PublicKey clientPubKey = null;
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
			clientNonce = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire client public key.");
			e.printStackTrace();
			return false;
		}
		try {
			clientPubKey = k.PublicKeyFromByteCode(byteArray);
		} catch (Exception e) {
			System.err.println("Unable to decode client public key.");
			e.printStackTrace();
			return false;
		}

		// Encrypt server password + client nonce using server RSA public key
		byteArray = serverPassword.getBytes();
		ByteBuffer byteBuffer = ByteBuffer.allocate(byteArray.length
				+ clientNonce.length);
		byteBuffer.put(byteArray);
		byteBuffer.put(clientNonce);
		try {
			byteArray = s.encrypt(byteBuffer.array(), clientPubKey, "RSA");
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

		// Split encrypted server password + client nonce
		byteBuffer = ByteBuffer.wrap(byteArray);
		byte[][] serverSplitByteMsg = new byte[2][];
		serverSplitByteMsg[0] = new byte[byteBuffer.capacity() / 2];
		serverSplitByteMsg[1] = new byte[byteBuffer.capacity()
				- byteBuffer.capacity() / 2];
		byteBuffer.get(serverSplitByteMsg[0]);
		byteBuffer.get(serverSplitByteMsg[1]);
		byteBuffer.clear();

		// Send first half of encrypted server password + client nonce to server
		byteArray = MsgHandler.createNetworkMsg(serverSplitByteMsg[0]);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send first half to client.");
			e.printStackTrace();
			return false;
		}

		// Receive first half of ciphertext from client
		byte[][] clientSplitByteMsg = new byte[2][];
		try {
			clientSplitByteMsg[0] = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire first half from client.");
			e.printStackTrace();
			return false;
		}

		// Receive second half of ciphertext from client
		try {
			clientSplitByteMsg[1] = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire second half from client.");
			e.printStackTrace();
			return false;
		}

		// Concentate client ciphertext
		byteBuffer = ByteBuffer.allocate(clientSplitByteMsg[0].length
				+ clientSplitByteMsg[1].length);
		byteBuffer.put(clientSplitByteMsg[0]);
		byteBuffer.put(clientSplitByteMsg[1]);
		byteArray = byteBuffer.array();
		byteBuffer.clear();

		// Decrypt client ciphertext using server RSA private key
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

		// Verify client password and server nonce
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
		if (!(verifyNonce == ByteBuffer.wrap(serverNonce).getInt() && verifyPW
				.contentEquals(clientPassword))) {
			return false;
		}

		// Send second half of encrypted server password + client nonce to
		// server
		byteArray = MsgHandler.createNetworkMsg(serverSplitByteMsg[1]);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send second half to client.");
			e.printStackTrace();
			return false;
		}

		return true;
	}

	/**
	 * The 2nd protocol.
	 * 
	 * @param in
	 *            input stream to receive the encrypted messages from client
	 * @param out
	 *            output stream to send the encrypted messages to client
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
		// Send server RSA public key and server Nonce to client
		byte[] clientNonce = new byte[4];
		byte[] serverNonce = new byte[4];
		random.nextBytes(serverNonce);
		byte[] byteArray = k.getRSAPubKey().getEncoded();
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
			byteArray = MsgHandler.createNetworkMsg(serverNonce);
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send server public key/nonce.");
			e.printStackTrace();
			return false;
		}

		// Acquire client RSA public key and client Nonce from client
		PublicKey clientPubKey = null;
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
			clientNonce = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire client public key.");
			e.printStackTrace();
			return false;
		}
		try {
			clientPubKey = k.PublicKeyFromByteCode(byteArray);
		} catch (Exception e) {
			System.err.println("Unable to decode client public key.");
			e.printStackTrace();
			return false;
		}

		// Encrypt server password + DES key + client nonce using server RSA
		// public key
		byteArray = serverPassword.getBytes();
		ByteBuffer byteBuffer = ByteBuffer.allocate(4
				+ k.getDESKey().getEncoded().length + byteArray.length
				+ clientNonce.length);
		byteBuffer.putInt(k.getDESKey().getEncoded().length);
		byteBuffer.put(k.getDESKey().getEncoded());
		byteBuffer.put(byteArray);
		byteBuffer.put(clientNonce);
		try {
			byteArray = s.encrypt(byteBuffer.array(), clientPubKey, "RSA");
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

		// Split encrypted server password + client nonce
		byteBuffer = ByteBuffer.wrap(byteArray);
		byte[][] serverSplitByteMsg = new byte[2][];
		serverSplitByteMsg[0] = new byte[byteBuffer.capacity() / 2];
		serverSplitByteMsg[1] = new byte[byteBuffer.capacity()
				- byteBuffer.capacity() / 2];
		byteBuffer.get(serverSplitByteMsg[0]);
		byteBuffer.get(serverSplitByteMsg[1]);
		byteBuffer.clear();

		// Send first half of encrypted server password + client nonce to server
		byteArray = MsgHandler.createNetworkMsg(serverSplitByteMsg[0]);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send first half to client.");
			e.printStackTrace();
			return false;
		}

		// Receive first half of ciphertext from client
		byte[][] clientSplitByteMsg = new byte[2][];
		try {
			clientSplitByteMsg[0] = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire first half from client.");
			e.printStackTrace();
			return false;
		}

		// Receive second half of ciphertext from client
		try {
			clientSplitByteMsg[1] = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire second half from client.");
			e.printStackTrace();
			return false;
		}

		// Concentate client ciphertext
		byteBuffer = ByteBuffer.allocate(clientSplitByteMsg[0].length
				+ clientSplitByteMsg[1].length);
		byteBuffer.put(clientSplitByteMsg[0]);
		byteBuffer.put(clientSplitByteMsg[1]);
		byteArray = byteBuffer.array();
		byteBuffer.clear();

		// Decrypt client ciphertext using server RSA private key
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

		// Verify client password and server nonce
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
		if (!(verifyNonce == ByteBuffer.wrap(serverNonce).getInt() && verifyPW
				.contentEquals(clientPassword))) {
			return false;
		}

		// Send second half of encrypted server password + client nonce to
		// server
		byteArray = MsgHandler.createNetworkMsg(serverSplitByteMsg[1]);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send second half to client.");
			e.printStackTrace();
			return false;
		}

		return true;

	}

	/**
	 * The 3rd protocol.
	 * 
	 * @param in
	 *            input stream to receive the encrypted messages from client
	 * @param out
	 *            output stream to send the encrypted messages to client
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
		// Send server RSA public key and server Nonce to client
		byte[] clientNonce = new byte[4];
		byte[] serverNonce = new byte[4];
		random.nextBytes(serverNonce);
		byte[] byteArray = k.getRSAPubKey().getEncoded();
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
			byteArray = MsgHandler.createNetworkMsg(serverNonce);
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send server public key/nonce.");
			e.printStackTrace();
			return false;
		}

		// Acquire client RSA public key and client Nonce from client
		PublicKey clientPubKey = null;
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
			clientNonce = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire client public key.");
			e.printStackTrace();
			return false;
		}
		try {
			clientPubKey = k.PublicKeyFromByteCode(byteArray);
		} catch (Exception e) {
			System.err.println("Unable to decode client public key.");
			e.printStackTrace();
			return false;
		}

		// Encrypt server password + DES key + client nonce using server RSA
		// public key
		byteArray = serverPassword.getBytes();
		ByteBuffer byteBuffer = ByteBuffer.allocate(4
				+ k.getDESKey().getEncoded().length + byteArray.length
				+ clientNonce.length);
		byteBuffer.putInt(k.getDESKey().getEncoded().length);
		byteBuffer.put(k.getDESKey().getEncoded());
		byteBuffer.put(byteArray);
		byteBuffer.put(clientNonce);
		byte[] serverCiphertext;
		try {
			serverCiphertext = s.encrypt(byteBuffer.array(), clientPubKey,
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

		// Create server MD5 digest using MD5 digest function
		byte[] serverMD5 = s.MD5Digest(serverCiphertext);

		// Send server MD5 digest to client
		byteArray = MsgHandler.createNetworkMsg(serverMD5);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send server MD5 digest to client.");
			e.printStackTrace();
			return false;
		}

		// Receive client MD5 digest from client
		byte[] clientMD5 = null;
		try {
			clientMD5 = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err
					.println("Unable to acquire client MD5 digest from client.");
			e.printStackTrace();
			return false;
		}

		// Receive ciphertext from client
		byte[] clientCiphertext = null;
		try {
			clientCiphertext = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err
					.println("Unable to acquire client ciphertext from client.");
			e.printStackTrace();
			return false;
		}

		// Create verification MD5 digest using MD5 digest function
		byte[] verificationMD5 = s.MD5Digest(clientCiphertext);

		// Verify MD5 digest
		try {
			if (!(new String(clientMD5, "UTF-8")).contentEquals(new String(
					verificationMD5, "UTF-8"))) {
				return false;
			}
		} catch (UnsupportedEncodingException e1) {
			System.err.println("UTF-8 format unsupported.");
			e1.printStackTrace();
			return false;
		}

		// Decrypt client ciphertext using server RSA private key
		try {
			byteArray = s.decrypt(clientCiphertext, k.getRSAPrivKey(), "RSA");
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

		// Verify client password and server nonce
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
		if (!(verifyNonce == ByteBuffer.wrap(serverNonce).getInt() && verifyPW
				.contentEquals(clientPassword))) {
			return false;
		}

		// Send server ciphertext to client
		byteArray = MsgHandler.createNetworkMsg(serverCiphertext);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send server ciphertext to client.");
			e.printStackTrace();
			return false;
		}

		return true;
	}

	/**
	 * The 4th protocol.
	 * 
	 * @param in
	 *            input stream to receive the encrypted messages from client
	 * @param out
	 *            output stream to send the encrypted messages to client
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

		// Send RSA pubkey to client
		byte[] byteArray = k.getRSAPubKey().getEncoded();
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send server public key.");
			e.printStackTrace();
			return false;
		}

		// Acquire RSA pubkey from client
		PublicKey clientPubKey = null;
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire client public key.");
			e.printStackTrace();
			return false;
		}
		try {
			clientPubKey = k.PublicKeyFromByteCode(byteArray);
		} catch (Exception e) {
			System.err.println("Unable to decode client public key.");
			e.printStackTrace();
			return false;
		}

		// --------------------------------------------------------------------
		// -Base Condition Established, P and G owns each other's RSA a priori-
		// --------------------------------------------------------------------

		// Encrypt symmetric key using client public key
		byte[] keyCipher;
		try {
			keyCipher = s.encrypt(k.getDESKey().getEncoded(), clientPubKey,
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

		// Send encrypted symmetric key to client
		try {
			out.write(MsgHandler.createNetworkMsg(keyCipher));
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send doubly encrypted nonce.");
			e.printStackTrace();
			return false;
		}

		// Encrypt nonce using server private key
		byte[] nonce = new byte[4];
		random.nextBytes(nonce);
		byte[] nonceCipher;
		try {
			nonceCipher = s.encrypt(nonce, k.getRSAPrivKey(), "RSA");
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

		// Double encrypt nonce using symmetric key
		try {
			nonceCipher = s.encrypt(nonceCipher, k.getDESKey(), "DES");
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

		// Send doubly encrypted nonce to client
		try {
			out.write(MsgHandler.createNetworkMsg(nonceCipher));
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send doubly encrypted nonce.");
			e.printStackTrace();
			return false;
		}

		// Acquire reply from client
		try {
			byteArray = MsgHandler.acquireNetworkMsg(in);
		} catch (IOException e) {
			System.err.println("Unable to acquire client reply.");
			e.printStackTrace();
			return false;
		}

		// Decrypt reply
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
		if (!(ByteBuffer.wrap(byteArray).getInt() == ByteBuffer.wrap(nonce)
				.getInt())) {
			return false;
		}

		// Encrypt verification status using symmetric key
		try {
			byteArray = s.encrypt("Verified".getBytes(), k.getDESKey(), "DES");
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

		// Send verification status
		byteArray = MsgHandler.createNetworkMsg(byteArray);
		try {
			out.write(byteArray);
			out.flush();
		} catch (IOException e) {
			System.err.println("Unable to send verification status.");
			e.printStackTrace();
			return false;
		}

		return true;
	}
}
